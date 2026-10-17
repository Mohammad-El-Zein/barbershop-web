package com.barbershop.booking;

import com.barbershop.payment.PaymentLedger.Settlement;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published inside the booking transaction. The mail module
 * listens AFTER_COMMIT, so emails only go out for changes that really happened,
 * and a mail failure can never roll back a booking. Raw link tokens only exist
 * here (the database stores their hashes) — the email needs them.
 */
public final class BookingEvents {

    private BookingEvents() {}

    /** New online request → "request received" to the customer, "new request" (accept/decline) to the barber. */
    public record Requested(UUID bookingId, String manageToken, String decisionToken) {}

    /** Barber accepted → "please pay the deposit" with the payment link. */
    public record Accepted(UUID bookingId, String paymentUrl, Instant paymentDeadline) {}

    public record Declined(UUID bookingId) {}

    /** Not answered (from PENDING_BARBER) or not paid (from AWAITING_PAYMENT) in time. */
    public record Expired(UUID bookingId, BookingStatus from) {}

    /** Deposit paid (or none due / manual booking) → confirmation with .ics; "payment received" to the barber. */
    public record Confirmed(UUID bookingId, String manageToken, boolean depositPaid, boolean manual) {}

    public record Cancelled(UUID bookingId, Actor by, Settlement deposit) {}

    public record Reminder(UUID bookingId, String manageToken) {}

    /** Admin refund override after the fact. */
    public record Refunded(UUID bookingId) {}
}
