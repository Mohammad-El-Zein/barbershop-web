package com.barbershop.availability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure availability calculation (no Spring, no database) — fully unit-testable.
 *
 * <p>For one day: each staff member's working ranges (local times in the shop's
 * timezone) are turned into absolute instants, then candidate start times are
 * generated on a fixed grid from the start of each range ({@code step}, e.g.
 * 15 min). A candidate is a slot when
 * <ul>
 *   <li>the whole service ({@code duration}) fits before the range ends,</li>
 *   <li>it starts no earlier than {@code earliestStart} (now + minimum notice),</li>
 *   <li>and it overlaps none of the staff member's busy intervals
 *       (bookings, personal time off, shop-wide closures).</li>
 * </ul>
 * Results of all staff are merged per start time; each slot lists who is free.
 *
 * <p>DST-safe: local times are resolved with {@link ZonedDateTime#of}, and the
 * grid advances in absolute time.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {}

    /** Half-open interval [start, end). */
    public record Interval(Instant start, Instant end) {
        public boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }

    public record LocalRange(LocalTime start, LocalTime end) {}

    /** One staff member's working ranges on that day and everything that blocks them. */
    public record StaffDay(UUID staffId, List<LocalRange> workingRanges, List<Interval> busy) {}

    public record Request(LocalDate date, ZoneId zone, Duration duration, Duration step, Instant earliestStart,
                          List<StaffDay> staff) {}

    /** A bookable start time and the staff members free for the whole service. */
    public record Slot(Instant start, Instant end, List<UUID> staffIds) {}

    public static List<Slot> compute(Request r) {
        if (r.duration().isZero() || r.duration().isNegative()) throw new IllegalArgumentException("duration");
        if (r.step().isZero() || r.step().isNegative()) throw new IllegalArgumentException("step");

        Map<Instant, List<UUID>> byStart = new TreeMap<>();
        for (StaffDay staff : r.staff()) {
            for (LocalRange range : staff.workingRanges()) {
                Instant rangeStart = ZonedDateTime.of(r.date(), range.start(), r.zone()).toInstant();
                Instant rangeEnd = ZonedDateTime.of(r.date(), range.end(), r.zone()).toInstant();
                for (Instant t = rangeStart; !t.plus(r.duration()).isAfter(rangeEnd); t = t.plus(r.step())) {
                    Instant end = t.plus(r.duration());
                    if (t.isBefore(r.earliestStart())) continue;
                    if (isBusy(staff.busy(), t, end)) continue;
                    byStart.computeIfAbsent(t, k -> new ArrayList<>()).add(staff.staffId());
                }
            }
        }
        List<Slot> slots = new ArrayList<>(byStart.size());
        byStart.forEach((start, ids) -> slots.add(new Slot(start, start.plus(r.duration()), List.copyOf(ids))));
        return slots;
    }

    private static boolean isBusy(List<Interval> busy, Instant start, Instant end) {
        for (Interval b : busy) {
            if (b.overlaps(start, end)) return true;
        }
        return false;
    }
}
