package com.portfolio.auth.internal;

import com.portfolio.auth.AdminPrincipal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService
{
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final TwoFactorService twoFactor;
    /** Hash checked when the e-mail is unknown, so both failures take the same time. */
    private final String dummyHash;

    AuthService(AdminUserRepository repository, PasswordEncoder passwordEncoder, LoginRateLimiter rateLimiter,
        JwtService jwtService, TwoFactorService twoFactor)
    {
        this.twoFactor = twoFactor;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Checks the credentials. Returns a session, or a two-factor step when the authenticator app is enabled.
     *
     * @throws TooManyLoginAttemptsException after too many attempts from this IP within a minute
     * @throws InvalidCredentialsException for an unknown e-mail or a wrong password
     */
    @Transactional(readOnly = true)
    LoginResult login(String email, String password, String clientIp, boolean remember)
    {
        throttle(clientIp);
        Optional<AdminUser> admin = repository.findByEmail(EmailNormalizer.normalize(email));
        String hash = admin.map(AdminUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (admin.isEmpty() || !matches)
        {
            LOG.info("Failed login attempt");
            throw new InvalidCredentialsException();
        }
        return firstStepDone(admin.get(), remember);
    }

    LoginResult login(String email, String password, String clientIp)
    {
        return login(email, password, clientIp, false);
    }

    /**
     * Google confirmed this e-mail address: same outcome as a correct password. Only an existing administrator
     * account can sign in; Google never creates one.
     */
    @Transactional(readOnly = true)
    Optional<LoginResult> loginWithGoogle(String email, boolean remember)
    {
        Optional<AdminUser> admin = repository.findByEmail(EmailNormalizer.normalize(email));
        if (admin.isEmpty())
        {
            LOG.info("Google sign-in refused: no administrator with this address");
        }
        return admin.map(found -> firstStepDone(found, remember));
    }

    /**
     * Second step: the code from the authenticator app (or a recovery code).
     *
     * @throws NotAuthenticatedException when the first step is missing or expired
     * @throws TwoFactorService.InvalidCodeException for a wrong code
     */
    @Transactional
    LoginResult verifyTwoFactor(String mfaToken, String code, String clientIp)
    {
        JwtService.PendingMfa step = Optional.ofNullable(mfaToken).flatMap(jwtService::verifyMfa)
            .orElseThrow(NotAuthenticatedException::new);
        throttle(clientIp);
        // Five tries per first step: more guesses need the password (or Google) again.
        throttle("mfa:" + mfaToken);
        if (!twoFactor.verifyLogin(step.adminId(), code))
        {
            LOG.info("Failed two-factor attempt");
            throw new TwoFactorService.InvalidCodeException();
        }
        AdminUser admin = repository.findById(step.adminId()).orElseThrow(NotAuthenticatedException::new);
        return session(admin, step.remember());
    }

    private LoginResult firstStepDone(AdminUser admin, boolean remember)
    {
        if (admin.isTotpEnabled())
        {
            return new LoginResult(null, null, null, jwtService.issueMfa(admin.getId(), remember));
        }
        return session(admin, remember);
    }

    private LoginResult session(AdminUser admin, boolean remember)
    {
        AdminPrincipal principal = new AdminPrincipal(admin.getId(), admin.getEmail());
        Duration lifetime = jwtService.lifetime(remember);
        return new LoginResult(principal, jwtService.issue(principal, lifetime), lifetime, null);
    }

    private void throttle(String key)
    {
        Duration wait = rateLimiter.tryConsume(key);
        if (!wait.isZero())
        {
            throw new TooManyLoginAttemptsException(wait);
        }
    }

    @Transactional(readOnly = true)
    Optional<AdminPrincipal> currentAdmin(AdminPrincipal principal)
    {
        return repository.findById(principal.id())
            .map(admin -> new AdminPrincipal(admin.getId(), admin.getEmail()));
    }

    /**
     * Either a session ({@code admin}, {@code token}, {@code lifetime}) or, when two-factor authentication is on,
     * only {@code mfaToken}.
     */
    record LoginResult(AdminPrincipal admin, String token, Duration lifetime, String mfaToken)
    {
        boolean mfaRequired()
        {
            return mfaToken != null;
        }
    }
}
