package com.barbershop.booking;

import com.barbershop.common.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Time-driven transitions, run every minute by {@link BookingJobScheduler} (and
 * directly by tests):
 * <ol>
 *   <li>expire requests the barber did not answer in time (→ EXPIRED + emails),</li>
 *   <li>expire acceptances that were not paid in time (→ EXPIRED + email),</li>
 *   <li>auto-complete CONFIRMED bookings {@code auto_complete_after} after their end
 *       (unless a barber marked them NO_SHOW before),</li>
 *   <li>send the reminder {@code reminder_before} ahead.</li>
 * </ol>
 * Candidates are selected by id, then every booking is processed in its own
 * transaction under a row lock and re-checked — safe with several instances and
 * against a barber clicking at the same moment. One failure never stops the batch.
 */
@Service
public class BookingJobs {

    private static final Logger log = LoggerFactory.getLogger(BookingJobs.class);

    public record Report(int expiredRequests, int expiredPayments, int completed, int reminders) {
        public int total() {
            return expiredRequests + expiredPayments + completed + reminders;
        }
    }

    private final BookingRepository bookings;
    private final BookingLifecycle lifecycle;
    private final AppProperties.Jobs config;
    private final Clock clock;

    public BookingJobs(BookingRepository bookings, BookingLifecycle lifecycle, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.lifecycle = lifecycle;
        this.config = props.jobs();
        this.clock = clock;
    }

    public Report runAll() {
        Instant now = Instant.now(clock);
        int batch = config.batchSize();
        Report r = new Report(
                each(bookings.findIdsAwaitingBarberPastDeadline(now, PageRequest.of(0, batch)), lifecycle::expireIfDue),
                each(bookings.findIdsAwaitingPaymentPastDeadline(now, PageRequest.of(0, batch)), lifecycle::expireIfDue),
                each(bookings.findIdsToAutoComplete(now, batch), lifecycle::autoCompleteIfDue),
                each(bookings.findIdsDueForReminder(now, batch), lifecycle::remindIfDue));
        if (r.total() > 0) log.info("Booking jobs: {}", r);
        return r;
    }

    private static int each(List<UUID> ids, Predicate<UUID> action) {
        int done = 0;
        for (UUID id : ids) {
            try {
                if (action.test(id)) done++;
            } catch (RuntimeException e) {
                log.error("Booking job failed for {}: {}", id, e.getMessage(), e);
            }
        }
        return done;
    }
}
