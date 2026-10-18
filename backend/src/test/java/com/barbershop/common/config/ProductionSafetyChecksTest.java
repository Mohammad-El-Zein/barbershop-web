package com.barbershop.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Secret strength and the prod-only checks (cookie, CORS, frontend URL). */
class ProductionSafetyChecksTest {

    private static final String STRONG = "q8Vf2LwZ7rT1mNcX9bYpK3sJhD6gA0eU5iO4lQzWxRvB";

    private static List<String> secret(String value) {
        List<String> p = new ArrayList<>();
        ProductionSafetyChecks.secret("APP_JWT_SECRET", value, p);
        return p;
    }

    @Test
    void secretsNeedLengthVarietyAndNoPlaceholder() {
        assertThat(secret(STRONG)).isEmpty();
        assertThat(secret("short")).singleElement().asString().contains("at least 32");
        assertThat(secret("a".repeat(40))).singleElement().asString().contains("entropy");
        assertThat(secret("please-change-me-" + STRONG)).singleElement().asString().contains("placeholder");
        assertThat(secret(null)).hasSize(1);
    }

    @Test
    void prodNeedsSecureCookieHttpsCorsAndHttpsFrontend() {
        assertThat(String.join("\n", ProductionSafetyChecks.check(props(false, List.of("*", "http://shop.example"),
                "http://shop.example"), true)))
                .contains("APP_COOKIE_SECURE", "'*'", "'http://shop.example'", "APP_FRONTEND_BASE_URL");
        assertThat(ProductionSafetyChecks.check(props(true, List.of("https://www.shop.example"), "https://www.shop.example"),
                true)).isEmpty();
        // outside prod only the secrets are checked (warnings)
        assertThat(ProductionSafetyChecks.check(props(false, List.of("http://localhost:3000"), "http://localhost:3000"),
                false)).isEmpty();
    }

    private static AppProperties props(boolean secureCookie, List<String> origins, String frontend) {
        return new AppProperties(frontend, new AppProperties.Cors(origins),
                new AppProperties.Jwt(STRONG, java.time.Duration.ofMinutes(15), "i"),
                new AppProperties.Auth(java.time.Duration.ofDays(30), "c", "/", secureCookie, "Strict", 5,
                        java.time.Duration.ofMinutes(15), java.time.Duration.ofHours(24), java.time.Duration.ofHours(1),
                        java.time.Duration.ofHours(72), 10, "v"),
                new AppProperties.Tokens(""), null, new AppProperties.AdminBootstrap(null, null, null, null, null), null, null, null,
                null, null, null);
    }
}
