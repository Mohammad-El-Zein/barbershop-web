package com.barbershop.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
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
 * SMTP server for the whole test run, the demo seed ("demo" profile), a random
 * JWT secret + admin credentials (no secrets in the repo). Scheduled jobs are
 * off — tests run {@code BookingJobs.runAll()} themselves.
 *
 * <p>Email links are read from the captured mails exactly like a user would
 * click them ({@link #tokenFromMail}). Mails are sent by {@code @Async} listeners;
 * {@link AsyncTasks} tells when they are done, so the mail helpers wait for exactly
 * that (no polling, no sleeps), and a test never starts while the previous test's
 * mails are still being sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Import(AsyncTasks.class)
// rate limiting off for the functional tests; RateLimitIT overrides this (subclass @TestPropertySource wins)
@TestPropertySource(properties = {"app.rate-limit.enabled=false", "app.jobs.enabled=false"})
public abstract class AbstractIntegrationTest {

    protected static final String TENANT = "the-barber-news";
    protected static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    protected static final UUID TENANT_ID = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000001");
    protected static final UUID HAIRCUT = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000101");   // 45 min, 35 €
    protected static final UUID BEARD = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000102");     // 30 min, 25 €
    protected static final UUID PREMIUM = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000103");   // 90 min, 60 €
    protected static final UUID LUCA = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000201");
    protected static final UUID MO = UUID.fromString("7b3f1c2e-0000-4000-8000-000000000202");

    protected static final String ADMIN_EMAIL = "admin@test.example";
    protected static final String ADMIN_PASSWORD = "test-" + UUID.randomUUID();
    protected static final String BASE = "/api/public/" + TENANT;

    protected static final Pattern MANAGE_LINK = Pattern.compile("/booking/manage/([A-Za-z0-9_-]{43})");
    protected static final Pattern PAY_LINK = Pattern.compile("/pay/([A-Za-z0-9_-]{43})");
    protected static final Pattern DECISION_LINK = Pattern.compile("/barber/requests/([A-Za-z0-9_-]{43})");
    protected static final Pattern VERIFY_LINK = Pattern.compile("/account/verify/([A-Za-z0-9_-]{43})");
    protected static final Pattern RESET_LINK = Pattern.compile("/account/reset-password/([A-Za-z0-9_-]{43})");

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

    /** How long async work (mail listeners) may take before a helper gives up. */
    private static final Duration ASYNC_MAX = Duration.ofSeconds(15);

    /**
     * "Today" for the current test, read once in {@link #cleanDatabase}: dates derived from it
     * ({@link #next}) can't jump a week if midnight passes between two calls in one test.
     */
    private static LocalDate today = LocalDate.now(BERLIN);

    @BeforeEach
    void cleanDatabase() throws Exception {
        // the previous test's mail listeners must not run against (or mail into) this test
        AsyncTasks.awaitIdle(ASYNC_MAX);
        today = LocalDate.now(BERLIN);
        jdbc.update("delete from booking");          // cascades items, payments, payment events, booking tokens
        jdbc.update("delete from action_token");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from loyalty_account");
        jdbc.update("delete from app_user where role <> 'ADMIN'");
        jdbc.update("update app_user set failed_login_count = 0, locked_until = null");
        jdbc.update("delete from time_off");
        jdbc.update("update staff set active = true where id in (?, ?)", LUCA, MO); // demo barbers only
        jdbc.update("""
                update tenant set profile_street = 'Musterstraße 1', profile_postal_code = '10115',
                  profile_city = 'Berlin', profile_country = 'DE', profile_phone = '+49 30 0000000',
                  profile_email = 'hello@example.com', profile_price_range = '€€'
                where id = ?""", TENANT_ID);
        jdbc.update("""
                update tenant set barber_response_min = 720, payment_deadline_min = 1440,
                  deadline_before_start_min = 120, deposit_cents = 1000, refund_cutoff_min = 1440, online_cancel_cutoff_min = 120,
                  loyalty_threshold = 10, loyalty_percent = 50, slot_step_min = 15, buffer_min = 0,
                  min_lead_min = 240, max_days_ahead = 60, auto_complete_after_min = 120, reminder_before_min = 1440
                where id = ?""", TENANT_ID);
        MAIL.purgeEmailFromAllMailboxes();
    }

    // ── time helpers ─────────────────────────────────────────────────────────

    /** The next given weekday that is at least 3 days away (well beyond lead time, within 60 days). */
    protected static LocalDate next(DayOfWeek day) {
        return today.plusDays(3).with(TemporalAdjusters.nextOrSame(day));
    }

    protected static OffsetDateTime at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), BERLIN).toOffsetDateTime();
    }

    /** Moves a booking in time (keeps its length), e.g. to test "less than 24 h before" or "already over". */
    protected void moveBookingTo(Object bookingId, Instant newStart) {
        jdbc.update("""
                update booking set end_time = ?::timestamptz + (end_time - start_time),
                                   blocked_until = ?::timestamptz + (blocked_until - start_time),
                                   start_time = ?
                where id = ?::uuid""",
                Timestamp.from(newStart), Timestamp.from(newStart), Timestamp.from(newStart), bookingId.toString());
    }

    protected static Instant fromNow(Duration d) {
        return Instant.now().plus(d).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
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

    protected ResponseEntity<Map<String, Object>> get(String url, String lang, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(lang, token)), JSON);
    }

    protected ResponseEntity<List<Map<String, Object>>> getList(String url, String lang, String token) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(lang, token)), JSON_LIST);
    }

    protected ResponseEntity<Map<String, Object>> call(HttpMethod method, String url, Object body, String lang,
                                                       String token) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers(lang, token)), JSON);
    }

    protected ResponseEntity<Map<String, Object>> post(String url, Object body) {
        return call(HttpMethod.POST, url, body, "de", null);
    }

    /** A guest booking request; the customer name is unique per email (barber mails are matched by it). */
    protected static Map<String, Object> booking(List<UUID> services, UUID staff, OffsetDateTime start, String email,
                                                 String lang) {
        Map<String, Object> b = new HashMap<>();
        b.put("serviceIds", services);
        if (staff != null) b.put("staffId", staff);
        b.put("start", start.toString());
        b.put("customerName", nameFor(email));
        b.put("customerPhone", "+49 151 2345678");
        b.put("customerEmail", email);
        b.put("consent", true);
        if (lang != null) b.put("lang", lang);
        return b;
    }

    protected static Map<String, Object> booking(UUID service, UUID staff, OffsetDateTime start, String email,
                                                 String lang) {
        return booking(List.of(service), staff, start, email, lang);
    }

    protected ResponseEntity<Map<String, Object>> book(Map<String, Object> body) {
        return call(HttpMethod.POST, BASE + "/bookings", body, "de", null);
    }

    protected ResponseEntity<Map<String, Object>> book(Map<String, Object> body, String token) {
        return call(HttpMethod.POST, BASE + "/bookings", body, "de", token);
    }

    protected String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected String login(String email, String password) {
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.POST, "/api/auth/" + TENANT + "/login",
                Map.of("email", email, "password", password), "en", null);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("login failed: " + r);
        return (String) r.getBody().get("accessToken");
    }

    protected static String uniqueEmail() {
        return "customer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected static String nameFor(String email) {
        return "Kunde " + email.substring(0, email.indexOf('@'));
    }

    // ── mail helpers ─────────────────────────────────────────────────────────

    /** The (async) mails to a recipient, once all mail work has finished. */
    protected static List<MimeMessage> mailsTo(String recipient, int expected) throws Exception {
        return awaitMails(m -> recipients(m).contains(recipient), expected, recipient);
    }

    /** The first mail to {@code recipient} whose subject contains {@code subjectPart}. */
    protected static MimeMessage mailTo(String recipient, String subjectPart) throws Exception {
        return awaitMails(m -> recipients(m).contains(recipient) && subject(m).contains(subjectPart), 1,
                recipient + " / " + subjectPart).getFirst();
    }

    /** Waits for a mail to {@code recipient} (subject part) whose text contains {@code textPart}. */
    protected static MimeMessage mailContaining(String recipient, String subjectPart, String textPart) throws Exception {
        return awaitMails(m -> {
            if (!recipients(m).contains(recipient) || !subject(m).contains(subjectPart)) return false;
            try {
                return text(m).contains(textPart);
            } catch (Exception e) {
                return false;
            }
        }, 1, recipient + " / " + subjectPart + " / " + textPart).getFirst();
    }

    /** How many mails match, counted after all mail work has finished (so "exactly once" is final). */
    protected static long countMails(String recipient, String subjectPart) throws InterruptedException {
        AsyncTasks.awaitIdle(ASYNC_MAX);
        return Arrays.stream(MAIL.getReceivedMessages())
                .filter(m -> recipients(m).contains(recipient) && subject(m).contains(subjectPart)).count();
    }

    /** Extracts the token of a link (e.g. PAY_LINK) from the newest matching mail. */
    protected static String tokenFromMail(String recipient, String subjectPart, Pattern link) throws Exception {
        List<MimeMessage> found = awaitMails(m -> recipients(m).contains(recipient)
                && subject(m).contains(subjectPart), 1, recipient + " / " + subjectPart);
        Matcher matcher = link.matcher(text(found.getLast()));
        if (!matcher.find()) throw new AssertionError("no link " + link + " in mail '" + subject(found.getLast()) + "'");
        return matcher.group(1);
    }

    /** HTML + plain text of a mail (all text parts). */
    protected static String text(MimeMessage m) throws Exception {
        StringBuilder sb = new StringBuilder();
        collectText(m, sb);
        return sb.toString();
    }

    protected static List<String> attachmentNames(MimeMessage m) throws Exception {
        List<String> names = new ArrayList<>();
        collectAttachments(m, names);
        return names;
    }

    private static List<MimeMessage> awaitMails(java.util.function.Predicate<MimeMessage> filter, int expected,
                                                String what) throws Exception {
        AsyncTasks.awaitIdle(ASYNC_MAX);
        List<MimeMessage> found = Arrays.stream(MAIL.getReceivedMessages()).filter(filter).toList();
        if (found.size() >= expected) return found;
        throw new AssertionError("expected " + expected + " mail(s) for " + what + "; got: "
                + Arrays.stream(MAIL.getReceivedMessages()).map(m -> recipients(m) + " " + subject(m)).toList());
    }

    private static String recipients(MimeMessage m) {
        try {
            return Arrays.toString(m.getAllRecipients());
        } catch (Exception e) {
            return "";
        }
    }

    protected static String subject(MimeMessage m) {
        try {
            return m.getSubject() == null ? "" : m.getSubject();
        } catch (Exception e) {
            return "";
        }
    }

    private static void collectText(Part p, StringBuilder sb) throws Exception {
        if (p.isMimeType("text/*") && !Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
            sb.append(p.getContent()).append('\n');
        } else if (p.getContent() instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) collectText(mp.getBodyPart(i), sb);
        }
    }

    private static void collectAttachments(Part p, List<String> names) throws Exception {
        if (p.getContent() instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.getFileName() != null) names.add(part.getFileName());
                else collectAttachments(part, names);
            }
        }
    }
}
