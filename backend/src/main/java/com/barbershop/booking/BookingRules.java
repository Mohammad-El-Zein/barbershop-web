package com.barbershop.booking;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure time rules of the lifecycle (unit-tested): response/payment deadlines and
 * the deposit refund rule.
 */
public final class BookingRules {

    private BookingRules() {}

    /**
     * The barber must answer within {@code response} of the request, but at the latest
     * {@code beforeStart} before the appointment. Never earlier than the request itself.
     */
    public static Instant barberDeadline(Instant requestedAt, Instant start, Duration response, Duration beforeStart) {
        return clamp(requestedAt, min(requestedAt.plus(response), start.minus(beforeStart)));
    }

    /**
     * The customer must pay within {@code payment} of the acceptance, but at the latest
     * {@code beforeStart} before the appointment. Never earlier than the acceptance itself.
     */
    public static Instant paymentDeadline(Instant acceptedAt, Instant start, Duration payment, Duration beforeStart) {
        return clamp(acceptedAt, min(acceptedAt.plus(payment), start.minus(beforeStart)));
    }

    /**
     * Is the deposit refunded on cancellation?
     * <ul>
     *   <li>Cancelled by the shop (barber/admin): always — the customer did nothing wrong.</li>
     *   <li>Cancelled by the customer: only if at least {@code cutoff} (default 24 h)
     *       before the appointment — exactly 24 h still counts.</li>
     * </ul>
     */
    public static boolean depositRefundable(Actor cancelledBy, Instant cancelledAt, Instant start, Duration cutoff) {
        if (cancelledBy == Actor.BARBER || cancelledBy == Actor.ADMIN || cancelledBy == Actor.SYSTEM) return true;
        return !cancelledAt.isAfter(start.minus(cutoff));
    }

    /**
     * Can the customer still cancel online? Until {@code cutoff} (default 2 h) before the
     * start — exactly 2 h before is too late. After that they have to contact the shop.
     * (Independent of the refund rule: cancelling 5 h before works online, but the deposit is kept.)
     */
    public static boolean onlineCancellable(Instant now, Instant start, Duration cutoff) {
        return now.isBefore(start.minus(cutoff));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant clamp(Instant floor, Instant value) {
        return value.isBefore(floor) ? floor : value;
    }
}
