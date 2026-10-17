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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barbershop.common.error.ApiException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The complete transition table — every allowed move, and that nothing else is allowed. */
class BookingStateMachineTest {

    /** from, to, actors that may do it. Everything not listed must be rejected. */
    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of(PENDING_BARBER, AWAITING_PAYMENT, EnumSet.of(BARBER, ADMIN)),
                Arguments.of(PENDING_BARBER, DECLINED, EnumSet.of(BARBER, ADMIN)),
                Arguments.of(PENDING_BARBER, EXPIRED, EnumSet.of(SYSTEM)),
                Arguments.of(PENDING_BARBER, CANCELLED, EnumSet.of(CUSTOMER, BARBER, ADMIN)),
                Arguments.of(AWAITING_PAYMENT, CONFIRMED, EnumSet.of(PAYMENT)),
                Arguments.of(AWAITING_PAYMENT, EXPIRED, EnumSet.of(SYSTEM)),
                Arguments.of(AWAITING_PAYMENT, CANCELLED, EnumSet.of(CUSTOMER, BARBER, ADMIN)),
                Arguments.of(CONFIRMED, COMPLETED, EnumSet.of(BARBER, ADMIN, SYSTEM)),
                Arguments.of(CONFIRMED, NO_SHOW, EnumSet.of(BARBER, ADMIN)),
                Arguments.of(CONFIRMED, CANCELLED, EnumSet.of(CUSTOMER, BARBER, ADMIN)));
    }

    @ParameterizedTest(name = "{0} → {1} by {2}")
    @MethodSource("allowed")
    void allowedTransitionsAreAllowedExactlyForTheirActors(BookingStatus from, BookingStatus to, Set<Actor> actors) {
        for (Actor actor : Actor.values()) {
            assertThat(BookingStateMachine.canTransition(from, to, actor))
                    .as("%s → %s by %s", from, to, actor)
                    .isEqualTo(actors.contains(actor));
        }
    }

    @Test
    void nothingOutsideTheTableIsAllowed() {
        long allowedTriples = allowed().mapToLong(a -> ((Set<?>) a.get()[2]).size()).sum();
        long counted = 0;
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                for (Actor actor : Actor.values()) {
                    if (BookingStateMachine.canTransition(from, to, actor)) counted++;
                }
            }
        }
        assertThat(counted).isEqualTo(allowedTriples);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"COMPLETED", "DECLINED", "EXPIRED", "CANCELLED", "NO_SHOW"})
    void finalStatusesCannotBeLeft(BookingStatus terminal) {
        for (Actor actor : Actor.values()) assertThat(BookingStateMachine.targets(terminal, actor)).isEmpty();
        assertThat(terminal.isFinal()).isTrue();
    }

    @Test
    void importantForbiddenMoves() {
        // a customer can never accept, pay-confirm or complete
        assertThat(BookingStateMachine.canTransition(PENDING_BARBER, AWAITING_PAYMENT, CUSTOMER)).isFalse();
        assertThat(BookingStateMachine.canTransition(AWAITING_PAYMENT, CONFIRMED, CUSTOMER)).isFalse();
        // payment cannot skip the barber
        assertThat(BookingStateMachine.canTransition(PENDING_BARBER, CONFIRMED, PAYMENT)).isFalse();
        // an admin cannot fake a payment
        assertThat(BookingStateMachine.canTransition(AWAITING_PAYMENT, CONFIRMED, ADMIN)).isFalse();
        // no-show only after confirmation; a confirmed booking does not expire
        assertThat(BookingStateMachine.canTransition(AWAITING_PAYMENT, NO_SHOW, BARBER)).isFalse();
        assertThat(BookingStateMachine.canTransition(CONFIRMED, EXPIRED, SYSTEM)).isFalse();
        // the system never declines or cancels on its own
        assertThat(BookingStateMachine.canTransition(PENDING_BARBER, DECLINED, SYSTEM)).isFalse();
        assertThat(BookingStateMachine.canTransition(CONFIRMED, CANCELLED, SYSTEM)).isFalse();
    }

    @Test
    void checkThrowsA409WithBothStatuses() {
        assertThatThrownBy(() -> BookingStateMachine.check(COMPLETED, CANCELLED, ADMIN))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.code()).isEqualTo("booking.transition_not_allowed");
                    assertThat(api.status().value()).isEqualTo(409);
                    assertThat(api.properties()).containsEntry("currentStatus", "COMPLETED")
                            .containsEntry("requestedStatus", "CANCELLED");
                });
    }

    @Test
    void slotBlockingStatusesMatchTheDatabaseConstraint() {
        assertThat(BookingStatus.SLOT_BLOCKING)
                .containsExactlyInAnyOrder(PENDING_BARBER, AWAITING_PAYMENT, CONFIRMED, COMPLETED);
        for (BookingStatus freeing : EnumSet.of(DECLINED, EXPIRED, CANCELLED, NO_SHOW)) {
            assertThat(freeing.blocksSlot()).as(freeing.name()).isFalse();
        }
    }

    @Test
    void initialStatusDependsOnTheSource() {
        assertThat(BookingStateMachine.initialStatus(BookingSource.ONLINE)).isEqualTo(PENDING_BARBER);
        assertThat(BookingStateMachine.initialStatus(BookingSource.WALK_IN)).isEqualTo(CONFIRMED);
        assertThat(BookingStateMachine.initialStatus(BookingSource.PHONE)).isEqualTo(CONFIRMED);
    }

    @Test
    void theFullHappyPathIsAValidChain() {
        BookingStatus s = PENDING_BARBER;
        BookingStateMachine.check(s, AWAITING_PAYMENT, BARBER);
        s = AWAITING_PAYMENT;
        BookingStateMachine.check(s, CONFIRMED, PAYMENT);
        s = CONFIRMED;
        BookingStateMachine.check(s, COMPLETED, SYSTEM);
    }
}
