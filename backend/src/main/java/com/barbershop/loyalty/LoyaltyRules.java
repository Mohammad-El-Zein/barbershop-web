package com.barbershop.loyalty;

/**
 * Pure loyalty rules (unit-tested). Every COMPLETED booking is one visit. Once
 * {@code threshold} visits (default 10) are collected, the NEXT booking gets the
 * discount; when that discounted booking is completed the cycle starts over.
 *
 * <pre>
 *  visits 1 … 10 completed  → counter 10 → discount available
 *  11th booking created     → discount reserved (counter stays 10)
 *  11th booking completed   → discount consumed, counter = 10 − 10 = 0
 * </pre>
 * Subtracting the threshold (instead of resetting to 0) keeps visits that were
 * completed while the discount was still reserved (e.g. an earlier booking that
 * was completed after the 11th was made).
 */
public final class LoyaltyRules {

    private LoyaltyRules() {}

    public static boolean discountAvailable(int visitsInCycle, int threshold, boolean hasReservation) {
        return !hasReservation && visitsInCycle >= threshold;
    }

    public static int afterCompletion(int visitsInCycle, boolean consumedDiscount, int threshold) {
        return consumedDiscount ? Math.max(0, visitsInCycle - threshold) : visitsInCycle + 1;
    }
}
