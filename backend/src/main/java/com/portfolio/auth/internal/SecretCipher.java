package com.portfolio.auth.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts TOTP secrets at rest (AES-256-GCM). The key is derived from {@code JWT_SECRET}: a database dump alone
 * does not reveal the secrets. Changing {@code JWT_SECRET} therefore disables existing authenticator set-ups.
 */
@Component
class SecretCipher
{
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    SecretCipher(AuthProperties properties)
    {
        this.key = new SecretKeySpec(sha256("portfolio-totp:" + properties.jwtSecret()), "AES");
    }

    String encrypt(String plain)
    {
        try
        {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                .put(iv).put(sealed).array());
        }
        catch (GeneralSecurityException exception)
        {
            throw new IllegalStateException("Cannot encrypt the secret", exception);
        }
    }

    String decrypt(String encoded)
    {
        try
        {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException | IllegalArgumentException exception)
        {
            throw new IllegalStateException("Cannot decrypt the secret (was JWT_SECRET changed?)", exception);
        }
    }

    static byte[] sha256(String value)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (GeneralSecurityException exception)
        {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
