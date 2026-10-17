package com.barbershop.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Deadlines (barber response, payment) and the deposit refund rule. */
class BookingRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final Duration H12 = Duration.ofHours(12);
    private static final Duration H24 = Duration.ofHours(24);
    private static final Duration H2 = Duration.ofHours(2);

    @Nested
    class BarberDeadline {

        @Test
        void twelveHoursWhenTheAppointmentIsFarAway() {
            Instant start = NOW.plus(Duration.ofDays(3));
            assertThat(BookingRules.barberDeadline(NOW, start, H12, H2)).isEqualTo(NOW.plus(H12));
        }

        @Test
        void atTheLatestTwoHoursBeforeTheAppointment() {
            Instant start = NOW.plus(Duration.ofHours(6));
            assertThat(BookingRules.barberDeadline(NOW, start, H12, H2)).isEqualTo(NOW.plus(Duration.ofHours(4)));
        }

        @Test
        void neverBeforeTheRequestItself() {
            Instant start = NOW.plus(Duration.ofMinutes(90)); // closer than the 2 h cap
            assertThat(BookingRules.barberDeadline(NOW, start, H12, H2)).isEqualTo(NOW);
        }
    }

    @Nested
    class PaymentDeadline {

        @Test
        void twentyFourHoursAfterAcceptance() {
            Instant start = NOW.plus(Duration.ofDays(5));
            assertThat(BookingRules.paymentDeadline(NOW, start, H24, H2)).isEqualTo(NOW.plus(H24));
        }

        @Test
        void atTheLatestTwoHoursBeforeTheAppointment() {
            Instant start = NOW.plus(Duration.ofHours(10));
            assertThat(BookingRules.paymentDeadline(NOW, start, H24, H2)).isEqualTo(NOW.plus(Duration.ofHours(8)));
        }
    }

    @Nested
    class OnlineCancellation {

        private final Instant start = NOW.plus(Duration.ofDays(1));

        @Test
        void possibleUntilTwoHoursBeforeTheStart() {
            assertThat(BookingRules.onlineCancellable(start.minus(Duration.ofHours(5)), start, H2)).isTrue();
            assertThat(BookingRules.onlineCancellable(start.minus(H2).minusSeconds(1), start, H2)).isTrue();
        }

        @Test
        void exactlyTwoHoursBeforeOrLaterIsTooLate() {
            assertThat(BookingRules.onlineCancellable(start.minus(H2), start, H2)).isFalse();
            assertThat(BookingRules.onlineCancellable(start.minus(Duration.ofMinutes(30)), start, H2)).isFalse();
            assertThat(BookingRules.onlineCancellable(start.plusSeconds(1), start, H2)).isFalse();
        }

        @Test
        void independentOfTheRefundRule() {
            // 5 h before: online cancellation still works, but the deposit is no longer refundable
            Instant fiveHoursBefore = start.minus(Duration.ofHours(5));
            assertThat(BookingRules.onlineCancellable(fiveHoursBefore, start, H2)).isTrue();
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, fiveHoursBefore, start, H24)).isFalse();
        }
    }

    @Nested
    class RefundRule {

        private final Instant start = NOW.plus(Duration.ofDays(2));

        @Test
        void customerCancellingAtLeast24HoursAheadGetsTheDepositBack() {
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(Duration.ofDays(1).plusMinutes(1)),
                    start, H24)).isTrue();
            // exactly 24 h before still counts
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(H24), start, H24)).isTrue();
        }

        @Test
        void customerCancellingLaterLosesTheDeposit() {
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(H24).plusSeconds(1), start, H24))
                    .isFalse();
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(Duration.ofHours(3)), start, H24))
                    .isFalse();
        }

        @Test
        void theShopCancellingAlwaysRefunds() {
            Instant lastMinute = start.minus(Duration.ofMinutes(5));
            assertThat(BookingRules.depositRefundable(Actor.BARBER, lastMinute, start, H24)).isTrue();
            assertThat(BookingRules.depositRefundable(Actor.ADMIN, lastMinute, start, H24)).isTrue();
        }

        @Test
        void aConfigurableCutoff() {
            Duration h48 = Duration.ofHours(48);
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(Duration.ofHours(30)), start, h48))
                    .isFalse();
            assertThat(BookingRules.depositRefundable(Actor.CUSTOMER, start.minus(Duration.ofHours(30)), start,
                    Duration.ZERO)).isTrue();
        }
    }
}
