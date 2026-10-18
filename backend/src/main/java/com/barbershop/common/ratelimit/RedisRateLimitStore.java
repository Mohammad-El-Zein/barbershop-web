package com.barbershop.common.ratelimit;

import com.barbershop.common.config.AppProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Token buckets in Redis (Bucket4j compare-and-swap on Lettuce): one shared limit for all
 * backend instances. Keys expire once a bucket would be full again, so Redis only holds
 * active clients. If Redis is unreachable, requests fail with 500 (fail closed) — run Redis
 * with the same availability as the database.
 */
public class RedisRateLimitStore implements RateLimitStore, AutoCloseable {

    private static final String PREFIX = "barbershop:rl:";

    private final RedisClient client;
    private final ProxyManager<byte[]> buckets;

    public RedisRateLimitStore(String redisUrl) {
        this.client = RedisClient.create(redisUrl);
        this.buckets = Bucket4jLettuce.casBasedBuilder(client)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .requestTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public Probe tryConsume(String key, AppProperties.RateLimit.Limit limit) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .build();
        ConsumptionProbe p = buckets.getProxy((PREFIX + key).getBytes(StandardCharsets.UTF_8), () -> config)
                .tryConsumeAndReturnRemaining(1);
        return new Probe(p.isConsumed(), p.getRemainingTokens(), p.getNanosToWaitForRefill());
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
