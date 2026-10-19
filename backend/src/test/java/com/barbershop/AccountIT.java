package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Accounts: registration, verification, sessions, password flows, GDPR, guest linking, barber logins. */
class AccountIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    PaymentService payments;

    // ── registration → verification → session ───────────────────────────────

    @Test
    void registerVerifyLoginRefreshLogout() throws Exception {
        String email = uniqueEmail();
        ResponseEntity<Map<String, Object>> reg = post(BASE + "/auth/register", registration(email, "en"));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // not verified yet → 403 only because the password is right
        ResponseEntity<Map<String, Object>> early = loginResponse(email, PASSWORD);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(early.getBody()).containsEntry("code", "auth.email_not_verified");

        String verify = tokenFromMail(email, "Please confirm your email address", VERIFY_LINK);
        assertThat(post("/api/public/auth/verify-email", Map.of("token", verify)).getBody())
                .containsEntry("verified", true).containsEntry("linkedGuestBookings", 0);
        assertThat(post("/api/public/auth/verify-email", Map.of("token", verify)).getBody())
                .containsEntry("code", "token.used");

        ResponseEntity<Map<String, Object>> login = loginResponse(email, PASSWORD);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookieHeader = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader).startsWith("bs_refresh=").contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(login.getBody()).containsKey("accessToken").doesNotContainKey("refreshToken");
        assertThat((Map<String, Object>) login.getBody().get("user")).containsEntry("role", "CUSTOMER")
                .containsEntry("emailVerified", true).containsEntry("lang", "en");
        String access = (String) login.getBody().get("accessToken");
        assertThat(get("/api/me", "en", access).getBody()).containsEntry("email", email);

        // refresh rotates the cookie
        String cookie1 = cookieValue(cookieHeader);
        ResponseEntity<Map<String, Object>> r1 = refresh(cookie1);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie2 = cookieValue(r1.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(cookie2).isNotEqualTo(cookie1);

        // reuse of the rotated cookie (outside the grace window) → the whole session family dies
        jdbc.update("update refresh_token set revoked_at = now() - interval '1 minute' where revoked_at is not null");
        assertThat(refresh(cookie1).getBody()).containsEntry("code", "auth.session_expired");
        assertThat(refresh(cookie2).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // fresh login, then logout
        String cookie3 = cookieValue(loginResponse(email, PASSWORD).getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        ResponseEntity<Map<String, Object>> logout = withCookie(HttpMethod.POST, "/api/auth/logout", cookie3, null);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(refresh(cookie3).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // a foreign site cannot use the cookie endpoints (rejected by CORS already, the Origin check is the backup)
        String fresh = cookieValue(loginResponse(email, PASSWORD).getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        HttpHeaders evil = headers("de", null);
        evil.add(HttpHeaders.COOKIE, "bs_refresh=" + fresh);
        evil.setOrigin("https://evil.example");
        assertThat(rest.exchange("/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, evil), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // the allowed frontend origin works
        assertThat(withCookie(HttpMethod.POST, "/api/auth/refresh", fresh, "http://localhost:3000").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void registrationNeedsConsentAndAStrongPassword() {
        Map<String, Object> noConsent = registration(uniqueEmail(), "en");
        noConsent.put("consent", false);
        assertThat(errors(post(BASE + "/auth/register", noConsent))).containsKey("consent");

        Map<String, Object> weak = registration(uniqueEmail(), "en");
        weak.put("password", "short");
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.POST, BASE + "/auth/register", weak, "en", null);
        assertThat(errors(r)).containsEntry("password", "The password must be at least 10 characters long.");
    }

    @Test
    void publicRegistrationAlwaysCreatesACustomer() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> body = registration(email, "de");
        body.put("role", "ADMIN"); // not part of the API: ignored
        body.put("staffId", LUCA.toString());
        post(BASE + "/auth/register", body);
        post("/api/public/auth/verify-email", Map.of("token", tokenFromMail(email, "Bitte bestätige", VERIFY_LINK)));
        assertThat(jdbc.queryForObject("select role from app_user where email = ?", String.class, email))
                .isEqualTo("CUSTOMER");
        String token = login(email, PASSWORD);
        assertThat(get("/api/admin/staff", "en", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/barber/requests", "en", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── no user enumeration ──────────────────────────────────────────────────

    @Test
    void responsesNeverRevealWhetherAnEmailIsRegistered() throws Exception {
        String email = registerAndVerify();
        String unknown = uniqueEmail();

        // register again with the same email: same answer, no second account, the owner is told
        ResponseEntity<Map<String, Object>> again = post(BASE + "/auth/register", registration(email, "de"));
        ResponseEntity<Map<String, Object>> fresh = post(BASE + "/auth/register", registration(unknown, "de"));
        assertThat(again.getStatusCode()).isEqualTo(fresh.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(again.getBody()).isEqualTo(fresh.getBody());
        assertThat(jdbc.queryForObject("select count(*) from app_user where lower(email) = lower(?)", Long.class, email))
                .isEqualTo(1);
        mailTo(email, "Registrierung bei");

        // wrong password vs unknown email: identical problem
        Map<String, Object> wrongPw = loginResponse(email, "wrong-password-123").getBody();
        Map<String, Object> noUser = loginResponse(uniqueEmail(), "wrong-password-123").getBody();
        assertThat(wrongPw).containsEntry("code", "auth.invalid_credentials");
        assertThat(noUser.get("code")).isEqualTo(wrongPw.get("code"));
        assertThat(noUser.get("detail")).isEqualTo(wrongPw.get("detail"));

        // forgot password: identical answers
        assertThat(post(BASE + "/auth/forgot-password", Map.of("email", email)).getBody())
                .isEqualTo(post(BASE + "/auth/forgot-password", Map.of("email", uniqueEmail())).getBody());
    }

    @Test
    void anAccountIsLockedAfterFiveFailedLogins() throws Exception {
        String email = registerAndVerify();
        for (int i = 0; i < 5; i++) {
            assertThat(loginResponse(email, "wrong-password-" + i).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        // even the right password does not work while locked — and the answer looks the same
        ResponseEntity<Map<String, Object>> locked = loginResponse(email, PASSWORD);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(locked.getBody()).containsEntry("code", "auth.invalid_credentials");
        assertThat(jdbc.queryForObject("select locked_until > now() from app_user where email = ?", Boolean.class,
                email)).isTrue();

        jdbc.update("update app_user set locked_until = now() - interval '1 second' where email = ?", email);
        assertThat(loginResponse(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── passwords ────────────────────────────────────────────────────────────

    @Test
    void forgotPasswordResetsItOnceAndEndsAllSessions() throws Exception {
        String email = registerAndVerify();
        String oldCookie = cookieValue(loginResponse(email, PASSWORD).getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        post(BASE + "/auth/forgot-password", Map.of("email", email));
        String reset = tokenFromMail(email, "Passwort zurücksetzen", RESET_LINK);
        assertThat(errors(post("/api/public/auth/reset-password", Map.of("token", reset, "newPassword", "kurz"))))
                .containsKey("password");
        assertThat(post("/api/public/auth/reset-password", Map.of("token", reset, "newPassword", "a-new-long-password"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/public/auth/reset-password", Map.of("token", reset, "newPassword", "another-one-123"))
                .getBody()).containsEntry("code", "token.used");

        assertThat(loginResponse(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse(email, "a-new-long-password").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh(oldCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordNeedsTheCurrentOneAndRenewsTheSession() throws Exception {
        String email = registerAndVerify();
        ResponseEntity<Map<String, Object>> login = loginResponse(email, PASSWORD);
        String access = (String) login.getBody().get("accessToken");
        String oldCookie = cookieValue(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        assertThat(errors(call(HttpMethod.POST, "/api/auth/password",
                Map.of("currentPassword", "nope-nope-nope", "newPassword", "brand-new-password"), "en", access)))
                .containsEntry("currentPassword", "The password is incorrect.");
        ResponseEntity<Map<String, Object>> changed = call(HttpMethod.POST, "/api/auth/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "brand-new-password"), "en", access);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("bs_refresh=");
        assertThat(refresh(oldCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse(email, "brand-new-password").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── guest → account ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void guestBookingsAreLinkedWhenTheSameEmailIsRegisteredAndVerified() throws Exception {
        String email = uniqueEmail();
        // two guest bookings with that email: one already completed, one still open
        String past = (String) book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.TUESDAY), "09:00"), email, "de"))
                .getBody().get("id");
        String admin = adminToken();
        call(HttpMethod.POST, "/api/admin/bookings/" + past + "/accept", null, "de", admin);
        payments.handlePaymentSucceeded(jdbc.queryForObject(
                "select provider_ref from payment where booking_id = ?::uuid", String.class, past));
        moveBookingTo(past, fromNow(Duration.ofDays(-7)));
        call(HttpMethod.POST, "/api/admin/bookings/" + past + "/complete", null, "de", admin);
        String open = (String) book(booking(BEARD, MO, at(next(DayOfWeek.WEDNESDAY), "12:00"), email, "de"))
                .getBody().get("id");
        // somebody else's guest booking stays untouched
        String other = (String) book(booking(BEARD, MO, at(next(DayOfWeek.WEDNESDAY), "14:00"), uniqueEmail(), "de"))
                .getBody().get("id");

        post(BASE + "/auth/register", registration(email.toUpperCase(), "de")); // case-insensitive match
        assertThat(jdbc.queryForObject("select count(*) from booking where user_id is not null", Long.class)).isZero();
        String verify = tokenFromMail(email.toUpperCase(), "Bitte bestätige", VERIFY_LINK);
        assertThat(post("/api/public/auth/verify-email", Map.of("token", verify)).getBody())
                .containsEntry("linkedGuestBookings", 2);

        String access = login(email, PASSWORD);
        List<Map<String, Object>> upcoming = getList("/api/me/bookings?scope=upcoming", "de", access).getBody();
        List<Map<String, Object>> history = getList("/api/me/bookings?scope=past", "de", access).getBody();
        assertThat(upcoming).extracting(b -> b.get("id")).containsExactly(open);
        assertThat(history).extracting(b -> b.get("id")).containsExactly(past);
        assertThat(get("/api/me/bookings/" + other, "de", access).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // the completed guest visit counts for loyalty
        assertThat(get("/api/me/loyalty", "de", access).getBody()).containsEntry("visitsInCurrentCycle", 1)
                .containsEntry("nextDiscountAvailable", false);

        // cancel my open request from "my bookings"
        Map<String, Object> cancelled = call(HttpMethod.POST, "/api/me/bookings/" + open + "/cancel", null, "de",
                access).getBody();
        assertThat(cancelled).containsEntry("status", "CANCELLED");
    }

    // ── GDPR ─────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void exportAndDeleteMyAccount() throws Exception {
        String email = registerAndVerify();
        String access = login(email, PASSWORD);
        Map<String, Object> req = booking(HAIRCUT, LUCA, at(next(DayOfWeek.THURSDAY), "10:00"), "ignored@example.com",
                null);
        req.remove("customerName");
        req.remove("customerEmail");
        req.remove("consent");
        ResponseEntity<Map<String, Object>> created = book(req, access);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("customerEmail", email).containsEntry("customerName", "Max Test");
        String id = (String) created.getBody().get("id");

        // "Pay now" in my bookings: only once the barber accepted, and only for my own booking
        assertThat(call(HttpMethod.POST, "/api/me/bookings/" + id + "/payment-link", null, "de", access).getBody())
                .containsEntry("code", "booking.not_awaiting_payment");
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "de", adminToken());
        Map<String, Object> link = call(HttpMethod.POST, "/api/me/bookings/" + id + "/payment-link", null, "de",
                access).getBody();
        assertThat((String) link.get("url")).contains("/de/pay/");
        assertThat(get("/api/public/payments/" + link.get("token"), "de").getBody()).containsEntry("status", "UNPAID");
        assertThat(call(HttpMethod.POST, "/api/me/bookings/" + UUID.randomUUID() + "/payment-link", null, "de", access)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> export = get("/api/me/export", "de", access);
        assertThat(export.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat((Map<String, Object>) export.getBody().get("account")).containsEntry("email", email);
        assertThat((List<?>) export.getBody().get("bookings")).hasSize(1);
        assertThat(export.getBody()).containsKeys("loyalty", "payments", "consentAt", "consentVersion");

        // 1 h before the start: too late for online cancellation, but account deletion still cancels it
        moveBookingTo(id, fromNow(Duration.ofHours(1)));
        assertThat(call(HttpMethod.POST, "/api/me/bookings/" + id + "/cancel", null, "de", access).getBody())
                .containsEntry("code", "booking.cancel_too_late");

        assertThat(errors(call(HttpMethod.POST, "/api/me/delete", Map.of("password", "wrong-wrong-wrong"), "de",
                access))).containsKey("password");
        assertThat(call(HttpMethod.POST, "/api/me/delete", Map.of("password", PASSWORD), "de", access)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> row = jdbc.queryForMap("select status, user_id, customer_name, customer_email, "
                + "customer_phone, cancelled_by from booking where id = ?::uuid", id);
        assertThat(row).containsEntry("status", "CANCELLED").containsEntry("cancelled_by", "CUSTOMER")
                .containsEntry("customer_name", "Gelöschter Kunde");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("customer_email")).isNull();
        assertThat(row.get("customer_phone")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Long.class, email)).isZero();
        assertThat(loginResponse(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── barber logins ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void aBarberGetsAnInviteSeesOnlyOwnRequestsAndCanAccept() throws Exception {
        String admin = adminToken();
        String barberEmail = "luca-" + uniqueEmail();
        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/admin/staff/" + LUCA + "/account",
                Map.of("email", barberEmail, "lang", "de"), "de", admin);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(HttpMethod.POST, "/api/admin/staff/" + LUCA + "/account", Map.of("email", uniqueEmail()), "de",
                admin).getBody()).containsEntry("code", "account.staff_has_account");

        String invite = tokenFromMail(barberEmail, "Dein Barber-Zugang", RESET_LINK);
        post("/api/public/auth/reset-password", Map.of("token", invite, "newPassword", "lucas-own-password"));
        String luca = login(barberEmail, "lucas-own-password");

        // a request for Luca now goes to Luca himself, with a copy to the configured admin (once)
        String customer = uniqueEmail();
        String id = (String) book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "15:00"), customer, "en"))
                .getBody().get("id");
        mailTo(barberEmail, "Neue Terminanfrage: " + nameFor(customer));
        assertThat(countMails(ADMIN_EMAIL, "Neue Terminanfrage: " + nameFor(customer))).isEqualTo(1);
        String moBooking = (String) book(booking(HAIRCUT, MO, at(next(DayOfWeek.FRIDAY), "15:00"), uniqueEmail(), "en"))
                .getBody().get("id");

        List<Map<String, Object>> requests = getList("/api/barber/requests", "de", luca).getBody();
        assertThat(requests).extracting(r -> r.get("id")).containsExactly(id);
        assertThat((List<String>) requests.getFirst().get("allowedTransitions"))
                .containsExactlyInAnyOrder("AWAITING_PAYMENT", "DECLINED", "CANCELLED");
        assertThat(getList("/api/barber/calendar?date=" + next(DayOfWeek.FRIDAY) + "&view=week", "de", luca).getBody())
                .extracting(r -> r.get("id")).containsExactly(id);

        assertThat(call(HttpMethod.POST, "/api/barber/bookings/" + id + "/accept", null, "de", luca).getBody())
                .containsEntry("status", "AWAITING_PAYMENT");
        // not his booking, not his area
        assertThat(call(HttpMethod.POST, "/api/barber/bookings/" + moBooking + "/accept", null, "de", luca)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/admin/bookings", "de", luca).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/barber/requests", "de", admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // a barber cannot complete before the appointment started
        payments.handlePaymentSucceeded(jdbc.queryForObject(
                "select provider_ref from payment where booking_id = ?::uuid", String.class, id));
        assertThat(call(HttpMethod.POST, "/api/barber/bookings/" + id + "/complete", null, "en", luca).getBody())
                .containsEntry("code", "booking.not_started");
    }

    @Test
    void theAdminCanSetABarbersInitialPasswordAndDeactivateTheBarber() throws Exception {
        String admin = adminToken();
        String barberEmail = "mo-" + uniqueEmail();
        assertThat(errors(call(HttpMethod.POST, "/api/admin/staff/" + MO + "/account",
                Map.of("email", barberEmail, "lang", "de", "password", "short"), "en", admin))).containsKey("password");

        Map<String, Object> created = call(HttpMethod.POST, "/api/admin/staff/" + MO + "/account",
                Map.of("email", barberEmail, "lang", "de", "password", "initial-pass-123"), "en", admin).getBody();
        assertThat(created).containsEntry("inviteSent", false);
        // ready at once, no invite mail
        assertThat(login(barberEmail, "initial-pass-123")).isNotBlank();
        assertThat(countMails(barberEmail, "Dein Barber-Zugang")).isZero();

        // deactivated: gone from the customers' barber selection and no more logins
        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + MO, null, "en", admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getList(BASE + "/staff", "en", null).getBody()).extracting(s -> s.get("id"))
                .doesNotContain(MO.toString());
        ResponseEntity<Map<String, Object>> denied = call(HttpMethod.POST, "/api/auth/" + TENANT + "/login",
                Map.of("email", barberEmail, "password", "initial-pass-123"), "en", null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(denied.getBody()).containsEntry("code", "auth.invalid_credentials");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> registration(String email, String lang) {
        Map<String, Object> m = new HashMap<>();
        m.put("email", email);
        m.put("password", PASSWORD);
        m.put("name", "Max Test");
        m.put("phone", "+49 170 1234567");
        m.put("lang", lang);
        m.put("consent", true);
        return m;
    }

    private String registerAndVerify() throws Exception {
        String email = uniqueEmail();
        post(BASE + "/auth/register", registration(email, "de"));
        String verify = tokenFromMail(email, "Bitte bestätige", VERIFY_LINK);
        post("/api/public/auth/verify-email", Map.of("token", verify));
        return email;
    }

    private ResponseEntity<Map<String, Object>> loginResponse(String email, String password) {
        return call(HttpMethod.POST, "/api/auth/" + TENANT + "/login", Map.of("email", email, "password", password), "de", null);
    }

    private ResponseEntity<Map<String, Object>> refresh(String cookie) {
        return withCookie(HttpMethod.POST, "/api/auth/refresh", cookie, null);
    }

    private ResponseEntity<Map<String, Object>> withCookie(HttpMethod method, String url, String cookie, String origin) {
        HttpHeaders h = headers("de", null);
        h.add(HttpHeaders.COOKIE, "bs_refresh=" + cookie);
        if (origin != null) h.setOrigin(origin);
        return rest.exchange(url, method, new HttpEntity<>(null, h), JSON);
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring("bs_refresh=".length(), setCookie.indexOf(';'));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errors(ResponseEntity<Map<String, Object>> r) {
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return (Map<String, String>) r.getBody().get("errors");
    }
}
