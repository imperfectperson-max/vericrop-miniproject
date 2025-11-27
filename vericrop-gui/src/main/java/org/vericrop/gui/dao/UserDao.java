package org.vericrop.gui.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.vericrop.gui.models.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for User operations.
 * Handles all database operations related to users including registration and authentication.
 */
public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    // Shared BCryptPasswordEncoder instance (thread-safe and expensive to instantiate)
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    
    private final DataSource dataSource;
    
    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Create a new user with hashed password
     * @param username Username (must be unique)
     * @param password Plaintext password (will be hashed)
     * @param email Email address
     * @param fullName Full name
     * @param role User role (FARMER, CONSUMER, ADMIN, SUPPLIER)
     * @return Created User object with ID, or null if creation failed
     */
    public User createUser(String username, String password, String email, String fullName, String role) {
        String sql = "INSERT INTO users (username, password_hash, email, full_name, role, status) " +
                     "VALUES (?, ?, ?, ?, ?, 'active') RETURNING id, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String passwordHash = PASSWORD_ENCODER.encode(password);
            
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, email);
            stmt.setString(4, fullName);
            stmt.setString(5, role);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(username, email, fullName, role);
                    user.setId(rs.getLong("id"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    user.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    logger.info("✅ User created successfully: {} (role: {})", username, role);
                    return user;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create user: {}", e.getMessage());
            if (e.getMessage().contains("duplicate key")) {
                logger.error("Username or email already exists: {}", username);
            }
        }
        return null;
    }
    
    /**
     * Find user by username
     * @param username Username to search for
     * @return Optional containing the User if found
     */
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Find user by ID
     * @param id User ID
     * @return Optional containing the User if found
     */
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Find all users with a specific role
     * @param role Role to filter by (FARMER, CONSUMER, ADMIN, SUPPLIER)
     * @return List of users with the specified role
     */
    public List<User> findByRole(String role) {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE role = ? AND status = 'active' ORDER BY username";
        
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, role);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding users by role: {}", e.getMessage());
        }
        return users;
    }
    
    /**
     * Get all active users
     * @return List of all active users
     */
    public List<User> findAllActive() {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE status = 'active' ORDER BY username";
        
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all active users: {}", e.getMessage());
        }
        return users;
    }
    
    /**
     * Search for active users by username or full name (partial match).
     * Useful for contact search in messaging.
     * 
     * @param searchTerm Search term to match against username or full name
     * @return List of matching users (excluding the current user if specified)
     */
    public List<User> searchUsers(String searchTerm) {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE status = 'active' " +
                     "AND (LOWER(username) LIKE LOWER(?) OR LOWER(full_name) LIKE LOWER(?)) " +
                     "ORDER BY username";
        
        List<User> users = new ArrayList<>();
        String pattern = "%" + searchTerm + "%";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching users: {}", e.getMessage());
        }
        return users;
    }
    
    /**
     * Get all active users except the specified user (for contact list).
     * 
     * @param excludeUsername Username to exclude from results
     * @return List of all active users except the specified one
     */
    public List<User> findAllActiveExcluding(String excludeUsername) {
        String sql = "SELECT id, username, email, full_name, role, status, last_login, " +
                     "failed_login_attempts, locked_until, created_at, updated_at " +
                     "FROM users WHERE status = 'active' AND username != ? ORDER BY username";
        
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, excludeUsername);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding all active users excluding {}: {}", excludeUsername, e.getMessage());
        }
        return users;
    }
    
    /**
     * Check if a username already exists
     * @param username Username to check
     * @return true if username exists
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking username existence: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Check if an email already exists
     * @param email Email to check
     * @return true if email exists
     */
    public boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking email existence: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Update user's last login timestamp
     * @param username Username
     */
    public void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating last login: {}", e.getMessage());
        }
    }
    
    /**
     * Map a ResultSet row to a User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));
        user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        
        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toLocalDateTime());
        }
        
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        if (lockedUntil != null) {
            user.setLockedUntil(lockedUntil.toLocalDateTime());
        }
        
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        user.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        
        return user;
    }
}
