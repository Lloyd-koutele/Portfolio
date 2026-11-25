package com.portfolio.auth.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Google sign-in (OpenID Connect, authorisation code + PKCE), in three hops:
 * <ol>
 * <li>{@code /api/auth/google}: a random {@code state}, {@code nonce} and PKCE verifier are kept in memory, a
 * random binding value goes into a cookie of this browser, and the browser is sent to Google;</li>
 * <li>{@code /api/auth/google/callback} (the redirect URI, possibly on another host such as {@code localhost}):
 * the code is exchanged and the ID token checked; a one-time ticket is issued;</li>
 * <li>{@code /api/auth/google/complete} on the administration's domain: the ticket is accepted only from the
 * browser holding the binding cookie, then the session (or the two-factor step) starts.</li>
 * </ol>
 * Everything expires after ten minutes, and each state or ticket works once.
 */
@Component
class GoogleSignIn
{
    static final Duration FLOW_LIFETIME = Duration.ofMinutes(10);
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(1);
    private static final int MAX_PENDING = 1_000;

    private final GoogleProperties properties;
    private final GoogleIdentityClient identities;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    GoogleSignIn(GoogleProperties properties, GoogleIdentityClient identities, Clock clock)
    {
        this.properties = properties;
        this.identities = identities;
        this.clock = clock;
    }

    boolean enabled()
    {
        return properties.enabled();
    }

    Start start(boolean remember)
    {
        evictExpired();
        if (pending.size() >= MAX_PENDING)
        {
            throw new IllegalStateException("Too many Google sign-ins in progress");
        }
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken() + randomToken();
        String binding = randomToken();
        pending.put(state, new Pending(nonce, verifier, binding, remember, clock.instant().plus(FLOW_LIFETIME)));
        String url = HttpGoogleIdentityClient.AUTHORIZATION_ENDPOINT
            + "?response_type=code"
            + "&client_id=" + encode(properties.clientId())
            + "&redirect_uri=" + encode(properties.redirectUri())
            + "&scope=" + encode("openid email")
            + "&state=" + state
            + "&nonce=" + nonce
            + "&code_challenge=" + challenge(verifier)
            + "&code_challenge_method=S256"
            + "&prompt=select_account";
        return new Start(url, binding);
    }

    /**
     * Handles Google's redirect.
     *
     * @return a one-time ticket for {@link #complete}, or empty when the state is unknown or the identity
     *     cannot be verified
     */
    Optional<String> callback(String state, String code)
    {
        if (state == null || code == null)
        {
            return Optional.empty();
        }
        Pending flow = pending.remove(state);
        if (flow == null || flow.expiresAt().isBefore(clock.instant()))
        {
            return Optional.empty();
        }
        return identities.exchange(code, flow.verifier(), flow.nonce())
            .filter(GoogleIdentityClient.GoogleIdentity::emailVerified)
            .filter(identity -> identity.email() != null && !identity.email().isBlank())
            .map(identity ->
            {
                String ticket = randomToken();
                tickets.put(ticket, new Ticket(identity.email(), flow.binding(), flow.remember(),
                    clock.instant().plus(TICKET_LIFETIME)));
                return ticket;
            });
    }

    /** Redeems a ticket, only for the browser that started the sign-in. */
    Optional<Completed> complete(String ticket, String binding)
    {
        evictExpired();
        if (ticket == null || binding == null)
        {
            return Optional.empty();
        }
        Ticket found = tickets.remove(ticket);
        if (found == null || found.expiresAt().isBefore(clock.instant()) || !found.binding().equals(binding))
        {
            return Optional.empty();
        }
        return Optional.of(new Completed(found.email(), found.remember()));
    }

    String adminBase()
    {
        return properties.adminBase();
    }

    private void evictExpired()
    {
        Instant now = clock.instant();
        pending.values().removeIf(flow -> flow.expiresAt().isBefore(now));
        tickets.values().removeIf(ticket -> ticket.expiresAt().isBefore(now));
    }

    private String randomToken()
    {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier)
    {
        byte[] digest = SecretCipher.sha256(verifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record Start(String authorizationUrl, String binding)
    {
    }

    record Completed(String email, boolean remember)
    {
    }

    private record Pending(String nonce, String verifier, String binding, boolean remember, Instant expiresAt)
    {
    }

    private record Ticket(String email, String binding, boolean remember, Instant expiresAt)
    {
    }
}
