package com.barbershop.staff;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.staff.OpeningHours.Range;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Shop opening hours = union of the barbers' working hours per weekday. */
class OpeningHoursTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static WorkingHours wh(UUID staff, int weekday, String from, String to) {
        return new WorkingHours(TENANT, staff, weekday, LocalTime.parse(from), LocalTime.parse(to));
    }

    private static Range range(int weekday, String opens, String closes) {
        return new Range(weekday, LocalTime.parse(opens), LocalTime.parse(closes));
    }

    @Test
    void overlappingRangesOfDifferentBarbersMerge() {
        // A has a lunch break, B covers it → one range 09–20
        List<Range> r = OpeningHours.of(List.of(wh(A, 1, "09:00", "13:00"), wh(A, 1, "14:00", "20:00"),
                wh(B, 1, "10:00", "19:00")));
        assertThat(r).containsExactly(range(1, "09:00", "20:00"));
    }

    @Test
    void aSharedBreakStaysAGap_andTouchingRangesJoin() {
        List<Range> r = OpeningHours.of(List.of(wh(A, 2, "14:00", "18:00"), wh(A, 2, "09:00", "12:00"),
                wh(B, 2, "12:00", "12:30")));
        assertThat(r).containsExactly(range(2, "09:00", "12:30"), range(2, "14:00", "18:00"));
    }

    @Test
    void daysAreSortedAndClosedDaysAreMissing() {
        List<Range> r = OpeningHours.of(List.of(wh(B, 6, "09:00", "18:00"), wh(A, 1, "09:00", "20:00"),
                wh(A, 6, "10:00", "12:00")));
        assertThat(r).containsExactly(range(1, "09:00", "20:00"), range(6, "09:00", "18:00"));
        assertThat(OpeningHours.of(List.of())).isEmpty();
    }
}
