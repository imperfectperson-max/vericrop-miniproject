package org.vericrop.gui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data loader that seeds demo users on application startup.
 * This loader is idempotent - it only creates users if they don't already exist.
 * 
 * DISABLED BY DEFAULT for production correctness.
 * Enable only in development/testing by setting:
 *   - System property: vericrop.loadDemoUsers=true
 *   - Environment variable: VERICROP_LOAD_DEMO_USERS=true
 * 
 * Demo users created (when enabled):
 * - producer_demo (PRODUCER role) - for producer/farmer operations
 * - logistics_demo (LOGISTICS role) - for logistics/supplier operations  
 * - consumer_demo (CONSUMER role) - for consumer operations
 * - admin_demo (ADMIN role) - has access to both producer and logistics UIs
 */
@Component
@Order(1) // Run early in startup
public class DemoUserDataLoader implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DemoUserDataLoader.class);
    
    // Demo password for all demo users (will be BCrypt encoded)
    private static final String DEMO_PASSWORD = "DemoPass123!";
    
    private final DataSource dataSource;
    private final BCryptPasswordEncoder passwordEncoder;
    
    // Demo user seeding disabled by default for production correctness
    @Value("${vericrop.loadDemoUsers:false}")
    private boolean loadDemoUsersEnabled;
    
    // Demo user definitions
    private static final List<DemoUser> DEMO_USERS = List.of(
        new DemoUser("producer_demo", "producer_demo@vericrop.local", "Demo Producer", "PRODUCER"),
        new DemoUser("logistics_demo", "logistics_demo@vericrop.local", "Demo Logistics", "LOGISTICS"),
        new DemoUser("consumer_demo", "consumer_demo@vericrop.local", "Demo Consumer", "CONSUMER"),
        new DemoUser("admin_demo", "admin_demo@vericrop.local", "Demo Admin", "ADMIN")
    );
    
    @Autowired
    public DemoUserDataLoader(DataSource dataSource) {
        this.dataSource = dataSource;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    @Override
    public void run(String... args) {
        // Check if demo user loading is enabled via system property or environment variable
        boolean enabledViaSysProp = "true".equalsIgnoreCase(System.getProperty("vericrop.loadDemoUsers"));
        boolean enabledViaEnv = "true".equalsIgnoreCase(System.getenv("VERICROP_LOAD_DEMO_USERS"));
        
        if (!loadDemoUsersEnabled && !enabledViaSysProp && !enabledViaEnv) {
            // Demo user seeding disabled by default for production correctness
            logger.info("=== Demo User Data Loader: DISABLED (set vericrop.loadDemoUsers=true to enable) ===");
            return;
        }
        
        logger.warn("⚠️  Demo User Data Loader: ENABLED - This should be disabled in production");
        logger.info("=== Starting Demo User Data Loader ===");
        
        List<String> createdUsers = new ArrayList<>();
        List<String> existingUsers = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection()) {
            for (DemoUser demoUser : DEMO_USERS) {
                if (userExists(conn, demoUser.username)) {
                    existingUsers.add(demoUser.username);
                    logger.debug("Demo user '{}' already exists, skipping", demoUser.username);
                } else {
                    createUser(conn, demoUser);
                    createdUsers.add(demoUser.username);
                    logger.info("✅ Created demo user: {} (role: {})", demoUser.username, demoUser.role);
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Failed to seed demo users: {}", e.getMessage(), e);
            // Don't fail startup - demo users are optional
            return;
        }
        
        // Log summary
        if (!createdUsers.isEmpty()) {
            logger.info("=== Demo Users Created: {} ===", createdUsers);
            logger.info("Demo credentials: username/<password> where password is: {}", DEMO_PASSWORD);
        }
        if (!existingUsers.isEmpty()) {
            logger.debug("Existing demo users (skipped): {}", existingUsers);
        }
        
        logger.info("=== Demo User Data Loader Complete ===");
    }
    
    /**
     * Check if a user with the given username already exists.
     */
    private boolean userExists(Connection conn, String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * Create a new demo user with the specified details.
     * Password is BCrypt encoded before storage.
     */
    private void createUser(Connection conn, DemoUser demoUser) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, email, full_name, role, status) " +
                     "VALUES (?, ?, ?, ?, ?, 'active')";
        
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, demoUser.username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, demoUser.email);
            stmt.setString(4, demoUser.fullName);
            stmt.setString(5, demoUser.role);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Internal class to hold demo user information.
     */
    private static class DemoUser {
        final String username;
        final String email;
        final String fullName;
        final String role;
        
        DemoUser(String username, String email, String fullName, String role) {
            this.username = username;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
        }
    }
}
