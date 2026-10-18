package com.barbershop.staff;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The shop's opening hours, derived from the barbers' working hours: per weekday
 * the union of all ranges (the shop is open while at least one barber works).
 * Overlapping or touching ranges merge; a gap where nobody works (a shared lunch
 * break) stays a gap, so a day can have two opening ranges.
 */
public final class OpeningHours {

    private OpeningHours() {}

    /** One opening range on an ISO weekday (1 = Monday … 7 = Sunday), local shop time. */
    public record Range(int weekday, LocalTime opens, LocalTime closes) {}

    public static List<Range> of(List<WorkingHours> hours) {
        List<WorkingHours> sorted = hours.stream()
                .sorted(Comparator.comparingInt(WorkingHours::getWeekday).thenComparing(WorkingHours::getStartTime))
                .toList();
        List<Range> out = new ArrayList<>();
        for (WorkingHours h : sorted) {
            Range last = out.isEmpty() ? null : out.getLast();
            if (last != null && last.weekday() == h.getWeekday() && !h.getStartTime().isAfter(last.closes())) {
                if (h.getEndTime().isAfter(last.closes())) {
                    out.set(out.size() - 1, new Range(last.weekday(), last.opens(), h.getEndTime()));
                }
            } else {
                out.add(new Range(h.getWeekday(), h.getStartTime(), h.getEndTime()));
            }
        }
        return out;
    }
}
