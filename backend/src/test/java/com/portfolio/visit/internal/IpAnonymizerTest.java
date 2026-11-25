package com.portfolio.visit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.settings.IpMode;
import java.time.Duration;
import java.time.Period;
import org.junit.jupiter.api.Test;

class IpAnonymizerTest
{
    private final IpAnonymizer anonymizer = anonymizer("unit-test-salt-0123456789");

    @Test
    void masksIpv4AndIpv6()
    {
        assertThat(IpAnonymizer.mask("41.82.14.25")).isEqualTo("41.82.14.xxx");
        assertThat(IpAnonymizer.mask("::ffff:41.82.14.25")).isEqualTo("41.82.14.xxx");
        assertThat(IpAnonymizer.mask("2a01:e0a:1f2:3b40::1")).isEqualTo("2a01:e0a:1f2::/48");
    }

    @Test
    void hashDependsOnTheSalt()
    {
        IpAnonymizer other = anonymizer("another-salt-0123456789");

        assertThat(anonymizer.hash("41.82.14.25")).isNotEqualTo(other.hash("41.82.14.25")).hasSize(64);
        assertThat(anonymizer.hash("41.82.14.25")).isEqualTo(anonymizer.hash("::ffff:41.82.14.25"));
    }

    @Test
    void displayFollowsTheMode()
    {
        String hash = anonymizer.hash("41.82.14.25");

        assertThat(anonymizer.display("41.82.14.25", hash, IpMode.FULL)).isEqualTo("41.82.14.25");
        assertThat(anonymizer.display("41.82.14.25", hash, IpMode.MASKED)).isEqualTo("41.82.14.xxx");
        assertThat(anonymizer.display("41.82.14.25", hash, IpMode.HASHED))
            .isEqualTo("visiteur-" + hash.substring(0, 6));
    }

    private static IpAnonymizer anonymizer(String salt)
    {
        return new IpAnonymizer(new TrackingProperties(salt, Duration.ofMinutes(30), Period.ofMonths(13)));
    }
}
