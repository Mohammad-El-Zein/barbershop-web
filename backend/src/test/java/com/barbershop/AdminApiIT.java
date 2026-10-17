package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Admin API: JWT auth, bookings, CRUD, schedules, stats and tenant isolation. */
class AdminApiIT extends AbstractIntegrationTest {

    @Test
    void loginFailsWithLocalizedMessage() {
        ResponseEntity<Map<String, Object>> en = call(HttpMethod.POST, "/api/admin/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", "wrong-password"), "en", null);
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(en.getBody()).containsEntry("code", "auth.invalid_credentials")
                .containsEntry("detail", "Email or password is incorrect.");

        ResponseEntity<Map<String, Object>> unknown = call(HttpMethod.POST, "/api/admin/auth/login",
                Map.of("email", "nobody@example.com", "password", "whatever-123"), "de", null);
        assertThat(unknown.getBody()).containsEntry("detail", "E-Mail oder Passwort ist falsch.");
    }

    @Test
    void adminEndpointsRequireAValidToken() {
        ResponseEntity<Map<String, Object>> none = call(HttpMethod.GET, "/api/admin/bookings", null, "de", null);
        assertThat(none.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(none.getBody()).containsEntry("code", "auth.unauthorized").containsEntry("detail", "Bitte melde dich an.");

        ResponseEntity<Map<String, Object>> forged = call(HttpMethod.GET, "/api/admin/bookings", null, "en",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.invalid");
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginReturnsATokenAndMe() {
        String token = adminToken();
        Map<String, Object> me = call(HttpMethod.GET, "/api/admin/me", null, "en", token).getBody();
        assertThat(me).containsEntry("email", ADMIN_EMAIL).containsEntry("role", "ADMIN");
        assertThat(((Map<?, ?>) me.get("tenant")).get("slug")).isEqualTo(TENANT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFilterAndCancelBookings() throws Exception {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        String email = uniqueEmail();
        book(booking(HAIRCUT, LUCA, at(tuesday, "09:00"), email, "en"));
        book(booking(BEARD, MO, at(tuesday, "11:00"), uniqueEmail(), "de"));
        String token = adminToken();

        Map<String, Object> page = call(HttpMethod.GET, "/api/admin/bookings?from=" + tuesday + "&to=" + tuesday,
                null, "de", token).getBody();
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertThat(page.get("totalItems")).isEqualTo(2);
        assertThat(items).extracting(i -> (Object) ((Map<?, ?>) i.get("service")).get("name"))
                .containsExactly("Haarschnitt", "Bart-Styling"); // sorted by start, localized for the admin
        assertThat(items.getFirst()).containsEntry("customerPhone", "+49 151 2345678").containsEntry("lang", "en");

        Map<String, Object> filtered = call(HttpMethod.GET, "/api/admin/bookings?staffId=" + MO, null, "de", token)
                .getBody();
        assertThat(filtered.get("totalItems")).isEqualTo(1);
        Map<String, Object> searched = call(HttpMethod.GET, "/api/admin/bookings?q=" + email.substring(0, 12), null,
                "de", token).getBody();
        assertThat(searched.get("totalItems")).isEqualTo(1);

        String id = (String) items.getFirst().get("id");
        Map<String, Object> cancelled = call(HttpMethod.POST, "/api/admin/bookings/" + id + "/cancel", null, "de",
                token).getBody();
        assertThat(cancelled).containsEntry("status", "CANCELLED");
        assertThat(cancelled.get("cancelledAt")).isNotNull();

        // the customer booked in English → the shop-side cancellation mail is English too
        List<MimeMessage> mails = mailsTo(email, 2);
        assertThat(mails).extracting(MimeMessage::getSubject)
                .anyMatch(s -> s.startsWith("Your appointment at The Barber News has been cancelled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void servicesCrudAndSoftDelete() {
        String token = adminToken();
        Map<String, Object> req = Map.of("nameDe", "Kinderhaarschnitt", "nameEn", "Kids Cut",
                "descriptionDe", "Für alle bis 12.", "descriptionEn", "For everyone up to 12.",
                "durationMin", 30, "priceCents", 2000, "priceFrom", false, "sortOrder", 4);
        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/admin/services", req, "de", token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");

        List<Map<String, Object>> publicEn = rest.exchange("/api/public/" + TENANT + "/services", HttpMethod.GET,
                new HttpEntity<>(headers("en", null)), JSON_LIST).getBody();
        assertThat(publicEn).anySatisfy(s -> {
            assertThat(s.get("name")).isEqualTo("Kids Cut");
            assertThat(((Map<?, ?>) s.get("price")).get("formatted")).isEqualTo("€20");
        });

        Map<String, Object> invalid = call(HttpMethod.POST, "/api/admin/services", Map.of("nameDe", "x"), "en", token)
                .getBody();
        assertThat(invalid).containsEntry("code", "validation.failed");
        assertThat((Map<String, String>) invalid.get("errors")).containsKeys("nameEn", "durationMin", "priceCents");

        assertThat(call(HttpMethod.DELETE, "/api/admin/services/" + id, null, "de", token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        List<Map<String, Object>> afterDelete = rest.exchange("/api/public/" + TENANT + "/services", HttpMethod.GET,
                new HttpEntity<>(headers("en", null)), JSON_LIST).getBody();
        assertThat(afterDelete).noneSatisfy(s -> assertThat(s.get("name")).isEqualTo("Kids Cut"));
        jdbc.update("delete from service where id = ?::uuid", id); // keep the shared demo data clean
    }

    @Test
    @SuppressWarnings("unchecked")
    void workingHoursValidationAndTimeOffBlocksAvailability() {
        String token = adminToken();
        Map<String, Object> overlapping = Map.of("entries", List.of(
                Map.of("weekday", 1, "start", "09:00", "end", "13:00"),
                Map.of("weekday", 1, "start", "12:00", "end", "18:00")));
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.PUT, "/api/admin/staff/" + MO + "/working-hours",
                overlapping, "en", token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).containsEntry("code", "working_hours.overlap");

        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        Map<String, Object> off = Map.of("staffId", LUCA.toString(), "start", at(tuesday, "00:00").toString(),
                "end", at(tuesday.plusDays(1), "00:00").toString(), "reason", "Fortbildung");
        assertThat(call(HttpMethod.POST, "/api/admin/time-off", off, "de", token).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> slots = get("/api/public/" + TENANT + "/availability?service=" + HAIRCUT + "&date="
                + tuesday, "de").getBody();
        assertThat((List<Map<String, Object>>) slots.get("slots")).isNotEmpty()
                .allSatisfy(s -> assertThat((List<String>) s.get("staffIds")).containsExactly(MO.toString()));
        // Luca only does the premium package → nothing that day
        Map<String, Object> premium = get("/api/public/" + TENANT + "/availability?service=" + PREMIUM + "&date="
                + tuesday, "de").getBody();
        assertThat((List<?>) premium.get("slots")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void statsPerDayAndPerService() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        book(booking(HAIRCUT, LUCA, at(tuesday, "09:00"), uniqueEmail(), "de"));
        book(booking(HAIRCUT, MO, at(tuesday, "10:00"), uniqueEmail(), "de"));
        Map<String, Object> beard = book(booking(BEARD, LUCA, at(tuesday.plusDays(1), "09:00"), uniqueEmail(), "de"))
                .getBody();
        call(HttpMethod.DELETE, "/api/public/bookings/" + beard.get("cancelToken"), null, "de", null);

        Map<String, Object> stats = call(HttpMethod.GET, "/api/admin/stats?from=" + tuesday + "&to="
                + tuesday.plusDays(1), null, "en", adminToken()).getBody();
        assertThat(stats).containsEntry("totalBooked", 2).containsEntry("totalCancelled", 1);
        assertThat((List<Map<String, Object>>) stats.get("perDay")).containsExactly(
                Map.of("date", tuesday.toString(), "booked", 2, "cancelled", 0),
                Map.of("date", tuesday.plusDays(1).toString(), "booked", 0, "cancelled", 1));
        assertThat((List<Map<String, Object>>) stats.get("perService")).anySatisfy(s -> {
            assertThat(s.get("name")).isEqualTo("Haircut");
            assertThat(s.get("booked")).isEqualTo(2);
        });
    }

    @Test
    void anAdminCannotSeeAnotherShopsBookings() {
        UUID otherTenant = UUID.randomUUID();
        UUID otherService = UUID.randomUUID();
        UUID otherStaff = UUID.randomUUID();
        UUID otherBooking = UUID.randomUUID();
        jdbc.update("insert into tenant (id, name, slug) values (?, 'Other Shop', ?)", otherTenant,
                "other-" + otherTenant.toString().substring(0, 8));
        jdbc.update("insert into service (id, tenant_id, name_de, name_en, duration_min, price_cents) "
                + "values (?, ?, 'X', 'X', 30, 1000)", otherService, otherTenant);
        jdbc.update("insert into staff (id, tenant_id, name) values (?, ?, 'Other')", otherStaff, otherTenant);
        LocalDate day = next(DayOfWeek.FRIDAY);
        jdbc.update("""
                insert into booking (id, tenant_id, service_id, staff_id, start_time, end_time, customer_name,
                  customer_phone, customer_email, locale, status, cancel_token_hash)
                values (?, ?, ?, ?, ?, ?, 'Secret', '+49 1', 'secret@example.com', 'de', 'CONFIRMED', ?)""",
                otherBooking, otherTenant, otherService, otherStaff, Timestamp.from(at(day, "10:00").toInstant()),
                Timestamp.from(at(day, "10:30").toInstant()), UUID.randomUUID().toString());
        try {
            String token = adminToken();
            assertThat(call(HttpMethod.GET, "/api/admin/bookings/" + otherBooking, null, "en", token).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(call(HttpMethod.POST, "/api/admin/bookings/" + otherBooking + "/cancel", null, "en", token)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            Map<String, Object> page = call(HttpMethod.GET, "/api/admin/bookings?from=" + day + "&to=" + day, null,
                    "en", token).getBody();
            assertThat(page.get("totalItems")).isEqualTo(0);
        } finally {
            jdbc.update("delete from tenant where id = ?", otherTenant);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApiDocsAreServed() {
        ResponseEntity<Map<String, Object>> docs = get("/v3/api-docs", "en");
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) docs.getBody().get("paths")).keySet())
                .contains("/api/public/{tenant}/availability", "/api/public/{tenant}/bookings", "/api/admin/bookings");
    }
}
