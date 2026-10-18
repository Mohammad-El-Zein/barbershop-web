package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.common.captcha.CaptchaFilter;
import com.barbershop.support.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Turnstile bot check on booking, registration and forgot-password, against a local stub of
 * Cloudflare's siteverify (token "human" passes, "bot" fails, "boom" makes the service fail).
 */
@TestPropertySource(properties = {"app.captcha.provider=turnstile", "app.captcha.secret=test-secret"})
class CaptchaIT extends AbstractIntegrationTest {

    private static final HttpServer SITEVERIFY;

    static {
        try {
            SITEVERIFY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SITEVERIFY.createContext("/siteverify", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = "";
            boolean secretOk = false;
            for (String pair : form.split("&")) {
                String[] kv = pair.split("=", 2);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                if (kv[0].equals("response")) response = v;
                if (kv[0].equals("secret")) secretOk = v.equals("test-secret");
            }
            if (response.equals("boom")) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            boolean ok = secretOk && response.equals("human");
            byte[] body = (ok ? "{\"success\":true}" : "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        SITEVERIFY.start();
    }

    @DynamicPropertySource
    static void siteverify(DynamicPropertyRegistry r) {
        r.add("app.captcha.verify-url", () -> "http://127.0.0.1:" + SITEVERIFY.getAddress().getPort() + "/siteverify");
    }

    private ResponseEntity<Map<String, Object>> postWith(String url, Object body, String captcha) {
        HttpHeaders h = headers("en", null);
        if (captcha != null) h.set(CaptchaFilter.HEADER, captcha);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JSON);
    }

    @Test
    void bookingNeedsAValidToken_andVerificationErrorsFailClosed() {
        Map<String, Object> request = booking(HAIRCUT, LUCA, at(next(DayOfWeek.WEDNESDAY), "10:00"), uniqueEmail(), "en");
        for (String token : new String[] {null, "bot", "boom"}) {
            ResponseEntity<Map<String, Object>> r = postWith(BASE + "/bookings", request, token);
            assertThat(r.getStatusCode()).as(String.valueOf(token)).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody()).containsEntry("code", "captcha.failed")
                    .containsEntry("detail", "Please confirm you are not a robot and try again.");
        }
        assertThat(jdbc.queryForObject("select count(*) from booking", Long.class)).isZero();
        assertThat(postWith(BASE + "/bookings", request, "human").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void registerAndForgotPasswordAreProtected_readsAndPreviewsAreNot() {
        Map<String, Object> register = Map.of("email", uniqueEmail(), "password", "a-long-password", "name", "Kim",
                "phone", "+49 170 1234567", "lang", "en", "consent", true);
        assertThat(postWith(BASE + "/auth/register", register, null).getBody()).containsEntry("code", "captcha.failed");
        assertThat(postWith(BASE + "/auth/register", register, "human").getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> forgot = Map.of("email", uniqueEmail());
        assertThat(postWith(BASE + "/auth/forgot-password", forgot, "bot").getBody()).containsEntry("code", "captcha.failed");
        assertThat(postWith(BASE + "/auth/forgot-password", forgot, "human").getStatusCode().is2xxSuccessful()).isTrue();

        // no token needed to look around or preview a price
        assertThat(getList(BASE + "/services", "en", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postWith(BASE + "/bookings/price-preview", Map.of("serviceIds", List.of(HAIRCUT)), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
