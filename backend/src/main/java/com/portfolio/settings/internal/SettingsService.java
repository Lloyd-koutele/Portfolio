package com.portfolio.settings.internal;

import com.portfolio.common.ApiException;
import com.portfolio.media.MediaApi;
import com.portfolio.media.MediaInfo;
import com.portfolio.media.MediaUsage;
import com.portfolio.settings.SettingsApi;
import com.portfolio.settings.SiteSettingsView;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettingsService implements SettingsApi, MediaUsage
{
    private final SiteSettingsRepository repository;
    private final MediaApi mediaApi;
    private final Clock clock;

    SettingsService(SiteSettingsRepository repository, MediaApi mediaApi, Clock clock)
    {
        this.repository = repository;
        this.mediaApi = mediaApi;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SiteSettingsView current()
    {
        return load().view();
    }

    @Transactional(readOnly = true)
    SettingsResponse read()
    {
        SiteSettings settings = load();
        return SettingsResponse.of(settings.view(), backgroundUrl(settings.getBackgroundMediaId()),
            settings.getUpdatedAt());
    }

    @Transactional
    SettingsResponse update(SettingsRequest request)
    {
        if (request.backgroundMediaId() != null && mediaApi.existing(List.of(request.backgroundMediaId())).isEmpty())
        {
            throw new UnknownBackgroundException();
        }
        SiteSettings settings = load();
        settings.update(request, clock.instant());
        return SettingsResponse.of(settings.view(), backgroundUrl(settings.getBackgroundMediaId()),
            settings.getUpdatedAt());
    }

    @Override
    @Transactional
    public boolean claimDemoSeeding()
    {
        return repository.claimDemoSeeding() == 1;
    }

    @Override
    @Transactional
    public void applyDemoLook(String title, UUID backgroundMediaId)
    {
        repository.applyDemoLook(title, backgroundMediaId);
    }

    @Override
    @Transactional
    public void changeBackgroundColor(String color)
    {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}"))
        {
            throw new IllegalArgumentException("Not a #rrggbb colour");
        }
        load().changeBackgroundColor(color, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsed(UUID mediaId)
    {
        return repository.existsByBackgroundMediaId(mediaId);
    }

    String backgroundUrl(UUID mediaId)
    {
        if (mediaId == null)
        {
            return null;
        }
        Map<UUID, MediaInfo> info = mediaApi.describe(List.of(mediaId));
        MediaInfo media = info.get(mediaId);
        return media == null ? null : media.url();
    }

    private SiteSettings load()
    {
        return Objects.requireNonNull(repository.findById(SiteSettings.SINGLETON_ID).orElse(null),
            "site_settings row is missing");
    }

    static class UnknownBackgroundException extends ApiException
    {
        UnknownBackgroundException()
        {
            super(HttpStatus.BAD_REQUEST, "Invalid settings", "Background media does not exist");
        }
    }
}
