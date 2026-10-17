package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.booking.CancelTokens;
import com.barbershop.support.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Public API end to end against PostgreSQL (Testcontainers) with the demo shop. */
class PublicApiIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/public/" + TENANT;

    // ── localization ─────────────────────────────────────────────────────────

    @Test
    void servicesAreLocalizedByAcceptLanguageAndLangParameter() {
        List<Map<String, Object>> en = rest.exchange(BASE + "/services", HttpMethod.GET,
                new HttpEntity<>(headers("en-US,en;q=0.9", null)), JSON_LIST).getBody();
        assertThat(en).extracting(s -> s.get("name")).containsExactly("Haircut", "Beard Sculpting", "Premium Styling");
        assertThat(((Map<?, ?>) en.getFirst().get("price")).get("formatted")).isEqualTo("from €35");

        // ?lang= wins over the header
        List<Map<String, Object>> de = rest.exchange(BASE + "/services?lang=de", HttpMethod.GET,
                new HttpEntity<>(headers("en", null)), JSON_LIST).getBody();
        assertThat(de).extracting(s -> s.get("name")).containsExactly("Haarschnitt", "Bart-Styling", "Premium-Paket");
        assertThat(((Map<?, ?>) de.getFirst().get("price")).get("formatted")).isEqualTo("ab 35 €");
        assertThat(de.getFirst().get("durationMin")).isEqualTo(45);

        // no header → German (default)
        List<Map<String, Object>> fallback = rest.exchange(BASE + "/services", HttpMethod.GET,
                new HttpEntity<>(headers(null, null)), JSON_LIST).getBody();
        assertThat(fallback.getFirst().get("name")).isEqualTo("Haarschnitt");
    }

    @Test
    void staffCanBeFilteredByServiceAndBiosAreLocalized() {
        List<Map<String, Object>> premium = rest.exchange(BASE + "/staff?service=" + PREMIUM, HttpMethod.GET,
                new HttpEntity<>(headers("en", null)), JSON_LIST).getBody();
        assertThat(premium).extracting(s -> s.get("name")).containsExactly("Luca");
        assertThat((String) premium.getFirst().get("bio")).startsWith("Specialist in fades");

        List<Map<String, Object>> all = rest.exchange(BASE + "/staff", HttpMethod.GET,
                new HttpEntity<>(headers("de", null)), JSON_LIST).getBody();
        assertThat(all).extracting(s -> s.get("name")).containsExactly("Luca", "Mo");
        assertThat((String) all.getFirst().get("bio")).startsWith("Spezialist für Fades");
    }

    @Test
    void errorsAreLocalizedProblemDetails() {
        ResponseEntity<Map<String, Object>> en = get("/api/public/no-such-shop/services", "en");
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(en.getBody()).containsEntry("code", "tenant.not_found").containsEntry("title", "Not found");
        assertThat((String) en.getBody().get("detail")).isEqualTo("There is no barbershop “no-such-shop”.");

        ResponseEntity<Map<String, Object>> de = get("/api/public/no-such-shop/services", "de");
        assertThat((String) de.getBody().get("detail")).isEqualTo("Den Barbershop „no-such-shop“ gibt es nicht.");
        assertThat(de.getBody()).containsKeys("status", "instance", "timestamp");
    }

    // ── availability ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void availabilityFollowsWorkingHoursBreaksAndStaff() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        Map<String, Object> body = get(BASE + "/availability?service=" + HAIRCUT + "&date=" + tuesday, "de").getBody();
        assertThat(body).containsEntry("timezone", "Europe/Berlin").containsEntry("durationMin", 45);
        List<Map<String, Object>> slots = (List<Map<String, Object>>) body.get("slots");
        Map<String, List<String>> staffByTime = new java.util.LinkedHashMap<>();
        slots.forEach(s -> staffByTime.put((String) s.get("time"), (List<String>) s.get("staffIds")));

        assertThat(staffByTime.get("09:00")).containsExactly(LUCA.toString());              // Mo starts at 10
        assertThat(staffByTime.get("10:00")).containsExactlyInAnyOrder(LUCA.toString(), MO.toString());
        assertThat(staffByTime.get("12:30")).containsExactly(MO.toString());                // Luca's lunch from 13:00
        assertThat(staffByTime.get("13:00")).containsExactly(MO.toString());
        assertThat(staffByTime.get("18:30")).containsExactly(LUCA.toString());              // Mo: last 18:15
        assertThat(staffByTime).doesNotContainKey("19:30");                                 // 45 min after 19:15 > 20:00
        assertThat(OffsetDateTime.parse((String) slots.getFirst().get("start"))).isEqualTo(at(tuesday, "09:00"));

        // only Luca offers the premium package; Sunday is closed
        Map<String, Object> premium = get(BASE + "/availability?service=" + PREMIUM + "&date=" + tuesday, "de").getBody();
        assertThat((List<Map<String, Object>>) premium.get("slots"))
                .allSatisfy(s -> assertThat((List<String>) s.get("staffIds")).containsExactly(LUCA.toString()));
        Map<String, Object> sunday = get(BASE + "/availability?service=" + HAIRCUT + "&date="
                + next(DayOfWeek.SUNDAY), "de").getBody();
        assertThat((List<?>) sunday.get("slots")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void availableDaysSkipSundays() {
        LocalDate from = next(DayOfWeek.MONDAY);
        Map<String, Object> body = get(BASE + "/availability/days?service=" + HAIRCUT + "&from=" + from + "&to="
                + from.plusDays(6), "de").getBody();
        List<String> days = (List<String>) body.get("days");
        assertThat(days).hasSize(6).doesNotContain(from.plusDays(6).toString()); // Mon–Sat, not Sunday
    }

    @Test
    void outOfRangeDateIs422() {
        ResponseEntity<Map<String, Object>> r = get(BASE + "/availability?service=" + HAIRCUT + "&date="
                + LocalDate.now(BERLIN).plusDays(400), "en");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).containsEntry("code", "availability.date_out_of_range");
    }

    // ── booking ──────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void bookingIsConfirmedEmailedInTheBookedLanguageAndRemovesTheSlot() throws Exception {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        String email = uniqueEmail();
        ResponseEntity<Map<String, Object>> created = book(booking(HAIRCUT, LUCA, at(tuesday, "10:00"), email, "en"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> b = created.getBody();
        assertThat(b).containsEntry("status", "CONFIRMED").containsEntry("lang", "en");
        assertThat((String) b.get("cancelToken")).hasSizeGreaterThan(40);
        assertThat(OffsetDateTime.parse((String) b.get("start"))).isEqualTo(at(tuesday, "10:00"));
        assertThat(OffsetDateTime.parse((String) b.get("end"))).isEqualTo(at(tuesday, "10:45"));
        assertThat(((Map<?, ?>) b.get("staff")).get("name")).isEqualTo("Luca");
        assertThat(created.getHeaders().getLocation()).isNotNull();
        // only the hash is stored
        String stored = jdbc.queryForObject("select cancel_token_hash from booking where id = ?::uuid", String.class,
                b.get("id"));
        assertThat(stored).isEqualTo(CancelTokens.hash((String) b.get("cancelToken")));

        MimeMessage mail = mailsTo(email, 1).getFirst();
        assertThat(mail.getSubject()).startsWith("Your appointment at The Barber News is confirmed");
        assertThat(mail.getContentLanguage()).containsExactly("en");

        Map<String, Object> slots = get(BASE + "/availability?service=" + HAIRCUT + "&date=" + tuesday, "de").getBody();
        Map<String, Object> ten = ((List<Map<String, Object>>) slots.get("slots")).stream()
                .filter(s -> "10:00".equals(s.get("time"))).findFirst().orElseThrow();
        assertThat((List<String>) ten.get("staffIds")).containsExactly(MO.toString()); // Luca is now busy
    }

    @Test
    void doubleBookingTheSameSlotIs409Localized() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        assertThat(book(booking(HAIRCUT, LUCA, at(tuesday, "11:00"), uniqueEmail(), "de")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> again = call(HttpMethod.POST, BASE + "/bookings",
                booking(HAIRCUT, LUCA, at(tuesday, "11:00"), uniqueEmail(), "en"), "en", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("code", "booking.slot_taken").containsEntry("title", "Conflict");

        // an overlapping (not identical) start is a conflict too
        ResponseEntity<Map<String, Object>> overlapping = book(booking(BEARD, LUCA, at(tuesday, "11:30"),
                uniqueEmail(), "de"));
        assertThat(overlapping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) overlapping.getBody().get("detail")).startsWith("Dieser Termin wurde gerade vergeben");
    }

    @Test
    void anyBarberGetsTheFreeOneThenConflicts() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        assertThat(book(booking(HAIRCUT, LUCA, at(tuesday, "15:00"), uniqueEmail(), "de")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map<String, Object>> any = book(booking(HAIRCUT, null, at(tuesday, "15:00"), uniqueEmail(), "de"));
        assertThat(any.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) any.getBody().get("staff")).get("name")).isEqualTo("Mo");
        assertThat(book(booking(HAIRCUT, null, at(tuesday, "15:00"), uniqueEmail(), "de")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidTimesAre422() {
        // closed on Sunday
        ResponseEntity<Map<String, Object>> sunday = book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.SUNDAY), "10:00"),
                uniqueEmail(), "de"));
        assertThat(sunday.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(sunday.getBody()).containsEntry("code", "booking.slot_invalid");
        // not on the 15-minute grid
        assertThat(book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.TUESDAY), "10:07"), uniqueEmail(), "de"))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // Mo doesn't offer the premium package
        ResponseEntity<Map<String, Object>> wrongStaff = book(booking(PREMIUM, MO, at(next(DayOfWeek.TUESDAY), "10:00"),
                uniqueEmail(), "de"));
        assertThat(wrongStaff.getBody()).containsEntry("code", "staff.does_not_offer_service");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationErrorsAreLocalizedPerField() {
        Map<String, Object> bad = booking(HAIRCUT, LUCA, at(next(DayOfWeek.TUESDAY), "10:00"), "not-an-email", null);
        bad.put("customerName", "M");
        bad.put("customerPhone", "abc");

        ResponseEntity<Map<String, Object>> de = call(HttpMethod.POST, BASE + "/bookings", bad, "de", null);
        assertThat(de.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(de.getBody()).containsEntry("code", "validation.failed");
        Map<String, String> errorsDe = (Map<String, String>) de.getBody().get("errors");
        assertThat(errorsDe).containsEntry("customerEmail", "Bitte gib eine gültige E-Mail-Adresse ein.")
                .containsEntry("customerName", "Muss zwischen 2 und 120 Zeichen lang sein.")
                .containsEntry("customerPhone", "Bitte gib eine gültige Telefonnummer ein.");

        ResponseEntity<Map<String, Object>> en = call(HttpMethod.POST, BASE + "/bookings?lang=en", bad, "de", null);
        assertThat((Map<String, String>) en.getBody().get("errors"))
                .containsEntry("customerEmail", "Please enter a valid email address.");

        ResponseEntity<Map<String, Object>> malformed = call(HttpMethod.POST, BASE + "/bookings", "{not json", "en", null);
        assertThat(malformed.getBody()).containsEntry("code", "request.invalid_body");
    }

    // ── cancel via token ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void cancelViaTokenFreesTheSlotAndSendsAMail() throws Exception {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        String email = uniqueEmail();
        Map<String, Object> created = book(booking(HAIRCUT, LUCA, at(tuesday, "16:00"), email, "de")).getBody();
        String token = (String) created.get("cancelToken");

        ResponseEntity<Map<String, Object>> found = get("/api/public/bookings/" + token, "en");
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).containsEntry("status", "CONFIRMED").doesNotContainKey("cancelToken");
        assertThat(((Map<?, ?>) found.getBody().get("service")).get("name")).isEqualTo("Haircut");

        ResponseEntity<Map<String, Object>> cancelled = call(HttpMethod.DELETE, "/api/public/bookings/" + token, null,
                "de", null);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED");
        // idempotent
        assertThat(call(HttpMethod.DELETE, "/api/public/bookings/" + token, null, "de", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        List<MimeMessage> mails = mailsTo(email, 2); // confirmation + cancellation, both in German
        assertThat(mails).extracting(MimeMessage::getSubject)
                .anyMatch(s -> s.startsWith("Dein Termin bei The Barber News wurde storniert"));

        Map<String, Object> slots = get("/api/public/" + TENANT + "/availability?service=" + HAIRCUT + "&date=" + tuesday,
                "de").getBody();
        assertThat(((List<Map<String, Object>>) slots.get("slots")).stream()
                .filter(s -> "16:00".equals(s.get("time")))
                .flatMap(s -> ((List<String>) s.get("staffIds")).stream())).contains(LUCA.toString());

        assertThat(get("/api/public/bookings/unknown-token", "en").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancellingWithinTheCutoffIs409() {
        // a booking starting in 1h (inserted directly: the API would not accept it due to the notice period)
        String token = CancelTokens.generate();
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        jdbc.update("""
                insert into booking (id, tenant_id, service_id, staff_id, start_time, end_time, customer_name,
                  customer_phone, customer_email, locale, status, cancel_token_hash)
                values (?, ?, ?, ?, ?, ?, 'Late', '+49 1', 'late@example.com', 'en', 'CONFIRMED', ?)""",
                UUID.randomUUID(), TENANT_ID, HAIRCUT, LUCA, java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(start.plus(45, ChronoUnit.MINUTES)), CancelTokens.hash(token));

        ResponseEntity<Map<String, Object>> r = call(HttpMethod.DELETE, "/api/public/bookings/" + token, null, "en", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("code", "booking.cancel_too_late");
        assertThat((String) r.getBody().get("detail")).contains("2 hours");
    }
}
