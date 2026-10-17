package com.barbershop.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: one PostgreSQL 17 Testcontainer and one GreenMail
 * SMTP server for the whole test run, the demo seed ("demo" profile), and a
 * random JWT secret + admin credentials (no secrets in the repo).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
// rate limiting off for the functional tests; RateLimitIT overrides this (subclass @TestPropertySource wins)
@TestPropertySource(properties = "app.rate-limit.enabled=false")
public abstract class AbstractIntegrationTest {

    protected static final String TENANT = "the-barber-news";
    protected static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    protected static final UUID TENANT_ID = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000001");
    protected static final UUID HAIRCUT = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000101");
    protected static final UUID BEARD = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000102");
    protected static final UUID PREMIUM = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000103");
    protected static final UUID LUCA = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000201");
    protected static final UUID MO = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000202");

    protected static final String ADMIN_EMAIL = "admin@test.example";
    protected static final String ADMIN_PASSWORD = "test-" + UUID.randomUUID();

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    protected static final GreenMail MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        POSTGRES.start();
        MAIL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.mail.host", () -> "localhost");
        r.add("spring.mail.port", () -> MAIL.getSmtp().getPort());
        r.add("app.jwt.secret", () -> "test-secret-" + UUID.randomUUID() + UUID.randomUUID());
        r.add("app.admin.email", () -> ADMIN_EMAIL);
        r.add("app.admin.password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() throws Exception {
        jdbc.update("delete from booking");
        jdbc.update("delete from time_off");
        MAIL.purgeEmailFromAllMailboxes();
    }

    // ── time helpers ─────────────────────────────────────────────────────────

    /** The next given weekday that is at least 3 days away (well beyond notice, within 60 days). */
    protected static LocalDate next(DayOfWeek day) {
        return LocalDate.now(BERLIN).plusDays(3).with(TemporalAdjusters.nextOrSame(day));
    }

    protected static OffsetDateTime at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), BERLIN).toOffsetDateTime();
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    protected static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};
    protected static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {};

    protected static HttpHeaders headers(String lang, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (lang != null) h.set(HttpHeaders.ACCEPT_LANGUAGE, lang);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    protected ResponseEntity<Map<String, Object>> get(String url, String lang) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(lang, null)), JSON);
    }

    protected ResponseEntity<Map<String, Object>> call(HttpMethod method, String url, Object body, String lang,
                                                       String token) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers(lang, token)), JSON);
    }

    protected static Map<String, Object> booking(UUID service, UUID staff, OffsetDateTime start, String email,
                                                 String lang) {
        Map<String, Object> b = new HashMap<>();
        b.put("serviceId", service);
        if (staff != null) b.put("staffId", staff);
        b.put("start", start.toString());
        b.put("customerName", "Max Mustermann");
        b.put("customerPhone", "+49 151 2345678");
        b.put("customerEmail", email);
        if (lang != null) b.put("lang", lang);
        return b;
    }

    protected ResponseEntity<Map<String, Object>> book(Map<String, Object> body) {
        return call(HttpMethod.POST, "/api/public/" + TENANT + "/bookings", body, "de", null);
    }

    protected String adminToken() {
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.POST, "/api/admin/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), "en", null);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("login failed: " + r);
        return (String) r.getBody().get("accessToken");
    }

    protected static String uniqueEmail() {
        return "customer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** Waits for (async) mails to a recipient and returns them. */
    protected static List<MimeMessage> mailsTo(String recipient, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<MimeMessage> found = Arrays.stream(MAIL.getReceivedMessages())
                    .filter(m -> {
                        try {
                            return Arrays.stream(m.getAllRecipients()).anyMatch(a -> a.toString().contains(recipient));
                        } catch (Exception e) {
                            return false;
                        }
                    }).toList();
            if (found.size() >= expected) return found;
            Thread.sleep(100);
        }
        throw new AssertionError("expected " + expected + " mail(s) to " + recipient);
    }
}
