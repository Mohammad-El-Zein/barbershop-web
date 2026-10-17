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
import com.barbershop.tenant.BookingPolicy;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads working hours, time off and slot-blocking bookings and feeds the pure
 * {@link AvailabilityCalculator}. All rules come from the tenant's
 * {@link BookingPolicy}: slot grid, buffer, lead time (default ≥ 2 h ahead),
 * max days ahead. Also used by the booking flow to check that a requested
 * start time is a real slot.
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

    /**
     * One day for ALL barbers at once: a column per barber (who offers every chosen
     * service) with their free start times, plus the merged "any barber" list.
     */
    public DayView day(String tenantSlug, List<UUID> serviceIds, LocalDate date) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        List<BarberService> services = catalog.requireActiveAll(tenant.getId(), serviceIds, config.maxServices());
        Duration duration = totalDuration(services);
        List<Staff> staff = candidates(tenant.getId(), serviceIds, null);
        requireBookableDate(tenant, date);
        ZoneId zone = tenant.zone();

        List<Slot> slots = slots(tenant, duration, staff, date, date, false);
        List<BarberColumn> columns = staff.stream()
                .map(s -> new BarberColumn(s.getId(), s.getName(), s.getPhotoUrl(), slots.stream()
                        .filter(slot -> slot.staffIds().contains(s.getId()))
                        .map(slot -> new SlotView(at(slot.start(), zone), at(slot.end(), zone),
                                HH_MM.format(slot.start().atZone(zone))))
                        .toList()))
                .toList();
        List<AnySlotView> any = slots.stream()
                .map(s -> new AnySlotView(at(s.start(), zone), at(s.end(), zone), HH_MM.format(s.start().atZone(zone)),
                        s.staffIds()))
                .toList();
        return new DayView(date, tenant.getTimezone(), serviceIds, (int) duration.toMinutes(),
                tenant.policy().getSlotStepMin(), columns, any);
    }

    /** Days in [from, to] that have at least one free slot (for a date picker). staffId null = any barber. */
    public AvailableDaysView availableDays(String tenantSlug, List<UUID> serviceIds, UUID staffId, LocalDate from,
                                           LocalDate to) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        List<BarberService> services = catalog.requireActiveAll(tenant.getId(), serviceIds, config.maxServices());
        List<Staff> staff = candidates(tenant.getId(), serviceIds, staffId);
        int maxDays = tenant.policy().getMaxDaysAhead();
        LocalDate today = today(tenant);
        LocalDate first = from == null || from.isBefore(today) ? today : from;
        LocalDate lastAllowed = today.plusDays(maxDays);
        LocalDate last = to == null || to.isAfter(lastAllowed) ? lastAllowed : to;
        if (last.isAfter(first.plusDays(62))) last = first.plusDays(62);
        if (last.isBefore(first)) throw ApiException.unprocessable("availability.date_out_of_range", maxDays);

        ZoneId zone = tenant.zone();
        List<LocalDate> days = slots(tenant, totalDuration(services), staff, first, last, false).stream()
                .map(s -> s.start().atZone(zone).toLocalDate()).distinct().toList();
        return new AvailableDaysView(first, last, days);
    }

    // ── used by the booking flow ─────────────────────────────────────────────

    /** Active staff offering all services, optionally restricted to one staff member. */
    public List<Staff> candidates(UUID tenantId, Collection<UUID> serviceIds, UUID staffId) {
        List<Staff> offering = staffDirectory.activeOfferingAll(tenantId, serviceIds);
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
     * (working hours, time off, lead time) is considered — used to tell "not a valid
     * time" (422) apart from "already taken" (409).
     */
    public List<Slot> slots(Tenant tenant, Duration duration, List<Staff> staff, LocalDate firstDay,
                            LocalDate lastDay, boolean ignoreBookings) {
        BookingPolicy policy = tenant.policy();
        ZoneId zone = tenant.zone();
        Instant windowStart = firstDay.atStartOfDay(zone).toInstant();
        Instant windowEnd = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
        List<UUID> staffIds = staff.stream().map(Staff::getId).toList();
        if (staffIds.isEmpty()) return List.of();

        List<WorkingHours> hours = workingHours.findByStaffIdInOrderByWeekdayAscStartTimeAsc(staffIds);
        List<TimeOff> absences = timeOff.findOverlapping(tenant.getId(), staffIds, windowStart, windowEnd);
        // bookings may reach into the window with their buffer → widen by the buffer
        List<Booking> booked = ignoreBookings ? List.of()
                : bookings.findBlockingOverlapping(staffIds, windowStart.minus(policy.buffer()), windowEnd);
        Instant earliest = earliestStart(tenant);

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
                        .forEach(b -> busy.add(new Interval(b.getStartTime(), b.getBlockedUntil())));
                staffDays.add(new StaffDay(s.getId(), ranges, busy));
            }
            result.addAll(AvailabilityCalculator.compute(new AvailabilityCalculator.Request(
                    day, zone, duration, policy.buffer(), policy.slotStep(), earliest, staffDays)));
        }
        return result;
    }

    /** now + lead time: nothing earlier can be booked online. */
    public Instant earliestStart(Tenant tenant) {
        return Instant.now(clock).plus(tenant.policy().minLead());
    }

    public void requireBookableDate(Tenant tenant, LocalDate date) {
        LocalDate today = today(tenant);
        int maxDays = tenant.policy().getMaxDaysAhead();
        if (date.isBefore(today) || date.isAfter(today.plusDays(maxDays))) {
            throw ApiException.unprocessable("availability.date_out_of_range", maxDays);
        }
    }

    public static Duration totalDuration(List<BarberService> services) {
        return services.stream().map(BarberService::duration).reduce(Duration.ZERO, Duration::plus);
    }

    private LocalDate today(Tenant tenant) {
        return LocalDate.now(clock.withZone(tenant.zone()));
    }

    private static OffsetDateTime at(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toOffsetDateTime();
    }

    // ── views ────────────────────────────────────────────────────────────────

    /** start/end carry the shop timezone's offset; time is the local "HH:mm" label. */
    public record SlotView(OffsetDateTime start, OffsetDateTime end, String time) {}

    public record AnySlotView(OffsetDateTime start, OffsetDateTime end, String time, List<UUID> staffIds) {}

    public record BarberColumn(UUID staffId, String name, String photoUrl, List<SlotView> slots) {}

    public record DayView(LocalDate date, String timezone, List<UUID> serviceIds, int durationMin, int slotStepMin,
                          List<BarberColumn> barbers, List<AnySlotView> anyBarber) {}

    public record AvailableDaysView(LocalDate from, LocalDate to, List<LocalDate> days) {}
}
