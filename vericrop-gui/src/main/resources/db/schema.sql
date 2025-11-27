-- VeriCrop Database Schema Initialization
-- This file provides idempotent schema creation for application startup.
-- Uses CREATE TABLE IF NOT EXISTS for safety when re-running.
-- All statements are PostgreSQL-compatible.

-- ============================================================================
-- Pre-requisites: Extensions and helper functions
-- ============================================================================

-- Ensure pgcrypto extension is available for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Function to update updated_at timestamp automatically (idempotent)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Core Tables
-- ============================================================================

-- Batches table: stores metadata for each batch in the supply chain
CREATE TABLE IF NOT EXISTS batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    farmer VARCHAR(255) NOT NULL,
    product_type VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    quality_score NUMERIC(5, 4),
    quality_label VARCHAR(50),
    data_hash VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    metadata JSONB,
    image_path VARCHAR(512),
    qr_code_path VARCHAR(512),
    prime_rate NUMERIC(5, 4),
    rejection_rate NUMERIC(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Batch quality history table: tracks quality changes over time
CREATE TABLE IF NOT EXISTS batch_quality_history (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(255) NOT NULL,
    quality_score NUMERIC(5, 4) NOT NULL,
    quality_label VARCHAR(50),
    location VARCHAR(255),
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    measured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Users table: stores user authentication and profile information
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    last_login TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Shipments table: stores shipment tracking information
CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL UNIQUE,
    batch_id VARCHAR(255) NOT NULL,
    origin VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    carrier VARCHAR(255),
    tracking_number VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    estimated_delivery TIMESTAMP,
    actual_delivery TIMESTAMP,
    blockchain_hash VARCHAR(255),
    ledger_record_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Shipment events table: tracks state changes and location updates
CREATE TABLE IF NOT EXISTS shipment_events (
    id BIGSERIAL PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    location VARCHAR(255),
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    notes TEXT,
    recorded_by VARCHAR(255),
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Messages table: stores user-to-user messages
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by_sender BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by_recipient BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Participants table: tracks connected GUI instances for contact discovery
CREATE TABLE IF NOT EXISTS participants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    instance_id VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    connection_info JSONB,
    gui_version VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Simulations table: stores simulation metadata with supplier/consumer relationships
CREATE TABLE IF NOT EXISTS simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    owner_user_id BIGINT NOT NULL,
    supplier_user_id BIGINT NOT NULL,
    consumer_user_id BIGINT NOT NULL,
    simulation_token VARCHAR(64) NOT NULL UNIQUE,
    meta JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Simulation batches table: stores batch data created during simulation runtime
CREATE TABLE IF NOT EXISTS simulation_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    simulation_id UUID NOT NULL,
    batch_index INTEGER NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    quality_score NUMERIC(5, 4),
    temperature NUMERIC(5, 2),
    humidity NUMERIC(5, 2),
    current_location VARCHAR(255),
    progress NUMERIC(5, 2) DEFAULT 0,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Indexes (use IF NOT EXISTS for idempotency)
-- ============================================================================

-- Batches indexes
CREATE INDEX IF NOT EXISTS idx_batches_batch_id ON batches(batch_id);
CREATE INDEX IF NOT EXISTS idx_batches_farmer ON batches(farmer);
CREATE INDEX IF NOT EXISTS idx_batches_status ON batches(status);
CREATE INDEX IF NOT EXISTS idx_batches_timestamp ON batches(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_batches_created_at ON batches(created_at DESC);

-- Batch quality history indexes
CREATE INDEX IF NOT EXISTS idx_quality_history_batch_id ON batch_quality_history(batch_id);
CREATE INDEX IF NOT EXISTS idx_quality_history_measured_at ON batch_quality_history(measured_at DESC);

-- Users indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Shipments indexes
CREATE INDEX IF NOT EXISTS idx_shipments_shipment_id ON shipments(shipment_id);
CREATE INDEX IF NOT EXISTS idx_shipments_batch_id ON shipments(batch_id);
CREATE INDEX IF NOT EXISTS idx_shipments_status ON shipments(status);
CREATE INDEX IF NOT EXISTS idx_shipments_created_at ON shipments(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shipments_tracking_number ON shipments(tracking_number);

-- Shipment events indexes
CREATE INDEX IF NOT EXISTS idx_shipment_events_shipment_id ON shipment_events(shipment_id);
CREATE INDEX IF NOT EXISTS idx_shipment_events_event_type ON shipment_events(event_type);
CREATE INDEX IF NOT EXISTS idx_shipment_events_recorded_at ON shipment_events(recorded_at DESC);

-- Messages indexes
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_id ON messages(recipient_id);
CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_is_read ON messages(is_read);

-- Participants indexes
CREATE INDEX IF NOT EXISTS idx_participants_user_id ON participants(user_id);
CREATE INDEX IF NOT EXISTS idx_participants_instance_id ON participants(instance_id);
CREATE INDEX IF NOT EXISTS idx_participants_status ON participants(status);
CREATE INDEX IF NOT EXISTS idx_participants_last_seen ON participants(last_seen DESC);

-- Simulations indexes
CREATE INDEX IF NOT EXISTS idx_simulations_owner_user_id ON simulations(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_simulations_supplier_user_id ON simulations(supplier_user_id);
CREATE INDEX IF NOT EXISTS idx_simulations_consumer_user_id ON simulations(consumer_user_id);
CREATE INDEX IF NOT EXISTS idx_simulations_status ON simulations(status);
CREATE INDEX IF NOT EXISTS idx_simulations_token ON simulations(simulation_token);
CREATE INDEX IF NOT EXISTS idx_simulations_created_at ON simulations(created_at DESC);

-- Simulation batches indexes
CREATE INDEX IF NOT EXISTS idx_simulation_batches_simulation_id ON simulation_batches(simulation_id);
CREATE INDEX IF NOT EXISTS idx_simulation_batches_status ON simulation_batches(status);
CREATE INDEX IF NOT EXISTS idx_simulation_batches_batch_index ON simulation_batches(batch_index);

-- ============================================================================
-- Triggers (drop and recreate to ensure they exist)
-- ============================================================================

DROP TRIGGER IF EXISTS update_batches_updated_at ON batches;
CREATE TRIGGER update_batches_updated_at
    BEFORE UPDATE ON batches
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_shipments_updated_at ON shipments;
CREATE TRIGGER update_shipments_updated_at
    BEFORE UPDATE ON shipments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_messages_updated_at ON messages;
CREATE TRIGGER update_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_participants_updated_at ON participants;
CREATE TRIGGER update_participants_updated_at
    BEFORE UPDATE ON participants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_simulations_updated_at ON simulations;
CREATE TRIGGER update_simulations_updated_at
    BEFORE UPDATE ON simulations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_simulation_batches_updated_at ON simulation_batches;
CREATE TRIGGER update_simulation_batches_updated_at
    BEFORE UPDATE ON simulation_batches
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
