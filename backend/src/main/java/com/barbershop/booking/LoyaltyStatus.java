package com.barbershop.booking;

/** The booking's relation to the loyalty reward. */
public enum LoyaltyStatus {
    /** No discount on this booking. */
    NONE,
    /** Discount reserved at creation; consumed when COMPLETED. */
    RESERVED,
    /** Reservation given back (cancelled / declined / expired / no-show). */
    RELEASED,
    CONSUMED
}
