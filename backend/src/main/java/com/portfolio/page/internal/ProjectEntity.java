package com.portfolio.page.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A project of the portfolio: a title (shown in the table of contents) and an ordered set of sheets (pages).
 */
@Entity
@Table(name = "project")
class ProjectEntity
{
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEntity()
    {
    }

    ProjectEntity(String title, int position, Instant now)
    {
        this.id = UUID.randomUUID();
        this.title = title;
        this.position = position;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void rename(String newTitle, Instant now)
    {
        this.title = newTitle;
        this.updatedAt = now;
    }

    void moveTo(int newPosition)
    {
        this.position = newPosition;
    }

    UUID getId()
    {
        return id;
    }

    String getTitle()
    {
        return title;
    }

    int getPosition()
    {
        return position;
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
