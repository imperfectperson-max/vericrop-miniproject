-- VeriCrop Messages Table for User Messaging System
-- Flyway Migration V4: User-to-user messaging functionality

-- Drop table if exists (for clean re-creation in development)
DROP TABLE IF EXISTS messages CASCADE;

-- Messages table: stores user-to-user messages
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,  -- NULL if unread, timestamp when first read
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by_sender BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by_recipient BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,  -- Additional flexible message data
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_recipient_id ON messages(recipient_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at DESC);
CREATE INDEX idx_messages_is_read ON messages(is_read);

-- Composite index for inbox queries (unread messages for a recipient)
CREATE INDEX idx_messages_recipient_unread ON messages(recipient_id, is_read, sent_at DESC);

-- Composite index for sent messages (messages sent by a user)
CREATE INDEX idx_messages_sender_sent ON messages(sender_id, sent_at DESC);

-- Trigger to automatically update updated_at on messages table
CREATE TRIGGER update_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE messages IS 'User-to-user messaging system for VeriCrop platform';
COMMENT ON COLUMN messages.sender_id IS 'User ID of the message sender';
COMMENT ON COLUMN messages.recipient_id IS 'User ID of the message recipient';
COMMENT ON COLUMN messages.is_read IS 'Whether the message has been read by the recipient';
COMMENT ON COLUMN messages.read_at IS 'Timestamp when the message was first read';
COMMENT ON COLUMN messages.deleted_by_sender IS 'Soft delete flag for sender (message hidden from sent items)';
COMMENT ON COLUMN messages.deleted_by_recipient IS 'Soft delete flag for recipient (message hidden from inbox)';
