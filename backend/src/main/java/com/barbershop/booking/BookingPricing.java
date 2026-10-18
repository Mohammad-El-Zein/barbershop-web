package com.barbershop.booking;

import java.util.List;

/**
 * Pure price calculation (unit-tested). All amounts in cents.
 *
 * <pre>
 * total     = Σ service prices
 * discount  = round_half_up(total × loyaltyPercent / 100)   (only when the loyalty reward applies)
 * deposit   = min(configured deposit, total − discount)     (never more than what is owed)
 * dueAtShop = total − discount − deposit                    (the deposit is deducted in the shop)
 * </pre>
 */
public final class BookingPricing {

    private BookingPricing() {}

    public record Line(int priceCents) {}

    public record Breakdown(int totalCents, int loyaltyDiscountCents, int depositCents, int amountDueAtShopCents) {}

    public static Breakdown calculate(List<Line> lines, int depositCents, boolean loyaltyApplies, int loyaltyPercent) {
        if (depositCents < 0) throw new IllegalArgumentException("deposit");
        if (loyaltyPercent < 0 || loyaltyPercent > 100) throw new IllegalArgumentException("loyaltyPercent");
        int total = 0;
        for (Line l : lines) {
            if (l.priceCents() < 0) throw new IllegalArgumentException("price");
            total = Math.addExact(total, l.priceCents());
        }
        int discount = loyaltyApplies ? (int) ((total * (long) loyaltyPercent + 50) / 100) : 0;
        int afterDiscount = total - discount;
        int deposit = Math.min(depositCents, afterDiscount);
        return new Breakdown(total, discount, deposit, afterDiscount - deposit);
    }
}
