package com.portfolio.visit.internal;

import com.portfolio.settings.IpMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Turns a client IP into what is stored: a salted SHA-256 hash (to count unique visitors) and a display value
 * that depends on the IP mode.
 */
@Component
class IpAnonymizer
{
    private final byte[] salt;

    IpAnonymizer(TrackingProperties properties)
    {
        this.salt = properties.salt().getBytes(StandardCharsets.UTF_8);
    }

    String hash(String ip)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(normalize(ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    String display(String ip, String hash, IpMode mode)
    {
        return switch (mode)
        {
            case FULL -> normalize(ip);
            case MASKED -> mask(ip);
            case HASHED -> "visiteur-" + hash.substring(0, 6);
        };
    }

    /** IPv4: last octet replaced by {@code xxx}. IPv6: truncated to the /48 prefix. */
    static String mask(String ip)
    {
        String value = normalize(ip);
        if (value.contains(":"))
        {
            try
            {
                byte[] bytes = InetAddress.getByName(value).getAddress();
                if (bytes.length == 16)
                {
                    return String.format("%x:%x:%x::/48",
                        ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF),
                        ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF),
                        ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF));
                }
            }
            catch (UnknownHostException exception)
            {
                return "inconnue";
            }
        }
        int lastDot = value.lastIndexOf('.');
        return lastDot > 0 ? value.substring(0, lastDot) + ".xxx" : "inconnue";
    }

    private static String normalize(String ip)
    {
        String value = ip == null ? "" : ip.trim();
        if (value.startsWith("::ffff:") && value.indexOf('.') > 0)
        {
            return value.substring("::ffff:".length());
        }
        return value;
    }
}
