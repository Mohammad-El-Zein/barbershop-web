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

/** Rate limiting on the public booking POST and the admin login (per client IP). */
// overrides the base class (which disables rate limiting): subclass @TestPropertySource takes precedence
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.bookings.capacity=2",
        "app.rate-limit.login.capacity=3"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    void bookingPostIsLimitedPerIp() {
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map<String, Object>> r = book(booking(HAIRCUT, LUCA,
                    at(next(DayOfWeek.MONDAY), "1" + i + ":00"), uniqueEmail(), "de"));
            assertThat(r.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        ResponseEntity<Map<String, Object>> limited = call(HttpMethod.POST, "/api/public/" + TENANT + "/bookings",
                booking(HAIRCUT, LUCA, at(next(DayOfWeek.MONDAY), "15:00"), uniqueEmail(), "en"), "en", null);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(limited.getBody()).containsEntry("code", "rate_limit.exceeded");
        assertThat((String) limited.getBody().get("detail")).startsWith("Too many requests");

        // GETs are not limited
        assertThat(rest.getForEntity("/api/public/" + TENANT + "/services", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginIsLimitedPerIp() {
        HttpStatus last = null;
        for (int i = 0; i < 4; i++) {
            last = HttpStatus.valueOf(call(HttpMethod.POST, "/api/admin/auth/login",
                    Map.of("email", "brute@example.com", "password", "guess-" + i), "de", null).getStatusCode().value());
        }
        assertThat(last).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
