package com.portfolio.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest
{
    private final ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.1", "172.16.0.0/12"));

    @Test
    void usesTheSocketAddressWithoutHeader()
    {
        assertThat(resolver.resolve(request("198.51.100.7", null))).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresForwardedForFromAnUntrustedClient()
    {
        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void readsForwardedForFromATrustedProxy()
    {
        assertThat(resolver.resolve(request("10.0.0.1", "41.82.14.25"))).isEqualTo("41.82.14.25");
    }

    @Test
    void skipsTrustedHopsAndIgnoresForgedLeftmostValues()
    {
        String header = "6.6.6.6, 41.82.14.25, 172.18.0.5";

        assertThat(resolver.resolve(request("10.0.0.1", header))).isEqualTo("41.82.14.25");
    }

    @Test
    void fallsBackToTheSocketAddressOnGarbage()
    {
        assertThat(resolver.resolve(request("10.0.0.1", "evil.example.com"))).isEqualTo("10.0.0.1");
    }

    @Test
    void supportsIpv6()
    {
        assertThat(resolver.resolve(request("10.0.0.1", "2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    @Test
    void trustsNothingWhenNoProxyIsConfigured()
    {
        ClientIpResolver noProxy = new ClientIpResolver(List.of());

        assertThat(noProxy.resolve(request("10.0.0.1", "41.82.14.25"))).isEqualTo("10.0.0.1");
    }

    private static MockHttpServletRequest request(String remote, String forwardedFor)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null)
        {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
