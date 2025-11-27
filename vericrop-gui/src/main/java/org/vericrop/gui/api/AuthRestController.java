package org.vericrop.gui.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.models.User;
import org.vericrop.gui.services.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST controller for authentication endpoints.
 * Provides user registration and login functionality with JWT token authentication.
 * 
 * <h2>Endpoint Summary</h2>
 * <ul>
 *   <li>POST /api/auth/register - Register a new user account</li>
 *   <li>POST /api/auth/login - Authenticate and receive JWT token</li>
 *   <li>GET /api/auth/validate - Validate a JWT token</li>
 *   <li>GET /api/auth/me - Get current user info from token</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Passwords are hashed using BCrypt with cost factor 10</li>
 *   <li>Account lockout after 5 failed login attempts (30 min lockout)</li>
 *   <li>JWT tokens expire after 24 hours (configurable)</li>
 *   <li>Error messages are sanitized to prevent information disclosure</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "User registration and login API")
public class AuthRestController {
    private static final Logger logger = LoggerFactory.getLogger(AuthRestController.class);
    
    // Security constants
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 255;
    
    // Password strength regex: at least 1 uppercase, 1 lowercase, 1 digit
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$"
    );
    
    // Email format regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    // Username format regex: alphanumeric and underscores only
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_]+$"
    );
    
    private final UserDao userDao;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    
    /**
     * Constructor with dependency injection.
     */
    public AuthRestController(UserDao userDao, JwtService jwtService, DataSource dataSource) {
        this.userDao = userDao;
        this.jwtService = jwtService;
        this.dataSource = dataSource;
        // Use explicit cost factor 10 for BCrypt (matches UserDao)
        this.passwordEncoder = new BCryptPasswordEncoder(10);
        logger.info("AuthRestController initialized");
    }
    
    // ==================== Registration Endpoint ====================
    
    /**
     * POST /api/auth/register
     * 
     * Register a new user account.
     */
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account with the provided details. " +
                      "Password must be at least 8 characters with uppercase, lowercase, and digit."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "User registered successfully",
                      "username": "john_doe",
                      "role": "PRODUCER"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Validation error or duplicate user",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "error": "Validation failed",
                      "details": "Username already exists"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Registration details",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RegisterRequest.class)))
            @RequestBody RegisterRequest request) {
        
        logger.info("Registration request received for username: {}", 
                   request != null ? request.getUsername() : "null");
        
        try {
            // Validate request
            ValidationResult validation = validateRegistration(request);
            if (!validation.isValid()) {
                logger.warn("Registration validation failed: {}", validation.getError());
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation failed", validation.getError()));
            }
            
            // Check for duplicate username
            if (userDao.usernameExists(request.getUsername())) {
                logger.warn("Registration failed: username already exists: {}", request.getUsername());
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Registration failed", "Username already exists"));
            }
            
            // Check for duplicate email
            if (userDao.emailExists(request.getEmail())) {
                logger.warn("Registration failed: email already exists: {}", request.getEmail());
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Registration failed", "Email already exists"));
            }
            
            // Normalize role
            String role = normalizeRole(request.getRole());
            
            // Create user (UserDao handles password hashing)
            User newUser = userDao.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFullName(),
                role
            );
            
            if (newUser == null) {
                logger.error("Registration failed: user creation returned null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Registration failed", 
                          "Could not create user. Please try again."));
            }
            
            // Build success response (don't return sensitive data)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("username", newUser.getUsername());
            response.put("role", newUser.getRole());
            
            logger.info("✅ User registered successfully: {} (role: {})", 
                       newUser.getUsername(), newUser.getRole());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Registration error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Registration failed", 
                      "An unexpected error occurred. Please try again."));
        }
    }
    
    // ==================== Login Endpoint ====================
    
    /**
     * POST /api/auth/login
     * 
     * Authenticate user and return JWT token.
     */
    @Operation(
        summary = "Login and get JWT token",
        description = "Authenticates user credentials and returns a JWT token for subsequent API calls. " +
                      "Include token in Authorization header as 'Bearer {token}'"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "Login successful",
                      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                      "username": "john_doe",
                      "role": "PRODUCER",
                      "expiresIn": 86400
                    }
                    """))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "error": "Authentication failed",
                      "details": "Invalid username or password"
                    }
                    """))),
        @ApiResponse(responseCode = "403", description = "Account locked",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "error": "Account locked",
                      "details": "Too many failed attempts. Try again later."
                    }
                    """)))
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Login credentials",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LoginRequest.class)))
            @RequestBody LoginRequest request) {
        
        logger.info("Login attempt for username: {}", 
                   request != null ? request.getUsername() : "null");
        
        try {
            // Validate request
            if (request == null || request.getUsername() == null || request.getPassword() == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation failed", "Username and password are required"));
            }
            
            String username = request.getUsername().trim();
            String password = request.getPassword();
            
            if (username.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation failed", "Username is required"));
            }
            
            if (password.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation failed", "Password is required"));
            }
            
            // Authenticate against database
            AuthResult authResult = authenticateUser(username, password);
            
            if (!authResult.isSuccess()) {
                int statusCode = authResult.isLocked() ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value();
                return ResponseEntity.status(statusCode)
                    .body(createErrorResponse(authResult.getError(), authResult.getDetails()));
            }
            
            // Generate JWT token
            String token = jwtService.generateToken(
                authResult.getUsername(), 
                authResult.getRole(), 
                authResult.getEmail()
            );
            
            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("token", token);
            response.put("username", authResult.getUsername());
            response.put("role", authResult.getRole());
            response.put("fullName", authResult.getFullName());
            response.put("expiresIn", 86400); // 24 hours in seconds
            
            logger.info("✅ Login successful for user: {} (role: {})", username, authResult.getRole());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Login failed", 
                      "An unexpected error occurred. Please try again."));
        }
    }
    
    // ==================== Token Validation Endpoints ====================
    
    /**
     * GET /api/auth/validate
     * 
     * Validate a JWT token.
     */
    @Operation(
        summary = "Validate JWT token",
        description = "Checks if the provided JWT token is valid and not expired"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid token", "No Bearer token provided"));
            }
            
            String token = authHeader.substring(7);
            
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid token", "Token is invalid or expired"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("username", jwtService.extractUsername(token));
            response.put("role", jwtService.extractRole(token));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Token validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Invalid token", "Token validation failed"));
        }
    }
    
    /**
     * GET /api/auth/me
     * 
     * Get current user info from JWT token.
     */
    @Operation(
        summary = "Get current user info",
        description = "Returns the current user's information based on the JWT token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User info retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Not authenticated", "No Bearer token provided"));
            }
            
            String token = authHeader.substring(7);
            
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Not authenticated", "Token is invalid or expired"));
            }
            
            String username = jwtService.extractUsername(token);
            
            // Fetch fresh user data from database
            return userDao.findByUsername(username)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("username", user.getUsername());
                    response.put("email", user.getEmail());
                    response.put("fullName", user.getFullName());
                    response.put("role", user.getRole());
                    response.put("status", user.getStatus());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Not authenticated", "User not found")));
            
        } catch (Exception e) {
            logger.error("Get user error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Not authenticated", "Could not retrieve user info"));
        }
    }
    
    // ==================== Health Check ====================
    
    /**
     * GET /api/auth/health
     * 
     * Health check endpoint for auth service.
     */
    @Operation(summary = "Health check", description = "Check auth service health")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "auth-api");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Authenticate user against database.
     */
    private AuthResult authenticateUser(String username, String password) {
        String sql = "SELECT id, username, password_hash, email, full_name, role, status, " +
                     "failed_login_attempts, locked_until FROM users WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    // User not found - use generic message to prevent user enumeration
                    return AuthResult.failure("Authentication failed", "Invalid username or password");
                }
                
                String status = rs.getString("status");
                if (!"active".equalsIgnoreCase(status)) {
                    return AuthResult.failure("Account inactive", 
                        "Your account is not active. Please contact support.");
                }
                
                // Check if account is locked
                Timestamp lockedUntil = rs.getTimestamp("locked_until");
                if (lockedUntil != null && lockedUntil.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    return AuthResult.locked("Account locked", 
                        "Too many failed login attempts. Please try again later.");
                }
                
                String passwordHash = rs.getString("password_hash");
                int failedAttempts = rs.getInt("failed_login_attempts");
                
                // Verify password
                if (!passwordEncoder.matches(password, passwordHash)) {
                    // Increment failed attempts
                    incrementFailedAttempts(username, failedAttempts);
                    return AuthResult.failure("Authentication failed", "Invalid username or password");
                }
                
                // Success - reset failed attempts and update last login
                resetFailedAttempts(username);
                updateLastLogin(username);
                
                return AuthResult.success(
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getString("email"),
                    rs.getString("full_name")
                );
            }
        } catch (SQLException e) {
            logger.error("Database error during authentication: {}", e.getMessage());
            return AuthResult.failure("Authentication failed", 
                "A system error occurred. Please try again.");
        }
    }
    
    private void resetFailedAttempts(String username) {
        String sql = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error resetting failed attempts: {}", e.getMessage());
        }
    }
    
    private void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating last login: {}", e.getMessage());
        }
    }
    
    private void incrementFailedAttempts(String username, int currentAttempts) {
        int newAttempts = currentAttempts + 1;
        
        if (newAttempts >= MAX_FAILED_ATTEMPTS) {
            // Use parameterized interval to avoid string concatenation in SQL
            // PostgreSQL supports make_interval function for safe interval creation
            String sql = "UPDATE users SET failed_login_attempts = ?, " +
                        "locked_until = CURRENT_TIMESTAMP + make_interval(mins => ?) WHERE username = ?";
            logger.warn("Account locked due to too many failed attempts: {}", username);
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newAttempts);
                stmt.setInt(2, LOCKOUT_DURATION_MINUTES);
                stmt.setString(3, username);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error locking account: {}", e.getMessage());
            }
        } else {
            String sql = "UPDATE users SET failed_login_attempts = ? WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newAttempts);
                stmt.setString(2, username);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error incrementing failed attempts: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Validate registration request.
     */
    private ValidationResult validateRegistration(RegisterRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request body is required");
        }
        
        // Validate username
        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            return ValidationResult.invalid("Username is required");
        }
        username = username.trim();
        if (username.length() < MIN_USERNAME_LENGTH) {
            return ValidationResult.invalid("Username must be at least " + MIN_USERNAME_LENGTH + " characters");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            return ValidationResult.invalid("Username must be " + MAX_USERNAME_LENGTH + " characters or less");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ValidationResult.invalid("Username can only contain letters, numbers, and underscores");
        }
        
        // Validate email
        String email = request.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.invalid("Email is required");
        }
        email = email.trim();
        if (email.length() > MAX_EMAIL_LENGTH) {
            return ValidationResult.invalid("Email must be " + MAX_EMAIL_LENGTH + " characters or less");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ValidationResult.invalid("Invalid email format");
        }
        
        // Validate password
        String password = request.getPassword();
        if (password == null || password.isEmpty()) {
            return ValidationResult.invalid("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return ValidationResult.invalid("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return ValidationResult.invalid("Password must be " + MAX_PASSWORD_LENGTH + " characters or less");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return ValidationResult.invalid("Password must contain at least one uppercase letter, one lowercase letter, and one digit");
        }
        
        // Validate full name
        String fullName = request.getFullName();
        if (fullName == null || fullName.trim().isEmpty()) {
            return ValidationResult.invalid("Full name is required");
        }
        if (fullName.trim().length() < 2) {
            return ValidationResult.invalid("Full name must be at least 2 characters");
        }
        
        // Validate role (optional - defaults to USER)
        String role = request.getRole();
        if (role != null && !role.trim().isEmpty()) {
            String normalizedRole = normalizeRole(role);
            if (normalizedRole == null) {
                return ValidationResult.invalid("Invalid role. Allowed: PRODUCER, CONSUMER, LOGISTICS, ADMIN");
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Normalize role to standard values.
     */
    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "USER";
        }
        
        String upper = role.trim().toUpperCase();
        switch (upper) {
            case "PRODUCER":
            case "FARMER":
                return "PRODUCER";
            case "CONSUMER":
                return "CONSUMER";
            case "LOGISTICS":
            case "SUPPLIER":
                return "LOGISTICS";
            case "ADMIN":
                return "ADMIN";
            case "USER":
                return "USER";
            default:
                return null; // Invalid role
        }
    }
    
    /**
     * Create error response map.
     */
    private Map<String, Object> createErrorResponse(String error, String details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        response.put("details", details);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    // ==================== Request/Response DTOs ====================
    
    /**
     * Registration request body.
     */
    @Schema(description = "User registration request")
    public static class RegisterRequest {
        @Schema(description = "Username (3-50 characters, alphanumeric and underscores)", 
                example = "john_doe", required = true)
        private String username;
        
        @Schema(description = "Email address", example = "john@example.com", required = true)
        private String email;
        
        @Schema(description = "Password (min 8 chars, uppercase, lowercase, digit)", 
                example = "SecurePass123", required = true)
        private String password;
        
        @Schema(description = "User's full name", example = "John Doe", required = true)
        private String fullName;
        
        @Schema(description = "User role (PRODUCER, CONSUMER, LOGISTICS, ADMIN)", 
                example = "PRODUCER")
        private String role;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
    
    /**
     * Login request body.
     */
    @Schema(description = "User login request")
    public static class LoginRequest {
        @Schema(description = "Username", example = "john_doe", required = true)
        private String username;
        
        @Schema(description = "Password", example = "SecurePass123", required = true)
        private String password;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    /**
     * Validation result helper class.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() { return valid; }
        public String getError() { return error; }
    }
    
    /**
     * Authentication result helper class.
     */
    private static class AuthResult {
        private final boolean success;
        private final boolean locked;
        private final String error;
        private final String details;
        private final String username;
        private final String role;
        private final String email;
        private final String fullName;
        
        private AuthResult(boolean success, boolean locked, String error, String details,
                          String username, String role, String email, String fullName) {
            this.success = success;
            this.locked = locked;
            this.error = error;
            this.details = details;
            this.username = username;
            this.role = role;
            this.email = email;
            this.fullName = fullName;
        }
        
        public static AuthResult success(String username, String role, String email, String fullName) {
            return new AuthResult(true, false, null, null, username, role, email, fullName);
        }
        
        public static AuthResult failure(String error, String details) {
            return new AuthResult(false, false, error, details, null, null, null, null);
        }
        
        public static AuthResult locked(String error, String details) {
            return new AuthResult(false, true, error, details, null, null, null, null);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isLocked() { return locked; }
        public String getError() { return error; }
        public String getDetails() { return details; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
    }
}
