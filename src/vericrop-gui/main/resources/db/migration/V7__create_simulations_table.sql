-- VeriCrop Simulations Table for Multi-User/Multi-Device Simulation Support
-- Flyway Migration V7: Simulation tracking with supplier/consumer selection and simulation_token

-- Ensures gen_random_uuid() is available for UUID primary keys
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Drop tables if they exist (for clean re-creation in development)
DROP TABLE IF EXISTS simulation_batches CASCADE;
DROP TABLE IF EXISTS simulations CASCADE;

-- Simulations table: stores simulation metadata with supplier/consumer relationships
CREATE TABLE simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'created',  -- created, running, completed, failed, stopped
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    supplier_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consumer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    simulation_token VARCHAR(64) NOT NULL UNIQUE,  -- Secure 64-character hex token for multi-device access
    meta JSONB,  -- Additional flexible simulation metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Simulation batches table: stores batch data created during simulation runtime
CREATE TABLE simulation_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id UUID NOT NULL REFERENCES simulations(id) ON DELETE CASCADE,
    batch_index INTEGER NOT NULL,  -- Sequential index of batch within simulation
    quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'created',  -- created, in_transit, delivered, failed
    quality_score NUMERIC(5, 4),  -- Quality score from ML prediction (0.0 to 1.0)
    temperature NUMERIC(5, 2),  -- Current/last temperature reading
    humidity NUMERIC(5, 2),  -- Current/last humidity reading
    current_location VARCHAR(255),  -- Current location name
    progress NUMERIC(5, 2) DEFAULT 0,  -- Progress percentage (0-100)
    metadata JSONB,  -- Additional flexible batch data
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
CREATE INDEX idx_simulations_owner_user_id ON simulations(owner_user_id);
CREATE INDEX idx_simulations_supplier_user_id ON simulations(supplier_user_id);
CREATE INDEX idx_simulations_consumer_user_id ON simulations(consumer_user_id);
CREATE INDEX idx_simulations_status ON simulations(status);
CREATE INDEX idx_simulations_token ON simulations(simulation_token);
CREATE INDEX idx_simulations_created_at ON simulations(created_at DESC);

CREATE INDEX idx_simulation_batches_simulation_id ON simulation_batches(simulation_id);
CREATE INDEX idx_simulation_batches_status ON simulation_batches(status);
CREATE INDEX idx_simulation_batches_batch_index ON simulation_batches(batch_index);

-- Partial index for active simulations (performance optimization)
CREATE INDEX idx_simulations_active ON simulations(status) WHERE status IN ('created', 'running');

-- Trigger to automatically update updated_at on simulations table
CREATE TRIGGER update_simulations_updated_at
    BEFORE UPDATE ON simulations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger to automatically update updated_at on simulation_batches table
CREATE TRIGGER update_simulation_batches_updated_at
    BEFORE UPDATE ON simulation_batches
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE simulations IS 'Simulation sessions with supplier/consumer relationships for access control';
COMMENT ON COLUMN simulations.simulation_token IS 'Secure token for multi-device/multi-instance access to the same simulation';
COMMENT ON COLUMN simulations.owner_user_id IS 'User who created/owns the simulation';
COMMENT ON COLUMN simulations.supplier_user_id IS 'Supplier user who can view this simulation';
COMMENT ON COLUMN simulations.consumer_user_id IS 'Consumer user who can view this simulation';
COMMENT ON TABLE simulation_batches IS 'Batch records created during simulation for report generation';
COMMENT ON COLUMN simulation_batches.batch_index IS 'Sequential index of batch within the simulation';
