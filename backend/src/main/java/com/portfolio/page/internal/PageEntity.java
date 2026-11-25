package com.portfolio.page.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "page")
class PageEntity
{
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    /** Position among the sheets of its project (unused for fixed pages). */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageKind kind;

    @Column(name = "project_id")
    private UUID projectId;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "thumbnail_media_id")
    private UUID thumbnailMediaId;


    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PageEntity()
    {
    }

    PageEntity(PageKind kind, UUID projectId, String slug, String title, int position, String content,
        UUID thumbnailMediaId, Instant now)
    {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.projectId = projectId;
        this.slug = slug;
        this.title = title;
        this.position = position;
        this.content = content;
        this.thumbnailMediaId = thumbnailMediaId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void edit(String newTitle, String newSlug, String newContent, UUID newThumbnail, Instant now)
    {
        this.title = newTitle;
        this.slug = newSlug;
        this.content = newContent;
        this.thumbnailMediaId = newThumbnail;
        this.updatedAt = now;
    }


    UUID getId()
    {
        return id;
    }

    PageKind getKind()
    {
        return kind;
    }

    UUID getProjectId()
    {
        return projectId;
    }

    String getSlug()
    {
        return slug;
    }

    String getTitle()
    {
        return title;
    }

    int getPosition()
    {
        return position;
    }


    String getContent()
    {
        return content;
    }


    UUID getThumbnailMediaId()
    {
        return thumbnailMediaId;
    }

    int getVersion()
    {
        return version;
    }

    Instant getCreatedAt()
    {
        return createdAt;
    }

    Instant getUpdatedAt()
    {
        return updatedAt;
    }
}
