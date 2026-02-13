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
    rating
        INTEGER
        DEFAULT
            0,
    last_scanned
        INTEGER,
    is_missing
        BOOLEAN
        DEFAULT
            0
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
            ) ON DELETE CASCADE
);

-- FTS5 Virtual Table for high-performance search
-- We store a concatenated "blob" of all metadata here (e.g., "model:flux steps:20")
CREATE
    VIRTUAL TABLE IF NOT EXISTS metadata_fts USING fts5
(
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
            ) ON DELETE CASCADE
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

-- Collections table
CREATE TABLE IF NOT EXISTS collections
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT UNIQUE NOT NULL,
    created_at   INTEGER,
    is_smart     BOOLEAN DEFAULT FALSE,
    filters_json TEXT
);

-- Collection Images table
CREATE TABLE IF NOT EXISTS collection_images
(
    collection_id INTEGER,
    image_id      INTEGER,
    added_at      INTEGER,
    is_manual     BOOLEAN DEFAULT 0,
    PRIMARY KEY (collection_id, image_id),
    FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE,
    FOREIGN KEY (image_id) REFERENCES images (id) ON DELETE CASCADE
);

-- Add table for collection exclusions (Blacklist)
CREATE TABLE IF NOT EXISTS collection_exclusions (
    collection_id INTEGER,
    image_id INTEGER,
    PRIMARY KEY (collection_id, image_id),
    FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE,
    FOREIGN KEY (image_id) REFERENCES images (id) ON DELETE CASCADE
);

-- Indexes for standard lookup
CREATE INDEX IF NOT EXISTS idx_file_path ON images (file_path);
CREATE INDEX IF NOT EXISTS idx_file_hash ON images (file_hash);
CREATE INDEX IF NOT EXISTS idx_tags_text ON image_tags (tag);
CREATE INDEX IF NOT EXISTS idx_images_is_missing ON images (is_missing);
