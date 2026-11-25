package com.portfolio.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Finds the real client address. {@code X-Forwarded-For} is only trusted when the request comes from a
 * configured proxy ({@code app.tracking.trusted-proxies}, addresses or CIDR ranges); otherwise anyone could
 * forge it. Walking the header from right to left, the first address that is not a trusted proxy is the client.
 */
@Component
public class ClientIpResolver
{
    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${app.tracking.trusted-proxies:}") List<String> trustedProxies)
    {
        this.trustedProxies = trustedProxies.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
    }

    public String resolve(HttpServletRequest request)
    {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank() || !isTrusted(remote))
        {
            return remote;
        }
        List<String> hops = Arrays.stream(forwarded.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        for (int index = hops.size() - 1; index >= 0; index--)
        {
            String hop = hops.get(index);
            if (!isValidAddress(hop))
            {
                return remote;
            }
            if (!isTrusted(hop))
            {
                return hop;
            }
        }
        return hops.isEmpty() ? remote : hops.get(0);
    }

    private boolean isTrusted(String address)
    {
        if (!isValidAddress(address))
        {
            return false;
        }
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static boolean isValidAddress(String address)
    {
        if (address == null || address.isEmpty() || address.length() > 45)
        {
            return false;
        }
        return address.matches("[0-9a-fA-F:.]+") && (address.contains(".") || address.contains(":"));
    }
}
