package com.portfolio.auth.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google's OpenID Connect endpoints: the code (with its PKCE verifier) is exchanged for an ID token, whose
 * signature (Google's published keys), issuer, audience, expiry and nonce are then checked.
 */
@Component
class HttpGoogleIdentityClient implements GoogleIdentityClient
{
    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(HttpGoogleIdentityClient.class);

    private final GoogleProperties properties;
    private final RestClient http;
    private final Clock clock;
    private volatile NimbusJwtDecoder decoder;

    HttpGoogleIdentityClient(GoogleProperties properties, RestClient.Builder builder, Clock clock)
    {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.http = builder.requestFactory(factory).build();
        this.clock = clock;
    }

    @Override
    public Optional<GoogleIdentity> exchange(String code, String codeVerifier, String nonce)
    {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        try
        {
            JsonNode response = http.post().uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
            String idToken = response == null ? null : response.path("id_token").asText(null);
            if (idToken == null)
            {
                return Optional.empty();
            }
            Jwt jwt = decoder().decode(idToken);
            if (!nonce.equals(jwt.getClaimAsString("nonce")))
            {
                return Optional.empty();
            }
            return Optional.of(new GoogleIdentity(jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))));
        }
        catch (RestClientException | JwtException exception)
        {
            LOG.warn("Google sign-in failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private NimbusJwtDecoder decoder()
    {
        NimbusJwtDecoder current = decoder;
        if (current == null)
        {
            current = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).jwsAlgorithm(SignatureAlgorithm.RS256).build();
            JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
            timestamps.setClock(clock);
            OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected issuer", null));
            OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(properties.clientId())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected audience", null));
            current.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, issuer, audience));
            decoder = current;
        }
        return current;
    }
}
