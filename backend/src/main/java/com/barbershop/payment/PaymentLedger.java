package com.barbershop.payment;

import com.barbershop.booking.Actor;
import com.barbershop.booking.Booking;
import com.barbershop.common.config.FrontendLinks;
import com.barbershop.common.error.ApiException;
import com.barbershop.payment.PaymentLogEntry.Type;
import com.barbershop.payment.PaymentProvider.CreatedPayment;
import com.barbershop.payment.PaymentProvider.PaymentRequest;
import com.barbershop.payment.PaymentProvider.RefundResult;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Low-level deposit bookkeeping, always inside the caller's transaction:
 * create the payment (+ single-use payment link), mark paid, refund or forfeit
 * on cancellation, admin refund override. Every step is recorded in
 * payment_event. Never changes the booking status — that is the lifecycle's job.
 */
@Service
public class PaymentLedger {

    private static final Logger log = LoggerFactory.getLogger(PaymentLedger.class);

    /** What happened to the deposit when a booking ended early. */
    public enum Settlement {
        /** No payment or not paid yet — nothing to do. */
        NONE,
        REFUNDED,
        FORFEITED
    }

    public record PaymentLink(Payment payment, String rawToken, String url) {}

    private final PaymentRepository payments;
    private final PaymentLogRepository paymentLog;
    private final PaymentProvider provider;
    private final ActionTokenService tokens;
    private final FrontendLinks links;
    private final Clock clock;

    public PaymentLedger(PaymentRepository payments, PaymentLogRepository paymentLog, PaymentProvider provider,
                         ActionTokenService tokens, FrontendLinks links, Clock clock) {
        this.payments = payments;
        this.paymentLog = paymentLog;
        this.provider = provider;
        this.tokens = tokens;
        this.links = links;
        this.clock = clock;
    }

    public boolean isDemo() {
        return provider.isDemo();
    }

    /** UNPAID payment for the booking's deposit + the single-use link to the payment page. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentLink create(Booking b, Instant linkExpiresAt) {
        Instant now = Instant.now(clock);
        ActionTokenService.Issued token = tokens.issue(b.getTenantId(), TokenPurpose.PAYMENT, b.getId(), null, null,
                linkExpiresAt);
        String pageUrl = links.pay(b.lang(), token.raw());
        CreatedPayment created = provider.createPayment(new PaymentRequest(b.getTenantId(), b.getId(),
                b.getDepositCents(), b.getCurrency(), "Deposit " + b.serviceNames(b.lang()), b.getCustomerEmail(),
                b.getLocale(), pageUrl));
        Payment p = payments.save(new Payment(b.getTenantId(), b.getId(), provider.id(), created.providerRef(),
                b.getDepositCents(), b.getCurrency(), now));
        record(p, Type.CREATED, null, null, provider.isDemo() ? "demo mode" : null);
        return new PaymentLink(p, token.raw(), created.paymentUrl());
    }

    /**
     * A fresh single-use link to the payment page of an UNPAID deposit (for the status page
     * and "my bookings" — the first link only went out by email). Expires with the deadline.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issuePaymentLink(Booking b) {
        Payment p = payments.findByBookingId(b.getId())
                .filter(x -> x.getStatus() == PaymentStatus.UNPAID)
                .orElseThrow(() -> ApiException.conflict("booking.not_awaiting_payment"));
        if (b.getPaymentDeadlineAt() == null || !Instant.now(clock).isBefore(b.getPaymentDeadlineAt())) {
            throw ApiException.conflict("booking.not_awaiting_payment");
        }
        return tokens.issue(b.getTenantId(), TokenPurpose.PAYMENT, b.getId(), b.getUserId(), p.getId(),
                b.getPaymentDeadlineAt()).raw();
    }

    public String paymentPageUrl(Booking b, String rawToken) {
        return links.pay(b.lang(), rawToken);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findForBooking(UUID bookingId) {
        return payments.findByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public List<PaymentLogEntry> history(UUID paymentId) {
        return paymentLog.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void markPaid(Payment p, String providerEventId) {
        p.markPaid(Instant.now(clock));
        record(p, Type.SUCCEEDED, providerEventId, Actor.PAYMENT.name(), provider.isDemo() ? "simulated in demo mode" : null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void recordDuplicate(Payment p, String providerEventId) {
        // the unique provider_event_id must not be repeated → keep it only in the detail
        record(p, Type.DUPLICATE_SUCCESS_IGNORED, null, Actor.PAYMENT.name(),
                providerEventId == null ? null : "event " + providerEventId);
    }

    /** A payment that arrived after the booking expired or was cancelled: give it back right away. */
    @Transactional(propagation = Propagation.MANDATORY)
    void refundLatePayment(Payment p, String providerEventId) {
        markPaid(p, providerEventId);
        refund(p, Type.LATE_PAYMENT_REFUNDED, Actor.SYSTEM, "booking no longer active");
    }

    /**
     * Settles the deposit when a booking is cancelled or a no-show is recorded:
     * PAID → REFUNDED (refund = true) or FORFEITED. Already settled payments are
     * returned unchanged; unpaid ones are left alone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Settlement settle(Booking b, boolean refund, Actor actor, String reason) {
        Optional<Payment> found = payments.findByBookingIdForUpdate(b.getId());
        if (found.isEmpty()) return Settlement.NONE;
        Payment p = found.get();
        return switch (p.getStatus()) {
            case UNPAID -> Settlement.NONE;
            case REFUNDED -> Settlement.REFUNDED;
            case FORFEITED -> Settlement.FORFEITED;
            case PAID -> {
                if (refund) {
                    refund(p, Type.REFUNDED, actor, reason);
                    yield Settlement.REFUNDED;
                }
                p.markForfeited(Instant.now(clock));
                record(p, Type.FORFEITED, null, actor.name(), reason);
                yield Settlement.FORFEITED;
            }
        };
    }

    /** Admin override: refunds a PAID or FORFEITED deposit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment refundOverride(UUID bookingId, String reason) {
        Payment p = payments.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> ApiException.unprocessable("payment.not_found"));
        if (p.getStatus() != PaymentStatus.PAID && p.getStatus() != PaymentStatus.FORFEITED) {
            throw ApiException.conflict("payment.not_refundable")
                    .with("paymentStatus", p.getStatus().name());
        }
        refund(p, Type.REFUND_OVERRIDE, Actor.ADMIN, reason);
        return p;
    }

    private void refund(Payment p, Type type, Actor actor, String reason) {
        RefundResult result = provider.refund(p, reason);
        if (!result.succeeded()) {
            throw new IllegalStateException("refund failed at provider: " + result.detail());
        }
        p.markRefunded(Instant.now(clock));
        String detail = join(result.detail(), result.providerRefundRef(), reason);
        record(p, type, null, actor.name(), detail);
        log.info("Payment {} of booking {} refunded ({})", p.getId(), p.getBookingId(), detail);
    }

    private void record(Payment p, Type type, String providerEventId, String actor, String detail) {
        paymentLog.save(new PaymentLogEntry(p.getId(), type, providerEventId, actor, truncate(detail),
                Instant.now(clock)));
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null || s.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(s);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
