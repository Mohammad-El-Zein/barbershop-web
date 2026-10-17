package com.barbershop.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code payment.*}: which provider is active, and the prod safety switch for the demo provider. */
@ConfigurationProperties("payment")
public record PaymentProperties(@DefaultValue("demo") String provider, @DefaultValue Demo demo) {

    public record Demo(@DefaultValue("false") boolean allowInProd) {}
}
