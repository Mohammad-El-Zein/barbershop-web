package com.barbershop.support;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Knows how much {@code @Async} work (the mail listeners) is still in flight, so tests can
 * wait for exactly that instead of polling or sleeping.
 *
 * <p>Spring Boot applies a {@link TaskDecorator} bean to its auto-configured executor (here
 * the virtual-thread {@code SimpleAsyncTaskExecutor}). Decorating happens at submit time —
 * in the request thread, when the transaction commits and the
 * {@code @TransactionalEventListener} hands the event to the executor — so a task is counted
 * before the HTTP response is even written, and uncounted only after it has finished (mail
 * delivered: GreenMail stores a message before it acknowledges the SMTP transfer).
 *
 * <p>The count is static: the mail helpers are static, tests run one at a time, and every
 * Spring test context (some ITs start their own) decorates into the same counter.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AsyncTasks {

    private static final Object LOCK = new Object();
    private static int inFlight;

    @Bean
    TaskDecorator inFlightTracking() {
        return task -> {
            synchronized (LOCK) {
                inFlight++;
            }
            return () -> {
                try {
                    task.run();
                } finally {
                    synchronized (LOCK) {
                        inFlight--;
                        LOCK.notifyAll();
                    }
                }
            };
        };
    }

    /** Blocks until no async task is running or queued; fails if that takes longer than {@code max}. */
    public static void awaitIdle(Duration max) throws InterruptedException {
        long deadline = System.nanoTime() + max.toNanos();
        synchronized (LOCK) {
            while (inFlight > 0) {
                long left = deadline - System.nanoTime();
                if (left <= 0) throw new AssertionError(inFlight + " async task(s) still running after " + max);
                LOCK.wait(Math.max(1, left / 1_000_000));
            }
        }
    }
}
