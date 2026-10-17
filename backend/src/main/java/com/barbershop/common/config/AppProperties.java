package com.barbershop.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration (all values come from environment
 * variables). Per-shop booking rules (deadlines, deposit, loyalty, slot grid) are
 * NOT here: they live on the tenant ({@link com.barbershop.tenant.BookingPolicy}).
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Auth auth,
        @NotNull Tokens tokens,
        @Valid @NotNull Links links,
        @NotNull AdminBootstrap admin,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Booking booking,
        @Valid @NotNull Jobs jobs,
        @Valid @NotNull RateLimit rateLimit) {

    public record Cors(@NotNull List<String> allowedOrigins) {}

    /** Short-lived access tokens (HS256). */
    public record Jwt(@NotBlank String secret, @NotNull Duration accessTtl, @NotBlank String issuer) {}

    public record Auth(
            @NotNull Duration refreshTtl,
            @NotBlank String refreshCookieName,
            @NotBlank String refreshCookiePath,
            boolean cookieSecure,
            @NotBlank String cookieSameSite,
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockout,
            @NotNull Duration emailVerifyTtl,
            @NotNull Duration passwordResetTtl,
            @NotNull Duration barberInviteTtl,
            @Min(8) int minPasswordLength,
            @NotBlank String consentVersion) {}

    /** Key for hashing email-link tokens; empty → derived from the JWT secret. */
    public record Tokens(String secret) {}

    /**
     * Frontend paths used in email links, relative to {@code frontendBaseUrl}.
     * Placeholders: {lang}, {token}.
     */
    public record Links(
            @NotBlank String book,
            @NotBlank String manage,
            @NotBlank String pay,
            @NotBlank String barberDecision,
            @NotBlank String verifyEmail,
            @NotBlank String resetPassword,
            @NotBlank String account) {}

    /** Bootstrap admin from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD (optional). */
    public record AdminBootstrap(String email, String password, String tenant) {}

    public record Mail(@NotBlank String from) {}

    /** App-wide booking limits (per-shop rules are on the tenant). */
    public record Booking(@Min(1) int maxServices, @Min(1) int maxOpenRequestsPerEmail) {}

    /** Scheduled jobs: expiry, reminders, auto-complete. */
    public record Jobs(boolean enabled, @NotNull Duration interval, @Min(1) int batchSize) {}

    public record RateLimit(boolean enabled,
                            @Valid @NotNull Limit bookings,
                            @Valid @NotNull Limit login,
                            @Valid @NotNull Limit authMail,
                            @Valid @NotNull Limit tokenActions,
                            @Valid @NotNull Limit refresh) {
        public record Limit(@Min(1) int capacity, @NotNull Duration period) {}
    }
}
