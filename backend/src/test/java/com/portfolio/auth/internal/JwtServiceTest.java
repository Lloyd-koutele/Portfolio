package com.portfolio.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.auth.AdminPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest
{
    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-123";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final AdminPrincipal admin = new AdminPrincipal(UUID.randomUUID(), "admin@test.local");

    @Test
    void issuedTokenVerifiesBackToTheSameAdmin()
    {
        JwtService service = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.verify(service.issue(admin))).contains(admin);
    }

    @Test
    void tokenExpiresAfterEightHours()
    {
        String token = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC)).issue(admin);
        JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofHours(8)).plusSeconds(1), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected()
    {
        String token = service("another-secret-key-that-is-also-long-enough", Clock.systemUTC()).issue(admin);

        assertThat(service(SECRET, Clock.systemUTC()).verify(token)).isEmpty();
    }

    @Test
    void garbageIsRejected()
    {
        assertThat(service(SECRET, Clock.systemUTC()).verify("not.a.jwt")).isEmpty();
    }

    @Test
    void shortSecretIsRefusedAtStartup()
    {
        assertThatThrownBy(() -> properties("too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void propertiesNeverPrintSecrets()
    {
        assertThat(properties(SECRET).toString()).doesNotContain(SECRET).doesNotContain("pw-123456789");
    }

    private static JwtService service(String secret, Clock clock)
    {
        return new JwtService(properties(secret), clock);
    }

    private static AuthProperties properties(String secret)
    {
        return new AuthProperties(secret, Duration.ofHours(8), Duration.ofDays(30),
            Duration.ofMinutes(5), "PORTFOLIO_SESSION", true, 5, "a@b.c", "pw-123456789");
    }
}
