-- The CV offered for download (one at most) and the log of its downloads.

CREATE TABLE cv_document (
    id          smallint     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    stored_name varchar(64)  NOT NULL,
    file_name   varchar(160) NOT NULL,
    size_bytes  bigint       NOT NULL CHECK (size_bytes > 0),
    uploaded_at timestamptz  NOT NULL
);

-- One row per download, IP stored like visits (according to the site's IP mode); purged after 13 months.
CREATE TABLE download (
    id          bigserial     PRIMARY KEY,
    occurred_at timestamptz   NOT NULL DEFAULT now(),
    file        varchar(10)   NOT NULL CHECK (file IN ('CV')),
    source      varchar(10)   NOT NULL CHECK (source IN ('BOOK', 'HOME', 'DIRECT')),
    file_name   varchar(160)  NOT NULL,
    ip_display  varchar(64)   NOT NULL,
    ip_hash     char(64)      NOT NULL,
    user_agent  text,
    device_type varchar(10)   NOT NULL CHECK (device_type IN ('DESKTOP', 'MOBILE', 'TABLET', 'BOT')),
    is_bot      boolean       NOT NULL DEFAULT false,
    referer     varchar(2048)
);
CREATE INDEX idx_download_occurred_at ON download (occurred_at);
