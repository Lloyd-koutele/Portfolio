package com.portfolio.auth.internal;

import com.portfolio.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-factor authentication: set-up (secret + QR code), activation with a first code, recovery codes, and the
 * code check at login. Once enabled, it is required after the password and after Google.
 */
@Service
class TwoFactorService
{
    static final String ISSUER = "Portfolio";

    private final AdminUserRepository repository;
    private final Totp totp;
    private final SecretCipher cipher;
    private final RecoveryCodes recoveryCodes;

    TwoFactorService(AdminUserRepository repository, Totp totp, SecretCipher cipher, RecoveryCodes recoveryCodes)
    {
        this.repository = repository;
        this.totp = totp;
        this.cipher = cipher;
        this.recoveryCodes = recoveryCodes;
    }

    @Transactional(readOnly = true)
    Status status(UUID adminId)
    {
        AdminUser admin = load(adminId);
        return new Status(admin.isTotpEnabled(), admin.isTotpEnabled() ? recoveryCodes.remaining(adminId) : 0);
    }

    /** Starts (or restarts) the set-up: a new secret waits for its first valid code. */
    @Transactional
    Setup setup(UUID adminId)
    {
        AdminUser admin = load(adminId);
        if (admin.isTotpEnabled())
        {
            throw new TwoFactorStateException("Two-factor authentication is already enabled");
        }
        String secret = totp.newSecret();
        admin.startTotpSetup(cipher.encrypt(secret));
        return new Setup(secret, Totp.uri(ISSUER, admin.getEmail(), secret));
    }

    /** Confirms the set-up with a code from the app; returns the recovery codes, shown only once. */
    @Transactional
    List<String> enable(UUID adminId, String code)
    {
        AdminUser admin = load(adminId);
        if (admin.isTotpEnabled() || admin.getTotpPendingSecret() == null)
        {
            throw new TwoFactorStateException("Start the set-up first");
        }
        OptionalLong step = totp.verify(cipher.decrypt(admin.getTotpPendingSecret()), code, 0);
        if (step.isEmpty())
        {
            throw new InvalidCodeException();
        }
        admin.enableTotp(step.getAsLong());
        return recoveryCodes.regenerate(adminId);
    }

    /** Turns two-factor authentication off; needs a current code (or a recovery code). */
    @Transactional
    void disable(UUID adminId, String code)
    {
        AdminUser admin = load(adminId);
        if (!admin.isTotpEnabled())
        {
            throw new TwoFactorStateException("Two-factor authentication is not enabled");
        }
        if (!check(admin, code))
        {
            throw new InvalidCodeException();
        }
        admin.disableTotp();
        recoveryCodes.deleteAll(adminId);
    }

    @Transactional
    List<String> regenerateRecoveryCodes(UUID adminId, String code)
    {
        AdminUser admin = load(adminId);
        if (!admin.isTotpEnabled())
        {
            throw new TwoFactorStateException("Two-factor authentication is not enabled");
        }
        if (!check(admin, code))
        {
            throw new InvalidCodeException();
        }
        return recoveryCodes.regenerate(adminId);
    }

    /** Second step of the login: an app code, or else an unused recovery code. */
    @Transactional
    boolean verifyLogin(UUID adminId, String code)
    {
        return repository.findById(adminId).filter(AdminUser::isTotpEnabled).map(admin -> check(admin, code))
            .orElse(false);
    }

    private boolean check(AdminUser admin, String code)
    {
        String trimmed = code == null ? "" : code.replace(" ", "").trim();
        OptionalLong step = totp.verify(cipher.decrypt(admin.getTotpSecret()), trimmed, admin.getTotpLastStep());
        if (step.isPresent())
        {
            admin.acceptTotpStep(step.getAsLong());
            return true;
        }
        return recoveryCodes.consume(admin.getId(), trimmed);
    }

    private AdminUser load(UUID adminId)
    {
        return repository.findById(adminId).orElseThrow(NotAuthenticatedException::new);
    }

    record Status(boolean enabled, int recoveryCodesLeft)
    {
    }

    record Setup(String secret, String otpauthUri)
    {
    }

    static class InvalidCodeException extends ApiException
    {
        InvalidCodeException()
        {
            super(HttpStatus.BAD_REQUEST, "Invalid code", "The verification code is wrong or expired");
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("code", "INVALID_CODE");
        }
    }

    static class TwoFactorStateException extends ApiException
    {
        TwoFactorStateException(String detail)
        {
            super(HttpStatus.CONFLICT, "Two-factor state", detail);
        }
    }
}
