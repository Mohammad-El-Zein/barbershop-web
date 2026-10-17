package com.barbershop.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.availability.AvailabilityCalculator.Interval;
import com.barbershop.availability.AvailabilityCalculator.LocalRange;
import com.barbershop.availability.AvailabilityCalculator.Request;
import com.barbershop.availability.AvailabilityCalculator.Slot;
import com.barbershop.availability.AvailabilityCalculator.StaffDay;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Pure unit tests of the slot calculation — no Spring, no database. */
class AvailabilityCalculatorTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);
    private static final UUID LUCA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MO = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Duration MIN30 = Duration.ofMinutes(30);
    private static final Duration MIN15 = Duration.ofMinutes(15);
    private static final Instant LONG_AGO = Instant.EPOCH;

    private static LocalRange range(String from, String to) {
        return new LocalRange(LocalTime.parse(from), LocalTime.parse(to));
    }

    private static Instant at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), BERLIN).toInstant();
    }

    private static List<String> localTimes(List<Slot> slots) {
        return slots.stream().map(s -> s.start().atZone(BERLIN).toLocalTime().toString()).toList();
    }

    private static List<Slot> compute(LocalDate date, Duration duration, Duration step, Instant earliest,
                                      StaffDay... staff) {
        return compute(date, duration, Duration.ZERO, step, earliest, staff);
    }

    private static List<Slot> compute(LocalDate date, Duration duration, Duration buffer, Duration step,
                                      Instant earliest, StaffDay... staff) {
        return AvailabilityCalculator.compute(new Request(date, BERLIN, duration, buffer, step, earliest,
                List.of(staff)));
    }

    @Nested
    @DisplayName("basic grid")
    class Basics {

        @Test
        void slotsOnTheStepGridWhereTheWholeServiceFits() {
            List<Slot> slots = compute(TUESDAY, MIN30, MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("09:00", "10:00")), List.of()));
            // 09:00, 09:15, 09:30 — 09:45 would end at 10:15, after closing
            assertThat(localTimes(slots)).containsExactly("09:00", "09:15", "09:30");
            assertThat(slots.getFirst().end()).isEqualTo(at(TUESDAY, "09:30"));
            assertThat(slots).allSatisfy(s -> assertThat(s.staffIds()).containsExactly(LUCA));
        }

        @Test
        void serviceLongerThanTheRangeYieldsNothing() {
            assertThat(compute(TUESDAY, Duration.ofMinutes(90), MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("09:00", "10:00")), List.of()))).isEmpty();
        }

        @Test
        void noWorkingHoursNoSlots() {
            assertThat(compute(TUESDAY, MIN30, MIN15, LONG_AGO, new StaffDay(LUCA, List.of(), List.of()))).isEmpty();
            assertThat(compute(TUESDAY, MIN30, MIN15, LONG_AGO)).isEmpty();
        }

        @Test
        void exactFitAtClosingTimeIsAllowed() {
            List<Slot> slots = compute(TUESDAY, MIN30, MIN30, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("19:00", "20:00")), List.of()));
            assertThat(localTimes(slots)).containsExactly("19:00", "19:30");
        }

        @Test
        void rejectsNonPositiveDurationOrStep() {
            StaffDay luca = new StaffDay(LUCA, List.of(range("09:00", "10:00")), List.of());
            assertThatThrownBy(() -> compute(TUESDAY, Duration.ZERO, MIN15, LONG_AGO, luca))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> compute(TUESDAY, MIN30, Duration.ZERO, LONG_AGO, luca))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("busy time")
    class Busy {

        @Test
        void bookingsBlockEveryOverlappingCandidate() {
            Interval booking = new Interval(at(TUESDAY, "10:00"), at(TUESDAY, "10:45"));
            List<Slot> slots = compute(TUESDAY, MIN30, MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("09:00", "12:00")), List.of(booking)));
            // 09:30 ends exactly at 10:00 → ok (half-open); 09:45..10:30 overlap; 10:45 starts at the end → ok
            assertThat(localTimes(slots)).containsExactly(
                    "09:00", "09:15", "09:30", "10:45", "11:00", "11:15", "11:30");
        }

        @Test
        void wholeDayTimeOffRemovesAllSlots() {
            Interval off = new Interval(TUESDAY.atStartOfDay(BERLIN).toInstant(),
                    TUESDAY.plusDays(1).atStartOfDay(BERLIN).toInstant());
            assertThat(compute(TUESDAY, MIN30, MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("09:00", "18:00")), List.of(off)))).isEmpty();
        }

        @Test
        void lunchBreakSplitsTheDayAndNoSlotSpansIt() {
            List<Slot> slots = compute(TUESDAY, Duration.ofMinutes(45), MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("12:00", "13:00"), range("14:00", "15:00")), List.of()));
            assertThat(localTimes(slots)).containsExactly("12:00", "12:15", "14:00", "14:15");
        }
    }

    @Nested
    @DisplayName("multiple services and buffer")
    class MultipleServices {

        /** Haircut 45 + beard 30 = one 75-minute appointment. */
        private final Duration haircutPlusBeard = Duration.ofMinutes(45).plus(Duration.ofMinutes(30));

        @Test
        void theSummedDurationMustFitBeforeClosing() {
            List<Slot> slots = compute(TUESDAY, haircutPlusBeard, MIN15, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("17:00", "19:00")), List.of()));
            // 17:45 + 75 min = 19:00 still fits; 18:00 would end 19:15
            assertThat(localTimes(slots)).containsExactly("17:00", "17:15", "17:30", "17:45");
            assertThat(slots.getFirst().end()).isEqualTo(at(TUESDAY, "18:15"));
        }

        @Test
        void aLongerCombinationNeedsALongerGap() {
            Interval before = new Interval(at(TUESDAY, "09:00"), at(TUESDAY, "10:00"));
            Interval after = new Interval(at(TUESDAY, "11:00"), at(TUESDAY, "12:00"));
            StaffDay luca = new StaffDay(LUCA, List.of(range("09:00", "12:00")), List.of(before, after));
            // a 45-min haircut fits the one-hour gap, haircut + beard (75 min) does not
            assertThat(localTimes(compute(TUESDAY, Duration.ofMinutes(45), MIN15, LONG_AGO, luca)))
                    .containsExactly("10:00", "10:15");
            assertThat(compute(TUESDAY, haircutPlusBeard, MIN15, LONG_AGO, luca)).isEmpty();
        }

        @Test
        void theBufferAfterAnAppointmentMustBeFreeButMayPassClosingTime() {
            Interval next = new Interval(at(TUESDAY, "10:00"), at(TUESDAY, "10:30"));
            StaffDay luca = new StaffDay(LUCA, List.of(range("09:00", "11:00")), List.of(next));
            List<Slot> withBuffer = compute(TUESDAY, MIN30, Duration.ofMinutes(15), MIN15, LONG_AGO, luca);
            // 09:30 would end 10:00 but needs cleanup until 10:15 → blocked; 10:30 ends at 11:00 (buffer after closing ok)
            assertThat(localTimes(withBuffer)).containsExactly("09:00", "09:15", "10:30");
            assertThat(withBuffer.getFirst().end()).isEqualTo(at(TUESDAY, "09:30")); // the slot end excludes the buffer
        }
    }

    @Nested
    @DisplayName("notice and staff merging")
    class NoticeAndMerging {

        @Test
        void candidatesBeforeTheEarliestStartAreSkipped() {
            Instant earliest = at(TUESDAY, "09:40"); // e.g. now 08:40 + 1h notice
            List<Slot> slots = compute(TUESDAY, MIN30, MIN15, earliest,
                    new StaffDay(LUCA, List.of(range("09:00", "10:30")), List.of()));
            assertThat(localTimes(slots)).containsExactly("09:45", "10:00");
        }

        @Test
        void anyBarberMergesStaffPerStartTime() {
            List<Slot> slots = compute(TUESDAY, MIN30, MIN30, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("09:00", "10:30")), List.of()),
                    new StaffDay(MO, List.of(range("10:00", "11:00")), List.of()));
            assertThat(localTimes(slots)).containsExactly("09:00", "09:30", "10:00", "10:30");
            assertThat(slots.get(0).staffIds()).containsExactly(LUCA);
            assertThat(slots.get(2).staffIds()).containsExactlyInAnyOrder(LUCA, MO);
            assertThat(slots.get(3).staffIds()).containsExactly(MO);
        }
    }

    @Nested
    @DisplayName("time zones and DST (Europe/Berlin)")
    class TimeZones {

        @Test
        void localTimesAreResolvedInTheShopTimezone() {
            LocalDate winter = LocalDate.of(2026, 1, 13);
            LocalDate summer = LocalDate.of(2026, 7, 14);
            StaffDay nine = new StaffDay(LUCA, List.of(range("09:00", "09:30")), List.of());
            assertThat(compute(winter, MIN30, MIN30, LONG_AGO, nine).getFirst().start())
                    .isEqualTo(Instant.parse("2026-01-13T08:00:00Z")); // CET = UTC+1
            assertThat(compute(summer, MIN30, MIN30, LONG_AGO, nine).getFirst().start())
                    .isEqualTo(Instant.parse("2026-07-14T07:00:00Z")); // CEST = UTC+2
        }

        @Test
        void springForwardDayHasOneHourLess() {
            // 2026-03-29: clocks jump from 02:00 to 03:00 → 01:00–04:00 local is only 2 real hours
            LocalDate dst = LocalDate.of(2026, 3, 29);
            List<Slot> slots = compute(dst, MIN30, MIN30, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("01:00", "04:00")), List.of()));
            assertThat(slots).hasSize(4);
            assertThat(localTimes(slots)).containsExactly("01:00", "01:30", "03:00", "03:30");
            assertThat(slots).allSatisfy(s -> assertThat(Duration.between(s.start(), s.end())).isEqualTo(MIN30));
        }

        @Test
        void fallBackDayHasOneHourMore() {
            // 2026-10-25: 03:00 → 02:00 → 01:00–04:00 local is 4 real hours
            LocalDate dst = LocalDate.of(2026, 10, 25);
            List<Slot> slots = compute(dst, MIN30, MIN30, LONG_AGO,
                    new StaffDay(LUCA, List.of(range("01:00", "04:00")), List.of()));
            assertThat(slots).hasSize(8);
            assertThat(slots.getFirst().start()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
            assertThat(slots.getLast().start()).isEqualTo(Instant.parse("2026-10-25T02:30:00Z"));
        }
    }
}
