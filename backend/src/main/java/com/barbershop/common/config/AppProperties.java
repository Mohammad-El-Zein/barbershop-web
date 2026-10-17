package com.barbershop.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed view of the {@code app.*} configuration (all values come from environment variables). */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @NotNull AdminBootstrap admin,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Booking booking,
        @Valid @NotNull RateLimit rateLimit) {

    public record Cors(@NotNull List<String> allowedOrigins) {}

    public record Jwt(@NotBlank String secret, @NotNull Duration ttl, @NotBlank String issuer) {}

    /** Bootstrap admin from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD (optional). */
    public record AdminBootstrap(String email, String password, String tenant) {}

    public record Mail(@NotBlank String from) {}

    public record Booking(
            @NotNull Duration slotStep,
            @NotNull Duration minNotice,
            @Min(1) int maxDaysAhead,
            @NotNull Duration cancelCutoff,
            boolean autoConfirm) {}

    public record RateLimit(boolean enabled, @Valid @NotNull Limit bookings, @Valid @NotNull Limit login) {
        public record Limit(@Min(1) int capacity, @NotNull Duration period) {}
    }
}
