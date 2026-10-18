package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Admin API: auth, manual bookings, refund override, customers, settings, CRUD, stats, tenant isolation. */
class AdminApiIT extends AbstractIntegrationTest {

    @Autowired
    PaymentService payments;

    @Test
    void adminEndpointsRequireAnAdminToken() {
        assertThat(get("/api/admin/bookings", "en").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/admin/bookings", "en", "garbage").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map<String, Object>> login = call(HttpMethod.POST, "/api/auth/" + TENANT + "/login",
                Map.of("email", ADMIN_EMAIL, "password", "wrong-password"), "de", null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login.getBody()).containsEntry("code", "auth.invalid_credentials");

        String token = adminToken();
        assertThat(get("/api/me", "en", token).getBody()).containsEntry("role", "ADMIN")
                .containsEntry("email", ADMIN_EMAIL);
        assertThat(get("/api/admin/bookings", "en", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── manual bookings ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void walkInBookingsAreConfirmedImmediatelyWithoutDeposit() throws Exception {
        String token = adminToken();
        LocalDate monday = next(DayOfWeek.MONDAY);
        String email = uniqueEmail();
        Map<String, Object> req = new HashMap<>(Map.of("serviceIds", List.of(HAIRCUT, BEARD), "staffId", LUCA,
                "start", at(monday, "10:07").toString(), "customerName", "Walk-in Willi", "source", "WALK_IN",
                "customerEmail", email, "lang", "de"));
        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/admin/bookings", req, "de", token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> b = created.getBody();
        assertThat(b).containsEntry("status", "CONFIRMED").containsEntry("source", "WALK_IN"); // off-grid is fine
        Map<String, Object> price = (Map<String, Object>) b.get("price");
        assertThat((Map<String, Object>) price.get("deposit")).containsEntry("cents", 0);
        assertThat((Map<String, Object>) price.get("amountDueAtShop")).containsEntry("cents", 6000);
        assertThat(b.get("payment")).isNull();
        assertThat(attachmentNames(mailTo(email, "Termin bestätigt"))).containsExactly("termin.ics");

        // overlaps are still refused, and online customers can't book the time
        req.put("start", at(monday, "11:00").toString());
        assertThat(call(HttpMethod.POST, "/api/admin/bookings", req, "de", token).getBody())
                .containsEntry("code", "booking.slot_taken");
        assertThat(book(booking(HAIRCUT, LUCA, at(monday, "10:30"), uniqueEmail(), "de")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        // a phone booking without email is fine
        Map<String, Object> phone = new HashMap<>(Map.of("serviceIds", List.of(BEARD), "staffId", MO,
                "start", at(monday, "15:00").toString(), "customerName", "Telefon Tina", "source", "PHONE"));
        assertThat(call(HttpMethod.POST, "/api/admin/bookings", phone, "de", token).getBody())
                .containsEntry("status", "CONFIRMED");
    }

    // ── refunds ──────────────────────────────────────────────────────────────

    @Test
    void refundOverrideGivesBackAForfeitedDeposit() throws Exception {
        String token = adminToken();
        String email = uniqueEmail();
        String id = confirmed(email, at(next(DayOfWeek.TUESDAY), "11:00"));
        moveBookingTo(id, fromNow(Duration.ofHours(6)));
        String manage = tokenFromMail(email, "Termin bestätigt", MANAGE_LINK);
        post("/api/public/manage/" + manage + "/cancel", null);
        assertThat(paymentStatus(id)).isEqualTo("FORFEITED");

        ResponseEntity<Map<String, Object>> refunded = call(HttpMethod.POST, "/api/admin/bookings/" + id + "/refund",
                Map.of("reason", "Kulanz"), "de", token);
        assertThat(refunded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) refunded.getBody().get("payment")).get("status")).isEqualTo("REFUNDED");
        assertThat(text(mailTo(email, "Erstattung deiner Anzahlung"))).contains("nur simuliert");
        assertThat(jdbc.queryForList("select type from payment_event e join payment p on p.id = e.payment_id "
                + "where p.booking_id = ?::uuid order by e.created_at", String.class, id))
                .containsExactly("CREATED", "SUCCEEDED", "FORFEITED", "REFUND_OVERRIDE");

        assertThat(call(HttpMethod.POST, "/api/admin/bookings/" + id + "/refund", null, "de", token).getBody())
                .containsEntry("code", "payment.not_refundable").containsEntry("paymentStatus", "REFUNDED");
    }

    @Test
    void adminCancelRefundsByDefault_orKeepsTheDepositOnRequest() throws Exception {
        String token = adminToken();
        String a = confirmed(uniqueEmail(), at(next(DayOfWeek.WEDNESDAY), "10:00"));
        String b = confirmed(uniqueEmail(), at(next(DayOfWeek.WEDNESDAY), "15:00"));
        call(HttpMethod.POST, "/api/admin/bookings/" + a + "/cancel", Map.of("reason", "Krank"), "de", token);
        call(HttpMethod.POST, "/api/admin/bookings/" + b + "/cancel", Map.of("refund", false), "de", token);
        assertThat(paymentStatus(a)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(b)).isEqualTo("FORFEITED");
        // final states cannot be changed any more
        assertThat(call(HttpMethod.POST, "/api/admin/bookings/" + a + "/accept", null, "en", token).getBody())
                .containsEntry("code", "booking.transition_not_allowed").containsEntry("currentStatus", "CANCELLED");
    }

    // ── customers & settings ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void customerListShowsAccountsAndGuestsWithVisits() throws Exception {
        String token = adminToken();
        String guest = uniqueEmail();
        String visit = confirmed(guest, at(next(DayOfWeek.THURSDAY), "09:00"));
        moveBookingTo(visit, fromNow(Duration.ofDays(-3)));
        call(HttpMethod.POST, "/api/admin/bookings/" + visit + "/complete", null, "de", token);
        book(booking(BEARD, MO, at(next(DayOfWeek.THURSDAY), "12:00"), guest, "de"));

        String member = uniqueEmail();
        post(BASE + "/auth/register", Map.of("email", member, "password", "member-password", "name", "Mitglied Mia",
                "phone", "+49 170 1111111", "lang", "de", "consent", true));
        post("/api/public/auth/verify-email", Map.of("token", tokenFromMail(member, "Bitte bestätige", VERIFY_LINK)));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) get("/api/admin/customers", "de", token)
                .getBody().get("items");
        assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("email", guest).containsEntry("registered", false)
                .containsEntry("completedVisits", 1).containsEntry("totalBookings", 2));
        assertThat(rows).anySatisfy(r -> assertThat(r).containsEntry("email", member).containsEntry("registered", true)
                .containsEntry("completedVisits", 0).containsEntry("nextDiscountAvailable", false));
        Map<String, Object> filtered = get("/api/admin/customers?q=mia", "de", token).getBody();
        assertThat(filtered).containsEntry("totalItems", 1);
    }

    @Test
    void settingsAreValidatedAndApplyImmediately() {
        String token = adminToken();
        Map<String, Object> settings = new HashMap<>(get("/api/admin/settings", "de", token).getBody());
        assertThat(settings).containsEntry("depositCents", 1000).containsEntry("barberResponseMin", 720)
                .containsEntry("paymentDeadlineMin", 1440).containsEntry("refundCutoffMin", 1440);

        settings.put("loyaltyPercent", 150);
        assertThat(call(HttpMethod.PUT, "/api/admin/settings", settings, "en", token).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        settings.put("loyaltyPercent", 50);
        settings.put("depositCents", 1500);
        assertThat(call(HttpMethod.PUT, "/api/admin/settings", settings, "en", token).getBody())
                .containsEntry("depositCents", 1500);
        assertThat(((Map<?, ?>) get(BASE, "de").getBody().get("bookingRules")).get("depositCents")).isEqualTo(1500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theShopProfileIsValidatedAndPublic() {
        String token = adminToken();
        Map<String, Object> profile = new HashMap<>(get("/api/admin/settings/profile", "de", token).getBody());
        assertThat(profile).containsEntry("city", "Berlin").containsEntry("country", "DE");

        profile.put("country", "de");
        assertThat(call(HttpMethod.PUT, "/api/admin/settings/profile", profile, "en", token).getBody())
                .containsEntry("code", "validation.failed");
        profile.put("country", "AT");
        profile.put("city", "Wien");
        profile.put("phone", "");                               // empty clears the field
        assertThat(call(HttpMethod.PUT, "/api/admin/settings/profile", profile, "en", token).getBody())
                .containsEntry("city", "Wien").doesNotContainKey("phone"); // null fields are omitted
        assertThat((Map<String, Object>) get(BASE, "de").getBody().get("profile")).containsEntry("city", "Wien")
                .containsEntry("country", "AT");

        // admin only
        assertThat(call(HttpMethod.GET, "/api/admin/settings/profile", null, "en", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── catalogue, schedule, stats (from phase 2) ────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void servicesCrudAndSoftDelete() {
        String token = adminToken();
        Map<String, Object> req = Map.of("nameDe", "Kinderhaarschnitt", "nameEn", "Kids Cut",
                "durationMin", 30, "priceCents", 2000, "priceFrom", false, "sortOrder", 4);
        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/admin/services", req, "de", token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(getList(BASE + "/services", "en", null).getBody()).anySatisfy(s -> {
            assertThat(s.get("name")).isEqualTo("Kids Cut");
            assertThat(((Map<?, ?>) s.get("price")).get("formatted")).isEqualTo("€20");
        });
        Map<String, Object> invalid = call(HttpMethod.POST, "/api/admin/services", Map.of("nameDe", "x"), "en", token)
                .getBody();
        assertThat((Map<String, String>) invalid.get("errors")).containsKeys("nameEn", "durationMin", "priceCents");
        assertThat(call(HttpMethod.DELETE, "/api/admin/services/" + id, null, "de", token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getList(BASE + "/services", "en", null).getBody())
                .noneSatisfy(s -> assertThat(s.get("name")).isEqualTo("Kids Cut"));
        jdbc.update("delete from service where id = ?::uuid", id); // keep the shared demo data clean
    }

    @Test
    @SuppressWarnings("unchecked")
    void workingHoursValidationAndTimeOffBlocksAvailability() {
        String token = adminToken();
        Map<String, Object> overlapping = Map.of("entries", List.of(
                Map.of("weekday", 1, "start", "09:00", "end", "13:00"),
                Map.of("weekday", 1, "start", "12:00", "end", "18:00")));
        assertThat(call(HttpMethod.PUT, "/api/admin/staff/" + MO + "/working-hours", overlapping, "en", token)
                .getBody()).containsEntry("code", "working_hours.overlap");

        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        Map<String, Object> off = Map.of("staffId", LUCA.toString(), "start", at(tuesday, "00:00").toString(),
                "end", at(tuesday.plusDays(1), "00:00").toString(), "reason", "Fortbildung");
        assertThat(call(HttpMethod.POST, "/api/admin/time-off", off, "de", token).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Map<String, Object> day = get(BASE + "/availability?services=" + HAIRCUT + "&date=" + tuesday, "de").getBody();
        for (Map<String, Object> column : (List<Map<String, Object>>) day.get("barbers")) {
            List<?> slots = (List<?>) column.get("slots");
            if (LUCA.toString().equals(column.get("staffId"))) assertThat(slots).isEmpty();
            else assertThat(slots).isNotEmpty();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void statsCountBookedAndLostPerDayAndService() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        book(booking(HAIRCUT, LUCA, at(tuesday, "09:00"), uniqueEmail(), "de"));
        book(booking(List.of(HAIRCUT, BEARD), MO, at(tuesday, "10:00"), uniqueEmail(), "de"));
        String declined = (String) book(booking(BEARD, LUCA, at(tuesday.plusDays(1), "09:00"), uniqueEmail(), "de"))
                .getBody().get("id");
        String token = adminToken();
        call(HttpMethod.POST, "/api/admin/bookings/" + declined + "/decline", null, "de", token);

        Map<String, Object> stats = get("/api/admin/stats?from=" + tuesday + "&to=" + tuesday.plusDays(1), "en", token)
                .getBody();
        assertThat(stats).containsEntry("totalBooked", 2).containsEntry("totalCancelled", 1);
        assertThat((List<Map<String, Object>>) stats.get("perService")).anySatisfy(s -> {
            assertThat(s.get("name")).isEqualTo("Haircut");
            assertThat(s.get("booked")).isEqualTo(2);
        });
    }

    @Test
    void anAdminCannotSeeAnotherShopsBookings() {
        UUID otherTenant = UUID.randomUUID();
        UUID otherStaff = UUID.randomUUID();
        UUID otherBooking = UUID.randomUUID();
        jdbc.update("insert into tenant (id, name, slug) values (?, 'Other Shop', ?)", otherTenant,
                "other-" + otherTenant.toString().substring(0, 8));
        jdbc.update("insert into staff (id, tenant_id, name) values (?, ?, 'Other')", otherStaff, otherTenant);
        LocalDate day = next(DayOfWeek.FRIDAY);
        jdbc.update("""
                insert into booking (id, tenant_id, staff_id, status, start_time, end_time, blocked_until,
                  customer_name, customer_email, locale, currency, price_total_cents, deposit_cents,
                  amount_due_at_shop_cents)
                values (?, ?, ?, 'CONFIRMED', ?, ?, ?, 'Secret', 'secret@example.com', 'de', 'EUR', 1000, 0, 1000)""",
                otherBooking, otherTenant, otherStaff, Timestamp.from(at(day, "10:00").toInstant()),
                Timestamp.from(at(day, "10:30").toInstant()), Timestamp.from(at(day, "10:30").toInstant()));
        try {
            String token = adminToken();
            for (String path : List.of("", "/cancel", "/complete", "/refund")) {
                HttpMethod m = path.isEmpty() ? HttpMethod.GET : HttpMethod.POST;
                assertThat(call(m, "/api/admin/bookings/" + otherBooking + path, null, "en", token).getStatusCode())
                        .as(path).isEqualTo(HttpStatus.NOT_FOUND);
            }
            assertThat(get("/api/admin/bookings?from=" + day + "&to=" + day, "en", token).getBody())
                    .containsEntry("totalItems", 0);
        } finally {
            jdbc.update("delete from booking where id = ?", otherBooking);
            jdbc.update("delete from tenant where id = ?", otherTenant);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApiDocsListTheNewEndpoints() {
        ResponseEntity<Map> docs = rest.exchange("/v3/api-docs", HttpMethod.GET, new HttpEntity<>(headers("en", null)),
                Map.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) docs.getBody().get("paths")).keySet()).contains(
                "/api/public/{tenant}/availability", "/api/public/{tenant}/bookings",
                "/api/public/{tenant}/bookings/price-preview", "/api/public/barber-decision/{token}/accept",
                "/api/public/payments/{token}/simulate", "/api/public/manage/{token}/cancel",
                "/api/public/{tenant}/auth/register", "/api/auth/refresh", "/api/me/bookings", "/api/me/export",
                "/api/barber/requests", "/api/admin/bookings/{id}/refund", "/api/admin/customers",
                "/api/admin/settings", "/api/public/booking-status/{token}");
        // schemas are named by simple class name — two records with the same name would merge silently
        // (the frontend's generated types depend on this): the catalogue price is its own schema
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) docs.getBody().get("components"))
                .get("schemas");
        Map<String, Object> serviceView = (Map<String, Object>) schemas.get("ServiceView");
        assertThat(((Map<String, Object>) ((Map<String, Object>) serviceView.get("properties")).get("price")).get("$ref"))
                .isEqualTo("#/components/schemas/CatalogPrice");
        assertThat((Map<String, Object>) ((Map<String, Object>) schemas.get("PriceView")).get("properties"))
                .containsKeys("total", "deposit", "amountDueAtShop");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String confirmed(String email, java.time.OffsetDateTime start) {
        String id = (String) book(booking(HAIRCUT, LUCA, start, email, "de")).getBody().get("id");
        call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "de", adminToken());
        payments.handlePaymentSucceeded(jdbc.queryForObject(
                "select provider_ref from payment where booking_id = ?::uuid", String.class, id));
        return id;
    }

    private String paymentStatus(String id) {
        return jdbc.queryForObject("select status from payment where booking_id = ?::uuid", String.class, id);
    }
}
