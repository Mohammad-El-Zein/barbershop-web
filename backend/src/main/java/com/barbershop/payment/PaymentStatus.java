package com.barbershop.payment;

/** Deposit payment status. */
public enum PaymentStatus {
    /** Created when the barber accepts; waiting for the customer. */
    UNPAID,
    PAID,
    /** Given back (cancelled in time, cancelled by the shop, or admin override). */
    REFUNDED,
    /** Kept by the shop (late cancellation or no-show). */
    FORFEITED
}
