package com.portfolio.settings.internal;

import com.portfolio.settings.IpMode;
import com.portfolio.settings.SiteSettingsView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_settings")
class SiteSettings
{
    static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String title;

    @Column(name = "page_width", nullable = false)
    private int pageWidth;

    @Column(name = "page_height", nullable = false)
    private int pageHeight;

    @Column(name = "background_color", nullable = false)
    private String backgroundColor;

    @Column(name = "background_media_id")
    private UUID backgroundMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ip_mode", nullable = false)
    private IpMode ipMode;

    @Column(name = "privacy_text", nullable = false)
    private String privacyText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SiteSettings()
    {
    }

    void update(SettingsRequest request, Instant now)
    {
        this.title = request.title().trim();
        this.pageWidth = request.pageWidth();
        this.pageHeight = request.pageHeight();
        this.backgroundColor = request.backgroundColor();
        this.backgroundMediaId = request.backgroundMediaId();
        this.ipMode = request.ipMode();
        this.privacyText = request.privacyText() == null ? "" : request.privacyText();
        this.updatedAt = now;
    }

    void changeBackgroundColor(String color, Instant now)
    {
        this.backgroundColor = color;
        this.updatedAt = now;
    }

    UUID getBackgroundMediaId()
    {
        return backgroundMediaId;
    }

    Instant getUpdatedAt()
    {
        return updatedAt;
    }

    SiteSettingsView view()
    {
        return new SiteSettingsView(title, pageWidth, pageHeight, backgroundColor, backgroundMediaId, ipMode,
            privacyText);
    }
}
