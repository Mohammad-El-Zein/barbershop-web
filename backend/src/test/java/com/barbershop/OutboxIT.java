package com.barbershop;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.common.i18n.Lang;
import com.barbershop.mail.MailContent;
import com.barbershop.mail.MailOutbox;
import com.barbershop.mail.MailOutboxWorker;
import com.barbershop.support.AbstractIntegrationTest;
import com.barbershop.support.AsyncTasks;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Email outbox: mails are stored in the business transaction and sent after the commit;
 * failed sends are retried with exponential backoff and end up DEAD (visible to the
 * admin, who can retry). The SMTP server is made unreachable by pointing the sender at a
 * closed port for the duration of a test.
 */
class OutboxIT extends AbstractIntegrationTest {

    @Autowired
    private MailOutbox outbox;

    @Autowired
    private MailOutboxWorker worker;

    @Autowired
    private JavaMailSenderImpl sender;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void smtpBack() {
        sender.setPort(MAIL.getSmtp().getPort());
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("select kind, status, attempts, next_attempt_at, html is null as cleared, last_error "
                + "from mail_outbox order by kind");
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedMailsAreRetriedWithBackoff_thenDead_andTheAdminCanRetry() throws Exception {
        sender.setPort(1); // nothing listens there: every send fails
        String email = uniqueEmail();
        assertThat(book(booking(HAIRCUT, LUCA, at(next(DayOfWeek.TUESDAY), "10:00"), email, "de")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        AsyncTasks.awaitIdle(Duration.ofSeconds(15)); // the post-commit attempt has run (and failed)

        // customer + barber (admin fallback) mail, first attempt failed → RETRY in ~30 s
        assertThat(rows()).hasSize(2).allSatisfy(r -> {
            assertThat(r).containsEntry("status", "RETRY").containsEntry("attempts", 1).containsEntry("cleared", false);
            assertThat((String) r.get("last_error")).isNotBlank();
        });
        Long secondsUntilRetry = jdbc.queryForObject(
                "select min(extract(epoch from next_attempt_at - now()))::bigint from mail_outbox", Long.class);
        assertThat(secondsUntilRetry).isBetween(20L, 31L);

        // not due yet: nothing happens
        assertThat(worker.processDue()).isZero();
        assertThat(rows()).allSatisfy(r -> assertThat(r).containsEntry("attempts", 1));

        // the backoff doubles; after the 8th attempt the mail is given up (DEAD)
        for (int attempt = 2; attempt <= 8; attempt++) {
            jdbc.update("update mail_outbox set next_attempt_at = now() - interval '1 second'");
            worker.processDue();
            int expected = attempt;
            assertThat(rows()).allSatisfy(r -> assertThat(r).containsEntry("attempts", expected));
            if (attempt == 2) {
                Long wait = jdbc.queryForObject(
                        "select min(extract(epoch from next_attempt_at - now()))::bigint from mail_outbox", Long.class);
                assertThat(wait).isBetween(50L, 61L); // 30 s × 2
            }
        }
        assertThat(rows()).allSatisfy(r -> assertThat(r).containsEntry("status", "DEAD"));
        assertThat(countMails(email, "Deine Terminanfrage")).isZero();

        // the admin sees both and retries them once SMTP works again
        String admin = adminToken();
        List<Map<String, Object>> problems = getList("/api/admin/outbox", "de", admin).getBody();
        assertThat(problems).hasSize(2).allSatisfy(p -> assertThat(p).containsEntry("status", "DEAD")
                .containsEntry("attempts", 8));
        sender.setPort(MAIL.getSmtp().getPort());
        for (Map<String, Object> p : problems) {
            assertThat(call(HttpMethod.POST, "/api/admin/outbox/" + p.get("id") + "/retry", null, "de", admin)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        mailTo(email, "Deine Terminanfrage");
        assertThat(rows()).allSatisfy(r -> assertThat(r).containsEntry("status", "SENT").containsEntry("cleared", true));
        assertThat(getList("/api/admin/outbox", "de", admin).getBody()).isEmpty();

        // a delivered mail can't be "retried"; other tenants' mails are invisible
        assertThat(call(HttpMethod.POST, "/api/admin/outbox/" + problems.getFirst().get("id") + "/retry", null, "en",
                admin).getBody()).containsEntry("code", "outbox.not_found");
    }

    @Test
    void aRolledBackChangeQueuesNoMail_aCommittedOneIsSentAfterTheCommit() throws Exception {
        String rolledBack = uniqueEmail();
        tx.executeWithoutResult(status -> {
            outbox.enqueue(new MailContent(rolledBack, Lang.EN, "Shop").subject("Rolled back"), "test", TENANT_ID,
                    null, null);
            status.setRollbackOnly();
        });
        String committed = uniqueEmail();
        tx.executeWithoutResult(status -> outbox.enqueue(new MailContent(committed, Lang.EN, "Shop")
                .subject("Committed").paragraph("Hello"), "test", TENANT_ID, null, null));

        assertThat(mailTo(committed, "Committed")).isNotNull();
        assertThat(countMails(rolledBack, "Rolled back")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox", Long.class)).isEqualTo(1);
    }
}
