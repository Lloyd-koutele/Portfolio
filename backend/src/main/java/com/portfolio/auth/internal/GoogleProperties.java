package com.portfolio.auth.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * "Continuer avec Google" ({@code app.auth.google.*}, from {@code GOOGLE_*} in .env). The button only appears
 * when the client id and secret are both set.
 *
 * @param clientId OAuth client id from the Google Cloud console
 * @param clientSecret OAuth client secret
 * @param redirectUri authorised redirect URI registered in the console (ends with /api/auth/google/callback)
 * @param adminUrl address of the administration, where the browser lands after the callback
 */
@ConfigurationProperties(prefix = "app.auth.google")
record GoogleProperties(
    String clientId,
    String clientSecret,
    @DefaultValue("https://portfolio-admin/api/auth/google/callback") String redirectUri,
    @DefaultValue("https://portfolio-admin") String adminUrl)
{
    boolean enabled()
    {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    String adminBase()
    {
        return adminUrl.endsWith("/") ? adminUrl.substring(0, adminUrl.length() - 1) : adminUrl;
    }

    @Override
    public String toString()
    {
        return "GoogleProperties[enabled=" + enabled() + ", redirectUri=" + redirectUri + ", adminUrl=" + adminUrl
            + "]";
    }
}
