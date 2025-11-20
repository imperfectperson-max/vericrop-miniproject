-- VeriCrop Users Table for Authentication
-- Flyway Migration V2: User authentication and authorization

-- Drop table if exists (for clean re-creation in development)
DROP TABLE IF EXISTS users CASCADE;

-- Users table: stores user authentication and profile information
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,  -- BCrypt hashed password
    email VARCHAR(255) UNIQUE,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER, ADMIN, FARMER, SUPPLIER, CONSUMER
    status VARCHAR(50) NOT NULL DEFAULT 'active',  -- active, inactive, suspended
    last_login TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB  -- Additional flexible user data
);

-- Indexes for performance optimization
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);

-- Partial index for active users (performance optimization)
CREATE INDEX idx_users_active ON users(status) WHERE status = 'active';

-- Trigger to automatically update updated_at on users table
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE users IS 'User authentication and authorization';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password (never store plaintext)';
COMMENT ON COLUMN users.role IS 'User role for authorization (USER, ADMIN, FARMER, SUPPLIER, CONSUMER)';
COMMENT ON COLUMN users.failed_login_attempts IS 'Counter for failed login attempts (security)';
COMMENT ON COLUMN users.locked_until IS 'Account locked until this timestamp after too many failed attempts';

-- Insert default admin user (password: admin123)
-- BCrypt hash for 'admin123' with cost factor 10
INSERT INTO users (username, password_hash, email, full_name, role, status)
VALUES (
    'admin',
    '$2a$10$rBV2/eHbz9kBQzR8xC4anuBZ8Y6yAL7CJvKKkqxBvLPQHHKKjFLz2',
    'admin@vericrop.local',
    'VeriCrop Administrator',
    'ADMIN',
    'active'
);

-- Insert demo farmer user (password: farmer123)
INSERT INTO users (username, password_hash, email, full_name, role, status)
VALUES (
    'farmer',
    '$2a$10$U1pDvXD5wH5y8ZQ7n9r8O.jV3KNY8j3r9sQP.rY8K9mVN2QH5xO6.',
    'farmer@vericrop.local',
    'Demo Farmer',
    'FARMER',
    'active'
);

-- Insert demo supplier user (password: supplier123)
INSERT INTO users (username, password_hash, email, full_name, role, status)
VALUES (
    'supplier',
    '$2a$10$vWdKG6jN7xR8L4oQ3eT9juHYxC5mP8nZ2wR7QiV5bN9sY3jT6kH8K',
    'supplier@vericrop.local',
    'Demo Supplier',
    'SUPPLIER',
    'active'
);
