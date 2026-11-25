package com.portfolio.auth.internal;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limits login attempts per client IP (Bucket4j token bucket, in memory).
 */
@Component
class LoginRateLimiter
{
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int attemptsPerMinute;

    LoginRateLimiter(AuthProperties properties)
    {
        this.attemptsPerMinute = properties.loginAttemptsPerMinute();
    }

    /**
     * Consumes one attempt for this IP.
     *
     * @return {@link Duration#ZERO} when the attempt is allowed, otherwise the time to wait
     */
    Duration tryConsume(String clientIp)
    {
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed())
        {
            return Duration.ZERO;
        }
        return Duration.ofNanos(probe.getNanosToWaitForRefill());
    }

    /**
     * Forgets IPs whose bucket is full again, so the map does not grow forever.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    void evictIdleBuckets()
    {
        buckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= attemptsPerMinute);
    }

    int trackedClients()
    {
        return buckets.size();
    }

    private Bucket newBucket()
    {
        Bandwidth limit = Bandwidth.builder()
            .capacity(attemptsPerMinute)
            .refillIntervally(attemptsPerMinute, WINDOW)
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
