package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Security headers on API responses, request ids, and only the health endpoints of actuator. */
class SecurityHeadersIT extends AbstractIntegrationTest {

    @Test
    void apiResponsesCarrySecurityHeaders() {
        HttpHeaders h = get(BASE, "de").getHeaders();
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(h.getFirst("Content-Security-Policy")).contains("default-src 'none'", "frame-ancestors 'none'");
        assertThat(h.getFirst("Permissions-Policy")).contains("camera=()");
        assertThat(h.getCacheControl()).contains("no-store");
        // HSTS only over HTTPS (the test runs on plain http; behind the proxy X-Forwarded-Proto=https)
        assertThat(h.getFirst("Strict-Transport-Security")).isNull();
        HttpHeaders viaHttps = rest.exchange(BASE, HttpMethod.GET,
                new HttpEntity<>(withHeader("X-Forwarded-Proto", "https")), JSON).getHeaders();
        assertThat(viaHttps.getFirst("Strict-Transport-Security")).contains("max-age=31536000", "includeSubDomains");
    }

    @Test
    void everyResponseHasARequestId_aSaneIncomingOneIsKept() {
        String generated = get(BASE, "de").getHeaders().getFirst("X-Request-Id");
        assertThat(generated).matches("[0-9a-f-]{36}");
        ResponseEntity<Map<String, Object>> echoed = rest.exchange(BASE, HttpMethod.GET,
                new HttpEntity<>(withHeader("X-Request-Id", "proxy-req-1234abcd")), JSON);
        assertThat(echoed.getHeaders().getFirst("X-Request-Id")).isEqualTo("proxy-req-1234abcd");
        ResponseEntity<Map<String, Object>> injected = rest.exchange(BASE, HttpMethod.GET,
                new HttpEntity<>(withHeader("X-Request-Id", "evil <script> id")), JSON);
        assertThat(injected.getHeaders().getFirst("X-Request-Id")).doesNotContain("evil");
    }

    @Test
    void actuatorExposesOnlyHealth_withoutDetails() {
        ResponseEntity<Map<String, Object>> health = get("/actuator/health", "de");
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).containsEntry("status", "UP").doesNotContainKeys("components", "details");
        assertThat(get("/actuator/health/readiness", "de").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/liveness", "de").getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String hidden : new String[] {"/actuator/info", "/actuator/env", "/actuator/metrics", "/actuator"}) {
            assertThat(get(hidden, "de").getStatusCode().is2xxSuccessful()).as(hidden).isFalse();
        }
    }

    private static HttpHeaders withHeader(String name, String value) {
        HttpHeaders h = headers("de", null);
        h.set(name, value);
        return h;
    }
}
