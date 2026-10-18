package com.barbershop.common.retention;

import com.barbershop.common.config.AppProperties;
import com.barbershop.common.i18n.Messages;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data-protection retention (GDPR storage limitation), run daily by {@link RetentionScheduler}
 * (and directly by tests). Every rule is idempotent plain SQL — safe with several instances.
 * <ul>
 *   <li>cancelled / declined / expired bookings: anonymised {@code cancelledBookingsMonths}
 *       after the appointment (name → placeholder; email, phone, note and campaign data
 *       removed; date, services and amounts stay for the shop's statistics),</li>
 *   <li>completed / no-show bookings: the same after {@code completedBookingsMonths}
 *       (0 = keep — the period depends on tax / bookkeeping rules),</li>
 *   <li>customer accounts whose email was never verified: deleted after
 *       {@code unverifiedAccountsDays},</li>
 *   <li>email delivery log (sent / given-up mails): deleted after {@code outboxDays}.</li>
 * </ul>
 * The periods must match the privacy policy (frontend legal.config.ts → retention).
 * Account data export (GET /api/me/export) is unaffected.
 */
@Service
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    public record Report(int anonymisedClosed, int anonymisedCompleted, int deletedUnverifiedAccounts,
                         int deletedMailLog) {
        public int total() {
            return anonymisedClosed + anonymisedCompleted + deletedUnverifiedAccounts + deletedMailLog;
        }
    }

    private final JdbcClient jdbc;
    private final AppProperties.Retention config;
    private final Messages messages;
    private final Clock clock;

    public RetentionJob(JdbcClient jdbc, AppProperties props, Messages messages, Clock clock) {
        this.jdbc = jdbc;
        this.config = props.retention();
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public Report runAll() {
        Instant now = Instant.now(clock);
        String placeholder = messages.get(Locale.GERMAN, "retention.anonymised_customer");
        int closed = anonymise("'DECLINED', 'EXPIRED', 'CANCELLED'", monthsAgo(now, config.cancelledBookingsMonths()),
                placeholder);
        int completed = config.completedBookingsMonths() > 0
                ? anonymise("'COMPLETED', 'NO_SHOW'", monthsAgo(now, config.completedBookingsMonths()), placeholder)
                : 0;
        int accounts = jdbc.sql("""
                        delete from app_user
                        where role = 'CUSTOMER' and email_verified_at is null and created_at < :cutoff""")
                .param("cutoff", Timestamp.from(now.minus(java.time.Duration.ofDays(config.unverifiedAccountsDays()))))
                .update();
        int mails = jdbc.sql("""
                        delete from mail_outbox
                        where status in ('SENT', 'DEAD') and created_at < :cutoff""")
                .param("cutoff", Timestamp.from(now.minus(java.time.Duration.ofDays(config.outboxDays()))))
                .update();
        Report r = new Report(closed, completed, accounts, mails);
        if (r.total() > 0) log.info("Retention: {}", r);
        return r;
    }

    private int anonymise(String statuses, Instant cutoff, String placeholder) {
        return jdbc.sql("""
                        update booking
                        set customer_name = :placeholder, customer_email = null, customer_phone = null,
                            customer_note = null, status_reason = null,
                            attr_source = null, attr_medium = null, attr_campaign = null, attr_term = null,
                            attr_content = null, attr_gclid = null, attr_fbclid = null, attr_landing_path = null,
                            attr_captured_at = null
                        where status in (%s) and end_time < :cutoff
                          and (customer_email is not null or customer_phone is not null or customer_note is not null
                               or customer_name <> :placeholder or attr_captured_at is not null
                               or attr_landing_path is not null)""".formatted(statuses))
                .param("placeholder", placeholder)
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }

    private static Instant monthsAgo(Instant now, int months) {
        return now.atOffset(ZoneOffset.UTC).minusMonths(months).toInstant();
    }
}
