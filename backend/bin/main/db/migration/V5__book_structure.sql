-- Book structure: Cover, Table of contents, Summary of projects, then projects (each with any number of sheets),
-- then Contact. Fixed pages exist once; every other page is a sheet of a project.

CREATE TABLE project (
    id         uuid         PRIMARY KEY,
    title      varchar(200) NOT NULL,
    position   integer      NOT NULL CHECK (position >= 0),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_position ON project (position);

ALTER TABLE page ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'PROJECT_SHEET'
    CHECK (kind IN ('COVER', 'TABLE_OF_CONTENTS', 'SUMMARY', 'PROJECT_SHEET', 'CONTACT'));
ALTER TABLE page ADD COLUMN project_id uuid REFERENCES project (id) ON DELETE CASCADE;

-- Pages of the demo content become the fixed pages.
UPDATE page SET kind = 'COVER' WHERE slug = 'couverture';
UPDATE page SET kind = 'TABLE_OF_CONTENTS' WHERE slug = 'sommaire';
UPDATE page SET kind = 'SUMMARY' WHERE slug = 'projets';
UPDATE page SET kind = 'CONTACT' WHERE slug = 'contact';

-- Every other existing page becomes a project with a single sheet, in the current order.
INSERT INTO project (id, title, position, created_at, updated_at)
SELECT id, title, row_number() OVER (ORDER BY position) - 1, created_at, now()
FROM page
WHERE kind = 'PROJECT_SHEET';

UPDATE page SET project_id = id, position = 0 WHERE kind = 'PROJECT_SHEET';

ALTER TABLE page ADD CONSTRAINT ck_page_project CHECK ((kind = 'PROJECT_SHEET') = (project_id IS NOT NULL));
CREATE UNIQUE INDEX uk_page_fixed_kind ON page (kind) WHERE kind <> 'PROJECT_SHEET';
CREATE INDEX idx_page_project_position ON page (project_id, position);
