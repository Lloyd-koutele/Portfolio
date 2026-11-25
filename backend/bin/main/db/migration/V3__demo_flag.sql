-- Demo content is created once; deleting every page afterwards must not bring it back.
ALTER TABLE site_settings ADD COLUMN demo_seeded boolean NOT NULL DEFAULT false;
