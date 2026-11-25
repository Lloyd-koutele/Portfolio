package com.portfolio.auth.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authentication settings ({@code app.auth.*}).
 *
 * @param jwtSecret HS256 signing key, at least 32 bytes
 * @param sessionDuration lifetime of the session cookie and token
 * @param rememberDuration lifetime of the session when "Se souvenir de moi" is ticked
 * @param mfaDuration time allowed to type the two-factor code after the first step
 * @param cookieName name of the HttpOnly session cookie
 * @param cookieSecure whether cookies carry the {@code Secure} flag (false only for plain-HTTP development)
 * @param loginAttemptsPerMinute login attempts allowed per minute and per IP
 * @param adminEmail e-mail of the administrator created on first start
 * @param adminPassword password of the administrator created on first start
 */
@ConfigurationProperties(prefix = "app.auth")
record AuthProperties(
    String jwtSecret,
    @DefaultValue("8h") Duration sessionDuration,
    @DefaultValue("30d") Duration rememberDuration,
    @DefaultValue("5m") Duration mfaDuration,
    @DefaultValue("PORTFOLIO_SESSION") String cookieName,
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue("5") int loginAttemptsPerMinute,
    String adminEmail,
    String adminPassword)
{
    static final int MIN_SECRET_BYTES = 32;

    AuthProperties
    {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES)
        {
            throw new IllegalStateException("JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (loginAttemptsPerMinute < 1)
        {
            throw new IllegalStateException("app.auth.login-attempts-per-minute must be positive");
        }
    }

    @Override
    public String toString()
    {
        return "AuthProperties[sessionDuration=" + sessionDuration + ", rememberDuration=" + rememberDuration
            + ", cookieName=" + cookieName
            + ", cookieSecure=" + cookieSecure + ", loginAttemptsPerMinute=" + loginAttemptsPerMinute + "]";
    }
}
