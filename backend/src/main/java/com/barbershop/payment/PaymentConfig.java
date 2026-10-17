package com.barbershop.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Selects the {@link PaymentProvider} from {@code payment.provider} and enforces
 * the safety guard: with the {@code prod} profile the demo provider is refused
 * (the application does not start) unless {@code payment.demo.allow-in-prod=true}.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaymentProvider paymentProvider(PaymentProperties props, Environment env) {
        String provider = props.provider() == null ? "" : props.provider().trim().toLowerCase();
        checkDemoGuard(provider, props, env);
        return switch (provider) {
            case DemoPaymentProvider.ID -> {
                log.warn("Payment provider: DEMO — no real payments, deposits are only simulated");
                yield new DemoPaymentProvider();
            }
            // case "stripe" -> new StripePaymentProvider(...);   see backend/README.md
            default -> throw new IllegalStateException("Unknown payment.provider '" + props.provider()
                    + "'. Supported: demo");
        };
    }

    static void checkDemoGuard(String provider, PaymentProperties props, Environment env) {
        if (!DemoPaymentProvider.ID.equals(provider) || !env.acceptsProfiles(Profiles.of("prod"))) return;
        if (props.demo() == null || !props.demo().allowInProd()) {
            throw new IllegalStateException("""
                    Refusing to start: the `prod` profile is active but payment.provider=demo \
                    (no real payments). Configure a real provider, or set \
                    payment.demo.allow-in-prod=true (PAYMENT_DEMO_ALLOW_IN_PROD=true) to run the demo in production \
                    on purpose.""");
        }
        log.warn("!!! payment.provider=demo is running with the prod profile (payment.demo.allow-in-prod=true) !!!");
    }
}
