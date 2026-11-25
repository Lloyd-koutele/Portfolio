-- The "Summary of projects" page is no longer part of the book: Cover, Table of contents, projects, Contact.
-- Its visits stay (page_id becomes NULL).
DELETE FROM page WHERE kind = 'SUMMARY';

ALTER TABLE page DROP CONSTRAINT page_kind_check;
ALTER TABLE page ADD CONSTRAINT page_kind_check
    CHECK (kind IN ('COVER', 'TABLE_OF_CONTENTS', 'PROJECT_SHEET', 'CONTACT'));
