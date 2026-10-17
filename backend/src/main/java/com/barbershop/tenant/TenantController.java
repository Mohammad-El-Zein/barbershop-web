package com.barbershop.tenant;

import com.barbershop.payment.PaymentProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}")
@Tag(name = "Public: shop")
public class TenantController {

    private final TenantService tenants;
    private final PaymentProvider payments;

    public TenantController(TenantService tenants, PaymentProvider payments) {
        this.tenants = tenants;
        this.payments = payments;
    }

    @GetMapping
    @Operation(summary = "Shop info (name, timezone, currency, theme) and the booking rules customers see")
    public TenantView get(@PathVariable("tenant") String slug) {
        Tenant t = tenants.requireBySlug(slug);
        return TenantView.of(t, payments.isDemo());
    }

    /** The customer-relevant part of the booking policy (for texts like "10 € deposit, free cancellation up to 24 h"). */
    public record BookingRules(int depositCents, int refundCutoffMin, int onlineCancelCutoffMin,
                               int barberResponseMin, int paymentDeadlineMin, int minLeadMin, int maxDaysAhead,
                               int slotStepMin, int loyaltyThreshold, int loyaltyPercent, boolean demoPayments) {}

    public record TenantView(String slug, String name, String timezone, String defaultLocale, String currency,
                             String logoUrl, Map<String, Object> theme, BookingRules bookingRules) {
        static TenantView of(Tenant t, boolean demo) {
            BookingPolicy p = t.policy();
            return new TenantView(t.getSlug(), t.getName(), t.getTimezone(), t.getDefaultLocale(), t.getCurrency(),
                    t.getLogoUrl(), t.getTheme(), new BookingRules(p.getDepositCents(), p.getRefundCutoffMin(),
                    p.getOnlineCancelCutoffMin(), p.getBarberResponseMin(), p.getPaymentDeadlineMin(),
                    p.getMinLeadMin(), p.getMaxDaysAhead(), p.getSlotStepMin(), p.getLoyaltyThreshold(),
                    p.getLoyaltyPercent(), demo));
        }
    }
}
