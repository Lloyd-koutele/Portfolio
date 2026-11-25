package com.portfolio.settings.internal;

import com.portfolio.settings.IpMode;
import com.portfolio.settings.SiteSettingsView;
import java.time.Instant;
import java.util.UUID;

record SettingsResponse(String title, int pageWidth, int pageHeight, String backgroundColor,
    UUID backgroundMediaId, String backgroundUrl, IpMode ipMode, String privacyText, Instant updatedAt)
{
    static SettingsResponse of(SiteSettingsView view, String backgroundUrl, Instant updatedAt)
    {
        return new SettingsResponse(view.title(), view.pageWidth(), view.pageHeight(), view.backgroundColor(),
            view.backgroundMediaId(), backgroundUrl, view.ipMode(), view.privacyText(), updatedAt);
    }
}
