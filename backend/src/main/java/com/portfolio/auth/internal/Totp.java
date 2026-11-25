package com.portfolio.auth.internal;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30-second steps, 6 digits), as computed by Google
 * Authenticator, Microsoft Authenticator, Authy, 1Password… One step of clock drift is tolerated either way.
 */
@Component
class Totp
{
    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20;
    private static final int DRIFT_STEPS = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    Totp(Clock clock)
    {
        this.clock = clock;
    }

    /** A new random secret, Base32-encoded (the form authenticator apps expect). */
    String newSecret()
    {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    /** The {@code otpauth://} address shown as a QR code. */
    static String uri(String issuer, String account, String secret)
    {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
            + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Checks a code against the current step, one step before and one after.
     *
     * @param lastStep last step already accepted for this secret; it and earlier steps are refused (no replay)
     * @return the matching step, or empty when the code is wrong or already used
     */
    OptionalLong verify(String secret, String code, long lastStep)
    {
        if (code == null || !code.matches("\\d{" + DIGITS + "}"))
        {
            return OptionalLong.empty();
        }
        byte[] key = decodeBase32(secret);
        long current = clock.instant().getEpochSecond() / STEP_SECONDS;
        for (long step = current - DRIFT_STEPS; step <= current + DRIFT_STEPS; step++)
        {
            if (step > lastStep && MessageDigest.isEqual(code(key, step).getBytes(StandardCharsets.US_ASCII),
                code.getBytes(StandardCharsets.US_ASCII)))
            {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** The code for a given step (exposed for tests). */
    static String code(byte[] key, long step)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
        }
        catch (GeneralSecurityException exception)
        {
            throw new IllegalStateException("HmacSHA1 is not available", exception);
        }
    }

    static String base32(byte[] bytes)
    {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes)
        {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5)
            {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0)
        {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String text)
    {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char character : clean.toCharArray())
        {
            int value = BASE32.indexOf(character);
            if (value < 0)
            {
                throw new IllegalArgumentException("Not a Base32 secret");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8)
            {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
