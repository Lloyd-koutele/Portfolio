package com.portfolio.page.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copies of the book taken just before a theme is applied: each page's content and thumbnail as they were, and the
 * colour around the book. The last {@link #KEPT} copies are kept; their media stay in use until then.
 */
@Component
class ThemeSnapshots
{
    static final int KEPT = 10;

    /** One page as it was before the theme, and its version right after the theme was applied. */
    record SnapshotPage(UUID id, JsonNode content, UUID thumbnailMediaId, int versionAfter)
    {
    }

    record Snapshot(long id, Instant appliedAt, String themeId, String backgroundColor, List<SnapshotPage> pages)
    {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    ThemeSnapshots(JdbcTemplate jdbc, ObjectMapper objectMapper)
    {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    long save(String themeId, String backgroundColor, List<SnapshotPage> pages)
    {
        Long id = jdbc.queryForObject("""
            INSERT INTO theme_snapshot (theme_id, background_color, pages) VALUES (?, ?, ?::jsonb) RETURNING id
            """, Long.class, themeId, backgroundColor, write(pages));
        return id == null ? 0 : id;
    }

    /**
     * Keeps the last {@link #KEPT} copies.
     *
     * @return the thumbnails of the copies dropped, which may no longer be used anywhere
     */
    Set<UUID> prune()
    {
        List<Snapshot> dropped = jdbc.query("""
            SELECT id, applied_at, theme_id, background_color, pages::text FROM theme_snapshot
            WHERE id NOT IN (SELECT id FROM theme_snapshot ORDER BY id DESC LIMIT ?)
            """, (rs, row) -> new Snapshot(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
                rs.getString(4), parse(rs.getString(5))), KEPT);
        Set<UUID> thumbnails = new HashSet<>();
        for (Snapshot snapshot : dropped)
        {
            thumbnails.addAll(thumbnails(snapshot));
            delete(snapshot.id());
        }
        return thumbnails;
    }

    Optional<Snapshot> latest()
    {
        return jdbc.query("""
            SELECT id, applied_at, theme_id, background_color, pages::text FROM theme_snapshot
            ORDER BY id DESC LIMIT 1
            """, (rs, row) -> new Snapshot(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
                rs.getString(4), parse(rs.getString(5))))
            .stream().findFirst();
    }

    void delete(long id)
    {
        jdbc.update("DELETE FROM theme_snapshot WHERE id = ?", id);
    }

    /** Media referenced by a kept copy (they must not be deleted while it can still be restored). */
    boolean references(UUID mediaId)
    {
        Boolean used = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM theme_snapshot s
                           WHERE jsonb_path_exists(s.pages, '$.** ? (@ == $id)', jsonb_build_object('id', ?::text)))
            """, Boolean.class, mediaId.toString());
        return Boolean.TRUE.equals(used);
    }

    static Set<UUID> thumbnails(Snapshot snapshot)
    {
        Set<UUID> ids = new HashSet<>();
        for (SnapshotPage page : snapshot.pages())
        {
            if (page.thumbnailMediaId() != null)
            {
                ids.add(page.thumbnailMediaId());
            }
        }
        return ids;
    }

    private String write(List<SnapshotPage> pages)
    {
        try
        {
            return objectMapper.writeValueAsString(pages);
        }
        catch (JsonProcessingException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }

    private List<SnapshotPage> parse(String json)
    {
        try
        {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, SnapshotPage.class));
        }
        catch (JsonProcessingException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }
}
