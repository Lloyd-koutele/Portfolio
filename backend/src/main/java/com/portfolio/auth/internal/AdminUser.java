package com.portfolio.auth.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_user")
class AdminUser
{
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Encrypted TOTP secret, set once two-factor authentication is enabled. */
    @Column(name = "totp_secret")
    private String totpSecret;

    /** Encrypted secret waiting for its first valid code (set-up in progress). */
    @Column(name = "totp_pending_secret")
    private String totpPendingSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Last accepted 30-second step: a code can only be used once. */
    @Column(name = "totp_last_step", nullable = false)
    private long totpLastStep;

    protected AdminUser()
    {
    }

    AdminUser(String email, String passwordHash)
    {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    UUID getId()
    {
        return id;
    }

    String getEmail()
    {
        return email;
    }

    String getPasswordHash()
    {
        return passwordHash;
    }

    Instant getCreatedAt()
    {
        return createdAt;
    }

    String getTotpSecret()
    {
        return totpSecret;
    }

    String getTotpPendingSecret()
    {
        return totpPendingSecret;
    }

    boolean isTotpEnabled()
    {
        return totpEnabled;
    }

    long getTotpLastStep()
    {
        return totpLastStep;
    }

    void startTotpSetup(String encryptedSecret)
    {
        this.totpPendingSecret = encryptedSecret;
    }

    void enableTotp(long acceptedStep)
    {
        this.totpSecret = totpPendingSecret;
        this.totpPendingSecret = null;
        this.totpEnabled = true;
        this.totpLastStep = acceptedStep;
    }

    void disableTotp()
    {
        this.totpSecret = null;
        this.totpPendingSecret = null;
        this.totpEnabled = false;
        this.totpLastStep = 0;
    }

    void acceptTotpStep(long step)
    {
        this.totpLastStep = step;
    }
}
