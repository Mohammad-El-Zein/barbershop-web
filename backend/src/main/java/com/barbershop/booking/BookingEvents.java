package com.barbershop.booking;

import java.util.UUID;

/**
 * Domain events published inside the booking transaction. The mail module
 * listens AFTER_COMMIT, so emails only go out for bookings that really exist,
 * and a mail failure can never roll back a booking.
 */
public final class BookingEvents {

    private BookingEvents() {}

    /** rawCancelToken is only available here (the DB stores its hash) — the email link needs it. */
    public record BookingCreated(UUID bookingId, String rawCancelToken) {}

    /** PENDING → CONFIRMED by an admin. */
    public record BookingConfirmed(UUID bookingId) {}

    public record BookingCancelled(UUID bookingId, boolean byCustomer) {}
}
