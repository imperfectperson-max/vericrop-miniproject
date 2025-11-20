-- VeriCrop Batch Metadata Database Schema
-- PostgreSQL Database Schema for Batch Tracking and Quality Management

-- Drop tables if they exist (for clean re-creation)
DROP TABLE IF EXISTS batches CASCADE;
DROP TABLE IF EXISTS batch_quality_history CASCADE;

-- Batches table: stores metadata for each batch in the supply chain
CREATE TABLE batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    farmer VARCHAR(255) NOT NULL,
    product_type VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    quality_score DECIMAL(5, 4),  -- Quality score from ML prediction (0.0 to 1.0)
    quality_label VARCHAR(50),    -- Label: fresh, good, ripe, low_quality, etc.
    data_hash VARCHAR(255),       -- Hash of quality data for integrity verification
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'created',  -- created, in_transit, delivered, etc.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Batch quality history table: tracks quality changes over time
CREATE TABLE batch_quality_history (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(255) NOT NULL REFERENCES batches(batch_id) ON DELETE CASCADE,
    quality_score DECIMAL(5, 4) NOT NULL,
    quality_label VARCHAR(50),
    location VARCHAR(255),
    temperature DECIMAL(5, 2),
    humidity DECIMAL(5, 2),
    measured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Indexes for performance optimization
CREATE INDEX idx_batches_batch_id ON batches(batch_id);
CREATE INDEX idx_batches_farmer ON batches(farmer);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_timestamp ON batches(timestamp DESC);
CREATE INDEX idx_quality_history_batch_id ON batch_quality_history(batch_id);
CREATE INDEX idx_quality_history_measured_at ON batch_quality_history(measured_at DESC);

-- Function to update updated_at timestamp automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update updated_at on batches table
CREATE TRIGGER update_batches_updated_at
    BEFORE UPDATE ON batches
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for testing (optional)
-- INSERT INTO batches (batch_id, name, farmer, product_type, quantity, quality_score, quality_label, status)
-- VALUES 
--   ('BATCH_TEST_001', 'Test Batch 1', 'John Farmer', 'Apple', 100, 0.85, 'fresh', 'created'),
--   ('BATCH_TEST_002', 'Test Batch 2', 'Jane Producer', 'Orange', 150, 0.75, 'good', 'created');

COMMENT ON TABLE batches IS 'Main table for batch tracking and metadata';
COMMENT ON TABLE batch_quality_history IS 'Historical quality measurements for batches during transit';
COMMENT ON COLUMN batches.quality_score IS 'ML-predicted quality score (0.0 = poor, 1.0 = excellent)';
COMMENT ON COLUMN batches.data_hash IS 'SHA-256 hash of quality data for blockchain integration';
