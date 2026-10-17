package com.barbershop.booking;

/** Who triggers a booking transition. */
public enum Actor {
    CUSTOMER,
    BARBER,
    ADMIN,
    /** Scheduled jobs (expiry, auto-complete). */
    SYSTEM,
    /** The payment flow (provider event / demo simulation). */
    PAYMENT
}
