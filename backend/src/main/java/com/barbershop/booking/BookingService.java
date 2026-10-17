package com.barbershop.booking;

import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.auth.CurrentUser;
import com.barbershop.auth.Role;
import com.barbershop.availability.AvailabilityCalculator.Slot;
import com.barbershop.availability.AvailabilityService;
import com.barbershop.booking.BookingDtos.BookingAdminView;
import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.CreateBookingRequest;
import com.barbershop.booking.BookingDtos.LoyaltyInfo;
import com.barbershop.booking.BookingDtos.ManualBookingRequest;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.booking.BookingDtos.PricePreview;
import com.barbershop.booking.BookingDtos.ServiceLine;
import com.barbershop.booking.BookingEvents.Confirmed;
import com.barbershop.booking.BookingEvents.Requested;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.booking.BookingPricing.Breakdown;
import com.barbershop.booking.BookingPricing.Line;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.loyalty.LoyaltyService;
import com.barbershop.loyalty.LoyaltyService.LoyaltyView;
import com.barbershop.service.BarberService;
import com.barbershop.service.ServiceCatalog;
import com.barbershop.staff.Staff;
import com.barbershop.tenant.BookingPolicy;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Creating bookings (online requests and manual walk-in/phone bookings), the
 * price preview, and all read access (customer, barber, admin). Status changes
 * of existing bookings live in {@link BookingLifecycle}.
 *
 * <p>Double bookings are prevented in three layers:
 * <ol>
 *   <li>schedule check — the start must be a real slot (working hours, time off,
 *       lead time, horizon, grid); otherwise 422 {@code booking.slot_invalid},</li>
 *   <li>overlap check against slot-blocking bookings — 409 {@code booking.slot_taken},</li>
 *   <li>the database exclusion constraint {@code booking_no_overlap}, which also
 *       catches concurrent requests that passed both checks at the same moment
 *       — mapped to 409 as well.</li>
 * </ol>
 * Each insert attempt runs in its own transaction (a PostgreSQL constraint
 * violation aborts the transaction it happens in); for "any barber" the next
 * free barber is tried.
 *
 * <p>Transactions are declared per method (no class-level default), see {@link #create}.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final Set<String> ADMIN_SORTABLE = Set.of("startTime", "createdAt", "customerName", "status");
    /** Walk-ins may be entered right after they happened. */
    private static final Duration MANUAL_BACKDATE = Duration.ofHours(12);

    private final BookingRepository bookings;
    private final TenantService tenants;
    private final ServiceCatalog catalog;
    private final AvailabilityService availability;
    private final LoyaltyService loyalty;
    private final BookingLifecycle lifecycle;
    private final ActionTokenService tokens;
    private final AppUserRepository users;
    private final BookingViews views;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate writeTx;
    private final AppProperties props;
    private final Clock clock;

    public BookingService(BookingRepository bookings, TenantService tenants, ServiceCatalog catalog,
                          AvailabilityService availability, LoyaltyService loyalty, BookingLifecycle lifecycle,
                          ActionTokenService tokens, AppUserRepository users, BookingViews views,
                          ApplicationEventPublisher events, PlatformTransactionManager txManager, AppProperties props,
                          Clock clock) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.catalog = catalog;
        this.availability = availability;
        this.loyalty = loyalty;
        this.lifecycle = lifecycle;
        this.tokens = tokens;
        this.users = users;
        this.views = views;
        this.events = events;
        this.writeTx = new TransactionTemplate(txManager);
        this.props = props;
        this.clock = clock;
    }

    // ── price preview ────────────────────────────────────────────────────────

    /** Total, loyalty discount, deposit and amount due at the shop — before booking. */
    @Transactional(readOnly = true)
    public PricePreview pricePreview(String tenantSlug, List<UUID> serviceIds, CurrentUser user) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        BookingPolicy policy = tenant.policy();
        List<BarberService> services = catalog.requireActiveAll(tenant.getId(), serviceIds,
                props.booking().maxServices());
        Lang lang = Messages.currentLang();
        LoyaltyInfo loyaltyInfo = null;
        boolean applies = false;
        if (isCustomerOf(user, tenant)) {
            LoyaltyView lv = loyalty.view(user.userId(), policy);
            applies = lv.nextDiscountAvailable() && policy.getLoyaltyPercent() > 0;
            loyaltyInfo = new LoyaltyInfo(lv.visitsInCurrentCycle(), lv.threshold(), lv.discountPercent(),
                    lv.nextDiscountAvailable(), applies);
        }
        Breakdown price = BookingPricing.calculate(lines(services), policy.getDepositCents(), applies,
                policy.getLoyaltyPercent());
        List<ServiceLine> lines = services.stream()
                .map(s -> new ServiceLine(s.getId(), s.name(lang), s.getDurationMin(),
                        views.money(s.getPriceCents(), tenant.getCurrency(), lang), s.isPriceFrom()))
                .toList();
        return new PricePreview(lines, (int) AvailabilityService.totalDuration(services).toMinutes(),
                views.price(price, tenant.getCurrency(), lang), loyaltyInfo);
    }

    // ── create (online request) ──────────────────────────────────────────────

    /**
     * Deliberately NOT transactional (no annotation, and the class has none): every
     * insert attempt gets its own transaction, and the reads before it release their
     * connection right away. (NOT_SUPPORTED would still bind one EntityManager — and
     * its JDBC connection — for the whole method, so each request would hold two
     * connections and the pool could deadlock under a booking rush.)
     */
    public BookingView create(String tenantSlug, CreateBookingRequest req, CurrentUser user, Locale requestLocale) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        BookingPolicy policy = tenant.policy();
        ZoneId zone = tenant.zone();
        AppUser account = isCustomerOf(user, tenant)
                ? users.findById(user.userId()).orElseThrow(() -> ApiException.unauthorized("auth.unauthorized"))
                : null;
        Customer customer = customer(req, account, requestLocale);

        List<BarberService> services = catalog.requireActiveAll(tenant.getId(), req.serviceIds(),
                props.booking().maxServices());
        Duration duration = AvailabilityService.totalDuration(services);
        List<Staff> candidates = availability.candidates(tenant.getId(), req.serviceIds(), req.staffId());

        Instant start = req.start().toInstant();
        LocalDate day = start.atZone(zone).toLocalDate();
        availability.requireBookableDate(tenant, day);

        // 1) schedule: is this start time a slot for anyone (ignoring existing bookings)?
        Slot slot = availability.slots(tenant, duration, candidates, day, day, true).stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .orElseThrow(() -> ApiException.unprocessable("booking.slot_invalid"));
        List<Staff> working = candidates.stream().filter(s -> slot.staffIds().contains(s.getId())).toList();

        // 2) who is still free? "any barber" → the one with the fewest booked minutes that day
        List<Staff> free = freeStaff(working, start, start.plus(duration).plus(policy.buffer()), day, zone);
        if (free.isEmpty()) throw ApiException.conflict("booking.slot_taken");

        // anti-spam: open (unanswered/unpaid) requests per email address
        if (bookings.countOpenRequestsForEmail(tenant.getId(), customer.email())
                >= props.booking().maxOpenRequestsPerEmail()) {
            throw ApiException.unprocessable("booking.too_many_open_requests",
                    props.booking().maxOpenRequestsPerEmail());
        }

        // 3) insert; the exclusion constraint is the final word under concurrency
        for (Staff staff : free) {
            try {
                Booking saved = writeTx.execute(tx -> insertRequest(tenant, staff, account, start, services, customer,
                        req.note()));
                log.info("Booking request {} created: tenant={} staff={} start={} services={}", saved.getId(),
                        tenant.getSlug(), staff.getId(), start, services.size());
                return views.customer(saved, tenant, Messages.currentLang());
            } catch (DataIntegrityViolationException e) {
                if (!isOverlap(e)) throw e;
                log.info("Slot {} for staff {} was taken concurrently, trying next candidate", start, staff.getId());
            }
        }
        throw ApiException.conflict("booking.slot_taken");
    }

    private Booking insertRequest(Tenant tenant, Staff staff, AppUser account, Instant start,
                                  List<BarberService> services, Customer c, String note) {
        BookingPolicy policy = tenant.policy();
        Instant now = Instant.now(clock);
        Booking b = new Booking(tenant.getId(), staff, account == null ? null : account.getId(), BookingSource.ONLINE,
                start, policy.buffer(), services, c.name(), c.email(), c.phone(), c.lang(), blankToNull(note), now,
                tenant.getCurrency(), now);
        b.setBarberDeadlineAt(BookingRules.barberDeadline(now, start, policy.barberResponse(),
                policy.deadlineBeforeStart()));
        b.applyPricing(BookingPricing.calculate(lines(services), policy.getDepositCents(), false,
                policy.getLoyaltyPercent()));
        bookings.saveAndFlush(b); // ← exclusion constraint fires here
        if (account != null && loyalty.tryReserve(account.getId(), tenant.getId(), b.getId(), policy)) {
            b.applyPricing(BookingPricing.calculate(lines(services), policy.getDepositCents(), true,
                    policy.getLoyaltyPercent()));
            b.setLoyaltyStatus(LoyaltyStatus.RESERVED);
        }
        String manage = lifecycle.issueManageToken(b);
        String decision = tokens.issue(tenant.getId(), TokenPurpose.BARBER_DECISION, b.getId(), null, null,
                b.getBarberDeadlineAt()).raw();
        events.publishEvent(new Requested(b.getId(), manage, decision));
        return b;
    }

    // ── create (manual, admin) ───────────────────────────────────────────────

    /** Walk-in / phone: CONFIRMED immediately, no deposit, no lead time or grid. Overlaps are still refused. */
    @Transactional
    public BookingAdminView createManual(UUID tenantId, ManualBookingRequest req) {
        if (req.source() == BookingSource.ONLINE) throw ApiException.unprocessable("booking.manual_source");
        Tenant tenant = tenants.require(tenantId);
        BookingPolicy policy = tenant.policy();
        List<BarberService> services = catalog.requireActiveAll(tenantId, req.serviceIds(),
                props.booking().maxServices());
        Staff staff = availability.candidates(tenantId, req.serviceIds(), req.staffId()).getFirst();
        Instant start = req.start().toInstant();
        Instant now = Instant.now(clock);
        if (start.isBefore(now.minus(MANUAL_BACKDATE))) throw ApiException.unprocessable("booking.slot_invalid");
        if (start.isAfter(now.plus(Duration.ofDays(policy.getMaxDaysAhead() + 1L)))) {
            throw ApiException.unprocessable("availability.date_out_of_range", policy.getMaxDaysAhead());
        }

        String email = blankToNull(req.customerEmail());
        Lang lang = req.lang() != null ? Lang.parse(req.lang()) : defaultLang(tenant);
        // a registered, verified customer with that email collects the visit
        AppUser account = email == null ? null : users.findByTenantAndEmail(tenantId, email)
                .filter(u -> u.getRole() == Role.CUSTOMER && u.isEmailVerified()).orElse(null);

        Booking b = new Booking(tenantId, staff, account == null ? null : account.getId(), req.source(), start,
                policy.buffer(), services, req.customerName().trim(), email, blankToNull(req.customerPhone()), lang,
                blankToNull(req.note()), null, tenant.getCurrency(), now);
        b.applyPricing(BookingPricing.calculate(lines(services), 0, false, policy.getLoyaltyPercent()));
        try {
            bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            if (isOverlap(e)) throw ApiException.conflict("booking.slot_taken");
            throw e;
        }
        if (account != null && loyalty.tryReserve(account.getId(), tenantId, b.getId(), policy)) {
            b.applyPricing(BookingPricing.calculate(lines(services), 0, true, policy.getLoyaltyPercent()));
            b.setLoyaltyStatus(LoyaltyStatus.RESERVED);
        }
        if (email != null) events.publishEvent(new Confirmed(b.getId(), lifecycle.issueManageToken(b), false, true));
        log.info("Manual {} booking {} created for staff {} at {}", req.source(), b.getId(), staff.getId(), start);
        return views.admin(b, tenant, Messages.currentLang(), Actor.ADMIN);
    }

    // ── customer ("my bookings") ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BookingView> myBookings(CurrentUser user, boolean upcoming) {
        Tenant tenant = tenants.require(user.tenantId());
        Instant now = Instant.now(clock);
        List<Booking> list = upcoming ? bookings.findUpcomingForUser(user.userId(), now)
                : bookings.findPastForUser(user.userId(), now);
        return views.customer(list, tenant, Messages.currentLang());
    }

    @Transactional(readOnly = true)
    public BookingView myBooking(CurrentUser user, UUID id) {
        Tenant tenant = tenants.require(user.tenantId());
        return views.customer(requireOwned(user, id), tenant, Messages.currentLang());
    }

    /** Read-write transaction: the lifecycle joins it (a read-only one would never flush). */
    @Transactional
    public BookingView cancelMine(CurrentUser user, UUID id, String reason) {
        requireOwned(user, id);
        Booking b = lifecycle.cancel(id, Actor.CUSTOMER, Scope.tenant(user.tenantId()), reason, null).booking();
        return views.customer(reload(b.getId()), tenants.require(user.tenantId()), Messages.currentLang());
    }

    private Booking requireOwned(CurrentUser user, UUID id) {
        return bookings.findByIdAndTenantId(id, user.tenantId())
                .filter(b -> user.userId().equals(b.getUserId()))
                .orElseThrow(() -> ApiException.notFound("booking.not_found"));
    }

    // ── barber ───────────────────────────────────────────────────────────────

    /** The barber's own bookings overlapping [from, to] (local days), all statuses. */
    @Transactional(readOnly = true)
    public List<BookingAdminView> calendar(CurrentUser barber, LocalDate from, LocalDate to) {
        Tenant tenant = tenants.require(barber.tenantId());
        ZoneId zone = tenant.zone();
        if (to.isBefore(from) || to.isAfter(from.plusDays(31))) throw ApiException.unprocessable("calendar.invalid_range");
        List<Booking> list = bookings.findForStaffBetween(barber.requireStaffId(), from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
        return views.admin(list, tenant, Messages.currentLang(), Actor.BARBER);
    }

    @Transactional(readOnly = true)
    public List<BookingAdminView> pendingRequests(CurrentUser barber) {
        Tenant tenant = tenants.require(barber.tenantId());
        List<Booking> list = bookings.findByStaffIdAndStatusOrderByStartTimeAsc(barber.requireStaffId(),
                BookingStatus.PENDING_BARBER);
        return views.admin(list, tenant, Messages.currentLang(), Actor.BARBER);
    }

    @Transactional(readOnly = true)
    public BookingAdminView barberGet(CurrentUser barber, UUID id) {
        Tenant tenant = tenants.require(barber.tenantId());
        Booking b = bookings.findByIdAndTenantId(id, barber.tenantId())
                .filter(x -> x.getStaff().getId().equals(barber.requireStaffId()))
                .orElseThrow(() -> ApiException.notFound("booking.not_found"));
        return views.admin(b, tenant, Messages.currentLang(), Actor.BARBER);
    }

    // ── admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
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
        return new PageView<>(views.admin(result.getContent(), tenant, Messages.currentLang(), Actor.ADMIN),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BookingAdminView adminGet(UUID tenantId, UUID id) {
        Tenant tenant = tenants.require(tenantId);
        Booking b = bookings.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("booking.not_found"));
        return views.admin(b, tenant, Messages.currentLang(), Actor.ADMIN);
    }

    /** Fresh view after a lifecycle operation (which ran in its own transaction). */
    @Transactional(readOnly = true)
    public BookingAdminView adminView(UUID id, Actor viewer) {
        Booking b = reload(id);
        return views.admin(b, tenants.require(b.getTenantId()), Messages.currentLang(), viewer);
    }

    @Transactional(readOnly = true)
    public BookingView customerView(UUID id) {
        Booking b = reload(id);
        return views.customer(b, tenants.require(b.getTenantId()), Messages.currentLang());
    }

    // ── accounts: guest linking and GDPR ─────────────────────────────────────

    /**
     * After a customer verified their email: their earlier guest bookings with the same
     * email become part of the account. Already completed ones count as loyalty visits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int linkGuestBookings(UUID tenantId, String email, UUID userId) {
        int linked = bookings.linkGuestBookings(tenantId, email, userId);
        if (linked > 0) {
            long completed = bookings.countByUserIdAndStatus(userId, BookingStatus.COMPLETED);
            loyalty.addLinkedVisits(userId, tenantId, (int) completed);
            log.info("Linked {} guest booking(s) to account {} ({} completed visits)", linked, userId, completed);
        }
        return linked;
    }

    /**
     * Account deletion: open future bookings are cancelled (normal refund rule), then
     * every booking of the user is anonymised — the shop keeps dates and amounts only.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int anonymiseForUser(UUID tenantId, UUID userId, String placeholderName) {
        Instant now = Instant.now(clock);
        List<Booking> all = bookings.findByUserIdOrderByStartTimeDesc(userId);
        for (Booking b : all) {
            if (b.getStatus().isOpen() && now.isBefore(b.getStartTime())) {
                lifecycle.cancel(b.getId(), Actor.CUSTOMER, Scope.tenant(tenantId), "account deleted", null);
            }
        }
        all.forEach(b -> b.anonymise(placeholderName));
        return all.size();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Customer(String name, String email, String phone, Lang lang) {}

    /** Guests must give name, email, phone and consent; customers default to their account. */
    private static Customer customer(CreateBookingRequest req, AppUser account, Locale requestLocale) {
        Lang requested = req.lang() != null ? Lang.parse(req.lang()) : null;
        if (account != null) {
            String phone = blankToNull(req.customerPhone()) != null ? req.customerPhone().trim() : account.getPhone();
            if (phone == null) throw validation("customerPhone", "validation.required");
            String name = blankToNull(req.customerName()) != null ? req.customerName().trim() : account.getName();
            return new Customer(name, account.getEmail(), phone, requested != null ? requested : account.lang());
        }
        Map<String, String> missing = new LinkedHashMap<>();
        if (blankToNull(req.customerName()) == null) missing.put("customerName", "validation.required");
        if (blankToNull(req.customerEmail()) == null) missing.put("customerEmail", "validation.required");
        if (blankToNull(req.customerPhone()) == null) missing.put("customerPhone", "validation.required");
        if (!Boolean.TRUE.equals(req.consent())) missing.put("consent", "validation.consent");
        if (!missing.isEmpty()) throw ApiException.validation(missing);
        return new Customer(req.customerName().trim(), req.customerEmail().trim(), req.customerPhone().trim(),
                requested != null ? requested : Lang.of(requestLocale));
    }

    private static ApiException validation(String field, String code) {
        return ApiException.validation(Map.of(field, code));
    }

    /** Free barbers for [start, blockedEnd), the least booked (minutes that day) first. */
    private List<Staff> freeStaff(List<Staff> working, Instant start, Instant blockedEnd, LocalDate day, ZoneId zone) {
        if (working.isEmpty()) return List.of();
        List<UUID> ids = working.stream().map(Staff::getId).toList();
        Set<UUID> busy = bookings.findBlockingOverlapping(ids, start, blockedEnd).stream()
                .map(b -> b.getStaff().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> bookedMinutes = new HashMap<>();
        bookings.findBlockingOverlapping(ids, day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant())
                .forEach(b -> bookedMinutes.merge(b.getStaff().getId(), b.durationMin(), Integer::sum));
        List<Staff> free = new ArrayList<>(working.stream().filter(s -> !busy.contains(s.getId())).toList());
        // stable sort: equal load keeps the shop's staff order
        free.sort(Comparator.comparingInt(s -> bookedMinutes.getOrDefault(s.getId(), 0)));
        return free;
    }

    private static List<Line> lines(List<BarberService> services) {
        return services.stream().map(s -> new Line(s.getPriceCents(), s.isPriceFrom())).toList();
    }

    private static boolean isCustomerOf(CurrentUser user, Tenant tenant) {
        return user != null && user.is(Role.CUSTOMER) && user.tenantId().equals(tenant.getId());
    }

    private Booking reload(UUID id) {
        return bookings.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("booking.not_found"));
    }

    private static Lang defaultLang(Tenant tenant) {
        Lang l = Lang.parse(tenant.getDefaultLocale());
        return l != null ? l : Lang.DEFAULT;
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
