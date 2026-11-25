package com.portfolio.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings bound from {@code app.*}. Values come from environment variables (see .env.example).
 *
 * @param origins allowed browser origins (CORS), comma-separated in {@code APP_ORIGIN}: behind a TLS proxy the
 *     backend sees plain HTTP, so every public address of the site (e.g. both domains) must be listed
 * @param uploadDir directory where uploaded files are stored
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotEmpty List<@NotBlank String> origins, @NotBlank String uploadDir)
{
}
