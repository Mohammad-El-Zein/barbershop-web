package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Admin → Barbers: deleting a barber without bookings, the barber's login state and email,
 * and start passwords that are shown once and never stored, logged or mailed in plain text.
 */
@ExtendWith(OutputCaptureExtension.class)
class BarberAdminIT extends AbstractIntegrationTest {

    /** Keep the shared demo data clean: only Luca and Mo stay; the other shop goes (cascade). */
    @AfterEach
    void removeOwnBarbers() {
        jdbc.update("delete from booking where staff_id not in (?, ?)", LUCA, MO);
        jdbc.update("delete from staff where tenant_id = ? and id not in (?, ?)", TENANT_ID, LUCA, MO);
        jdbc.update("delete from tenant where slug like 'other-%'");
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void aBarberWithoutBookingsIsDeletedWithEverythingThatBelongsToThem() {
        String admin = adminToken();
        String id = newBarber(admin, "Delete Me");
        String email = "del-" + uniqueEmail();
        call(HttpMethod.POST, "/api/admin/time-off", Map.of("staffId", id,
                "start", at(next(DayOfWeek.MONDAY), "09:00").toString(),
                "end", at(next(DayOfWeek.MONDAY), "12:00").toString()), "en", admin);
        String startPassword = (String) call(HttpMethod.POST, "/api/admin/staff/" + id + "/account",
                Map.of("email", email, "generatePassword", true), "en", admin).getBody().get("startPassword");
        String cookie = loginCookie(email, startPassword);
        String userId = jdbc.queryForObject("select id::text from app_user where lower(email) = lower(?)", String.class, email);
        assertThat(count("select count(*) from refresh_token where user_id = ?::uuid", userId)).isPositive();

        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + id + "/permanently", null, "en", admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(get("/api/admin/staff/" + id, "en", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String table : List.of("staff_service", "working_hours", "time_off")) {
            assertThat(count("select count(*) from " + table + " where staff_id = ?::uuid", id)).as(table).isZero();
        }
        assertThat(count("select count(*) from app_user where id = ?::uuid", userId)).isZero();
        assertThat(count("select count(*) from refresh_token where user_id = ?::uuid", userId)).isZero();
        assertThat(refresh(cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse(email, startPassword).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aBarberWithAnOpenRequestIsNotDeleted_onceDeclinedItIs() {
        String admin = adminToken();
        String id = newBarber(admin, "Has Bookings");
        String booking = (String) book(booking(HAIRCUT, UUID.fromString(id), at(next(DayOfWeek.FRIDAY), "10:00"),
                uniqueEmail(), "de")).getBody().get("id");

        ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/admin/staff/" + id + "/permanently",
                null, "de", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("code", "staff.has_active_bookings");
        assertThat((String) r.getBody().get("detail")).contains("inaktiv");
        assertThat(get("/api/admin/staff/" + id, "en", admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("select count(*) from working_hours where staff_id = ?::uuid", id)).isPositive();

        // declined = a dead end: deleted with the barber (more rules: ErasureIT)
        call(HttpMethod.POST, "/api/admin/bookings/" + booking + "/decline", Map.of(), "en", admin);
        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + id + "/permanently", null, "de", admin).getBody())
                .containsEntry("deleted", 1);
        assertThat(count("select count(*) from booking where id = ?::uuid", booking)).isZero();
    }

    @Test
    void onlyAnAdminOfTheSameShopMayDeleteOrManageLogins() {
        String admin = adminToken();
        // a barber of another shop: invisible here (404), untouched
        UUID otherTenant = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        jdbc.update("insert into tenant (id, name, slug) values (?, 'Other Shop', ?)", otherTenant,
                "other-" + otherTenant.toString().substring(0, 8));
        jdbc.update("insert into staff (id, tenant_id, name, active, sort_order, created_at, updated_at) "
                + "values (?, ?, 'Foreign', true, 0, now(), now())", foreign, otherTenant);
        for (var req : List.of(
                new Object[] {HttpMethod.DELETE, "/api/admin/staff/" + foreign + "/permanently"},
                new Object[] {HttpMethod.GET, "/api/admin/staff/" + foreign + "/account"},
                new Object[] {HttpMethod.POST, "/api/admin/staff/" + foreign + "/account/reset-password"})) {
            assertThat(call((HttpMethod) req[0], (String) req[1], null, "en", admin).getStatusCode())
                    .as(req[1].toString()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(count("select count(*) from staff where id = ?::uuid", foreign.toString())).isOne();

        // a barber (own login) and a customer are not admins
        String id = newBarber(admin, "Not An Admin");
        String email = "barber-" + uniqueEmail();
        String startPassword = (String) call(HttpMethod.POST, "/api/admin/staff/" + id + "/account",
                Map.of("email", email, "generatePassword", true), "en", admin).getBody().get("startPassword");
        String barber = login(email, startPassword);
        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + id + "/permanently", null, "en", barber)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/admin/staff/" + id + "/account/reset-password", null, "en", barber)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.DELETE, "/api/admin/staff/" + id + "/permanently", null, "en", null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── login: state, email, start passwords ─────────────────────────────────

    @Test
    void aGeneratedStartPasswordIsShownOnceStoredAsHashAndMustBeChanged(CapturedOutput output) throws Exception {
        String admin = adminToken();
        String id = newBarber(admin, "Start Password");
        assertThat(account(id, admin)).containsEntry("state", "NONE");

        String email = "start-" + uniqueEmail();
        Map<String, Object> created = call(HttpMethod.POST, "/api/admin/staff/" + id + "/account",
                Map.of("email", email, "lang", "de", "generatePassword", true), "en", admin).getBody();
        String startPassword = (String) created.get("startPassword");
        assertThat(startPassword).hasSizeGreaterThanOrEqualTo(12).matches("[A-Za-z0-9]+");
        assertThat(created).containsEntry("inviteSent", false);
        assertThat(jdbc.queryForObject("select password_hash from app_user where lower(email) = lower(?)",
                String.class, email)).startsWith("$2").doesNotContain(startPassword);

        // the admin sees state and email — never the password again
        Map<String, Object> view = account(id, admin);
        assertThat(view).containsEntry("state", "ACTIVE").containsEntry("email", email)
                .containsEntry("mustChangePassword", true).doesNotContainKey("startPassword");
        assertThat(view.toString()).doesNotContain(startPassword);

        // the barber signs in and has to choose an own password; the admin never sees it
        ResponseEntity<Map<String, Object>> login = loginResponse(email, startPassword);
        assertThat(user(login)).containsEntry("mustChangePassword", true);
        ResponseEntity<Map<String, Object>> changed = call(HttpMethod.POST, "/api/auth/password",
                Map.of("currentPassword", startPassword, "newPassword", "barbers-own-password"), "de",
                (String) login.getBody().get("accessToken"));
        assertThat(user(changed)).containsEntry("mustChangePassword", false);
        assertThat(account(id, admin)).containsEntry("mustChangePassword", false).containsEntry("state", "ACTIVE");
        assertThat(account(id, admin).toString()).doesNotContain("barbers-own-password");

        // never in a mail, never in the log
        assertThat(countMails(email, "")).isZero();
        assertThat(output.getAll()).doesNotContain(startPassword).doesNotContain("barbers-own-password");
    }

    @Test
    void resettingTheStartPasswordEndsAllSessionsAndIsShownOnce(CapturedOutput output) {
        String admin = adminToken();
        String id = newBarber(admin, "Reset Me");
        String email = "reset-" + uniqueEmail();
        call(HttpMethod.POST, "/api/admin/staff/" + id + "/account",
                Map.of("email", email, "password", "typed-by-admin-123"), "en", admin);
        assertThat(account(id, admin)).containsEntry("mustChangePassword", true);
        String cookie = loginCookie(email, "typed-by-admin-123");

        Map<String, Object> reset = call(HttpMethod.POST, "/api/admin/staff/" + id + "/account/reset-password", null,
                "en", admin).getBody();
        String newPassword = (String) reset.get("startPassword");
        assertThat(newPassword).hasSizeGreaterThanOrEqualTo(12).isNotEqualTo("typed-by-admin-123");

        assertThat(refresh(cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse(email, "typed-by-admin-123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(user(loginResponse(email, newPassword))).containsEntry("mustChangePassword", true);
        assertThat(output.getAll()).doesNotContain(newPassword).doesNotContain("typed-by-admin-123");
        // a second reset gives another password
        assertThat(call(HttpMethod.POST, "/api/admin/staff/" + id + "/account/reset-password", null, "en", admin)
                .getBody().get("startPassword")).isNotEqualTo(newPassword);
        // without a login there is nothing to reset
        String noLogin = newBarber(admin, "No Login");
        assertThat(call(HttpMethod.POST, "/api/admin/staff/" + noLogin + "/account/reset-password", null, "en", admin)
                .getBody()).containsEntry("code", "account.no_barber_login");
    }

    @Test
    void theAdminSeesAnOpenInviteAndCanCorrectTheLoginEmail() throws Exception {
        String admin = adminToken();
        String id = newBarber(admin, "Invited");
        String typo = "invitd-" + uniqueEmail();
        assertThat(call(HttpMethod.POST, "/api/admin/staff/" + id + "/account", Map.of("email", typo, "lang", "de"),
                "en", admin).getBody()).containsEntry("inviteSent", true).doesNotContainKey("startPassword");
        assertThat(account(id, admin)).containsEntry("state", "INVITED").containsEntry("email", typo);

        String fixed = "invited-" + uniqueEmail();
        assertThat(call(HttpMethod.PUT, "/api/admin/staff/" + id + "/account", Map.of("email", fixed), "en", admin)
                .getBody()).containsEntry("email", fixed).containsEntry("state", "INVITED");
        // the open invite goes again, to the new address; the old link no longer works
        String oldInvite = tokenFromMail(typo, "Dein Barber-Zugang", RESET_LINK);
        String invite = tokenFromMail(fixed, "Dein Barber-Zugang", RESET_LINK);
        assertThat(post("/api/public/auth/reset-password", Map.of("token", oldInvite, "newPassword", "own-password-1"))
                .getStatusCode().is2xxSuccessful()).isFalse();
        post("/api/public/auth/reset-password", Map.of("token", invite, "newPassword", "own-password-1"));
        assertThat(account(id, admin)).containsEntry("state", "ACTIVE").containsEntry("mustChangePassword", false);

        // an email of another account is refused
        assertThat(call(HttpMethod.PUT, "/api/admin/staff/" + id + "/account", Map.of("email", ADMIN_EMAIL), "de",
                admin).getBody()).containsEntry("code", "account.email_taken");
    }

    /**
     * Passwords leave the server in exactly two responses: the generated start password when the
     * admin creates a login and when the admin resets it. No other response schema has a password
     * field (the OpenAPI spec lists every endpoint and its response type).
     */
    @Test
    @SuppressWarnings("unchecked")
    void noEndpointReturnsAPasswordExceptTheOneTimeStartPassword() {
        Map<String, Object> docs = rest.exchange("/v3/api-docs", HttpMethod.GET,
                new HttpEntity<>(headers("en", null)), JSON).getBody();
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) docs.get("components")).get("schemas");
        List<String> found = new ArrayList<>();
        ((Map<String, Map<String, Object>>) docs.get("paths")).forEach((path, ops) -> ops.forEach((method, op) -> {
            Map<String, Object> responses = (Map<String, Object>) ((Map<String, Object>) op).get("responses");
            if (responses == null) return;
            responses.values().forEach(r -> passwordFields(r, schemas, new ArrayList<>())
                    .forEach(f -> found.add(method.toUpperCase() + " " + path + " → " + f)));
        }));
        assertThat(found).containsExactlyInAnyOrder(
                "POST /api/admin/staff/{id}/account → BarberAccountView.startPassword",
                "POST /api/admin/staff/{id}/account/reset-password → StartPasswordView.startPassword");
        // and the stored hash is in no schema at all
        assertThat(schemas.toString()).doesNotContainIgnoringCase("passwordHash").doesNotContainIgnoringCase("password_hash");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** String properties named like a password, in this schema and every schema it references. */
    @SuppressWarnings("unchecked")
    private static List<String> passwordFields(Object node, Map<String, Object> schemas, List<String> seen) {
        List<String> out = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String r && r.startsWith("#/components/schemas/")) {
                String name = r.substring("#/components/schemas/".length());
                if (seen.contains(name)) return out;
                seen.add(name);
                Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
                Map<String, Object> props = (Map<String, Object>) schema.get("properties");
                if (props != null) {
                    props.forEach((prop, def) -> {
                        if (prop.toLowerCase().contains("password") && "string".equals(((Map<String, Object>) def).get("type"))) {
                            out.add(name + "." + prop);
                        }
                    });
                }
                out.addAll(passwordFields(schema, schemas, seen));
                return out;
            }
            map.values().forEach(v -> out.addAll(passwordFields(v, schemas, seen)));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> out.addAll(passwordFields(v, schemas, seen)));
        }
        return out;
    }

    /** A new barber offering the haircut, working Monday–Saturday 09:00–19:00. */
    private String newBarber(String admin, String name) {
        String id = (String) call(HttpMethod.POST, "/api/admin/staff", Map.of("name", name), "en", admin).getBody()
                .get("id");
        call(HttpMethod.PUT, "/api/admin/staff/" + id + "/services", Map.of("serviceIds", List.of(HAIRCUT)), "en", admin);
        List<Map<String, Object>> week = new ArrayList<>();
        for (int d = 1; d <= 6; d++) week.add(Map.of("weekday", d, "start", "09:00", "end", "19:00"));
        assertThat(rest.exchange("/api/admin/staff/" + id + "/working-hours", HttpMethod.PUT,
                new HttpEntity<>(Map.of("entries", week), headers("en", admin)), JSON_LIST).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return id;
    }

    private Map<String, Object> account(String staffId, String admin) {
        ResponseEntity<Map<String, Object>> r = get("/api/admin/staff/" + staffId + "/account", "en", admin);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> user(ResponseEntity<Map<String, Object>> session) {
        assertThat(session.getStatusCode().is2xxSuccessful()).as(String.valueOf(session.getBody())).isTrue();
        return (Map<String, Object>) session.getBody().get("user");
    }

    private ResponseEntity<Map<String, Object>> loginResponse(String email, String password) {
        return call(HttpMethod.POST, "/api/auth/" + TENANT + "/login", Map.of("email", email, "password", password),
                "en", null);
    }

    /** Signs in and returns the refresh cookie's value. */
    private String loginCookie(String email, String password) {
        String setCookie = loginResponse(email, password).getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("bs_refresh=");
        return setCookie.substring("bs_refresh=".length(), setCookie.indexOf(';'));
    }

    private ResponseEntity<Map<String, Object>> refresh(String cookie) {
        HttpHeaders h = headers("en", null);
        h.add(HttpHeaders.COOKIE, "bs_refresh=" + cookie);
        return rest.exchange("/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, h), JSON);
    }

    private long count(String sql, String id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }
}
