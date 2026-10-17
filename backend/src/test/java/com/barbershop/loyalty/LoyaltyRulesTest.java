package com.barbershop.loyalty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 10 completed visits → the 11th booking gets the reward → the counter starts over. */
class LoyaltyRulesTest {

    private static final int THRESHOLD = 10;

    @Test
    void theRewardBecomesAvailableAfterTheThreshold() {
        assertThat(LoyaltyRules.discountAvailable(9, THRESHOLD, false)).isFalse();
        assertThat(LoyaltyRules.discountAvailable(10, THRESHOLD, false)).isTrue();
        assertThat(LoyaltyRules.discountAvailable(11, THRESHOLD, false)).isTrue();
    }

    @Test
    void aReservedRewardCannotBeUsedTwice() {
        assertThat(LoyaltyRules.discountAvailable(10, THRESHOLD, true)).isFalse();
    }

    @Test
    void elevenBookingsMakeOneCycle() {
        int visits = 0;
        for (int i = 1; i <= 10; i++) visits = LoyaltyRules.afterCompletion(visits, false, THRESHOLD);
        assertThat(visits).isEqualTo(10);
        assertThat(LoyaltyRules.discountAvailable(visits, THRESHOLD, false)).isTrue();
        // the 11th (discounted) booking is completed → counter resets
        visits = LoyaltyRules.afterCompletion(visits, true, THRESHOLD);
        assertThat(visits).isZero();
        assertThat(LoyaltyRules.discountAvailable(visits, THRESHOLD, false)).isFalse();
    }

    @Test
    void visitsCompletedWhileTheRewardWasReservedAreKept() {
        // 10 visits, reward reserved for a future booking, an older booking completes meanwhile → 11
        int visits = LoyaltyRules.afterCompletion(10, false, THRESHOLD);
        assertThat(visits).isEqualTo(11);
        assertThat(LoyaltyRules.afterCompletion(visits, true, THRESHOLD)).isEqualTo(1);
    }

    @Test
    void aSmallerThresholdWorksTheSame() {
        assertThat(LoyaltyRules.discountAvailable(3, 3, false)).isTrue();
        assertThat(LoyaltyRules.afterCompletion(3, true, 3)).isZero();
    }
}
