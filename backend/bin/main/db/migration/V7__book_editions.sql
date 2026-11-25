-- The public site is updated as a whole: the "Publier le site" button stores an edition of the book (every page in
-- book order, with its title, project and content). Readers only see the latest edition; everything else in the
-- administration is the working copy.

CREATE TABLE book_edition (
    id           bigserial   PRIMARY KEY,
    published_at timestamptz NOT NULL DEFAULT now(),
    pages        jsonb       NOT NULL
);

-- First edition = what readers currently see (the pages published one by one until now).
INSERT INTO book_edition (published_at, pages)
SELECT coalesce(max(p.published_at), now()),
       coalesce(jsonb_agg(jsonb_build_object(
                    'id', p.id, 'slug', p.slug, 'title', p.published_title, 'kind', p.kind,
                    'projectId', p.project_id, 'projectTitle', pr.title, 'content', p.published_content)
                ORDER BY CASE p.kind WHEN 'COVER' THEN 0 WHEN 'TABLE_OF_CONTENTS' THEN 1
                                     WHEN 'PROJECT_SHEET' THEN 2 ELSE 3 END,
                         pr.position NULLS FIRST, p.position, p.id), '[]'::jsonb)
FROM page p
LEFT JOIN project pr ON pr.id = p.project_id
WHERE p.status = 'PUBLISHED';

-- Per-page publication is gone.
ALTER TABLE page DROP CONSTRAINT ck_page_published_snapshot;
DROP INDEX IF EXISTS idx_page_status_position;
ALTER TABLE page DROP COLUMN status;
ALTER TABLE page DROP COLUMN published_title;
ALTER TABLE page DROP COLUMN published_content;
ALTER TABLE page DROP COLUMN published_at;
