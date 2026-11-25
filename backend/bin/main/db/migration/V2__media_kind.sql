-- Page thumbnails are stored as media but hidden from the media library.
ALTER TABLE media ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'UPLOAD' CHECK (kind IN ('UPLOAD', 'THUMBNAIL'));
CREATE INDEX idx_media_kind_created_at ON media (kind, created_at DESC);
