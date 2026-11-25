package com.portfolio.page.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PageRepository extends JpaRepository<PageEntity, UUID>
{
    List<PageEntity> findAllByOrderByPositionAsc();

    List<PageEntity> findByProjectIdOrderByPositionAsc(UUID projectId);

    Optional<PageEntity> findByKind(PageKind kind);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** Changes the order without touching the optimistic-lock version (an open editor stays valid). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PageEntity p set p.position = :position where p.id = :id")
    void updatePosition(@Param("id") UUID id, @Param("position") int position);

    @Query("select coalesce(max(p.position), -1) from PageEntity p where p.projectId = :projectId")
    int maxPositionInProject(@Param("projectId") UUID projectId);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM page
            WHERE thumbnail_media_id = :mediaId
               OR jsonb_path_exists(content, '$.** ? (@ == $id)', jsonb_build_object('id', CAST(:mediaId AS text)))

        )
        """, nativeQuery = true)
    boolean referencesMedia(@Param("mediaId") UUID mediaId);
}
