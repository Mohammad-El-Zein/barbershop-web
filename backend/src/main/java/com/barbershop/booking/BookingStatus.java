package com.barbershop.booking;

import java.util.EnumSet;
import java.util.Set;

/**
 * Booking lifecycle. Allowed transitions are defined in exactly one place:
 * {@link BookingStateMachine}.
 *
 * <pre>
 * PENDING_BARBER ──accept──▶ AWAITING_PAYMENT ──paid──▶ CONFIRMED ──▶ COMPLETED
 *       │                          │                       │
 *       ├─▶ DECLINED               ├─▶ EXPIRED             ├─▶ NO_SHOW
 *       ├─▶ EXPIRED                └─▶ CANCELLED           └─▶ CANCELLED
 *       └─▶ CANCELLED
 * </pre>
 */
public enum BookingStatus {
    /** Request sent, waiting for the barber to accept or decline. */
    PENDING_BARBER,
    /** Accepted; waiting for the customer's deposit. */
    AWAITING_PAYMENT,
    /** Deposit paid (or manual booking without deposit). */
    CONFIRMED,
    COMPLETED,
    DECLINED,
    EXPIRED,
    CANCELLED,
    NO_SHOW;

    /**
     * Statuses that occupy the barber's time — mirrored by the WHERE clause of the
     * DB exclusion constraint booking_no_overlap. COMPLETED keeps blocking too: the
     * time was really used (and a booking can be completed shortly after it started).
     */
    public static final Set<BookingStatus> SLOT_BLOCKING =
            EnumSet.of(PENDING_BARBER, AWAITING_PAYMENT, CONFIRMED, COMPLETED);

    /** Still in progress (the customer may cancel). */
    public static final Set<BookingStatus> OPEN = EnumSet.of(PENDING_BARBER, AWAITING_PAYMENT, CONFIRMED);

    public boolean blocksSlot() {
        return SLOT_BLOCKING.contains(this);
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isFinal() {
        return !isOpen();
    }
}
