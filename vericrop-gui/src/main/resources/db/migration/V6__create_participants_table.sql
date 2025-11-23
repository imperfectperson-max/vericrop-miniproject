-- VeriCrop Participants Table for GUI Instance Tracking
-- Flyway Migration V6: Track connected GUI participants for contacts and messaging

-- Drop table if exists (for clean re-creation in development)
DROP TABLE IF EXISTS participants CASCADE;

-- Participants table: tracks connected GUI instances for contact discovery
CREATE TABLE participants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instance_id VARCHAR(255) NOT NULL UNIQUE,  -- Unique identifier for this GUI instance
    display_name VARCHAR(255) NOT NULL,  -- Display name for this participant
    connection_info JSONB,  -- Connection details (host, port, endpoint, etc.)
    gui_version VARCHAR(50),  -- Version of the GUI application
    status VARCHAR(50) NOT NULL DEFAULT 'active',  -- active, inactive, disconnected
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- Last activity timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB  -- Additional flexible participant data
);

-- Indexes for performance optimization
CREATE INDEX idx_participants_user_id ON participants(user_id);
CREATE INDEX idx_participants_instance_id ON participants(instance_id);
CREATE INDEX idx_participants_status ON participants(status);
CREATE INDEX idx_participants_last_seen ON participants(last_seen DESC);

-- Partial index for active participants (most common query)
CREATE INDEX idx_participants_active ON participants(status, last_seen DESC) WHERE status = 'active';

-- Trigger to automatically update updated_at on participants table
CREATE TRIGGER update_participants_updated_at
    BEFORE UPDATE ON participants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE participants IS 'Connected GUI instances for contact discovery and messaging';
COMMENT ON COLUMN participants.instance_id IS 'Unique identifier for this GUI instance (e.g., UUID)';
COMMENT ON COLUMN participants.connection_info IS 'JSONB with connection details like host, port, endpoint';
COMMENT ON COLUMN participants.status IS 'Current connection status: active, inactive, disconnected';
COMMENT ON COLUMN participants.last_seen IS 'Last activity timestamp for presence tracking';
