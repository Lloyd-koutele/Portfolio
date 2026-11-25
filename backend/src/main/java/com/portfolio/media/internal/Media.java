package com.portfolio.media.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media")
class Media
{
    @Id
    private UUID id;

    @Column(name = "stored_name", nullable = false, unique = true)
    private String storedName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Media()
    {
    }

    Media(UUID id, String storedName, String originalName, String mimeType, long sizeBytes, int width, int height,
        MediaKind kind, Instant now)
    {
        this.id = id;
        this.storedName = storedName;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.kind = kind;
        this.createdAt = now;
    }

    Media(UUID id, String storedName, String originalName, ProcessedImage image, MediaKind kind, Instant now)
    {
        this.id = id;
        this.storedName = storedName;
        this.originalName = originalName;
        this.mimeType = image.mimeType();
        this.sizeBytes = image.content().length;
        this.width = image.width();
        this.height = image.height();
        this.kind = kind;
        this.createdAt = now;
    }

    UUID getId()
    {
        return id;
    }

    String getStoredName()
    {
        return storedName;
    }

    String getOriginalName()
    {
        return originalName;
    }

    String getMimeType()
    {
        return mimeType;
    }

    long getSizeBytes()
    {
        return sizeBytes;
    }

    int getWidth()
    {
        return width;
    }

    int getHeight()
    {
        return height;
    }

    MediaKind getKind()
    {
        return kind;
    }

    Instant getCreatedAt()
    {
        return createdAt;
    }

    boolean isVideo()
    {
        return mimeType.startsWith("video/");
    }

    String url()
    {
        return "/media/" + storedName;
    }
}
