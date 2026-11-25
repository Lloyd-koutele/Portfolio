package com.portfolio.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpTest
{
    /** RFC 6238 appendix B secret (SHA-1). */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors()
    {
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void base32RoundTrips()
    {
        String encoded = Totp.base32(RFC_KEY);

        assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.decodeBase32(encoded)).isEqualTo(RFC_KEY);
    }

    @Test
    void acceptsOneStepOfDriftAndRefusesReplays()
    {
        Totp totp = at(1_111_111_109L);
        String secret = Totp.base32(RFC_KEY);
        long step = 1_111_111_109L / 30;

        assertThat(totp.verify(secret, Totp.code(RFC_KEY, step), 0)).hasValue(step);
        assertThat(totp.verify(secret, Totp.code(RFC_KEY, step - 1), 0)).hasValue(step - 1);
        assertThat(totp.verify(secret, Totp.code(RFC_KEY, step + 1), 0)).hasValue(step + 1);
        assertThat(totp.verify(secret, Totp.code(RFC_KEY, step + 2), 0)).isEmpty();
        assertThat(totp.verify(secret, Totp.code(RFC_KEY, step), step)).isEmpty();
        assertThat(totp.verify(secret, "12345", 0)).isEmpty();
        assertThat(totp.verify(secret, "abcdef", 0)).isEmpty();
    }

    @Test
    void newSecretsAreRandomAndDecodable()
    {
        Totp totp = at(0);
        String first = totp.newSecret();

        assertThat(first).hasSize(32).isNotEqualTo(totp.newSecret());
        assertThat(Totp.decodeBase32(first)).hasSize(20);
    }

    @Test
    void uriFollowsTheKeyUriFormat()
    {
        assertThat(Totp.uri("Portfolio", "admin@test.local", "ABC"))
            .isEqualTo("otpauth://totp/Portfolio:admin%40test.local?secret=ABC&issuer=Portfolio"
                + "&algorithm=SHA1&digits=6&period=30");
    }

    private static Totp at(long epochSecond)
    {
        return new Totp(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }
}
