package com.portfolio.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the administrator from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} when no account exists yet.
 * An existing account is never modified. The password is never logged.
 */
@Component
class AdminAccountInitializer implements ApplicationRunner
{
    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger LOG = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    AdminAccountInitializer(AdminUserRepository repository, PasswordEncoder passwordEncoder,
        AuthProperties properties)
    {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        if (repository.count() > 0)
        {
            return;
        }
        String email = EmailNormalizer.normalize(properties.adminEmail());
        String password = properties.adminPassword();
        if (email.isEmpty() || password == null || password.isEmpty())
        {
            LOG.warn("No administrator account exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH)
        {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH
                + " characters long");
        }
        repository.save(new AdminUser(email, passwordEncoder.encode(password)));
        LOG.info("Administrator account created");
    }
}
