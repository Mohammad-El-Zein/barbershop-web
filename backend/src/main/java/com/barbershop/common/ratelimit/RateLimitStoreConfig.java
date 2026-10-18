package com.barbershop.common.ratelimit;

import com.barbershop.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the rate-limit store: APP_RATE_LIMIT_STORE=memory (default) or redis (+ APP_RATE_LIMIT_REDIS_URL). */
@Configuration
public class RateLimitStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitStoreConfig.class);

    @Bean
    RateLimitStore rateLimitStore(AppProperties props) {
        AppProperties.RateLimit rl = props.rateLimit();
        return switch (rl.store()) {
            case "memory" -> {
                log.info("Rate limits: in-memory (per instance; use APP_RATE_LIMIT_STORE=redis with several replicas)");
                yield new InMemoryRateLimitStore();
            }
            case "redis" -> {
                if (rl.redisUrl() == null || rl.redisUrl().isBlank()) {
                    throw new IllegalStateException("APP_RATE_LIMIT_STORE=redis needs APP_RATE_LIMIT_REDIS_URL");
                }
                log.info("Rate limits: shared Redis store");
                yield new RedisRateLimitStore(rl.redisUrl());
            }
            default -> throw new IllegalStateException("Unknown APP_RATE_LIMIT_STORE '" + rl.store() + "' (memory|redis)");
        };
    }
}
