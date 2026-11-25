-- Newspaper model: the editor autosaves the working copy (`content`, `title`); readers only see the snapshot
-- taken when the page was last published.
ALTER TABLE page ADD COLUMN published_title varchar(200);
ALTER TABLE page ADD COLUMN published_content jsonb;
ALTER TABLE page ADD COLUMN published_at timestamptz;

UPDATE page
SET published_title = title, published_content = content, published_at = updated_at
WHERE status = 'PUBLISHED';

ALTER TABLE page ADD CONSTRAINT ck_page_published_snapshot
    CHECK (status = 'DRAFT' OR (published_content IS NOT NULL AND published_title IS NOT NULL));
