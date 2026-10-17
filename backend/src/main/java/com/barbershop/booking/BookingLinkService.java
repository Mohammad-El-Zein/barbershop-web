package com.barbershop.booking;

import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.DecisionView;
import com.barbershop.booking.BookingDtos.PaymentLinkView;
import com.barbershop.booking.BookingDtos.StaffRef;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.payment.PaymentLedger;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.ActionToken;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The email-link pages: the customer's manage link and the barber's accept/decline
 * link. Email scanners open links automatically, therefore:
 * <ul>
 *   <li>GET ({@code …Page}) only shows the booking — it never changes anything,</li>
 *   <li>the action is a POST from the page's confirm button; it consumes the
 *       single-use token in the same transaction as the status change.</li>
 * </ul>
 * Used or expired links answer 410 with a localized message and the booking's
 * current status, so the page can say e.g. "already accepted".
 */
@Service
public class BookingLinkService {

    private final ActionTokenService tokens;
    private final BookingRepository bookings;
    private final BookingLifecycle lifecycle;
    private final BookingViews views;
    private final LoyaltyNotes loyaltyNotes;
    private final TenantService tenants;
    private final PaymentLedger ledger;
    private final Clock clock;

    public BookingLinkService(ActionTokenService tokens, BookingRepository bookings, BookingLifecycle lifecycle,
                              BookingViews views, LoyaltyNotes loyaltyNotes, TenantService tenants,
                              PaymentLedger ledger, Clock clock) {
        this.tokens = tokens;
        this.bookings = bookings;
        this.lifecycle = lifecycle;
        this.views = views;
        this.loyaltyNotes = loyaltyNotes;
        this.tenants = tenants;
        this.ledger = ledger;
        this.clock = clock;
    }

    // ── customer manage link ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingView managePage(String rawToken) {
        Booking b = usableBooking(TokenPurpose.BOOKING_MANAGE, rawToken);
        return views.customer(b, tenants.require(b.getTenantId()), Messages.currentLang());
    }

    @Transactional
    public BookingView cancelViaManageLink(String rawToken, String reason) {
        ActionToken t = tokens.consume(TokenPurpose.BOOKING_MANAGE, rawToken);
        lifecycle.cancel(t.getBookingId(), Actor.CUSTOMER, Scope.ANY, reason, null);
        Booking b = load(t);
        return views.customer(b, tenants.require(b.getTenantId()), Messages.currentLang());
    }

    // ── status page right after booking (read-only link) ─────────────────────

    /** GET: the live status for the status page. The link is reusable; only expiry ends it. */
    @Transactional(readOnly = true)
    public BookingView statusPage(String rawToken) {
        Booking b = statusBooking(rawToken);
        return views.customer(b, tenants.require(b.getTenantId()), Messages.currentLang());
    }

    /** POST: a fresh single-use payment link while the booking awaits the deposit. */
    @Transactional
    public PaymentLinkView paymentLinkFromStatus(String rawToken) {
        return paymentLink(lifecycle.lockForPayment(statusBooking(rawToken).getId()));
    }

    /** The same for a logged-in customer's own booking ("pay now" in my bookings). */
    @Transactional
    public PaymentLinkView paymentLinkForOwner(UUID tenantId, UUID userId, UUID bookingId) {
        Booking b = lifecycle.lockForPayment(bookingId);
        if (!tenantId.equals(b.getTenantId()) || !userId.equals(b.getUserId())) {
            throw ApiException.notFound("booking.not_found");
        }
        return paymentLink(b);
    }

    private PaymentLinkView paymentLink(Booking b) {
        if (b.getStatus() != BookingStatus.AWAITING_PAYMENT) throw ApiException.conflict("booking.not_awaiting_payment");
        String raw = ledger.issuePaymentLink(b);
        return new PaymentLinkView(raw, ledger.paymentPageUrl(b, raw));
    }

    private Booking statusBooking(String rawToken) {
        ActionToken t = tokens.find(TokenPurpose.BOOKING_STATUS, rawToken);
        if (t.isExpired(Instant.now(clock))) throw ApiException.gone("token.expired");
        return load(t);
    }

    // ── barber accept / decline link ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public DecisionView decisionPage(String rawToken) {
        Booking b = usableBooking(TokenPurpose.BARBER_DECISION, rawToken);
        return decisionView(b);
    }

    @Transactional
    public DecisionView accept(String rawToken) {
        ActionToken t = tokens.consume(TokenPurpose.BARBER_DECISION, rawToken);
        lifecycle.accept(t.getBookingId(), Actor.BARBER, Scope.ANY);
        return decisionView(load(t));
    }

    @Transactional
    public DecisionView decline(String rawToken, String reason) {
        ActionToken t = tokens.consume(TokenPurpose.BARBER_DECISION, rawToken);
        lifecycle.decline(t.getBookingId(), Actor.BARBER, Scope.ANY, reason);
        return decisionView(load(t));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Resolves the link for a GET; used/expired → 410 with the booking's current status. */
    private Booking usableBooking(TokenPurpose purpose, String rawToken) {
        ActionToken t = tokens.find(purpose, rawToken);
        Booking b = load(t);
        if (t.isUsed()) throw ApiException.gone("token.used").with("bookingStatus", b.getStatus().name());
        if (t.isExpired(Instant.now(clock))) {
            throw ApiException.gone("token.expired").with("bookingStatus", b.getStatus().name());
        }
        return b;
    }

    private Booking load(ActionToken t) {
        return bookings.findWithDetailsById(t.getBookingId()).orElseThrow(() -> ApiException.notFound("token.invalid"));
    }

    private DecisionView decisionView(Booking b) {
        Tenant tenant = tenants.require(b.getTenantId());
        ZoneId zone = tenant.zone();
        Lang lang = Messages.currentLang();
        List<String> actions = b.getStatus() == BookingStatus.PENDING_BARBER ? List.of("ACCEPT", "DECLINE") : List.of();
        return new DecisionView(b.getId(), b.getStatus(), b.getCustomerName(), views.lines(b, lang),
                new StaffRef(b.getStaff().getId(), b.getStaff().getName()),
                b.getStartTime().atZone(zone).toOffsetDateTime(), b.getEndTime().atZone(zone).toOffsetDateTime(),
                b.durationMin(), b.getCustomerNote(), loyaltyNotes.forBarber(b, tenant.policy(), lang),
                views.price(b, lang),
                b.getBarberDeadlineAt() == null ? null : b.getBarberDeadlineAt().atZone(zone).toOffsetDateTime(),
                actions);
    }
}
