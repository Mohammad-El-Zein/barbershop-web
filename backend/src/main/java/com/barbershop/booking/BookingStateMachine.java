package com.barbershop.booking;

import static com.barbershop.booking.Actor.ADMIN;
import static com.barbershop.booking.Actor.BARBER;
import static com.barbershop.booking.Actor.CUSTOMER;
import static com.barbershop.booking.Actor.PAYMENT;
import static com.barbershop.booking.Actor.SYSTEM;
import static com.barbershop.booking.BookingStatus.AWAITING_PAYMENT;
import static com.barbershop.booking.BookingStatus.CANCELLED;
import static com.barbershop.booking.BookingStatus.COMPLETED;
import static com.barbershop.booking.BookingStatus.CONFIRMED;
import static com.barbershop.booking.BookingStatus.DECLINED;
import static com.barbershop.booking.BookingStatus.EXPIRED;
import static com.barbershop.booking.BookingStatus.NO_SHOW;
import static com.barbershop.booking.BookingStatus.PENDING_BARBER;

import com.barbershop.common.error.ApiException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * THE single definition of the booking lifecycle: which status may move to which
 * other status, and who may trigger it. Pure (no Spring) and fully unit-tested.
 * {@link Booking#transitionTo} is the only caller; every status change in the
 * application goes through it.
 *
 * <table>
 *   <tr><th>from</th><th>to</th><th>by</th></tr>
 *   <tr><td>PENDING_BARBER</td><td>AWAITING_PAYMENT</td><td>barber, admin (accept)</td></tr>
 *   <tr><td>PENDING_BARBER</td><td>DECLINED</td><td>barber, admin</td></tr>
 *   <tr><td>PENDING_BARBER</td><td>EXPIRED</td><td>system (barber did not answer)</td></tr>
 *   <tr><td>PENDING_BARBER</td><td>CANCELLED</td><td>customer, barber, admin</td></tr>
 *   <tr><td>AWAITING_PAYMENT</td><td>CONFIRMED</td><td>payment (deposit paid / none due)</td></tr>
 *   <tr><td>AWAITING_PAYMENT</td><td>EXPIRED</td><td>system (not paid in time)</td></tr>
 *   <tr><td>AWAITING_PAYMENT</td><td>CANCELLED</td><td>customer, barber, admin</td></tr>
 *   <tr><td>CONFIRMED</td><td>COMPLETED</td><td>barber, admin, system (auto-complete)</td></tr>
 *   <tr><td>CONFIRMED</td><td>NO_SHOW</td><td>barber, admin</td></tr>
 *   <tr><td>CONFIRMED</td><td>CANCELLED</td><td>customer, barber, admin</td></tr>
 * </table>
 * COMPLETED, DECLINED, EXPIRED, CANCELLED and NO_SHOW are final.
 */
public final class BookingStateMachine {

    private static final Map<BookingStatus, Map<BookingStatus, Set<Actor>>> RULES = new EnumMap<>(BookingStatus.class);

    static {
        allow(PENDING_BARBER, AWAITING_PAYMENT, BARBER, ADMIN);
        allow(PENDING_BARBER, DECLINED, BARBER, ADMIN);
        allow(PENDING_BARBER, EXPIRED, SYSTEM);
        allow(PENDING_BARBER, CANCELLED, CUSTOMER, BARBER, ADMIN);

        allow(AWAITING_PAYMENT, CONFIRMED, PAYMENT);
        allow(AWAITING_PAYMENT, EXPIRED, SYSTEM);
        allow(AWAITING_PAYMENT, CANCELLED, CUSTOMER, BARBER, ADMIN);

        allow(CONFIRMED, COMPLETED, BARBER, ADMIN, SYSTEM);
        allow(CONFIRMED, NO_SHOW, BARBER, ADMIN);
        allow(CONFIRMED, CANCELLED, CUSTOMER, BARBER, ADMIN);
    }

    private BookingStateMachine() {}

    private static void allow(BookingStatus from, BookingStatus to, Actor... actors) {
        RULES.computeIfAbsent(from, k -> new EnumMap<>(BookingStatus.class))
                .put(to, Collections.unmodifiableSet(EnumSet.of(actors[0], actors)));
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to, Actor actor) {
        return RULES.getOrDefault(from, Map.of()).getOrDefault(to, Set.of()).contains(actor);
    }

    /** Throws 409 {@code booking.transition_not_allowed} (with from/to) if the move is not allowed. */
    public static void check(BookingStatus from, BookingStatus to, Actor actor) {
        if (!canTransition(from, to, actor)) {
            throw ApiException.conflict("booking.transition_not_allowed")
                    .with("currentStatus", from.name()).with("requestedStatus", to.name());
        }
    }

    /** Statuses this actor can move a booking to from {@code from} (for "allowed actions" in views). */
    public static Set<BookingStatus> targets(BookingStatus from, Actor actor) {
        Set<BookingStatus> result = EnumSet.noneOf(BookingStatus.class);
        RULES.getOrDefault(from, Map.of()).forEach((to, actors) -> {
            if (actors.contains(actor)) result.add(to);
        });
        return result;
    }

    /** Initial status of a new booking: online requests need the barber, manual ones are confirmed directly. */
    public static BookingStatus initialStatus(BookingSource source) {
        return source == BookingSource.ONLINE ? PENDING_BARBER : CONFIRMED;
    }
}
