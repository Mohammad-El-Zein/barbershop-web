package com.barbershop.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbershop.common.config.AppProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * Rate-limit stores: two store instances stand for two backend replicas. With Redis they
 * share one limit; in memory each replica allows the full limit (why Redis is required as
 * soon as the backend scales out).
 */
class RateLimitStoreIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final AppProperties.RateLimit.Limit THREE_PER_MINUTE = new AppProperties.RateLimit.Limit(3, Duration.ofMinutes(1));

    @BeforeAll
    static void start() {
        REDIS.start();
    }

    @AfterAll
    static void stop() {
        REDIS.stop();
    }

    private static String url() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }

    @Test
    void redis_twoInstancesShareOneLimit() {
        String key = "bookings|203.0.113." + UUID.randomUUID().hashCode();
        try (RedisRateLimitStore a = new RedisRateLimitStore(url()); RedisRateLimitStore b = new RedisRateLimitStore(url())) {
            assertThat(a.tryConsume(key, THREE_PER_MINUTE).consumed()).isTrue();
            assertThat(b.tryConsume(key, THREE_PER_MINUTE).consumed()).isTrue();
            RateLimitStore.Probe third = a.tryConsume(key, THREE_PER_MINUTE);
            assertThat(third.consumed()).isTrue();
            assertThat(third.remaining()).isZero();

            RateLimitStore.Probe fourth = b.tryConsume(key, THREE_PER_MINUTE);
            assertThat(fourth.consumed()).isFalse();
            assertThat(fourth.nanosToWait()).isPositive();

            // other clients are unaffected
            assertThat(a.tryConsume(key + "-other", THREE_PER_MINUTE).consumed()).isTrue();
        }
    }

    @Test
    void memory_eachInstanceHasItsOwnLimit() {
        InMemoryRateLimitStore a = new InMemoryRateLimitStore();
        InMemoryRateLimitStore b = new InMemoryRateLimitStore();
        for (int i = 0; i < 3; i++) assertThat(a.tryConsume("k", THREE_PER_MINUTE).consumed()).isTrue();
        assertThat(a.tryConsume("k", THREE_PER_MINUTE).consumed()).isFalse();
        assertThat(b.tryConsume("k", THREE_PER_MINUTE).consumed()).isTrue(); // not shared
    }
}
