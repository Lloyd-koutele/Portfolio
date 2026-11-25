-- Applying a theme to the whole book first keeps a copy of what it replaces (every page's content and thumbnail,
-- and the colour around the book), so "Revenir en arrière" can put it back. The last 10 copies are kept.

CREATE TABLE theme_snapshot (
    id               bigserial    PRIMARY KEY,
    applied_at       timestamptz  NOT NULL DEFAULT now(),
    theme_id         varchar(40)  NOT NULL,
    background_color varchar(7)   NOT NULL CHECK (background_color ~ '^#[0-9a-fA-F]{6}$'),
    -- [{ "id", "content", "thumbnailMediaId", "versionAfter" }]: the page as it was, and its version once themed
    pages            jsonb        NOT NULL
);
