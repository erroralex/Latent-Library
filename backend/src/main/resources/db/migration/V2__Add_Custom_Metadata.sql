-- Add custom user fields to images table to support manual overrides and notes
ALTER TABLE images ADD COLUMN user_notes TEXT;
ALTER TABLE images ADD COLUMN custom_prompt TEXT;
ALTER TABLE images ADD COLUMN custom_negative_prompt TEXT;
ALTER TABLE images ADD COLUMN custom_model TEXT;

-- Recreate FTS table to include new columns for high-performance search
DROP TABLE IF EXISTS metadata_fts;
CREATE VIRTUAL TABLE metadata_fts USING fts5
(
    image_id UNINDEXED,
    global_text,
    ai_tags,
    user_notes,
    custom_prompt,
    custom_negative_prompt
);

-- Note: We will need to re-populate the FTS index after this migration.
-- The application logic handles this via the FtsService.rebuildFtsIndex() method.
