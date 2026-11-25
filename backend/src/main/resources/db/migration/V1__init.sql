-- Initial schema: five tables. All timestamps are timestamptz (UTC).

CREATE TABLE admin_user (
    id            uuid         PRIMARY KEY,
    email         varchar(320) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_admin_user_email UNIQUE (email)
);

CREATE TABLE media (
    id            uuid         PRIMARY KEY,
    stored_name   varchar(100) NOT NULL,
    original_name varchar(255) NOT NULL,
    mime_type     varchar(50)  NOT NULL,
    size_bytes    bigint       NOT NULL CHECK (size_bytes >= 0),
    width         integer      NOT NULL CHECK (width > 0),
    height        integer      NOT NULL CHECK (height > 0),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_media_stored_name UNIQUE (stored_name)
);

CREATE TABLE site_settings (
    id                  smallint     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    title               varchar(120) NOT NULL,
    page_width          integer      NOT NULL CHECK (page_width BETWEEN 100 AND 4000),
    page_height         integer      NOT NULL CHECK (page_height BETWEEN 100 AND 4000),
    background_color    varchar(9)   NOT NULL,
    -- Extension to the spec: blurred backdrop photo behind the book (see README, Decisions).
    background_media_id uuid         REFERENCES media (id) ON DELETE SET NULL,
    ip_mode             varchar(10)  NOT NULL CHECK (ip_mode IN ('FULL', 'MASKED', 'HASHED')),
    privacy_text        text         NOT NULL DEFAULT '',
    updated_at          timestamptz  NOT NULL DEFAULT now()
);

INSERT INTO site_settings (id, title, page_width, page_height, background_color, ip_mode)
VALUES (1, 'Portfolio', 800, 1000, '#1f1a17', 'MASKED');

CREATE TABLE page (
    id                 uuid         PRIMARY KEY,
    slug               varchar(120) NOT NULL,
    title              varchar(200) NOT NULL,
    position           integer      NOT NULL CHECK (position >= 0),
    status             varchar(10)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    content            jsonb        NOT NULL,
    thumbnail_media_id uuid         REFERENCES media (id) ON DELETE SET NULL,
    version            integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_page_slug UNIQUE (slug),
    CONSTRAINT ck_page_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE INDEX idx_page_position ON page (position);
CREATE INDEX idx_page_status_position ON page (status, position);

CREATE TABLE visit (
    id          bigserial     PRIMARY KEY,
    occurred_at timestamptz   NOT NULL DEFAULT now(),
    ip_display  varchar(64)   NOT NULL,
    ip_hash     char(64)      NOT NULL,
    user_agent  text,
    device_type varchar(10)   NOT NULL CHECK (device_type IN ('DESKTOP', 'MOBILE', 'TABLET', 'BOT')),
    is_bot      boolean       NOT NULL DEFAULT false,
    referer     varchar(2048),
    page_id     uuid          REFERENCES page (id) ON DELETE SET NULL,
    page_slug   varchar(120)  NOT NULL,
    session_id  uuid          NOT NULL
);

CREATE INDEX idx_visit_occurred_at ON visit (occurred_at);
CREATE INDEX idx_visit_page_occurred_at ON visit (page_id, occurred_at);
CREATE INDEX idx_visit_ip_hash_occurred_at ON visit (ip_hash, occurred_at);
