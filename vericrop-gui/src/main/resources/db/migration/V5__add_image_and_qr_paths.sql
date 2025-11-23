-- VeriCrop Batch Metadata Enhancement
-- Flyway Migration V5: Add image and QR code paths to batches table
-- Adds support for storing file paths for batch images and generated QR codes

-- Add image_path column to store the path to batch image files
ALTER TABLE batches 
ADD COLUMN image_path VARCHAR(512);

-- Add qr_code_path column to store the path to generated QR code files
ALTER TABLE batches 
ADD COLUMN qr_code_path VARCHAR(512);

-- Add prime_rate and rejection_rate columns for batch quality metrics
ALTER TABLE batches 
ADD COLUMN prime_rate NUMERIC(5, 4);

ALTER TABLE batches 
ADD COLUMN rejection_rate NUMERIC(5, 4);

-- Add indexes for new columns to improve query performance
CREATE INDEX idx_batches_image_path ON batches(image_path) WHERE image_path IS NOT NULL;
CREATE INDEX idx_batches_qr_code_path ON batches(qr_code_path) WHERE qr_code_path IS NOT NULL;

-- Comments for documentation
COMMENT ON COLUMN batches.image_path IS 'Path to the batch image file used for quality prediction';
COMMENT ON COLUMN batches.qr_code_path IS 'Path to the generated QR code file for this batch';
COMMENT ON COLUMN batches.prime_rate IS 'Percentage of items classified as prime quality (0.0 to 1.0)';
COMMENT ON COLUMN batches.rejection_rate IS 'Percentage of items rejected due to low quality (0.0 to 1.0)';
