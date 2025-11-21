package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.util.ValidationUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory fallback authentication service for local testing.
 * Stores users in memory with simple validation.
 */
public class FallbackAuthService implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(FallbackAuthService.class);
    
    private final Map<String, UserInfo> users = new HashMap<>();
    private String currentUser;
    private UserInfo currentUserInfo;
    
    public FallbackAuthService() {
        logger.info("✅ FallbackAuthService initialized (in-memory mode)");
        // Pre-populate with test users for each role
        users.put("farmer", new UserInfo("farmer", "password", "farmer@vericrop.local", "Test Farmer", "farmer"));
        users.put("consumer", new UserInfo("consumer", "password", "consumer@vericrop.local", "Test Consumer", "consumer"));
        users.put("supplier", new UserInfo("supplier", "password", "supplier@vericrop.local", "Test Supplier", "supplier"));
        users.put("admin", new UserInfo("admin", "password", "admin@vericrop.local", "Test Admin", "admin"));
        logger.info("Pre-populated test users: farmer, consumer, supplier, admin (password: 'password')");
    }
    
    @Override
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Login failed: empty username");
            return false;
        }
        
        if (password == null || password.trim().isEmpty()) {
            logger.warn("Login failed: empty password");
            return false;
        }
        
        UserInfo user = users.get(username.toLowerCase());
        if (user != null && user.password.equals(password)) {
            this.currentUser = username.toLowerCase();
            this.currentUserInfo = user;
            logger.info("✅ User logged in (fallback mode): {} (role: {})", currentUser, user.role);
            return true;
        }
        
        logger.warn("Login failed: invalid credentials for user: {}", username);
        return false;
    }
    
    @Override
    public boolean register(String username, String password, String email, String fullName, String role) {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Registration failed: empty username");
            return false;
        }
        
        if (!ValidationUtil.isValidPassword(password, 6)) {
            logger.warn("Registration failed: password must be at least 6 characters");
            return false;
        }
        
        if (!ValidationUtil.isValidEmail(email)) {
            logger.warn("Registration failed: invalid email");
            return false;
        }
        
        String usernameLower = username.toLowerCase();
        if (users.containsKey(usernameLower)) {
            logger.warn("Registration failed: username already exists: {}", username);
            return false;
        }
        
        // Validate role
        String normalizedRole = role != null ? role.toLowerCase() : "consumer";
        if (!normalizedRole.equals("farmer") && !normalizedRole.equals("consumer") && 
            !normalizedRole.equals("supplier") && !normalizedRole.equals("admin")) {
            normalizedRole = "consumer";
        }
        
        UserInfo newUser = new UserInfo(usernameLower, password, email, fullName, normalizedRole);
        users.put(usernameLower, newUser);
        
        logger.info("✅ User registered (fallback mode): {} (role: {})", usernameLower, normalizedRole);
        return true;
    }
    
    @Override
    public void logout() {
        if (currentUser != null) {
            logger.info("User logged out (fallback mode): {}", currentUser);
        }
        this.currentUser = null;
        this.currentUserInfo = null;
    }
    
    @Override
    public boolean isAuthenticated() {
        return currentUser != null && currentUserInfo != null;
    }
    
    @Override
    public String getCurrentUser() {
        return currentUser;
    }
    
    @Override
    public String getCurrentRole() {
        return currentUserInfo != null ? currentUserInfo.role : null;
    }
    
    @Override
    public String getCurrentEmail() {
        return currentUserInfo != null ? currentUserInfo.email : null;
    }
    
    @Override
    public String getCurrentFullName() {
        return currentUserInfo != null ? currentUserInfo.fullName : null;
    }
    
    private static class UserInfo {
        final String username;
        final String password;
        final String email;
        final String fullName;
        final String role;
        
        UserInfo(String username, String password, String email, String fullName, String role) {
            this.username = username;
            this.password = password;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
        }
    }
}
