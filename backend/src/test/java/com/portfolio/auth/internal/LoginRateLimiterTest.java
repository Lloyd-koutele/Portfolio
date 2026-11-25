package com.portfolio.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest
{
    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-123";

    private final LoginRateLimiter limiter = new LoginRateLimiter(
        new AuthProperties(SECRET, Duration.ofHours(8), Duration.ofDays(30), Duration.ofMinutes(5), "S", true, 5,
            null, null));

    @Test
    void allowsFiveAttemptsThenAsksToWait()
    {
        for (int attempt = 0; attempt < 5; attempt++)
        {
            assertThat(limiter.tryConsume("192.0.2.1")).isZero();
        }

        Duration wait = limiter.tryConsume("192.0.2.1");

        assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
        assertThat(limiter.tryConsume("192.0.2.2")).isZero();
    }

    @Test
    void evictsOnlyFullBuckets()
    {
        limiter.tryConsume("192.0.2.1");

        limiter.evictIdleBuckets();

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }
}
