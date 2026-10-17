package com.barbershop.token;

/** What an email-link token may be used for (part of its hash, see {@link TokenHasher}). */
public enum TokenPurpose {
    /** Customer confirms their email address (account). */
    EMAIL_VERIFY,
    /** Forgot password, and the "set your password" invite for barbers. */
    PASSWORD_RESET,
    /** Barber accepts or declines a booking request. */
    BARBER_DECISION,
    /** Guest/customer views or cancels their booking. */
    BOOKING_MANAGE,
    /** Customer opens the (demo) payment page for the deposit. */
    PAYMENT
}
