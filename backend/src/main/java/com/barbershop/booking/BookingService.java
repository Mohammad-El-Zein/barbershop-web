package com.barbershop.booking;

import com.barbershop.availability.AvailabilityCalculator.Slot;
import com.barbershop.availability.AvailabilityService;
import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.CreateBookingRequest;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.booking.BookingDtos.ServiceRef;
import com.barbershop.booking.BookingDtos.StaffRef;
import com.barbershop.booking.BookingEvents.BookingCancelled;
import com.barbershop.booking.BookingEvents.BookingConfirmed;
import com.barbershop.booking.BookingEvents.BookingCreated;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.service.BarberService;
import com.barbershop.service.PriceFormatter;
import com.barbershop.service.ServiceCatalog;
import com.barbershop.staff.Staff;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creating, viewing and cancelling bookings.
 *
 * <p>Double bookings are prevented in three layers:
 * <ol>
 *   <li>schedule check — the start must be a real slot (working hours, time off,
 *       notice); otherwise 422 {@code booking.slot_invalid},</li>
 *   <li>overlap check against existing bookings — 409 {@code booking.slot_taken},</li>
 *   <li>the database exclusion constraint {@code booking_no_overlap}, which also
 *       catches concurrent requests that passed both checks at the same moment
 *       — mapped to 409 as well.</li>
 * </ol>
 * Each insert attempt runs in its own transaction: a PostgreSQL constraint
 * violation aborts the transaction it happens in, and for "any barber" the next
 * free barber is tried.
 */
@Service
@Transactional(readOnly = true)
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final Set<String> ADMIN_SORTABLE = Set.of("startTime", "createdAt", "customerName", "status");

    private final BookingRepository bookings;
    private final TenantService tenants;
    private final ServiceCatalog catalog;
    private final AvailabilityService availability;
    private final PriceFormatter prices;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate writeTx;
    private final AppProperties.Booking config;
    private final Clock clock;

    public BookingService(BookingRepository bookings, TenantService tenants, ServiceCatalog catalog,
                          AvailabilityService availability, PriceFormatter prices, ApplicationEventPublisher events,
                          PlatformTransactionManager txManager, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.catalog = catalog;
        this.availability = availability;
        this.prices = prices;
        this.events = events;
        this.writeTx = new TransactionTemplate(txManager);
        this.config = props.booking();
        this.clock = clock;
    }

    // ── create ───────────────────────────────────────────────────────────────

    /** Runs outside a surrounding transaction on purpose: every insert attempt gets its own. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingView create(String tenantSlug, CreateBookingRequest req, Locale requestLocale) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        ZoneId zone = tenant.zone();
        BarberService service = catalog.requireActive(tenant.getId(), req.serviceId());
        List<Staff> candidates = availability.candidates(tenant.getId(), service.getId(), req.staffId());

        Instant start = req.start().toInstant();
        Instant end = start.plus(service.duration());
        LocalDate day = start.atZone(zone).toLocalDate();
        availability.requireBookableDate(tenant, day);

        // 1) schedule: is this start time a slot for anyone (ignoring existing bookings)?
        Slot slot = availability.slots(tenant, service, candidates, day, day, true).stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .orElseThrow(() -> ApiException.unprocessable("booking.slot_invalid"));
        List<Staff> working = candidates.stream().filter(s -> slot.staffIds().contains(s.getId())).toList();

        // 2) who is still free? For "any barber": the least busy that day first.
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<Staff> free = new ArrayList<>(working.stream()
                .filter(s -> !bookings.existsActiveOverlap(s.getId(), start, end))
                .toList());
        free.sort(Comparator.comparingLong(s -> bookings.countActiveForStaff(s.getId(), dayStart, dayEnd)));
        if (free.isEmpty()) throw ApiException.conflict("booking.slot_taken");

        Lang lang = req.lang() != null ? Lang.parse(req.lang()) : Lang.of(requestLocale);
        BookingStatus status = config.autoConfirm() ? BookingStatus.CONFIRMED : BookingStatus.PENDING;

        // 3) insert; the exclusion constraint is the final word under concurrency
        for (Staff staff : free) {
            String rawToken = CancelTokens.generate();
            try {
                Booking saved = writeTx.execute(tx -> {
                    Booking b = new Booking(tenant.getId(), service, staff, start, end, req.customerName().trim(),
                            req.customerPhone().trim(), req.customerEmail().trim(), lang, status,
                            CancelTokens.hash(rawToken), blankToNull(req.notes()), Instant.now(clock));
                    bookings.saveAndFlush(b);
                    events.publishEvent(new BookingCreated(b.getId(), rawToken));
                    return b;
                });
                log.info("Booking {} created: tenant={} staff={} start={}", saved.getId(), tenant.getSlug(),
                        staff.getId(), start);
                return view(saved, tenant, Messages.currentLang(), rawToken);
            } catch (DataIntegrityViolationException e) {
                if (!isOverlap(e)) throw e;
                log.info("Slot {} for staff {} was taken concurrently, trying next candidate", start, staff.getId());
            }
        }
        throw ApiException.conflict("booking.slot_taken");
    }

    // ── customer: view / cancel via token ────────────────────────────────────

    public BookingView getByToken(String token) {
        Booking b = requireByToken(token);
        return view(b, tenants.require(b.getTenantId()), Messages.currentLang(), null);
    }

    /** Idempotent: cancelling an already cancelled booking returns it unchanged. */
    @Transactional
    public BookingView cancelByToken(String token) {
        Booking b = requireByToken(token);
        Tenant tenant = tenants.require(b.getTenantId());
        if (b.getStatus() != BookingStatus.CANCELLED) {
            Instant now = Instant.now(clock);
            if (!now.isBefore(b.getStartTime().minus(config.cancelCutoff()))) {
                throw ApiException.conflict("booking.cancel_too_late", config.cancelCutoff().toHours());
            }
            b.cancel(now);
            events.publishEvent(new BookingCancelled(b.getId(), true));
        }
        return view(b, tenant, Messages.currentLang(), null);
    }

    private Booking requireByToken(String token) {
        if (!CancelTokens.plausible(token)) throw ApiException.notFound("booking.not_found");
        return bookings.findByCancelTokenHash(CancelTokens.hash(token))
                .orElseThrow(() -> ApiException.notFound("booking.not_found"));
    }

    // ── admin ────────────────────────────────────────────────────────────────

    public PageView<BookingAdminView> adminList(UUID tenantId, LocalDate from, LocalDate to, UUID staffId,
                                                BookingStatus status, String query, int page, int size, String sort) {
        Tenant tenant = tenants.require(tenantId);
        ZoneId zone = tenant.zone();
        Specification<Booking> spec = (root, q, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (from != null) {
            Instant f = from.atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), f));
        }
        if (to != null) {
            Instant t = to.plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("startTime"), t));
        }
        if (staffId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("staff").get("id"), staffId));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("customerName")), like),
                    cb.like(cb.lower(root.get("customerEmail")), like),
                    cb.like(root.get("customerPhone"), like)));
        }
        String sortField = sort != null && ADMIN_SORTABLE.contains(sort.replaceFirst("^-", "")) ? sort : "startTime";
        Sort order = sortField.startsWith("-") ? Sort.by(sortField.substring(1)).descending() : Sort.by(sortField);
        int pageSize = Math.min(Math.max(size, 1), 200);
        Page<Booking> result = bookings.findAll(spec, PageRequest.of(Math.max(page, 0), pageSize, order));
        Lang lang = Messages.currentLang();
        return new PageView<>(result.getContent().stream().map(b -> adminView(b, zone, lang)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public BookingAdminView adminGet(UUID tenantId, UUID id) {
        Tenant tenant = tenants.require(tenantId);
        return adminView(requireForTenant(tenantId, id), tenant.zone(), Messages.currentLang());
    }

    /** Admins can cancel at any time (no cutoff); the customer gets a cancellation email. */
    @Transactional
    public BookingAdminView adminCancel(UUID tenantId, UUID id) {
        Tenant tenant = tenants.require(tenantId);
        Booking b = requireForTenant(tenantId, id);
        if (b.getStatus() != BookingStatus.CANCELLED) {
            b.cancel(Instant.now(clock));
            events.publishEvent(new BookingCancelled(b.getId(), false));
        }
        return adminView(b, tenant.zone(), Messages.currentLang());
    }

    @Transactional
    public BookingAdminView adminConfirm(UUID tenantId, UUID id) {
        Tenant tenant = tenants.require(tenantId);
        Booking b = requireForTenant(tenantId, id);
        if (b.getStatus() != BookingStatus.PENDING) throw ApiException.conflict("booking.not_pending");
        b.confirm();
        events.publishEvent(new BookingConfirmed(b.getId()));
        return adminView(b, tenant.zone(), Messages.currentLang());
    }

    private Booking requireForTenant(UUID tenantId, UUID id) {
        return bookings.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("booking.not_found"));
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    private BookingView view(Booking b, Tenant tenant, Lang lang, String rawToken) {
        ZoneId zone = tenant.zone();
        BarberService s = b.getService();
        return new BookingView(b.getId(), b.getStatus(),
                new ServiceRef(s.getId(), s.name(lang), s.getDurationMin()),
                new StaffRef(b.getStaff().getId(), b.getStaff().getName()),
                b.getStartTime().atZone(zone).toOffsetDateTime(), b.getEndTime().atZone(zone).toOffsetDateTime(),
                tenant.getTimezone(), b.getCustomerName(), b.getCustomerEmail(), b.getLocale(),
                prices.view(s.getPriceCents(), tenant.getCurrency(), s.isPriceFrom(), lang),
                b.getStartTime().minus(config.cancelCutoff()).atZone(zone).toOffsetDateTime(),
                rawToken);
    }

    private static BookingAdminView adminView(Booking b, ZoneId zone, Lang lang) {
        BarberService s = b.getService();
        return new BookingAdminView(b.getId(), b.getStatus(),
                new ServiceRef(s.getId(), s.name(lang), s.getDurationMin()),
                new StaffRef(b.getStaff().getId(), b.getStaff().getName()),
                b.getStartTime().atZone(zone).toOffsetDateTime(), b.getEndTime().atZone(zone).toOffsetDateTime(),
                b.getCustomerName(), b.getCustomerPhone(), b.getCustomerEmail(), b.getLocale(), b.getNotes(),
                b.getCreatedAt().atZone(zone).toOffsetDateTime(),
                b.getCancelledAt() == null ? null : b.getCancelledAt().atZone(zone).toOffsetDateTime());
    }

    /** True if the violation is the booking_no_overlap exclusion constraint (SQLSTATE 23P01). */
    static boolean isOverlap(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) return true;
            if (t.getMessage() != null && t.getMessage().contains("booking_no_overlap")) return true;
        }
        return false;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
