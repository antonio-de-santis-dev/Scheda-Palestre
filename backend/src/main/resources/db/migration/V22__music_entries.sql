CREATE TABLE music_entries (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title varchar(120) NOT NULL CHECK (length(btrim(title)) > 0),
    kind varchar(8) NOT NULL CHECK (kind IN ('VIDEO', 'PLAYLIST')),
    youtube_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ux_music_entries_user_content UNIQUE (user_id, kind, youtube_id)
);
CREATE INDEX ix_music_entries_user_created ON music_entries (user_id, created_at, id);
