package com.barbershop.common.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs {@link RetentionJob} daily (APP_RETENTION_CRON, default 03:30 server time). Off with APP_JOBS_ENABLED=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private final RetentionJob job;

    public RetentionScheduler(RetentionJob job) {
        this.job = job;
    }

    @Scheduled(cron = "${app.retention.cron}")
    public void run() {
        job.runAll();
    }
}
