package com.barbershop.booking;

import com.barbershop.booking.BookingDtos.BookingView;
import com.barbershop.booking.BookingDtos.DecisionView;
import com.barbershop.booking.BookingDtos.StaffRef;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.ActionToken;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
    private final Clock clock;

    public BookingLinkService(ActionTokenService tokens, BookingRepository bookings, BookingLifecycle lifecycle,
                              BookingViews views, LoyaltyNotes loyaltyNotes, TenantService tenants, Clock clock) {
        this.tokens = tokens;
        this.bookings = bookings;
        this.lifecycle = lifecycle;
        this.views = views;
        this.loyaltyNotes = loyaltyNotes;
        this.tenants = tenants;
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
