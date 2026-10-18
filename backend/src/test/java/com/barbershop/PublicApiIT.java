package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Public API: shop data, availability (multi-service, per barber), price preview, booking validation. */
class PublicApiIT extends AbstractIntegrationTest {

    // ── shop data ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shopInfoContainsTheCustomerFacingBookingRules() {
        Map<String, Object> shop = get(BASE, "de").getBody();
        assertThat(shop).containsEntry("name", "The Barber News").containsEntry("timezone", "Europe/Berlin");
        assertThat((Map<String, Object>) shop.get("bookingRules"))
                .containsEntry("depositCents", 1000).containsEntry("refundCutoffMin", 1440)
                .containsEntry("minLeadMin", 240).containsEntry("onlineCancelCutoffMin", 120).containsEntry("loyaltyThreshold", 10)
                .containsEntry("loyaltyPercent", 50).containsEntry("demoPayments", true);
    }

    @Test
    void servicesAreLocalizedByAcceptLanguageAndLangParameter() {
        List<Map<String, Object>> de = getList(BASE + "/services", "de", null).getBody();
        List<Map<String, Object>> en = getList(BASE + "/services?lang=en", "de", null).getBody();
        assertThat(de).extracting(s -> s.get("name")).containsExactly("Haarschnitt", "Bart-Styling", "Premium-Paket");
        assertThat(en).extracting(s -> s.get("name")).containsExactly("Haircut", "Beard Sculpting", "Premium Styling");
        assertThat(((Map<?, ?>) de.getFirst().get("price")).get("formatted")).isEqualTo("ab 35 €");
        assertThat(((Map<?, ?>) en.getFirst().get("price")).get("formatted")).isEqualTo("from €35");
    }

    @Test
    void errorsAreLocalizedProblemDetails() {
        ResponseEntity<Map<String, Object>> de = get("/api/public/unknown-shop/services", "de");
        ResponseEntity<Map<String, Object>> en = get("/api/public/unknown-shop/services", "en");
        assertThat(de.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(de.getBody()).containsEntry("code", "tenant.not_found").containsEntry("title", "Nicht gefunden");
        assertThat(en.getBody()).containsEntry("title", "Not found")
                .containsEntry("detail", "There is no barbershop “unknown-shop”.");
        assertThat(de.getHeaders().getFirst("Content-Language")).isEqualTo("de");
    }

    // ── availability ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void oneDayForAllBarbersAtOnce_columnsPlusAnyBarber() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        Map<String, Object> day = get(BASE + "/availability?services=" + HAIRCUT + "&date=" + tuesday, "de").getBody();
        assertThat(day).containsEntry("durationMin", 45).containsEntry("slotStepMin", 15)
                .containsEntry("timezone", "Europe/Berlin");

        Map<String, List<String>> columns = columns(day);
        assertThat(columns.keySet()).containsExactly(LUCA.toString(), MO.toString());
        assertThat(columns.get(LUCA.toString())).startsWith("09:00", "09:15").contains("12:15", "14:00", "19:15")
                .doesNotContain("12:30", "13:00", "19:30"); // lunch 13–14, closes 20:00
        assertThat(columns.get(MO.toString())).startsWith("10:00").contains("13:00", "18:15").doesNotContain("18:30");

        Map<String, List<String>> any = anyBarber(day);
        assertThat(any.get("09:00")).containsExactly(LUCA.toString());
        assertThat(any.get("10:00")).containsExactlyInAnyOrder(LUCA.toString(), MO.toString());
        assertThat(any.get("13:00")).containsExactly(MO.toString());
        List<Map<String, Object>> anySlots = (List<Map<String, Object>>) day.get("anyBarber");
        assertThat(OffsetDateTime.parse((String) anySlots.getFirst().get("start"))).isEqualTo(at(tuesday, "09:00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleServicesUseTheSummedDurationAndOnlyBarbersOfferingAll() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        Map<String, Object> combo = get(BASE + "/availability?services=" + HAIRCUT + "," + BEARD + "&date=" + tuesday,
                "de").getBody();
        assertThat(combo).containsEntry("durationMin", 75);
        Map<String, List<String>> columns = columns(combo);
        assertThat(columns.get(LUCA.toString())).contains("11:45").doesNotContain("12:00") // 12:00 + 75 > 13:00
                .contains("18:45").doesNotContain("19:00");
        assertThat(columns.get(MO.toString())).contains("17:45").doesNotContain("18:00");

        // only Luca offers the premium package → only his column
        Map<String, Object> premium = get(BASE + "/availability?services=" + PREMIUM + "," + BEARD + "&date="
                + tuesday, "de").getBody();
        assertThat(columns(premium).keySet()).containsExactly(LUCA.toString());
        assertThat(premium).containsEntry("durationMin", 120);
        // a booked combination blocks the whole 75 minutes
        book(booking(List.of(HAIRCUT, BEARD), MO, at(tuesday, "10:00"), uniqueEmail(), "de"));
        Map<String, Object> beardDay = get(BASE + "/availability?services=" + BEARD + "&date=" + tuesday, "de").getBody();
        assertThat(columns(beardDay).get(MO.toString())).doesNotContain("10:00", "10:45", "11:00").contains("11:15");
        // … and the taken times stay in Mo's column, greyed out (available=false), but not in "any barber" for Mo
        List<Map<String, Object>> moSlots = ((List<Map<String, Object>>) beardDay.get("barbers")).stream()
                .filter(c -> MO.toString().equals(c.get("staffId")))
                .flatMap(c -> ((List<Map<String, Object>>) c.get("slots")).stream()).toList();
        assertThat(moSlots).anySatisfy(s -> assertThat(s).containsEntry("time", "10:00").containsEntry("available", false));
        assertThat(anyBarber(beardDay).get("10:00")).doesNotContain(MO.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nothingCanBeBookedWithinTheFourHourLeadTime() {
        java.time.Instant earliest = java.time.Instant.now().plus(java.time.Duration.ofHours(4));
        for (int d = 0; d <= 1; d++) { // today and tomorrow: both may be inside the lead time
            LocalDate day = LocalDate.now(BERLIN).plusDays(d);
            Map<String, Object> body = get(BASE + "/availability?services=" + HAIRCUT + "&date=" + day, "de").getBody();
            assertThat((List<Map<String, Object>>) body.get("anyBarber")).allSatisfy(s ->
                    assertThat(OffsetDateTime.parse((String) s.get("start")).toInstant()).isAfterOrEqualTo(earliest));
        }
    }

    @Test
    void leadTimeSlotStepAndHorizonComeFromTheTenantPolicy() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        jdbc.update("update tenant set slot_step_min = 30 where id = ?", TENANT_ID);
        assertThat(columns(get(BASE + "/availability?services=" + HAIRCUT + "&date=" + tuesday, "de").getBody())
                .get(LUCA.toString())).isNotEmpty().allSatisfy(t -> assertThat(t).matches("\\d\\d:(00|30)"));

        // a 30-day lead time empties the next week, a day 40+ days out still has slots
        jdbc.update("update tenant set min_lead_min = ? where id = ?", 30 * 24 * 60, TENANT_ID);
        assertThat(columns(get(BASE + "/availability?services=" + HAIRCUT + "&date=" + tuesday, "de").getBody())
                .values()).allSatisfy(slots -> assertThat(slots).isEmpty());
        LocalDate far = LocalDate.now(BERLIN).plusDays(40).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        assertThat(columns(get(BASE + "/availability?services=" + HAIRCUT + "&date=" + far, "de").getBody())
                .get(LUCA.toString())).isNotEmpty();
        // and a booking inside the lead time is refused
        assertThat(book(booking(HAIRCUT, LUCA, at(tuesday, "10:00"), uniqueEmail(), "de")).getBody())
                .containsEntry("code", "booking.slot_invalid");

        ResponseEntity<Map<String, Object>> tooFar = get(BASE + "/availability?services=" + HAIRCUT + "&date="
                + LocalDate.now(BERLIN).plusDays(61), "en");
        assertThat(tooFar.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooFar.getBody()).containsEntry("code", "availability.date_out_of_range");
    }

    @Test
    @SuppressWarnings("unchecked")
    void availableDaysSkipSundays() {
        LocalDate from = next(DayOfWeek.MONDAY);
        Map<String, Object> body = get(BASE + "/availability/days?services=" + HAIRCUT + "," + BEARD + "&from=" + from
                + "&to=" + from.plusDays(6), "de").getBody();
        assertThat((List<String>) body.get("days")).hasSize(6).doesNotContain(from.plusDays(6).toString());
    }

    // ── price preview ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void pricePreviewForAGuest() {
        Map<String, Object> p = call(HttpMethod.POST, BASE + "/bookings/price-preview",
                Map.of("serviceIds", List.of(HAIRCUT, BEARD)), "en", null).getBody();
        assertThat(p).containsEntry("durationMin", 75).doesNotContainKey("loyalty");
        Map<String, Object> price = (Map<String, Object>) p.get("price");
        assertThat((Map<String, Object>) price.get("total")).containsEntry("cents", 6000).containsEntry("formatted", "€60");
        assertThat((Map<String, Object>) price.get("deposit")).containsEntry("cents", 1000);
        assertThat((Map<String, Object>) price.get("amountDueAtShop")).containsEntry("cents", 5000);
        assertThat(price).doesNotContainKey("priceFrom"); // booking prices are exact, even for "from" services

        assertThat(post(BASE + "/bookings/price-preview", Map.of("serviceIds", List.of(HAIRCUT, HAIRCUT))).getBody())
                .containsEntry("code", "service.duplicate");
        assertThat(post(BASE + "/bookings/price-preview", Map.of("serviceIds", List.of(UUID.randomUUID()))).getBody())
                .containsEntry("code", "service.not_found");
    }

    // ── booking requests ─────────────────────────────────────────────────────

    @Test
    void doubleBookingTheSameSlotIs409Localized() {
        OffsetDateTime slot = at(next(DayOfWeek.WEDNESDAY), "11:00");
        assertThat(book(booking(HAIRCUT, MO, slot, uniqueEmail(), "de")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map<String, Object>> second = call(HttpMethod.POST, BASE + "/bookings",
                booking(BEARD, MO, at(next(DayOfWeek.WEDNESDAY), "11:30"), uniqueEmail(), "en"), "en", null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("code", "booking.slot_taken")
                .containsEntry("detail", "This time was just booked by someone else. Please pick another time.");
    }

    @Test
    void anyBarberGetsTheLeastBusyFreeOneThenConflicts() {
        LocalDate thursday = next(DayOfWeek.THURSDAY);
        book(booking(HAIRCUT, LUCA, at(thursday, "09:00"), uniqueEmail(), "de")); // Luca has 45 min that day
        Map<String, Object> first = book(booking(HAIRCUT, null, at(thursday, "10:30"), uniqueEmail(), "de")).getBody();
        assertThat(((Map<?, ?>) first.get("staff")).get("name")).isEqualTo("Mo"); // the fairest choice
        Map<String, Object> second = book(booking(HAIRCUT, null, at(thursday, "10:30"), uniqueEmail(), "de")).getBody();
        assertThat(((Map<?, ?>) second.get("staff")).get("name")).isEqualTo("Luca");
        assertThat(book(booking(HAIRCUT, null, at(thursday, "10:30"), uniqueEmail(), "de")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidTimesAre422() {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        for (OffsetDateTime invalid : List.of(at(tuesday, "10:07"), at(tuesday, "13:00"), at(tuesday, "07:00"),
                at(next(DayOfWeek.SUNDAY), "12:00"))) {
            assertThat(book(booking(HAIRCUT, LUCA, invalid, uniqueEmail(), "de")).getBody())
                    .as(invalid.toString()).containsEntry("code", "booking.slot_invalid");
        }
        assertThat(book(booking(PREMIUM, MO, at(tuesday, "10:00"), uniqueEmail(), "de")).getBody())
                .containsEntry("code", "staff.does_not_offer_service");
    }

    @Test
    @SuppressWarnings("unchecked")
    void guestsNeedContactDataConsentAndAShortNote() {
        Map<String, Object> req = booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "10:00"), uniqueEmail(), "en");
        req.remove("consent");
        req.remove("customerPhone");
        ResponseEntity<Map<String, Object>> r = call(HttpMethod.POST, BASE + "/bookings", req, "en", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map<String, String>) r.getBody().get("errors"))
                .containsEntry("consent", "Please accept the privacy policy.")
                .containsEntry("customerPhone", "Required.");

        Map<String, Object> longNote = booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "10:00"), uniqueEmail(), "de");
        longNote.put("note", "x".repeat(501));
        longNote.put("customerEmail", "not-an-email");
        Map<String, Object> body = call(HttpMethod.POST, BASE + "/bookings", longNote, "de", null).getBody();
        assertThat((Map<String, String>) body.get("errors"))
                .containsEntry("note", "Darf höchstens 500 Zeichen lang sein.")
                .containsEntry("customerEmail", "Bitte gib eine gültige E-Mail-Adresse ein.");
    }

    @Test
    void atMostThreeOpenRequestsPerEmail() {
        String email = uniqueEmail();
        LocalDate friday = next(DayOfWeek.FRIDAY);
        for (String time : List.of("10:00", "11:00", "12:00")) {
            assertThat(book(booking(BEARD, MO, at(friday, time), email, "de")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        assertThat(book(booking(BEARD, MO, at(friday, "13:00"), email, "de")).getBody())
                .containsEntry("code", "booking.too_many_open_requests");
    }

    @Test
    void anExpiredOrForeignTokenIsNotALogin() {
        assertThat(rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(headers("en", "not.a.jwt")), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** staffId → local times of that barber's column. */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> columns(Map<String, Object> day) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) day.get("barbers")) {
            result.put((String) c.get("staffId"), ((List<Map<String, Object>>) c.get("slots")).stream()
                    .filter(s -> Boolean.TRUE.equals(s.get("available")))
                    .map(s -> (String) s.get("time")).toList());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> anyBarber(Map<String, Object> day) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) day.get("anyBarber")) {
            result.put((String) s.get("time"), (List<String>) s.get("staffIds"));
        }
        return result;
    }
}
