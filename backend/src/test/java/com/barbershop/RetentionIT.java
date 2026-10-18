package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.common.retention.RetentionJob;
import com.barbershop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Data-protection retention: anonymise old closed bookings, delete stale unverified accounts and old mail logs. */
class RetentionIT extends AbstractIntegrationTest {

    @Autowired
    private RetentionJob retention;

    private String bookAndCancel(String time) throws Exception {
        String email = uniqueEmail();
        Map<String, Object> body = new java.util.HashMap<>(booking(HAIRCUT, LUCA, at(next(DayOfWeek.THURSDAY), time), email, "de"));
        body.put("note", "Bitte mit Bart");
        body.put("attribution", Map.of("source", "google", "campaign", "herbst", "gclid", "Cj0",
                "capturedAt", java.time.OffsetDateTime.now().minusDays(1).toString()));
        String id = (String) book(body).getBody().get("id");
        post("/api/public/manage/" + tokenFromMail(email, "Deine Terminanfrage", MANAGE_LINK) + "/cancel", null);
        return id;
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("""
                select customer_name, customer_email, customer_phone, customer_note, attr_source, attr_gclid,
                       status, price_total_cents from booking where id = ?::uuid""", id);
    }

    @Test
    void oldClosedBookingsAreAnonymised_recentOnesAndConfirmedOnesStay() throws Exception {
        String old = bookAndCancel("10:00");
        String recent = bookAndCancel("11:00");
        String confirmedEmail = uniqueEmail();
        String confirmed = (String) book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.THURSDAY), "15:00"), confirmedEmail, "de"))
                .getBody().get("id");
        moveBookingTo(old, fromNow(Duration.ofDays(-210)));       // ~7 months ago
        moveBookingTo(confirmed, fromNow(Duration.ofDays(-400))); // completed bookings are kept by default

        RetentionJob.Report r = retention.runAll();
        assertThat(r.anonymisedClosed()).isEqualTo(1);
        assertThat(r.anonymisedCompleted()).isZero();

        assertThat(row(old)).containsEntry("customer_name", "Anonymisiert").containsEntry("customer_email", null)
                .containsEntry("customer_phone", null).containsEntry("customer_note", null)
                .containsEntry("attr_source", null).containsEntry("attr_gclid", null)
                .containsEntry("status", "CANCELLED").containsEntry("price_total_cents", 3500); // statistics stay
        assertThat(row(recent).get("customer_email")).isNotNull();
        assertThat(row(recent).get("attr_source")).isEqualTo("google");
        assertThat(row(confirmed)).containsEntry("customer_email", confirmedEmail);

        // idempotent
        assertThat(retention.runAll().total()).isZero();
    }

    @Test
    void staleUnverifiedAccountsAndOldMailLogsAreDeleted() throws Exception {
        String stale = uniqueEmail();
        String fresh = uniqueEmail();
        for (String email : new String[] {stale, fresh}) {
            post(BASE + "/auth/register", Map.of("email", email, "password", "a-long-password", "name", "Kim",
                    "phone", "+49 170 1234567", "lang", "de", "consent", true));
        }
        mailTo(stale, "Bitte bestätige");
        jdbc.update("update app_user set created_at = now() - interval '31 days' where lower(email) = lower(?)", stale);
        jdbc.update("update mail_outbox set created_at = now() - interval '31 days' where recipient = ?", stale);

        RetentionJob.Report r = retention.runAll();
        assertThat(r.deletedUnverifiedAccounts()).isEqualTo(1);
        assertThat(r.deletedMailLog()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user where lower(email) = lower(?)", Long.class, stale))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_user where lower(email) = lower(?)", Long.class, fresh))
                .isEqualTo(1);
        // verified accounts and the admin are never touched
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN'", Long.class)).isEqualTo(1);
    }
}
