-- Add is_missing column to images table to support lazy reconciliation
ALTER TABLE images ADD COLUMN is_missing BOOLEAN DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_images_is_missing ON images (is_missing);
