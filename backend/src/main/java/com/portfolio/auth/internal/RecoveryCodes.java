package com.portfolio.auth.internal;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-use recovery codes ({@code xxxx-xxxx}), for when the authenticator app is lost. Only their SHA-256
 * hashes are stored; the codes themselves are shown once.
 */
@Component
class RecoveryCodes
{
    static final int COUNT = 10;
    /** No 0/o, 1/l/i: easy to copy by hand. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int GROUP = 4;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RecoveryCodes(JdbcTemplate jdbc, Clock clock)
    {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Replaces every code of this administrator with {@link #COUNT} new ones and returns them. */
    List<String> regenerate(UUID adminId)
    {
        jdbc.update("DELETE FROM admin_recovery_code WHERE admin_id = ?", adminId);
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < COUNT; index++)
        {
            String code = group() + "-" + group();
            codes.add(code);
            jdbc.update("INSERT INTO admin_recovery_code (admin_id, code_hash) VALUES (?, ?)", adminId, hash(code));
        }
        return codes;
    }

    /** Uses up a code: true only the first time a valid code is given. */
    boolean consume(UUID adminId, String code)
    {
        if (code == null)
        {
            return false;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (!normalized.matches("[a-z0-9]{4}-?[a-z0-9]{4}"))
        {
            return false;
        }
        if (!normalized.contains("-"))
        {
            normalized = normalized.substring(0, GROUP) + "-" + normalized.substring(GROUP);
        }
        return jdbc.update("""
            UPDATE admin_recovery_code SET used_at = ?
            WHERE admin_id = ? AND code_hash = ? AND used_at IS NULL
            """, Timestamp.from(clock.instant()), adminId, hash(normalized)) == 1;
    }

    int remaining(UUID adminId)
    {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM admin_recovery_code WHERE admin_id = ? AND used_at IS NULL", Integer.class,
            adminId);
        return count == null ? 0 : count;
    }

    void deleteAll(UUID adminId)
    {
        jdbc.update("DELETE FROM admin_recovery_code WHERE admin_id = ?", adminId);
    }

    private String group()
    {
        StringBuilder out = new StringBuilder(GROUP);
        for (int index = 0; index < GROUP; index++)
        {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private static String hash(String code)
    {
        return HexFormat.of().formatHex(SecretCipher.sha256("recovery:" + code));
    }
}
