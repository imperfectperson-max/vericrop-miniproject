-- VeriCrop Shipments Table for Supply Chain Tracking
-- Flyway Migration V3: Shipment tracking and blockchain integration

-- Drop tables if they exist (for clean re-creation in development)
DROP TABLE IF EXISTS shipment_events CASCADE;
DROP TABLE IF EXISTS shipments CASCADE;

-- Shipments table: stores shipment tracking information
CREATE TABLE shipments (
    id BIGSERIAL PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL UNIQUE,
    batch_id VARCHAR(255) NOT NULL REFERENCES batches(batch_id) ON DELETE CASCADE,
    origin VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    carrier VARCHAR(255),
    tracking_number VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'created',  -- created, in_transit, delivered, cancelled
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    estimated_delivery TIMESTAMP,
    actual_delivery TIMESTAMP,
    blockchain_hash VARCHAR(255),  -- Hash of the blockchain block for this shipment
    ledger_record_id VARCHAR(255),  -- Reference to JSONL ledger record
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB  -- Additional flexible shipment data
);

-- Shipment events table: tracks state changes and location updates
CREATE TABLE shipment_events (
    id BIGSERIAL PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL REFERENCES shipments(shipment_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,  -- status_change, location_update, temperature_alert, etc.
    location VARCHAR(255),
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    notes TEXT,
    recorded_by VARCHAR(255),
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB  -- Additional flexible event data
);

-- Indexes for performance optimization
CREATE INDEX idx_shipments_shipment_id ON shipments(shipment_id);
CREATE INDEX idx_shipments_batch_id ON shipments(batch_id);
CREATE INDEX idx_shipments_status ON shipments(status);
CREATE INDEX idx_shipments_created_at ON shipments(created_at DESC);
CREATE INDEX idx_shipments_tracking_number ON shipments(tracking_number);

CREATE INDEX idx_shipment_events_shipment_id ON shipment_events(shipment_id);
CREATE INDEX idx_shipment_events_event_type ON shipment_events(event_type);
CREATE INDEX idx_shipment_events_recorded_at ON shipment_events(recorded_at DESC);

-- Partial index for active shipments (performance optimization)
CREATE INDEX idx_shipments_active ON shipments(status) WHERE status IN ('created', 'in_transit');

-- Trigger to automatically update updated_at on shipments table
CREATE TRIGGER update_shipments_updated_at
    BEFORE UPDATE ON shipments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE shipments IS 'Shipment tracking with blockchain integration';
COMMENT ON TABLE shipment_events IS 'Historical events and state changes for shipments';
COMMENT ON COLUMN shipments.blockchain_hash IS 'SHA-256 hash of blockchain block for immutability';
COMMENT ON COLUMN shipments.ledger_record_id IS 'Reference to JSONL ledger file record';
COMMENT ON COLUMN shipment_events.event_type IS 'Type of event (status_change, location_update, alert, etc.)';
