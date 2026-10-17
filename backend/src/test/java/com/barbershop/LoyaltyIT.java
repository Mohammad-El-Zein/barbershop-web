package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Loyalty: 10 completed visits → the 11th booking gets 50 % off the services →
 * the counter starts over. Reserved at creation, released on cancel, consumed
 * on completion. Accounts only.
 */
class LoyaltyIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "loyal-customer-pw";

    @Autowired
    PaymentService payments;

    @Test
    @SuppressWarnings("unchecked")
    void theEleventhBookingGetsHalfPrice_thenTheCycleStartsOver() throws Exception {
        String email = customer();
        String token = login(email, PASSWORD);
        String admin = adminToken();
        OffsetDateTime slot = at(next(DayOfWeek.TUESDAY), "10:00");

        for (int visit = 1; visit <= 10; visit++) {
            String id = createAs(token, slot);
            assertThat(jdbc.queryForObject("select loyalty_discount_cents from booking where id = ?::uuid",
                    Integer.class, id)).isZero();
            completeVisit(id, visit, admin);
            assertThat(get("/api/me/loyalty", "de", token).getBody())
                    .containsEntry("visitsInCurrentCycle", visit)
                    .containsEntry("nextDiscountAvailable", visit == 10);
        }

        // price preview: the customer sees the reward, a guest does not
        Map<String, Object> preview = call(HttpMethod.POST, BASE + "/bookings/price-preview",
                Map.of("serviceIds", List.of(HAIRCUT)), "de", token).getBody();
        Map<String, Object> price = (Map<String, Object>) preview.get("price");
        assertThat(cents(price, "total")).isEqualTo(3500);
        assertThat(cents(price, "loyaltyDiscount")).isEqualTo(1750);
        assertThat(cents(price, "deposit")).isEqualTo(1000);
        assertThat(cents(price, "amountDueAtShop")).isEqualTo(750);
        assertThat((Map<String, Object>) preview.get("loyalty")).containsEntry("visitsInCurrentCycle", 10)
                .containsEntry("appliesToThisBooking", true);
        Map<String, Object> guestPreview = post(BASE + "/bookings/price-preview",
                Map.of("serviceIds", List.of(HAIRCUT))).getBody();
        assertThat(guestPreview.get("loyalty")).isNull();
        assertThat(cents((Map<String, Object>) guestPreview.get("price"), "loyaltyDiscount")).isZero();

        // 11th booking: reserved
        String eleventh = createAs(token, slot);
        Map<String, Object> b11 = get("/api/me/bookings/" + eleventh, "de", token).getBody();
        assertThat(b11).containsEntry("loyalty", "RESERVED");
        assertThat(cents((Map<String, Object>) b11.get("price"), "loyaltyDiscount")).isEqualTo(1750);
        assertThat(get("/api/me/loyalty", "de", token).getBody()).containsEntry("nextDiscountAvailable", false)
                .containsEntry("discountReserved", true);
        // the barber's request mail says so
        mailContaining(ADMIN_EMAIL, "Neue Terminanfrage: Stammkunde", "Treuerabatt 50 % wird bei diesem Termin eingelöst");

        // cancelled → released, available again
        call(HttpMethod.POST, "/api/me/bookings/" + eleventh + "/cancel", null, "de", token);
        assertThat(jdbc.queryForObject("select loyalty_status from booking where id = ?::uuid", String.class, eleventh))
                .isEqualTo("RELEASED");
        assertThat(get("/api/me/loyalty", "de", token).getBody()).containsEntry("nextDiscountAvailable", true);

        // booked again and completed → consumed, new cycle
        String rebooked = createAs(token, slot);
        completeVisit(rebooked, 11, admin);
        assertThat(jdbc.queryForObject("select loyalty_status from booking where id = ?::uuid", String.class, rebooked))
                .isEqualTo("CONSUMED");
        assertThat(get("/api/me/loyalty", "de", token).getBody())
                .containsEntry("visitsInCurrentCycle", 0)
                .containsEntry("nextDiscountAvailable", false)
                .containsEntry("rewardsUsed", 1)
                .containsEntry("totalVisits", 11);
        assertThat(createAs(token, slot)).isNotNull();
        assertThat(jdbc.queryForObject("select max(loyalty_discount_cents) from booking where status = 'PENDING_BARBER'",
                Integer.class)).isZero();
    }

    @Test
    void twoParallelBookingsCannotBothGetTheReward() throws Exception {
        // threshold and percent are per-tenant settings
        Map<String, Object> settings = new HashMap<>(get("/api/admin/settings", "de", adminToken()).getBody());
        settings.put("loyaltyThreshold", 1);
        settings.put("loyaltyPercent", 20);
        assertThat(call(HttpMethod.PUT, "/api/admin/settings", settings, "de", adminToken()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String email = customer();
        String token = login(email, PASSWORD);
        completeVisit(createAs(token, at(next(DayOfWeek.THURSDAY), "10:00")), 1, adminToken());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        for (String time : List.of("15:00", "16:00")) {
            Map<String, Object> req = request(at(next(DayOfWeek.THURSDAY), time));
            futures.add(pool.submit(() -> {
                go.await();
                return book(req, token);
            }));
        }
        go.countDown();
        for (Future<ResponseEntity<Map<String, Object>>> f : futures) {
            assertThat(f.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        pool.shutdown();
        assertThat(jdbc.queryForList("select loyalty_discount_cents from booking where status = 'PENDING_BARBER'",
                Integer.class)).containsExactlyInAnyOrder(0, 700); // 20 % of 35 €, exactly once
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A verified customer account named "Stammkunde …". */
    private String customer() throws Exception {
        String email = uniqueEmail();
        post(BASE + "/auth/register", Map.of("email", email, "password", PASSWORD, "name", "Stammkunde Tom",
                "phone", "+49 170 7654321", "lang", "de", "consent", true));
        post("/api/public/auth/verify-email", Map.of("token", tokenFromMail(email, "Bitte bestätige", VERIFY_LINK)));
        return email;
    }

    private Map<String, Object> request(OffsetDateTime start) {
        Map<String, Object> req = new HashMap<>();
        req.put("serviceIds", List.of(HAIRCUT));
        req.put("staffId", LUCA);
        req.put("start", start.toString());
        return req; // name/email/phone come from the account
    }

    private String createAs(String token, OffsetDateTime start) {
        ResponseEntity<Map<String, Object>> r = book(request(start), token);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        return (String) r.getBody().get("id");
    }

    /** accept → paid → (moved into the past) → completed by the admin. */
    private void completeVisit(String id, int daysAgo, String admin) {
        assertThat(call(HttpMethod.POST, "/api/admin/bookings/" + id + "/accept", null, "de", admin).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        payments.handlePaymentSucceeded(jdbc.queryForObject(
                "select provider_ref from payment where booking_id = ?::uuid", String.class, id));
        moveBookingTo(id, fromNow(Duration.ofDays(-daysAgo)));
        assertThat(call(HttpMethod.POST, "/api/admin/bookings/" + id + "/complete", null, "de", admin).getBody())
                .containsEntry("status", "COMPLETED");
    }

    @SuppressWarnings("unchecked")
    private static int cents(Map<String, Object> parent, String key) {
        return (Integer) ((Map<String, Object>) parent.get(key)).get("cents");
    }
}
