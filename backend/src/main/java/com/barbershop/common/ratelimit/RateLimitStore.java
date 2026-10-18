package com.barbershop.common.ratelimit;

import com.barbershop.common.config.AppProperties;

/**
 * Where the rate-limit buckets live. {@code memory} (default): per backend instance —
 * correct for ONE instance. {@code redis}: shared by all instances — required as soon as
 * more than one backend replica serves traffic (e.g. Container Apps scaling out),
 * otherwise each replica allows the full limit. Selected by APP_RATE_LIMIT_STORE.
 */
public interface RateLimitStore {

    /** Result of taking one token: allowed?, tokens left, wait until the next token. */
    record Probe(boolean consumed, long remaining, long nanosToWait) {}

    Probe tryConsume(String key, AppProperties.RateLimit.Limit limit);
}
