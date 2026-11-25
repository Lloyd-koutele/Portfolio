package com.portfolio;

import com.portfolio.auth.internal.FakeGoogleIdentityClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a disposable PostgreSQL 16 for integration tests, and replaces Google's endpoints with a fake.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
{
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer()
    {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @Primary
    FakeGoogleIdentityClient fakeGoogleIdentityClient()
    {
        return new FakeGoogleIdentityClient();
    }
}
