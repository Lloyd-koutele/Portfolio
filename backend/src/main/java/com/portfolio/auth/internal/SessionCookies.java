package com.portfolio.auth.internal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the HttpOnly, SameSite=Strict session cookie that carries the JWT. The token never reaches
 * JavaScript or {@code localStorage}.
 */
@Component
class SessionCookies
{
    static final String MFA_COOKIE = "PORTFOLIO_MFA";
    static final String GOOGLE_COOKIE = "PORTFOLIO_GOOGLE";
    static final String GOOGLE_PATH = "/api/auth/google";

    private final AuthProperties properties;

    SessionCookies(AuthProperties properties)
    {
        this.properties = properties;
    }

    ResponseCookie create(String token, Duration maxAge)
    {
        return base(token).maxAge(maxAge).build();
    }

    ResponseCookie clear()
    {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** Short-lived cookie carrying the two-factor step token (same flags as the session). */
    ResponseCookie createMfa(String token, Duration maxAge)
    {
        return named(MFA_COOKIE, token).maxAge(maxAge).build();
    }

    ResponseCookie clearMfa()
    {
        return named(MFA_COOKIE, "").maxAge(Duration.ZERO).build();
    }

    /**
     * Binds a Google sign-in to the browser that started it. SameSite=Lax: the browser comes back from Google
     * through a top-level redirect, which Strict cookies would not follow.
     */
    ResponseCookie createGoogleBinding(String value, Duration maxAge)
    {
        return ResponseCookie.from(GOOGLE_COOKIE, value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite("Lax")
            .path(GOOGLE_PATH)
            .maxAge(maxAge)
            .build();
    }

    ResponseCookie clearGoogleBinding()
    {
        return createGoogleBinding("", Duration.ZERO);
    }

    Optional<String> read(HttpServletRequest request)
    {
        return read(request, properties.cookieName());
    }

    Optional<String> readMfa(HttpServletRequest request)
    {
        return read(request, MFA_COOKIE);
    }

    Optional<String> readGoogleBinding(HttpServletRequest request)
    {
        return read(request, GOOGLE_COOKIE);
    }

    private static Optional<String> read(HttpServletRequest request, String name)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value)
    {
        return named(properties.cookieName(), value);
    }

    private ResponseCookie.ResponseCookieBuilder named(String name, String value)
    {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite("Strict")
            .path("/");
    }
}
