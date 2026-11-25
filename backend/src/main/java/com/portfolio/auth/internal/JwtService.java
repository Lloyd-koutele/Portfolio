package com.portfolio.auth.internal;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.portfolio.auth.AdminPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies HS256 session tokens.
 */
@Component
class JwtService
{
    static final String ISSUER = "portfolio";
    /** Issuer of the short token between the password (or Google) and the two-factor code: never a session. */
    static final String MFA_ISSUER = "portfolio-mfa";
    static final String REMEMBER_CLAIM = "remember";
    static final String EMAIL_CLAIM = "email";
    static final String ROLES_CLAIM = "roles";
    static final String ADMIN_ROLE = "ADMIN";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration lifetime;
    private final Duration rememberLifetime;
    private final Duration mfaLifetime;
    private final Clock clock;

    JwtService(AuthProperties properties, Clock clock)
    {
        SecretKey key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        nimbusDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(timestamps));
        this.decoder = nimbusDecoder;
        this.lifetime = properties.sessionDuration();
        this.rememberLifetime = properties.rememberDuration();
        this.mfaLifetime = properties.mfaDuration();
        this.clock = clock;
    }

    String issue(AdminPrincipal admin)
    {
        return issue(admin, lifetime);
    }

    String issue(AdminPrincipal admin, Duration duration)
    {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(admin.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(duration))
            .claim(EMAIL_CLAIM, admin.email())
            .claim(ROLES_CLAIM, List.of(ADMIN_ROLE))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    Optional<AdminPrincipal> verify(String token)
    {
        try
        {
            Jwt jwt = decoder.decode(token);
            if (!ISSUER.equals(jwt.getClaimAsString("iss")))
            {
                return Optional.empty();
            }
            List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
            if (roles == null || !roles.contains(ADMIN_ROLE))
            {
                return Optional.empty();
            }
            UUID adminId = UUID.fromString(jwt.getSubject());
            return Optional.of(new AdminPrincipal(adminId, jwt.getClaimAsString(EMAIL_CLAIM)));
        }
        catch (JwtException | IllegalArgumentException exception)
        {
            return Optional.empty();
        }
    }

    /** Token proving the first step (password or Google) for this administrator, valid a few minutes. */
    String issueMfa(UUID adminId, boolean remember)
    {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(MFA_ISSUER)
            .subject(adminId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(mfaLifetime))
            .claim(REMEMBER_CLAIM, remember)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    Optional<PendingMfa> verifyMfa(String token)
    {
        try
        {
            Jwt jwt = decoder.decode(token);
            if (!MFA_ISSUER.equals(jwt.getClaimAsString("iss")))
            {
                return Optional.empty();
            }
            return Optional.of(new PendingMfa(UUID.fromString(jwt.getSubject()),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(REMEMBER_CLAIM))));
        }
        catch (JwtException | IllegalArgumentException exception)
        {
            return Optional.empty();
        }
    }

    Duration lifetime()
    {
        return lifetime;
    }

    Duration lifetime(boolean remember)
    {
        return remember ? rememberLifetime : lifetime;
    }

    Duration mfaLifetime()
    {
        return mfaLifetime;
    }

    /** First step done, two-factor code still expected. */
    record PendingMfa(UUID adminId, boolean remember)
    {
    }
}
