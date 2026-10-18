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
        @Valid @NotNull RateLimit rateLimit,
        @NotNull Captcha captcha,
        @Valid @NotNull Retention retention) {

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

    /**
     * Bootstrap admin from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD (optional) for the shop
     * APP_ADMIN_TENANT. A fresh production database has no shop (the demo seed only runs
     * with the demo profile): with APP_TENANT_NAME set, the shop is created if missing.
     */
    public record AdminBootstrap(String email, String password, String tenant, String tenantName,
                                 String tenantTimezone) {}

    public record Mail(@NotBlank String from, @Valid @NotNull Outbox outbox) {
        /**
         * Email outbox: attempts before a mail is DEAD, exponential backoff between attempts
         * (base × 2^(n-1), capped), how often the scheduler polls, rows per round.
         */
        public record Outbox(@Min(1) int maxAttempts, @NotNull Duration baseBackoff, @NotNull Duration maxBackoff,
                             @NotNull Duration pollInterval, @Min(1) int batchSize) {}
    }

    /** App-wide booking limits (per-shop rules are on the tenant). */
    public record Booking(@Min(1) int maxServices, @Min(1) int maxOpenRequestsPerEmail) {}

    /** Scheduled jobs: expiry, reminders, auto-complete. */
    public record Jobs(boolean enabled, @NotNull Duration interval, @Min(1) int batchSize) {}

    /**
     * Data-protection retention periods (RetentionJob); must match the privacy policy.
     * completedBookingsMonths 0 = keep completed bookings (bookkeeping / tax rules decide).
     */
    public record Retention(@Min(1) int cancelledBookingsMonths, @Min(0) int completedBookingsMonths,
                            @Min(1) int unverifiedAccountsDays, @Min(1) int outboxDays, @NotBlank String cron) {}

    /** Bot protection: provider none | turnstile (secret, optional verify URL for tests), prod guard override. */
    public record Captcha(String provider, String secret, String verifyUrl, boolean allowNoneInProd) {}

    /** store: memory (per instance) | redis (shared, needs redisUrl) — see RateLimitStore. */
    public record RateLimit(boolean enabled,
                            @NotBlank String store,
                            String redisUrl,
                            @Valid @NotNull Limit bookings,
                            @Valid @NotNull Limit login,
                            @Valid @NotNull Limit authMail,
                            @Valid @NotNull Limit tokenActions,
                            @Valid @NotNull Limit refresh) {
        public record Limit(@Min(1) int capacity, @NotNull Duration period) {}
    }
}
