-- Table for indexed images
CREATE TABLE IF NOT EXISTS images
(
    id
    INTEGER
    PRIMARY
    KEY
    AUTOINCREMENT,
    file_path
    TEXT
    UNIQUE
    NOT
    NULL,
    file_hash
    TEXT, -- SHA-256 hash for file recovery
    is_starred
    BOOLEAN
    DEFAULT
    0,
    last_scanned
    INTEGER
);

-- Table for parsed metadata (Key-Value pairs per image)
CREATE TABLE IF NOT EXISTS image_metadata
(
    image_id
    INTEGER,
    key
    TEXT,
    value
    TEXT,
    FOREIGN
    KEY
(
    image_id
) REFERENCES images
(
    id
)
    );

-- FTS5 Virtual Table for high-performance search
-- We store a concatenated "blob" of all metadata here (e.g., "model:flux steps:20")
CREATE
VIRTUAL TABLE IF NOT EXISTS metadata_fts USING fts5(
    image_id UNINDEXED,
    global_text
);

-- Table for tags
CREATE TABLE IF NOT EXISTS image_tags
(
    image_id
    INTEGER,
    tag
    TEXT,
    FOREIGN
    KEY
(
    image_id
) REFERENCES images
(
    id
)
    );

-- Settings table
CREATE TABLE IF NOT EXISTS settings
(
    key
    TEXT
    PRIMARY
    KEY,
    value
    TEXT
);

CREATE TABLE IF NOT EXISTS pinned_folders
(
    path
    TEXT
    UNIQUE
    NOT
    NULL
);

-- Indexes for standard lookup
CREATE INDEX IF NOT EXISTS idx_file_path ON images(file_path);
CREATE INDEX IF NOT EXISTS idx_file_hash ON images(file_hash);
CREATE INDEX IF NOT EXISTS idx_tags_text ON image_tags(tag);