package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * The booking lifecycle end to end, driven through the real email links:
 * request → barber accepts (link) → demo payment (link) → CONFIRMED, plus
 * decline, link replay, idempotent payment, cancellation with/without refund.
 * The barber has no own login here, so barber emails go to the admin (fallback).
 */
class BookingFlowIT extends AbstractIntegrationTest {

    @Autowired
    PaymentService paymentService;

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void requestAcceptDemoPaymentConfirmed_withAllEmails() throws Exception {
        LocalDate tuesday = next(DayOfWeek.TUESDAY);
        String email = uniqueEmail();
        Map<String, Object> req = booking(List.of(HAIRCUT, BEARD), LUCA, at(tuesday, "10:00"), email, "en");
        req.put("note", "Bitte nicht zu kurz an den Seiten");

        // 1) request
        ResponseEntity<Map<String, Object>> created = book(req);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> b = created.getBody();
        String id = (String) b.get("id");
        assertThat(b).containsEntry("status", "PENDING_BARBER").containsEntry("durationMin", 75)
                .containsEntry("lang", "en").doesNotContainKey("manageToken");
        assertThat(OffsetDateTime.parse((String) b.get("end"))).isEqualTo(at(tuesday, "11:15"));
        Map<String, Object> price = (Map<String, Object>) b.get("price");
        assertThat(cents(price, "total")).isEqualTo(6000);
        assertThat(cents(price, "deposit")).isEqualTo(1000);
        assertThat(cents(price, "amountDueAtShop")).isEqualTo(5000);
        assertThat((List<?>) b.get("services")).hasSize(2);

        MimeMessage received = mailTo(email, "Your appointment request at The Barber News");
        assertThat(received.getContentLanguage()).containsExactly("en");
        assertThat(text(received)).contains("Haircut + Beard Sculpting").contains("€10 deposit");

        // the barber's request mail (admin fallback, German): details + Accept/Decline at the end
        MimeMessage request = mailTo(ADMIN_EMAIL, "Neue Terminanfrage: " + nameFor(email));
        String requestText = text(request);
        assertThat(requestText).contains("Haarschnitt + Bart-Styling", "75 Min.", "Bitte nicht zu kurz an den Seiten",
                "Gast (kein Kundenkonto)", "Annehmen", "Ablehnen", "?action=accept", "?action=decline");
        String decision = tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK);

        // 2) opening the link (GET) — e.g. by an email scanner — changes nothing
        for (int i = 0; i < 2; i++) {
            Map<String, Object> page = get("/api/public/barber-decision/" + decision, "de").getBody();
            assertThat(page).containsEntry("status", "PENDING_BARBER").containsEntry("customerName", nameFor(email));
            assertThat((List<String>) page.get("actions")).containsExactly("ACCEPT", "DECLINE");
        }
        assertThat(status(id)).isEqualTo("PENDING_BARBER");

        // 3) accept (POST from the page's button)
        Map<String, Object> accepted = post("/api/public/barber-decision/" + decision + "/accept", null).getBody();
        assertThat(accepted).containsEntry("status", "AWAITING_PAYMENT");
        assertThat(paymentStatus(id)).isEqualTo("UNPAID");

        MimeMessage payMail = mailTo(email, "Luca accepted");
        assertThat(text(payMail)).contains("Pay now", "€10", "DEMO MODE");
        String pay = tokenFromMail(email, "Luca accepted", PAY_LINK);

        // 4) payment page (GET twice: still unpaid), demo banner flag, no card fields — just two actions
        for (int i = 0; i < 2; i++) {
            Map<String, Object> page = get("/api/public/payments/" + pay, "en").getBody();
            assertThat(page).containsEntry("demoMode", true).containsEntry("status", "UNPAID");
            assertThat(cents(page, "amount")).isEqualTo(1000);
            assertThat((List<String>) page.get("actions")).containsExactly("SIMULATE_PAYMENT", "CANCEL");
            assertThat(((Map<String, Object>) page.get("booking")).get("id")).isEqualTo(id);
        }
        assertThat(paymentStatus(id)).isEqualTo("UNPAID");

        // 5) simulate payment → CONFIRMED
        Map<String, Object> paid = post("/api/public/payments/" + pay + "/simulate", null).getBody();
        assertThat(paid).containsEntry("paymentStatus", "PAID").containsEntry("bookingStatus", "CONFIRMED")
                .containsEntry("alreadyProcessed", false).containsEntry("demoMode", true);
        assertThat(status(id)).isEqualTo("CONFIRMED");

        MimeMessage confirmed = mailTo(email, "Appointment confirmed");
        assertThat(text(confirmed)).contains("Deposit paid", "To pay at the shop", "€50", "DEMO MODE",
                "cancel for free");
        assertThat(attachmentNames(confirmed)).containsExactly("appointment.ics");
        MimeMessage barberPaid = mailTo(ADMIN_EMAIL, "Anzahlung erhalten: " + nameFor(email));
        assertThat(text(barberPaid)).contains("10 € Anzahlung bezahlt", "Haarschnitt + Bart-Styling", "10:00–11:15");
        // the barber sees the exact amount still to collect — no "ab" prefix
        assertThat(text(barberPaid)).contains("Im Laden zu zahlen: 50 €").doesNotContain("Im Laden zu zahlen: ab");

        assertThat(jdbc.queryForList("select e.type from payment_event e join payment p on p.id = e.payment_id "
                + "where p.booking_id = ?::uuid order by e.created_at", String.class, id))
                .containsExactly("CREATED", "SUCCEEDED");

        // the slot is gone from Luca's column
        assertThat(lucaTimes(tuesday, HAIRCUT)).doesNotContain("10:00", "10:30", "11:00").contains("11:15");
    }

    // ── decline ──────────────────────────────────────────────────────────────

    @Test
    void declineAsksForAnotherTimeAndFreesTheSlot() throws Exception {
        LocalDate wednesday = next(DayOfWeek.WEDNESDAY);
        String email = uniqueEmail();
        String id = (String) book(booking(HAIRCUT, LUCA, at(wednesday, "15:00"), email, "de")).getBody().get("id");
        assertThat(lucaTimes(wednesday, HAIRCUT)).doesNotContain("15:00");

        String decision = tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK);
        Map<String, Object> declined = post("/api/public/barber-decision/" + decision + "/decline",
                Map.of("reason", "Ich bin an dem Tag auf Fortbildung")).getBody();
        assertThat(declined).containsEntry("status", "DECLINED");
        assertThat(jdbc.queryForObject("select status_reason from booking where id = ?::uuid", String.class, id))
                .isEqualTo("Ich bin an dem Tag auf Fortbildung");

        MimeMessage mail = mailTo(email, "wurde abgelehnt");
        assertThat(text(mail)).contains("Andere Uhrzeit wählen", "/de/booking", "Ich bin an dem Tag auf Fortbildung");
        assertThat(lucaTimes(wednesday, HAIRCUT)).contains("15:00");
    }

    // ── link security ────────────────────────────────────────────────────────

    @Test
    void emailLinksAreSingleUseAndBoundToTheirPurpose() throws Exception {
        String email = uniqueEmail();
        book(booking(BEARD, MO, at(next(DayOfWeek.THURSDAY), "11:00"), email, "en"));
        String decision = tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK);
        String manage = tokenFromMail(email, "request", MANAGE_LINK);

        // a manage token is no decision token (the purpose is part of the hash)
        assertThat(post("/api/public/barber-decision/" + manage + "/accept", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(post("/api/public/barber-decision/" + decision + "/accept", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // replay: accept again, decline with the same link, open it again
        ResponseEntity<Map<String, Object>> again = call(HttpMethod.POST,
                "/api/public/barber-decision/" + decision + "/accept", null, "en", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(again.getBody()).containsEntry("code", "token.used")
                .containsEntry("detail", "This link has already been used.");
        assertThat(post("/api/public/barber-decision/" + decision + "/decline", null).getBody())
                .containsEntry("code", "token.used")
                .containsEntry("detail", "Dieser Link wurde bereits verwendet.");
        assertThat(get("/api/public/barber-decision/" + decision, "en").getBody())
                .containsEntry("code", "token.used").containsEntry("bookingStatus", "AWAITING_PAYMENT");

        // unknown / garbage tokens
        assertThat(get("/api/public/barber-decision/" + "A".repeat(43), "en").getBody())
                .containsEntry("code", "token.invalid");
        assertThat(get("/api/public/payments/" + "x".repeat(300), "en").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // after paying, the payment link is used up but still tells the page "paid"
        String pay = tokenFromMail(email, "accepted", PAY_LINK);
        post("/api/public/payments/" + pay + "/simulate", null);
        assertThat(get("/api/public/payments/" + pay, "en").getBody())
                .containsEntry("code", "token.used").containsEntry("paymentStatus", "PAID");
        // the database never contains the raw token
        assertThat(jdbc.queryForObject("select count(*) from action_token where token_hash in (?, ?, ?)", Long.class,
                decision, manage, pay)).isZero();
    }

    @Test
    void expiredLinksAreRejectedWithALocalizedMessage() throws Exception {
        String email = uniqueEmail();
        book(booking(BEARD, MO, at(next(DayOfWeek.THURSDAY), "14:00"), email, "de"));
        String decision = tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK);
        jdbc.update("update action_token set expires_at = now() - interval '1 minute' where purpose = 'BARBER_DECISION'");

        ResponseEntity<Map<String, Object>> r = call(HttpMethod.POST,
                "/api/public/barber-decision/" + decision + "/accept", null, "de", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(r.getBody()).containsEntry("code", "token.expired")
                .containsEntry("detail", "Dieser Link ist abgelaufen.");
    }

    // ── payment idempotency ──────────────────────────────────────────────────

    @Test
    void repeatedPaymentSuccessIsIdempotent() throws Exception {
        String email = uniqueEmail();
        String id = (String) book(booking(HAIRCUT, MO, at(next(DayOfWeek.FRIDAY), "12:00"), email, "en"))
                .getBody().get("id");
        post("/api/public/barber-decision/" + tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK) + "/accept",
                null);
        String pay = tokenFromMail(email, "accepted", PAY_LINK);

        assertThat(post("/api/public/payments/" + pay + "/simulate", null).getBody())
                .containsEntry("alreadyProcessed", false).containsEntry("bookingStatus", "CONFIRMED");
        // a double click: 200, same state, nothing happens again
        assertThat(post("/api/public/payments/" + pay + "/simulate", null).getBody())
                .containsEntry("alreadyProcessed", true).containsEntry("paymentStatus", "PAID");

        // the provider-webhook path (same method), even concurrently: no second confirmation
        String ref = jdbc.queryForObject("select provider_ref from payment where booking_id = ?::uuid", String.class, id);
        List<PaymentService.Outcome> outcomes = parallel(8, () -> paymentService.handlePaymentSucceeded(ref).outcome());
        assertThat(outcomes).containsOnly(PaymentService.Outcome.ALREADY_PROCESSED);

        mailTo(email, "Appointment confirmed");
        Thread.sleep(1000); // give any (wrong) duplicate mail time to arrive
        assertThat(countMails(email, "Appointment confirmed")).isEqualTo(1);
        assertThat(countMails(ADMIN_EMAIL, "Anzahlung erhalten: " + nameFor(email))).isEqualTo(1);
        assertThat(jdbc.queryForList("select e.type from payment_event e join payment p on p.id = e.payment_id "
                + "where p.booking_id = ?::uuid", String.class, id))
                .containsOnlyOnce("SUCCEEDED").filteredOn("DUPLICATE_SUCCESS_IGNORED"::equals).hasSize(9);
    }

    @Test
    void concurrentFirstPaymentEventsConfirmExactlyOnce() throws Exception {
        String email = uniqueEmail();
        String id = (String) book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.FRIDAY), "16:00"), email, "en"))
                .getBody().get("id");
        post("/api/public/barber-decision/" + tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK) + "/accept",
                null);
        String ref = jdbc.queryForObject("select provider_ref from payment where booking_id = ?::uuid", String.class, id);

        List<PaymentService.Outcome> outcomes = parallel(8, () -> paymentService.handlePaymentSucceeded(ref).outcome());
        assertThat(outcomes).filteredOn(PaymentService.Outcome.CONFIRMED::equals).hasSize(1);
        assertThat(outcomes).filteredOn(PaymentService.Outcome.ALREADY_PROCESSED::equals).hasSize(7);
        assertThat(status(id)).isEqualTo("CONFIRMED");
    }

    // ── cancellation & refund rule ───────────────────────────────────────────

    @Test
    void cancelAtLeast24HoursAheadRefundsTheDeposit() throws Exception {
        String email = uniqueEmail();
        String id = confirmedBooking(email, LUCA, at(next(DayOfWeek.MONDAY), "11:00"), "de");
        String manage = tokenFromMail(email, "Termin bestätigt", MANAGE_LINK);

        Map<String, Object> page = get("/api/public/manage/" + manage, "de").getBody();
        assertThat((Map<String, Object>) page.get("actions")).containsEntry("canCancel", true)
                .containsEntry("refundIfCancelledNow", true);

        Map<String, Object> cancelled = post("/api/public/manage/" + manage + "/cancel", Map.of("reason", "krank"))
                .getBody();
        assertThat(cancelled).containsEntry("status", "CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select detail from payment_event where type = 'REFUNDED'", String.class))
                .contains("refund simulated in demo mode");

        assertThat(text(mailTo(email, "wurde storniert"))).contains("wird dir erstattet", "Erstattung wurde nur simuliert");
        assertThat(text(mailTo(ADMIN_EMAIL, "Storniert: " + nameFor(email))))
                .contains(nameFor(email) + " hat den Termin storniert", "wird erstattet");
        // the manage link is used up; the slot is free again
        assertThat(post("/api/public/manage/" + manage + "/cancel", null).getBody()).containsEntry("code", "token.used");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelLessThan24HoursAheadForfeitsTheDeposit() throws Exception {
        String email = uniqueEmail();
        String id = confirmedBooking(email, MO, at(next(DayOfWeek.MONDAY), "12:00"), "en");
        String manage = tokenFromMail(email, "Appointment confirmed", MANAGE_LINK);
        moveBookingTo(id, fromNow(Duration.ofHours(5)));

        Map<String, Object> page = get("/api/public/manage/" + manage, "en").getBody();
        assertThat((Map<String, Object>) page.get("actions")).containsEntry("canCancel", true)
                .containsEntry("refundIfCancelledNow", false);

        post("/api/public/manage/" + manage + "/cancel", null);
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("FORFEITED");
        assertThat(text(mailTo(email, "has been cancelled"))).contains("less than 24 hours", "deposit is kept");
        assertThat(text(mailTo(ADMIN_EMAIL, "Storniert: " + nameFor(email)))).contains("bleibt beim Laden");
    }

    @Test
    void cancelOnThePaymentPageCancelsTheUnpaidBooking() throws Exception {
        String email = uniqueEmail();
        String id = (String) book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.SATURDAY), "10:00"), email, "en"))
                .getBody().get("id");
        post("/api/public/barber-decision/" + tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK) + "/accept",
                null);
        String pay = tokenFromMail(email, "accepted", PAY_LINK);

        assertThat(post("/api/public/payments/" + pay + "/cancel", null).getBody()).containsEntry("status", "CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("UNPAID");
        assertThat(post("/api/public/payments/" + pay + "/simulate", null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** request → accept via link → simulate payment via link. Returns the booking id. */
    private String confirmedBooking(String email, UUID staff, OffsetDateTime start, String lang) throws Exception {
        String id = (String) book(booking(HAIRCUT, staff, start, email, lang)).getBody().get("id");
        post("/api/public/barber-decision/" + tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK) + "/accept",
                null);
        String pay = tokenFromMail(email, "en".equals(lang) ? "accepted" : "zugesagt", PAY_LINK);
        post("/api/public/payments/" + pay + "/simulate", null);
        assertThat(status(id)).isEqualTo("CONFIRMED");
        return id;
    }

    @SuppressWarnings("unchecked")
    private List<String> lucaTimes(LocalDate day, UUID service) {
        Map<String, Object> body = get(BASE + "/availability?services=" + service + "&date=" + day, "de").getBody();
        return ((List<Map<String, Object>>) body.get("barbers")).stream()
                .filter(c -> LUCA.toString().equals(c.get("staffId")))
                .flatMap(c -> ((List<Map<String, Object>>) c.get("slots")).stream())
                .map(s -> (String) s.get("time")).toList();
    }

    private String status(String id) {
        return jdbc.queryForObject("select status from booking where id = ?::uuid", String.class, id);
    }

    private String paymentStatus(String id) {
        return jdbc.queryForObject("select status from payment where booking_id = ?::uuid", String.class, id);
    }

    @SuppressWarnings("unchecked")
    private static int cents(Map<String, Object> parent, String key) {
        return (Integer) ((Map<String, Object>) parent.get(key)).get("cents");
    }

    private static <T> List<T> parallel(int n, java.util.concurrent.Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return task.call();
            }));
        }
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) results.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }
}
