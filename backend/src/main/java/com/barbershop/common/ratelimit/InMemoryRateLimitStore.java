package com.barbershop.common.ratelimit;

import com.barbershop.common.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/** Token buckets in a bounded, expiring in-process cache (no unbounded memory growth). One instance only. */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    public Probe tryConsume(String key, AppProperties.RateLimit.Limit limit) {
        ConsumptionProbe p = buckets.get(key, k -> Bucket.builder()
                        .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                        .build())
                .tryConsumeAndReturnRemaining(1);
        return new Probe(p.isConsumed(), p.getRemainingTokens(), p.getNanosToWaitForRefill());
    }
}
