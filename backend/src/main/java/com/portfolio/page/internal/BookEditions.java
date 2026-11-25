package com.portfolio.page.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editions of the book. Publishing stores every page of the working copy, in book order, as one JSON document;
 * the public site always serves the latest edition. Built in SQL so the "up to date" check compares JSON
 * semantically (jsonb equality).
 */
@Component
class BookEditions
{
    /** Pages of the working copy in book order: cover, table of contents, projects with their sheets, contact. */
    static final String WORKING_BOOK_SQL = """
        SELECT coalesce(jsonb_agg(jsonb_build_object(
                   'id', p.id, 'slug', p.slug, 'title', p.title, 'kind', p.kind,
                   'projectId', p.project_id, 'projectTitle', pr.title, 'content', p.content)
               ORDER BY CASE p.kind WHEN 'COVER' THEN 0 WHEN 'TABLE_OF_CONTENTS' THEN 1
                                    WHEN 'PROJECT_SHEET' THEN 2 ELSE 3 END,
                        pr.position NULLS FIRST, p.position, p.id), '[]'::jsonb)
        FROM page p
        LEFT JOIN project pr ON pr.id = p.project_id
        """;

    private static final int KEPT_EDITIONS = 20;

    /** One page of an edition. */
    record EditionPage(UUID id, String slug, String title, String kind, UUID projectId, String projectTitle,
        JsonNode content)
    {
    }

    record Edition(long id, Instant publishedAt, List<EditionPage> pages)
    {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    BookEditions(JdbcTemplate jdbc, ObjectMapper objectMapper)
    {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Publishes the whole working copy as the new edition; keeps the last 20 editions. */
    @Transactional
    Instant publish()
    {
        Timestamp now = jdbc.queryForObject("""
            INSERT INTO book_edition (published_at, pages)
            SELECT now(), (%s)
            RETURNING published_at
            """.formatted(WORKING_BOOK_SQL), Timestamp.class);
        jdbc.update("DELETE FROM book_edition WHERE id NOT IN (SELECT id FROM book_edition ORDER BY id DESC LIMIT ?)",
            KEPT_EDITIONS);
        return now == null ? Instant.now() : now.toInstant();
    }

    @Transactional(readOnly = true)
    Optional<Edition> latest()
    {
        return jdbc.query("SELECT id, published_at, pages::text FROM book_edition ORDER BY id DESC LIMIT 1",
            (rs, row) -> new Edition(rs.getLong(1), rs.getTimestamp(2).toInstant(), parse(rs.getString(3))))
            .stream().findFirst();
    }

    /** True when the public site already shows exactly the working copy. */
    @Transactional(readOnly = true)
    boolean upToDate()
    {
        Boolean same = jdbc.queryForObject("""
            SELECT coalesce((SELECT pages FROM book_edition ORDER BY id DESC LIMIT 1) = (%s), false)
            """.formatted(WORKING_BOOK_SQL), Boolean.class);
        return Boolean.TRUE.equals(same);
    }

    /** Media referenced by the edition readers currently see (they must not be deleted). */
    boolean latestReferences(UUID mediaId)
    {
        Boolean used = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM (SELECT pages FROM book_edition ORDER BY id DESC LIMIT 1) e
                           WHERE jsonb_path_exists(e.pages, '$.** ? (@ == $id)', jsonb_build_object('id', ?::text)))
            """, Boolean.class, mediaId.toString());
        return Boolean.TRUE.equals(used);
    }

    private List<EditionPage> parse(String json)
    {
        try
        {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, EditionPage.class));
        }
        catch (JsonProcessingException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }
}
