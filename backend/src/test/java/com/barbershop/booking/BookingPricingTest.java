package com.barbershop.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.booking.BookingPricing.Breakdown;
import com.barbershop.booking.BookingPricing.Line;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Price, loyalty discount and deposit — all in cents. */
class BookingPricingTest {

    private static final Line HAIRCUT = new Line(3500, true);
    private static final Line BEARD = new Line(2500, false);

    @Test
    void multipleServicesAreSummedAndTheDepositIsDeductedAtTheShop() {
        Breakdown p = BookingPricing.calculate(List.of(HAIRCUT, BEARD), 1000, false, 50);
        assertThat(p.totalCents()).isEqualTo(6000);
        assertThat(p.loyaltyDiscountCents()).isZero();
        assertThat(p.depositCents()).isEqualTo(1000);
        assertThat(p.amountDueAtShopCents()).isEqualTo(5000);
        assertThat(p.priceFrom()).isTrue(); // one "from" price makes the total a "from" price
    }

    @Test
    void loyaltyRewardHalvesTheServiceTotalBeforeTheDeposit() {
        Breakdown p = BookingPricing.calculate(List.of(HAIRCUT, BEARD), 1000, true, 50);
        assertThat(p.totalCents()).isEqualTo(6000);
        assertThat(p.loyaltyDiscountCents()).isEqualTo(3000);
        assertThat(p.depositCents()).isEqualTo(1000);
        assertThat(p.amountDueAtShopCents()).isEqualTo(2000);
    }

    @Test
    void discountIsRoundedHalfUpToTheCent() {
        // 50 % of 35,05 € = 17,525 € → 17,53 €
        Breakdown p = BookingPricing.calculate(List.of(new Line(3505, false)), 1000, true, 50);
        assertThat(p.loyaltyDiscountCents()).isEqualTo(1753);
        assertThat(p.amountDueAtShopCents()).isEqualTo(3505 - 1753 - 1000);
        // 33 % of 10,00 € = 3,30 €
        assertThat(BookingPricing.calculate(List.of(new Line(1000, false)), 0, true, 33).loyaltyDiscountCents())
                .isEqualTo(330);
    }

    @Test
    void theDepositNeverExceedsWhatIsOwed() {
        Breakdown cheap = BookingPricing.calculate(List.of(new Line(1500, false)), 1000, true, 50);
        assertThat(cheap.loyaltyDiscountCents()).isEqualTo(750);
        assertThat(cheap.depositCents()).isEqualTo(750);
        assertThat(cheap.amountDueAtShopCents()).isZero();

        Breakdown free = BookingPricing.calculate(List.of(new Line(0, false)), 1000, false, 50);
        assertThat(free.depositCents()).isZero();
        assertThat(free.amountDueAtShopCents()).isZero();
    }

    @Test
    void manualBookingsHaveNoDeposit() {
        Breakdown p = BookingPricing.calculate(List.of(HAIRCUT), 0, false, 50);
        assertThat(p.depositCents()).isZero();
        assertThat(p.amountDueAtShopCents()).isEqualTo(3500);
    }

    @Test
    void theInvariantAlwaysHolds() {
        for (int price = 0; price <= 20000; price += 137) {
            for (boolean loyalty : new boolean[] {false, true}) {
                Breakdown p = BookingPricing.calculate(List.of(new Line(price, false)), 1000, loyalty, 50);
                assertThat(p.totalCents() - p.loyaltyDiscountCents() - p.depositCents())
                        .isEqualTo(p.amountDueAtShopCents()).isNotNegative();
            }
        }
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> BookingPricing.calculate(List.of(HAIRCUT), -1, false, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BookingPricing.calculate(List.of(HAIRCUT), 1000, true, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BookingPricing.calculate(List.of(new Line(-5, false)), 1000, false, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
