package com.barbershop.availability;

import com.barbershop.availability.AvailabilityCalculator.Interval;
import com.barbershop.availability.AvailabilityCalculator.LocalRange;
import com.barbershop.availability.AvailabilityCalculator.Slot;
import com.barbershop.availability.AvailabilityCalculator.StaffDay;
import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingRepository;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import com.barbershop.service.BarberService;
import com.barbershop.service.ServiceCatalog;
import com.barbershop.staff.Staff;
import com.barbershop.staff.StaffDirectory;
import com.barbershop.staff.TimeOff;
import com.barbershop.staff.TimeOffRepository;
import com.barbershop.staff.WorkingHours;
import com.barbershop.staff.WorkingHoursRepository;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads working hours, time off and bookings for a date range and feeds the
 * pure {@link AvailabilityCalculator}. Also used by the booking flow to check
 * that a requested start time is a real slot.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TenantService tenants;
    private final ServiceCatalog catalog;
    private final StaffDirectory staffDirectory;
    private final WorkingHoursRepository workingHours;
    private final TimeOffRepository timeOff;
    private final BookingRepository bookings;
    private final AppProperties.Booking config;
    private final Clock clock;

    public AvailabilityService(TenantService tenants, ServiceCatalog catalog, StaffDirectory staffDirectory,
                               WorkingHoursRepository workingHours, TimeOffRepository timeOff,
                               BookingRepository bookings, AppProperties props, Clock clock) {
        this.tenants = tenants;
        this.catalog = catalog;
        this.staffDirectory = staffDirectory;
        this.workingHours = workingHours;
        this.timeOff = timeOff;
        this.bookings = bookings;
        this.config = props.booking();
        this.clock = clock;
    }

    // ── public API ───────────────────────────────────────────────────────────

    public AvailabilityView availability(String tenantSlug, UUID serviceId, UUID staffId, LocalDate date) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        BarberService service = catalog.requireActive(tenant.getId(), serviceId);
        List<Staff> staff = candidates(tenant.getId(), service.getId(), staffId);
        requireBookableDate(tenant, date);
        ZoneId zone = tenant.zone();
        List<SlotView> slots = slots(tenant, service, staff, date, date, false).stream()
                .map(s -> new SlotView(at(s.start(), zone), at(s.end(), zone),
                        HH_MM.format(s.start().atZone(zone)), s.staffIds()))
                .toList();
        return new AvailabilityView(date, tenant.getTimezone(), service.getId(), service.getDurationMin(), slots);
    }

    /** Days in [from, to] that have at least one free slot (for a date picker). */
    public AvailableDaysView availableDays(String tenantSlug, UUID serviceId, UUID staffId, LocalDate from,
                                           LocalDate to) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        BarberService service = catalog.requireActive(tenant.getId(), serviceId);
        List<Staff> staff = candidates(tenant.getId(), service.getId(), staffId);
        LocalDate today = today(tenant);
        LocalDate first = from == null || from.isBefore(today) ? today : from;
        LocalDate lastAllowed = today.plusDays(config.maxDaysAhead());
        LocalDate last = to == null || to.isAfter(lastAllowed) ? lastAllowed : to;
        if (last.isAfter(first.plusDays(62))) last = first.plusDays(62);
        if (last.isBefore(first)) throw ApiException.unprocessable("availability.date_out_of_range", config.maxDaysAhead());

        List<Slot> all = slots(tenant, service, staff, first, last, false);
        ZoneId zone = tenant.zone();
        List<LocalDate> days = all.stream().map(s -> s.start().atZone(zone).toLocalDate()).distinct().toList();
        return new AvailableDaysView(first, last, days);
    }

    // ── used by the booking flow ─────────────────────────────────────────────

    /** Active staff offering the service, optionally restricted to one staff member. */
    public List<Staff> candidates(UUID tenantId, UUID serviceId, UUID staffId) {
        List<Staff> offering = staffDirectory.activeOffering(tenantId, serviceId);
        if (staffId == null) {
            if (offering.isEmpty()) throw ApiException.unprocessable("service.no_staff");
            return offering;
        }
        return offering.stream()
                .filter(s -> s.getId().equals(staffId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> ApiException.unprocessable("staff.does_not_offer_service"));
    }

    /**
     * Slots for [firstDay, lastDay]. With {@code ignoreBookings} only the schedule
     * (working hours, time off, notice) is considered — used to tell "not a valid
     * time" (422) apart from "already taken" (409).
     */
    public List<Slot> slots(Tenant tenant, BarberService service, List<Staff> staff, LocalDate firstDay,
                            LocalDate lastDay, boolean ignoreBookings) {
        ZoneId zone = tenant.zone();
        Instant windowStart = firstDay.atStartOfDay(zone).toInstant();
        Instant windowEnd = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
        List<UUID> staffIds = staff.stream().map(Staff::getId).toList();

        List<WorkingHours> hours = workingHours.findByStaffIdInOrderByWeekdayAscStartTimeAsc(staffIds);
        List<TimeOff> absences = timeOff.findOverlapping(tenant.getId(), staffIds, windowStart, windowEnd);
        List<Booking> booked = ignoreBookings ? List.of()
                : bookings.findActiveOverlapping(staffIds, windowStart, windowEnd);
        Instant earliest = Instant.now(clock).plus(config.minNotice());

        List<Slot> result = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            int weekday = day.getDayOfWeek().getValue();
            List<StaffDay> staffDays = new ArrayList<>();
            for (Staff s : staff) {
                List<LocalRange> ranges = hours.stream()
                        .filter(h -> h.getStaffId().equals(s.getId()) && h.getWeekday() == weekday)
                        .map(h -> new LocalRange(h.getStartTime(), h.getEndTime()))
                        .toList();
                if (ranges.isEmpty()) continue;
                List<Interval> busy = new ArrayList<>();
                absences.stream()
                        .filter(t -> t.getStaffId() == null || t.getStaffId().equals(s.getId()))
                        .forEach(t -> busy.add(new Interval(t.getStartAt(), t.getEndAt())));
                booked.stream()
                        .filter(b -> b.getStaff().getId().equals(s.getId()))
                        .forEach(b -> busy.add(new Interval(b.getStartTime(), b.getEndTime())));
                staffDays.add(new StaffDay(s.getId(), ranges, busy));
            }
            result.addAll(AvailabilityCalculator.compute(new AvailabilityCalculator.Request(
                    day, zone, service.duration(), config.slotStep(), earliest, staffDays)));
        }
        return result;
    }

    public void requireBookableDate(Tenant tenant, LocalDate date) {
        LocalDate today = today(tenant);
        if (date.isBefore(today) || date.isAfter(today.plusDays(config.maxDaysAhead()))) {
            throw ApiException.unprocessable("availability.date_out_of_range", config.maxDaysAhead());
        }
    }

    private LocalDate today(Tenant tenant) {
        return LocalDate.now(clock.withZone(tenant.zone()));
    }

    private static OffsetDateTime at(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toOffsetDateTime();
    }

    // ── views ────────────────────────────────────────────────────────────────

    /** start/end carry the shop timezone's offset; time is the local "HH:mm" label. */
    public record SlotView(OffsetDateTime start, OffsetDateTime end, String time, List<UUID> staffIds) {}

    public record AvailabilityView(LocalDate date, String timezone, UUID serviceId, int durationMin,
                                   List<SlotView> slots) {}

    public record AvailableDaysView(LocalDate from, LocalDate to, List<LocalDate> days) {}
}
