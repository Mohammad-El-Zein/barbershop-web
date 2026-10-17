package com.barbershop.booking;

/** How a booking was made. Manual bookings (walk-in / phone) skip barber approval and deposit. */
public enum BookingSource {
    ONLINE,
    WALK_IN,
    PHONE
}
