package com.barbershop.payment;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * DEMO ONLY — no real payment system, no money moves, no card data anywhere.
 * The "payment page" is our own frontend page with a "Simulate payment" button;
 * that button calls the backend, which goes through exactly the same
 * {@link PaymentService#handlePaymentSucceeded} a real webhook would use.
 * Refunds are only recorded ("refund simulated in demo mode").
 */
public class DemoPaymentProvider implements PaymentProvider {

    public static final String ID = "demo";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isDemo() {
        return true;
    }

    @Override
    public CreatedPayment createPayment(PaymentRequest request) {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return new CreatedPayment("demo_pay_" + HexFormat.of().formatHex(bytes), request.paymentPageUrl());
    }

    @Override
    public RefundResult refund(Payment payment, String reason) {
        return new RefundResult(true, "demo_refund_" + payment.getProviderRef().substring("demo_pay_".length()),
                "refund simulated in demo mode");
    }

    @Override
    public Optional<ProviderEvent> parseEvent(String payload, Map<String, String> headers) {
        throw new UnsupportedOperationException("the demo provider has no webhooks — use the simulate endpoint");
    }
}
