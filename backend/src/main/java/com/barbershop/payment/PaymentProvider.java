package com.barbershop.payment;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to a payment service provider. Only {@link DemoPaymentProvider} exists
 * today; a real one (e.g. Stripe Checkout + webhook, see backend/README.md)
 * implements this interface and is selected with {@code payment.provider}.
 *
 * <p>Contract: the provider never changes bookings itself. Whatever confirms a
 * payment — a verified webhook ({@link #parseEvent}) or the demo button — ends
 * up in {@link PaymentService#handlePaymentSucceeded}, which is idempotent.
 */
public interface PaymentProvider {

    /** Stored in payment.provider, e.g. "demo" or "stripe". */
    String id();

    /** True for the demo provider: no real money; UI and emails show a "demo mode" note. */
    boolean isDemo();

    /**
     * Creates the payment at the provider.
     *
     * @return the provider's reference (unique) and the URL the customer is sent to.
     *         For the demo provider that is our own frontend page {@code /[locale]/pay/[token]};
     *         a real provider would return its hosted checkout URL (or our page redirects there).
     */
    CreatedPayment createPayment(PaymentRequest request);

    /** Refunds a paid payment (fully). The demo provider only simulates it. */
    RefundResult refund(Payment payment, String reason);

    /**
     * Verifies and parses a provider webhook (signature check happens here).
     * Returns empty for events we do not care about.
     *
     * @throws UnsupportedOperationException if this provider has no webhooks (demo)
     */
    Optional<ProviderEvent> parseEvent(String payload, Map<String, String> headers);

    record PaymentRequest(UUID tenantId, UUID bookingId, int amountCents, String currency, String description,
                          String customerEmail, String lang, String paymentPageUrl) {}

    record CreatedPayment(String providerRef, String paymentUrl) {}

    record RefundResult(boolean succeeded, String providerRefundRef, String detail) {}

    record ProviderEvent(String eventId, Type type, String providerRef) {
        public enum Type { PAYMENT_SUCCEEDED, PAYMENT_FAILED, REFUNDED }
    }
}
