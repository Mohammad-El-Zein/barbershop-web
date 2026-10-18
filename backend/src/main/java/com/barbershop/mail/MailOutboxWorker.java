package com.barbershop.mail;

import com.barbershop.common.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the mails of the {@link MailOutbox}.
 *
 * <p>{@link #processDue} claims due rows in a short transaction with
 * {@code FOR UPDATE SKIP LOCKED} (safe with several backend instances), sends them
 * outside the transaction and records the result: SENT (content cleared), RETRY with
 * exponential backoff, or DEAD after {@code maxAttempts}. A row stuck in SENDING (a
 * crashed worker) is picked up again after 5 minutes. Log lines carry ids and kinds,
 * never addresses or content.
 */
@Service
public class MailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxWorker.class);
    private static final Duration STALE_SENDING = Duration.ofMinutes(5);
    private static final TypeReference<List<Map<String, String>>> ATTACHMENTS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final MailService mail;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final AppProperties.Mail.Outbox config;
    private final Clock clock;

    public MailOutboxWorker(JdbcClient jdbc, MailService mail, TransactionTemplate tx, ObjectMapper json,
                            AppProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.tx = tx;
        this.json = json;
        this.config = props.mail().outbox();
        this.clock = clock;
    }

    /** Sends whatever is due, right away (async; called after commits and by the scheduler). */
    @Async
    public void kick() {
        processDue();
    }

    private record Claimed(UUID id, String kind, String to, String subject, String lang, String html, String text,
                           String attachments, int attempts) {}

    /** One worker round: claim due mails, send them, record the outcome. Returns the number sent. */
    public int processDue() {
        int sent = 0;
        List<Claimed> batch;
        do {
            batch = claim();
            for (Claimed c : batch) {
                if (sendOne(c)) sent++;
            }
        } while (batch.size() == config.batchSize());
        return sent;
    }

    private List<Claimed> claim() {
        Instant now = Instant.now(clock);
        return tx.execute(status -> jdbc.sql("""
                        update mail_outbox o
                        set status = 'SENDING', claimed_at = :now, attempts = o.attempts + 1
                        where o.id in (select id from mail_outbox
                                       where next_attempt_at <= :now
                                         and (status in ('PENDING', 'RETRY')
                                              or (status = 'SENDING' and claimed_at < :stale))
                                       order by next_attempt_at
                                       limit :limit
                                       for update skip locked)
                        returning o.id, o.kind, o.recipient, o.subject, o.lang, o.html, o.plain_text,
                                  o.attachments::text as attachments, o.attempts""")
                .param("now", Timestamp.from(now))
                .param("stale", Timestamp.from(now.minus(STALE_SENDING)))
                .param("limit", config.batchSize())
                .query((rs, i) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("recipient"), rs.getString("subject"), rs.getString("lang"), rs.getString("html"),
                        rs.getString("plain_text"), rs.getString("attachments"), rs.getInt("attempts")))
                .list());
    }

    private boolean sendOne(Claimed c) {
        try {
            mail.send(new MailService.Rendered(c.to(), c.subject(), c.lang(), c.html(), c.text(),
                    attachments(c.attachments())));
            jdbc.sql("""
                            update mail_outbox set status = 'SENT', sent_at = :now, last_error = null,
                                   html = null, plain_text = null, attachments = null
                            where id = :id""")
                    .param("now", Timestamp.from(Instant.now(clock)))
                    .param("id", c.id())
                    .update();
            log.info("Sent {} mail {} (attempt {})", c.kind(), c.id(), c.attempts());
            return true;
        } catch (Exception ex) {
            boolean dead = c.attempts() >= config.maxAttempts();
            Instant next = Instant.now(clock).plus(backoff(c.attempts()));
            jdbc.sql("""
                            update mail_outbox set status = :status, next_attempt_at = :next, last_error = :error
                            where id = :id""")
                    .param("status", dead ? "DEAD" : "RETRY")
                    .param("next", Timestamp.from(next))
                    .param("error", cut(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 500))
                    .param("id", c.id())
                    .update();
            if (dead) {
                log.error("Mail {} ({}) failed {} times — DEAD, needs attention: {}", c.id(), c.kind(), c.attempts(),
                        ex.getClass().getSimpleName());
            } else {
                log.warn("Mail {} ({}) failed (attempt {}), retry at {}: {}", c.id(), c.kind(), c.attempts(), next,
                        ex.getClass().getSimpleName());
            }
            return false;
        }
    }

    /** base × 2^(attempt-1), capped: 30 s, 1 min, 2 min … ≤ 1 h by default. */
    Duration backoff(int attempt) {
        long factor = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        Duration d = config.baseBackoff().multipliedBy(factor);
        return d.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : d;
    }

    private List<MailContent.Attachment> attachments(String jsonText) throws Exception {
        if (jsonText == null) return List.of();
        return json.readValue(jsonText, ATTACHMENTS).stream()
                .map(a -> new MailContent.Attachment(a.get("filename"), a.get("contentType"),
                        Base64.getDecoder().decode(a.get("data"))))
                .toList();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
