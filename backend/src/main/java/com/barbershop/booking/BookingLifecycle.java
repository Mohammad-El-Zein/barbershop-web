package com.barbershop.booking;

import com.barbershop.booking.BookingEvents.Accepted;
import com.barbershop.booking.BookingEvents.Cancelled;
import com.barbershop.booking.BookingEvents.Confirmed;
import com.barbershop.booking.BookingEvents.Declined;
import com.barbershop.booking.BookingEvents.Expired;
import com.barbershop.booking.BookingEvents.Refunded;
import com.barbershop.booking.BookingEvents.Reminder;
import com.barbershop.common.error.ApiException;
import com.barbershop.loyalty.LoyaltyService;
import com.barbershop.payment.PaymentLedger;
import com.barbershop.payment.PaymentLedger.PaymentLink;
import com.barbershop.payment.PaymentLedger.Settlement;
import com.barbershop.tenant.BookingPolicy;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every status change of an existing booking, with its side effects, in one place:
 * <ul>
 *   <li>the booking row is locked ({@code SELECT … FOR UPDATE}), so a barber's
 *       click, the expiry job and a payment can never race each other,</li>
 *   <li>the move itself is validated by {@link BookingStateMachine},</li>
 *   <li>loyalty reservations are released/consumed, deposits refunded/forfeited,
 *       stale email links invalidated, and an event for the emails is published.</li>
 * </ul>
 * {@link Scope} limits who may touch a booking: an admin only within their
 * tenant, a barber only their own bookings, a token only its own booking.
 */
@Service
public class BookingLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycle.class);

    /** Restricts the bookings an operation may touch. Mismatch → 404 (no information leak). */
    public record Scope(UUID tenantId, UUID staffId) {
        public static final Scope ANY = new Scope(null, null);

        public static Scope tenant(UUID tenantId) {
            return new Scope(tenantId, null);
        }

        public static Scope staff(UUID tenantId, UUID staffId) {
            return new Scope(tenantId, staffId);
        }

        void check(Booking b) {
            if (tenantId != null && !tenantId.equals(b.getTenantId())) throw ApiException.notFound("booking.not_found");
            if (staffId != null && !staffId.equals(b.getStaff().getId())) throw ApiException.notFound("booking.not_found");
        }
    }

    public record CancelResult(Booking booking, Settlement deposit) {}

    private final BookingRepository bookings;
    private final TenantService tenants;
    private final LoyaltyService loyalty;
    private final PaymentLedger ledger;
    private final ActionTokenService tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingLifecycle(BookingRepository bookings, TenantService tenants, LoyaltyService loyalty,
                            PaymentLedger ledger, ActionTokenService tokens, ApplicationEventPublisher events,
                            Clock clock) {
        this.bookings = bookings;
        this.tenants = tenants;
        this.loyalty = loyalty;
        this.ledger = ledger;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
    }

    // ── barber decision ──────────────────────────────────────────────────────

    /**
     * PENDING_BARBER → AWAITING_PAYMENT: creates the UNPAID deposit payment and its
     * single-use payment link. Without a deposit (0 €) the booking is confirmed at once.
     */
    @Transactional
    public Booking accept(UUID bookingId, Actor actor, Scope scope) {
        Booking b = lock(bookingId, scope);
        Instant now = now();
        BookingPolicy policy = policy(b);
        b.transitionTo(BookingStatus.AWAITING_PAYMENT, actor, now);
        tokens.invalidateForBooking(b.getId(), TokenPurpose.BARBER_DECISION);

        if (b.getDepositCents() == 0) {
            b.transitionTo(BookingStatus.CONFIRMED, Actor.PAYMENT, now);
            events.publishEvent(new Confirmed(b.getId(), issueManageToken(b), false, false));
            log.info("Booking {} accepted by {} and confirmed (no deposit due)", b.getId(), actor);
            return b;
        }
        Instant deadline = BookingRules.paymentDeadline(now, b.getStartTime(), policy.paymentDeadline(),
                policy.deadlineBeforeStart());
        b.setPaymentDeadlineAt(deadline);
        PaymentLink link = ledger.create(b, deadline);
        events.publishEvent(new Accepted(b.getId(), link.url(), deadline));
        log.info("Booking {} accepted by {}; deposit payment {} due until {}", b.getId(), actor,
                link.payment().getId(), deadline);
        return b;
    }

    @Transactional
    public Booking decline(UUID bookingId, Actor actor, Scope scope, String reason) {
        Booking b = lock(bookingId, scope);
        b.transitionTo(BookingStatus.DECLINED, actor, now());
        b.setStatusReason(blankToNull(reason));
        releaseLoyalty(b);
        invalidateAllLinks(b);
        events.publishEvent(new Declined(b.getId()));
        log.info("Booking {} declined by {}", b.getId(), actor);
        return b;
    }

    // ── payment ──────────────────────────────────────────────────────────────

    /** AWAITING_PAYMENT → CONFIRMED. Only called by PaymentService (inside its transaction, booking locked). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPaid(Booking b) {
        b.transitionTo(BookingStatus.CONFIRMED, Actor.PAYMENT, now());
        tokens.invalidateForBooking(b.getId(), TokenPurpose.PAYMENT);
        events.publishEvent(new Confirmed(b.getId(), issueManageToken(b), true, false));
        log.info("Booking {} confirmed: deposit paid", b.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Booking lockForPayment(UUID bookingId) {
        return lock(bookingId, Scope.ANY);
    }

    // ── cancellation ─────────────────────────────────────────────────────────

    /**
     * → CANCELLED from PENDING_BARBER / AWAITING_PAYMENT / CONFIRMED. A paid deposit is
     * refunded if the shop cancels or the customer cancels at least {@code refund_cutoff}
     * before the appointment; otherwise it is forfeited. Admins may override the decision
     * with {@code refundOverride}. Customers can only cancel before the appointment starts.
     */
    @Transactional
    public CancelResult cancel(UUID bookingId, Actor actor, Scope scope, String reason, Boolean refundOverride) {
        Booking b = lock(bookingId, scope);
        Instant now = now();
        if (actor == Actor.CUSTOMER && !now.isBefore(b.getStartTime()) && b.getStatus().isOpen()) {
            throw ApiException.conflict("booking.cancel_too_late");
        }
        BookingPolicy policy = policy(b);
        boolean refund = actor == Actor.ADMIN && refundOverride != null
                ? refundOverride
                : BookingRules.depositRefundable(actor, now, b.getStartTime(), policy.refundCutoff());
        b.transitionTo(BookingStatus.CANCELLED, actor, now);
        b.setStatusReason(blankToNull(reason));
        Settlement deposit = ledger.settle(b, refund, actor, "cancelled by " + actor.name().toLowerCase());
        releaseLoyalty(b);
        invalidateAllLinks(b);
        events.publishEvent(new Cancelled(b.getId(), actor, deposit));
        log.info("Booking {} cancelled by {} (deposit: {})", b.getId(), actor, deposit);
        return new CancelResult(b, deposit);
    }

    // ── after the appointment ────────────────────────────────────────────────

    /** CONFIRMED → COMPLETED: counts a loyalty visit (or consumes the reserved reward). */
    @Transactional
    public Booking complete(UUID bookingId, Actor actor, Scope scope) {
        Booking b = lock(bookingId, scope);
        Instant now = now();
        if (actor != Actor.SYSTEM && now.isBefore(b.getStartTime())) throw ApiException.conflict("booking.not_started");
        b.transitionTo(BookingStatus.COMPLETED, actor, now);
        if (b.getUserId() != null) {
            boolean consumed = loyalty.recordCompletedVisit(b.getUserId(), b.getTenantId(), b.getId(), policy(b));
            if (consumed) b.setLoyaltyStatus(LoyaltyStatus.CONSUMED);
            else if (b.getLoyaltyStatus() == LoyaltyStatus.RESERVED) b.setLoyaltyStatus(LoyaltyStatus.RELEASED);
        }
        tokens.invalidateForBooking(b.getId(), TokenPurpose.BOOKING_MANAGE);
        log.info("Booking {} completed ({})", b.getId(), actor);
        return b;
    }

    /** CONFIRMED → NO_SHOW: the deposit is kept, a reserved reward goes back to the customer. */
    @Transactional
    public Booking noShow(UUID bookingId, Actor actor, Scope scope) {
        Booking b = lock(bookingId, scope);
        Instant now = now();
        if (now.isBefore(b.getStartTime())) throw ApiException.conflict("booking.not_started");
        b.transitionTo(BookingStatus.NO_SHOW, actor, now);
        ledger.settle(b, false, actor, "no-show");
        releaseLoyalty(b);
        tokens.invalidateForBooking(b.getId(), TokenPurpose.BOOKING_MANAGE);
        log.info("Booking {} marked as no-show by {}", b.getId(), actor);
        return b;
    }

    @Transactional
    public Booking refundOverride(UUID bookingId, Scope scope, String reason) {
        Booking b = lock(bookingId, scope);
        ledger.refundOverride(b.getId(), blankToNull(reason) == null ? "admin override" : reason);
        events.publishEvent(new Refunded(b.getId()));
        return b;
    }

    // ── scheduled jobs (each call = one booking, own transaction, re-checked under lock) ──

    /** Expires an unanswered request or an unpaid acceptance whose deadline has passed. */
    @Transactional
    public boolean expireIfDue(UUID bookingId) {
        Booking b = lock(bookingId, Scope.ANY);
        Instant now = now();
        BookingStatus from = b.getStatus();
        boolean due = (from == BookingStatus.PENDING_BARBER && isDue(b.getBarberDeadlineAt(), now))
                || (from == BookingStatus.AWAITING_PAYMENT && isDue(b.getPaymentDeadlineAt(), now));
        if (!due) return false;
        b.transitionTo(BookingStatus.EXPIRED, Actor.SYSTEM, now);
        releaseLoyalty(b);
        invalidateAllLinks(b);
        events.publishEvent(new Expired(b.getId(), from));
        log.info("Booking {} expired (was {})", b.getId(), from);
        return true;
    }

    @Transactional
    public boolean autoCompleteIfDue(UUID bookingId) {
        Booking b = lock(bookingId, Scope.ANY);
        if (b.getStatus() != BookingStatus.CONFIRMED) return false;
        if (now().isBefore(b.getEndTime().plus(policy(b).autoCompleteAfter()))) return false;
        complete(b.getId(), Actor.SYSTEM, Scope.ANY);
        return true;
    }

    /**
     * Sends the reminder once, {@code reminder_before} ahead. A booking confirmed only
     * shortly before (inside the window) is skipped: the confirmation just went out.
     */
    @Transactional
    public boolean remindIfDue(UUID bookingId) {
        Booking b = lock(bookingId, Scope.ANY);
        Instant now = now();
        if (b.getStatus() != BookingStatus.CONFIRMED || b.getReminderSentAt() != null) return false;
        if (b.getCustomerEmail() == null || !now.isBefore(b.getStartTime())) return false;
        Instant windowStart = b.getStartTime().minus(policy(b).reminderBefore());
        if (now.isBefore(windowStart)) return false;
        b.markReminderSent(now);
        if (b.getConfirmedAt() != null && b.getConfirmedAt().isAfter(windowStart)) return false;
        events.publishEvent(new Reminder(b.getId(), issueManageToken(b)));
        return true;
    }

    /** New manage link for a booking (customer emails). Valid until the appointment ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issueManageToken(Booking b) {
        return tokens.issue(b.getTenantId(), TokenPurpose.BOOKING_MANAGE, b.getId(), b.getUserId(), null,
                b.getEndTime()).raw();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Booking lock(UUID bookingId, Scope scope) {
        Booking b = bookings.findForUpdate(bookingId).orElseThrow(() -> ApiException.notFound("booking.not_found"));
        scope.check(b);
        return b;
    }

    private void releaseLoyalty(Booking b) {
        if (b.getUserId() != null && b.getLoyaltyStatus() == LoyaltyStatus.RESERVED) {
            loyalty.release(b.getUserId(), b.getTenantId(), b.getId());
            b.setLoyaltyStatus(LoyaltyStatus.RELEASED);
        }
    }

    private void invalidateAllLinks(Booking b) {
        tokens.invalidateForBooking(b.getId(), TokenPurpose.BARBER_DECISION);
        tokens.invalidateForBooking(b.getId(), TokenPurpose.PAYMENT);
        tokens.invalidateForBooking(b.getId(), TokenPurpose.BOOKING_MANAGE);
    }

    private BookingPolicy policy(Booking b) {
        Tenant tenant = tenants.require(b.getTenantId());
        return tenant.policy();
    }

    private static boolean isDue(Instant deadline, Instant now) {
        return deadline != null && !now.isBefore(deadline);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
