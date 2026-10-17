package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.booking.BookingJobs;
import com.barbershop.payment.PaymentService;
import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** The scheduled job: expiry (no answer / not paid), auto-complete, reminders. Deadlines are moved via SQL. */
class BookingJobsIT extends AbstractIntegrationTest {

    @Autowired
    BookingJobs jobs;

    @Autowired
    PaymentService payments;

    @Test
    void anUnansweredRequestExpiresAndFreesTheSlot() throws Exception {
        String email = uniqueEmail();
        String id = request(email, LUCA, at(next(DayOfWeek.TUESDAY), "16:00"), "de");
        String decision = tokenFromMail(ADMIN_EMAIL, nameFor(email), DECISION_LINK);

        assertThat(jobs.runAll().total()).isZero(); // deadline not reached yet
        assertThat(status(id)).isEqualTo("PENDING_BARBER");

        jdbc.update("update booking set barber_deadline_at = now() - interval '1 minute' where id = ?::uuid", id);
        assertThat(jobs.runAll().expiredRequests()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("EXPIRED");
        assertThat(text(mailTo(email, "ist abgelaufen"))).contains("nicht rechtzeitig beantworten", "Neuen Termin buchen");

        // the barber's link is dead now, the job is idempotent
        assertThat(post("/api/public/barber-decision/" + decision + "/accept", null).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
        assertThat(jobs.runAll().total()).isZero();
        // the slot can be booked again
        assertThat(book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.TUESDAY), "16:00"), uniqueEmail(), "de"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anUnpaidAcceptanceExpires_andALatePaymentIsRefunded() throws Exception {
        String email = uniqueEmail();
        String id = request(email, MO, at(next(DayOfWeek.WEDNESDAY), "13:00"), "en");
        adminAction(id, "accept");
        String pay = tokenFromMail(email, "accepted", PAY_LINK);

        jdbc.update("update booking set payment_deadline_at = now() - interval '1 minute' where id = ?::uuid", id);
        assertThat(jobs.runAll().expiredPayments()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("EXPIRED");
        assertThat(text(mailTo(email, "has expired"))).contains("didn’t arrive");

        assertThat(post("/api/public/payments/" + pay + "/simulate", null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        // a real provider might still report the payment — it must not resurrect the booking
        String ref = jdbc.queryForObject("select provider_ref from payment where booking_id = ?::uuid", String.class, id);
        assertThat(payments.handlePaymentSucceeded(ref).outcome()).isEqualTo(PaymentService.Outcome.REFUNDED_LATE);
        assertThat(status(id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from payment where booking_id = ?::uuid", String.class, id))
                .isEqualTo("REFUNDED");
    }

    @Test
    void confirmedBookingsAreCompletedTwoHoursAfterTheirEnd_unlessMarkedNoShow() throws Exception {
        String done = confirmed(uniqueEmail(), LUCA, at(next(DayOfWeek.THURSDAY), "09:00"));
        String noShow = confirmed(uniqueEmail(), MO, at(next(DayOfWeek.THURSDAY), "10:00"));
        String tooRecent = confirmed(uniqueEmail(), LUCA, at(next(DayOfWeek.THURSDAY), "15:00"));
        moveBookingTo(done, fromNow(Duration.ofHours(-4)));        // ended 3 h 15 ago
        moveBookingTo(noShow, fromNow(Duration.ofHours(-30)));
        moveBookingTo(tooRecent, fromNow(Duration.ofMinutes(-90))); // ended 45 min ago

        assertThat(adminAction(noShow, "no-show")).isEqualTo("NO_SHOW");
        assertThat(jobs.runAll().completed()).isEqualTo(1);

        assertThat(status(done)).isEqualTo("COMPLETED");
        assertThat(status(noShow)).isEqualTo("NO_SHOW");
        assertThat(jdbc.queryForObject("select status from payment where booking_id = ?::uuid", String.class, noShow))
                .isEqualTo("FORFEITED");
        assertThat(status(tooRecent)).isEqualTo("CONFIRMED");
    }

    @Test
    void theReminderIsSentOnce24HoursAhead() throws Exception {
        String email = uniqueEmail();
        String id = confirmed(email, LUCA, at(next(DayOfWeek.FRIDAY), "14:00"));
        assertThat(jobs.runAll().reminders()).isZero(); // still days away

        moveBookingTo(id, fromNow(Duration.ofHours(20)));
        jdbc.update("update booking set confirmed_at = now() - interval '3 days' where id = ?::uuid", id);
        assertThat(jobs.runAll().reminders()).isEqualTo(1);
        assertThat(text(mailTo(email, "Erinnerung"))).contains("kurze Erinnerung", "Termin verwalten");
        assertThat(jobs.runAll().reminders()).isZero();
        assertThat(countMails(email, "Erinnerung")).isEqualTo(1);
    }

    @Test
    void noReminderRightAfterALateConfirmation() throws Exception {
        String email = uniqueEmail();
        String id = confirmed(email, MO, at(next(DayOfWeek.FRIDAY), "15:00"));
        moveBookingTo(id, fromNow(Duration.ofHours(10))); // confirmed_at = just now, inside the window

        assertThat(jobs.runAll().reminders()).isZero();
        assertThat(jdbc.queryForObject("select reminder_sent_at is not null from booking where id = ?::uuid",
                Boolean.class, id)).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String request(String email, UUID staff, OffsetDateTime start, String lang) {
        return (String) book(booking(HAIRCUT, staff, start, email, lang)).getBody().get("id");
    }

    /** request → admin accepts → payment succeeds (same code path as the demo button / a webhook). */
    private String confirmed(String email, UUID staff, OffsetDateTime start) {
        String id = request(email, staff, start, "de");
        adminAction(id, "accept");
        payments.handlePaymentSucceeded(
                jdbc.queryForObject("select provider_ref from payment where booking_id = ?::uuid", String.class, id));
        assertThat(status(id)).isEqualTo("CONFIRMED");
        return id;
    }

    private String adminAction(String id, String action) {
        var r = call(HttpMethod.POST, "/api/admin/bookings/" + id + "/" + action, null, "de", adminToken());
        assertThat(r.getStatusCode()).as(action + ": " + r.getBody()).isEqualTo(HttpStatus.OK);
        return (String) r.getBody().get("status");
    }

    private String status(String id) {
        return jdbc.queryForObject("select status from booking where id = ?::uuid", String.class, id);
    }
}
