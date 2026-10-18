package com.barbershop.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polls the email outbox every {@code app.mail.outbox.poll-interval}: retries that became
 * due and anything a crashed instance left behind (new mails are sent right after their
 * commit). Off with APP_JOBS_ENABLED=false (tests call the worker directly).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MailOutboxScheduler {

    private final MailOutboxWorker worker;

    public MailOutboxScheduler(MailOutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox.poll-interval}", initialDelayString = "PT10S")
    public void poll() {
        worker.processDue();
    }
}
