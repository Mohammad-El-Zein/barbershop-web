package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** Rate limiting per client IP on booking, login, the mail-sending auth endpoints and email-link actions. */
// overrides the base class (which disables rate limiting): subclass @TestPropertySource takes precedence
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.bookings.capacity=2",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.auth-mail.capacity=2",
        "app.rate-limit.token-actions.capacity=2"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    void bookingPostIsLimitedPerIp() {
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map<String, Object>> r = book(booking(HAIRCUT, LUCA,
                    at(next(DayOfWeek.MONDAY), "1" + i + ":00"), uniqueEmail(), "de"));
            assertThat(r.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        ResponseEntity<Map<String, Object>> limited = call(HttpMethod.POST, BASE + "/bookings",
                booking(HAIRCUT, LUCA, at(next(DayOfWeek.MONDAY), "15:00"), uniqueEmail(), "en"), "en", null);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(limited.getBody()).containsEntry("code", "rate_limit.exceeded");
        assertThat((String) limited.getBody().get("detail")).startsWith("Too many requests");

        // GETs and the price preview are not limited
        assertThat(rest.getForEntity(BASE + "/services", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginIsLimitedPerIp() {
        HttpStatus last = null;
        for (int i = 0; i < 4; i++) {
            last = HttpStatus.valueOf(call(HttpMethod.POST, "/api/auth/" + TENANT + "/login",
                    Map.of("email", "brute@example.com", "password", "guess-" + i), "de", null).getStatusCode().value());
        }
        assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void mailSendingEndpointsAndLinkActionsAreLimited() {
        HttpStatus last = null;
        for (int i = 0; i < 3; i++) {
            last = HttpStatus.valueOf(post(BASE + "/auth/forgot-password", Map.of("email", uniqueEmail()))
                    .getStatusCode().value());
        }
        assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        for (int i = 0; i < 3; i++) {
            last = HttpStatus.valueOf(post("/api/public/barber-decision/" + "A".repeat(43) + "/accept", null)
                    .getStatusCode().value());
        }
        assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS); // guessing tokens is throttled
    }
}
