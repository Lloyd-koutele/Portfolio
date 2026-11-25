package com.portfolio.settings;

import java.util.UUID;

/**
 * Read-only view of the site settings for other modules.
 */
public record SiteSettingsView(String title, int pageWidth, int pageHeight, String backgroundColor,
    UUID backgroundMediaId, IpMode ipMode, String privacyText)
{
}
