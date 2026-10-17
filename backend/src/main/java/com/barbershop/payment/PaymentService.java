package com.barbershop.payment;

import com.barbershop.booking.Actor;
import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingLifecycle;
import com.barbershop.booking.BookingLifecycle.Scope;
import com.barbershop.booking.BookingStatus;
import com.barbershop.common.error.ApiException;
import com.barbershop.token.ActionToken;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment outcomes. {@link #handlePaymentSucceeded} is THE entry point for "money
 * arrived": the demo "Simulate payment" button calls it today, a real provider's
 * webhook will call it later — same code path, same guarantees:
 * <ul>
 *   <li>idempotent: a second success for the same payment changes nothing and
 *       sends no second email (recorded as DUPLICATE_SUCCESS_IGNORED),</li>
 *   <li>serialized: the payment and the booking rows are locked,</li>
 *   <li>a payment that arrives after the booking expired/was cancelled is
 *       refunded immediately instead of resurrecting the booking.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public enum Outcome { CONFIRMED, ALREADY_PROCESSED, REFUNDED_LATE }

    public record Result(Outcome outcome, Payment payment, Booking booking) {}

    private final PaymentRepository payments;
    private final PaymentLedger ledger;
    private final PaymentProvider provider;
    private final BookingLifecycle lifecycle;
    private final ActionTokenService tokens;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentLedger ledger, PaymentProvider provider,
                          BookingLifecycle lifecycle, ActionTokenService tokens, Clock clock) {
        this.payments = payments;
        this.ledger = ledger;
        this.provider = provider;
        this.lifecycle = lifecycle;
        this.tokens = tokens;
        this.clock = clock;
    }

    public boolean isDemo() {
        return provider.isDemo();
    }

    /**
     * "The provider says this payment succeeded." Idempotent.
     *
     * @param providerEventId the provider's event id (webhook), used for exactly-once
     *                        bookkeeping; may be null
     */
    @Transactional
    public Result handlePaymentSucceeded(String paymentRef, String providerEventId) {
        Payment p = payments.findByProviderRefForUpdate(paymentRef)
                .orElseThrow(() -> ApiException.notFound("payment.not_found"));
        Booking b = lifecycle.lockForPayment(p.getBookingId());
        if (p.getStatus() != PaymentStatus.UNPAID) {
            ledger.recordDuplicate(p, providerEventId);
            log.info("Payment {} already {} — success event ignored (idempotent)", p.getId(), p.getStatus());
            return new Result(Outcome.ALREADY_PROCESSED, p, b);
        }
        if (b.getStatus() != BookingStatus.AWAITING_PAYMENT) {
            ledger.refundLatePayment(p, providerEventId);
            log.warn("Payment {} arrived for booking {} in status {} — refunded", p.getId(), b.getId(), b.getStatus());
            return new Result(Outcome.REFUNDED_LATE, p, b);
        }
        ledger.markPaid(p, providerEventId);
        lifecycle.confirmPaid(b);
        return new Result(Outcome.CONFIRMED, p, b);
    }

    @Transactional
    public Result handlePaymentSucceeded(String paymentRef) {
        return handlePaymentSucceeded(paymentRef, null);
    }

    // ── demo payment page (/[locale]/pay/[token]) ────────────────────────────

    /** GET of the payment page: resolves the link, changes nothing. */
    @Transactional(readOnly = true)
    public PageData page(String rawToken) {
        ActionToken t = tokens.find(TokenPurpose.PAYMENT, rawToken);
        Payment p = payments.findByBookingId(t.getBookingId()).orElseThrow(() -> ApiException.notFound("token.invalid"));
        if (t.isUsed() || t.isExpired(Instant.now(clock))) {
            throw linkGone(t, p);
        }
        return new PageData(t, p);
    }

    public record PageData(ActionToken token, Payment payment) {}

    /**
     * "Simulate payment" (demo only). Repeating it is harmless: if the payment is
     * already PAID the current state is returned (200, alreadyProcessed) — no
     * second confirmation, no second email.
     */
    @Transactional
    public Result simulateSuccess(String rawToken) {
        if (!provider.isDemo()) throw ApiException.notFound("request.not_found");
        ActionToken t = tokens.lock(TokenPurpose.PAYMENT, rawToken);
        Payment p = payments.findByBookingId(t.getBookingId())
                .orElseThrow(() -> ApiException.notFound("token.invalid"));
        if (t.isUsed()) {
            if (p.getStatus() == PaymentStatus.PAID) return handlePaymentSucceeded(p.getProviderRef(), null);
            throw linkGone(t, p);
        }
        tokens.requireUsable(t);
        tokens.markUsed(t);
        return handlePaymentSucceeded(p.getProviderRef(), "demo-" + t.getId());
    }

    /** "Cancel" on the payment page: the customer cancels the booking (nothing was paid). */
    @Transactional
    public BookingLifecycle.CancelResult cancelFromPaymentPage(String rawToken) {
        ActionToken t = tokens.consume(TokenPurpose.PAYMENT, rawToken);
        return lifecycle.cancel(t.getBookingId(), Actor.CUSTOMER, Scope.ANY, "cancelled on the payment page", null);
    }

    /** Webhook entry for real providers; the demo provider has none (→ 404). */
    @Transactional
    public void handleWebhook(String providerId, String payload, Map<String, String> headers) {
        if (provider.isDemo() || !provider.id().equals(providerId)) throw ApiException.notFound("request.not_found");
        provider.parseEvent(payload, headers).ifPresent(event -> {
            if (event.type() == PaymentProvider.ProviderEvent.Type.PAYMENT_SUCCEEDED) {
                handlePaymentSucceeded(event.providerRef(), event.eventId());
            }
        });
    }

    private static ApiException linkGone(ActionToken t, Payment p) {
        return ApiException.gone(t.isUsed() ? "token.used" : "token.expired").with("paymentStatus", p.getStatus().name());
    }
}
