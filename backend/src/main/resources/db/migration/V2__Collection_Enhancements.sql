-- Add table for collection exclusions (Blacklist)
CREATE TABLE IF NOT EXISTS collection_exclusions (
    collection_id INTEGER,
    image_id INTEGER,
    PRIMARY KEY (collection_id, image_id),
    FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE,
    FOREIGN KEY (image_id) REFERENCES images (id) ON DELETE CASCADE
);

-- Add flag to distinguish manual additions from smart filter results
ALTER TABLE collection_images ADD COLUMN is_manual BOOLEAN DEFAULT 0;
