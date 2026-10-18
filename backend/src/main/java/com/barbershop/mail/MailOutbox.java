package com.barbershop.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional email outbox (table mail_outbox).
 *
 * <p>{@link #enqueue} renders the mail and inserts it in the CURRENT transaction —
 * the mail exists exactly when the business change commits (never for a rolled-back
 * change, never lost after a commit). After the commit the worker is kicked
 * ({@link MailOutboxWorker#kick}, async); the scheduler also polls ({@link MailOutboxScheduler}).
 *
 * <p>A mail that can't be RENDERED is logged and skipped (it must never block a booking).
 * A mail that can't be STORED fails the transaction — change and mail commit together or not
 * at all (that is the point of an outbox; in PostgreSQL the failed insert aborts the
 * transaction anyway).
 */
@Service
public class MailOutbox {

    private static final Logger log = LoggerFactory.getLogger(MailOutbox.class);

    private final JdbcClient jdbc;
    private final MailService mail;
    private final MailOutboxWorker worker;
    private final ObjectMapper json;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    public MailOutbox(JdbcClient jdbc, MailService mail, MailOutboxWorker worker, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.worker = worker;
        this.json = json;
        this.clock = clock;
    }

    /** Renders and stores a mail in the current transaction; the worker sends it after the commit. Null if not renderable. */
    public UUID enqueue(MailContent m, String kind, UUID tenantId, UUID bookingId, UUID userId) {
        MailService.Rendered r;
        try {
            r = mail.render(m);
        } catch (RuntimeException ex) {
            log.error("Could not render {} mail (booking {}, account {}) — skipped: {}", kind, bookingId, userId,
                    ex.getMessage(), ex);
            return null;
        }
        // entities of this transaction (a new account, a booking) must be in the database before the
        // JDBC insert references them
        if (TransactionSynchronizationManager.isActualTransactionActive()) em.flush();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into mail_outbox (id, tenant_id, kind, booking_id, user_id, recipient, subject, lang,
                                                 html, plain_text, attachments, status, next_attempt_at)
                        values (:id, :tenant, :kind, :booking, :user, :to, :subject, :lang, :html, :text,
                                cast(:attachments as jsonb), 'PENDING', :now)""")
                .param("id", id)
                .param("tenant", tenantId)
                .param("kind", kind)
                .param("booking", bookingId)
                .param("user", userId)
                .param("to", r.to())
                .param("subject", cut(r.subject(), 300))
                .param("lang", r.langCode())
                .param("html", r.html())
                .param("text", r.plainText())
                .param("attachments", attachmentsJson(r.attachments()))
                .param("now", Timestamp.from(Instant.now(clock)))
                .update();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.kick();
                }
            });
        } else {
            worker.kick();
        }
        return id;
    }

    /** Admin "retry": a DEAD or RETRY mail of this tenant is due again with a fresh attempt budget. */
    public boolean retry(UUID tenantId, UUID id) {
        int n = jdbc.sql("""
                        update mail_outbox set status = 'PENDING', attempts = 0, next_attempt_at = :now
                        where id = :id and tenant_id = :tenant and status in ('DEAD', 'RETRY')""")
                .param("now", Timestamp.from(Instant.now(clock)))
                .param("id", id)
                .param("tenant", tenantId)
                .update();
        if (n > 0) worker.kick();
        return n > 0;
    }

    private String attachmentsJson(List<MailContent.Attachment> list) {
        if (list.isEmpty()) return null;
        try {
            return json.writeValueAsString(list.stream().map(a -> Map.of("filename", a.filename(),
                    "contentType", a.contentType(), "data", Base64.getEncoder().encodeToString(a.data()))).toList());
        } catch (Exception e) {
            throw new IllegalStateException("attachments", e);
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
