package com.barbershop.booking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Triggers {@link BookingJobs} every {@code app.jobs.interval}. Off with APP_JOBS_ENABLED=false (tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BookingJobScheduler {

    private final BookingJobs jobs;

    public BookingJobScheduler(BookingJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.jobs.interval}", initialDelayString = "PT20S")
    public void run() {
        jobs.runAll();
    }
}
