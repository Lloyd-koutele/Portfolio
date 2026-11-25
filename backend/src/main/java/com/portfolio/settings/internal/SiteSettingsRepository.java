package com.portfolio.settings.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SiteSettingsRepository extends JpaRepository<SiteSettings, Short>
{
    boolean existsByBackgroundMediaId(UUID mediaId);

    @Modifying
    @Query(value = "UPDATE site_settings SET demo_seeded = true WHERE id = 1 AND NOT demo_seeded", nativeQuery = true)
    int claimDemoSeeding();

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE site_settings SET title = :title, background_media_id = :mediaId, updated_at = now()"
        + " WHERE id = 1", nativeQuery = true)
    void applyDemoLook(@Param("title") String title, @Param("mediaId") UUID mediaId);
}
