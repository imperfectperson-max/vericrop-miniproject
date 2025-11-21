package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.util.ValidationUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST API-based authentication service.
 * Connects to backend REST API for authentication.
 */
public class RestAuthService implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(RestAuthService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String currentUser;
    private String currentRole;
    private String currentEmail;
    private String currentFullName;
    private String authToken;
    
    public RestAuthService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        logger.info("✅ RestAuthService initialized with base URL: {}", this.baseUrl);
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
        
        try {
            Map<String, String> loginRequest = new HashMap<>();
            loginRequest.put("username", username);
            loginRequest.put("password", password);
            
            String jsonBody = objectMapper.writeValueAsString(loginRequest);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            Request request = new Request.Builder()
                    .url(baseUrl + "/auth/login")
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                    
                    this.currentUser = (String) result.get("username");
                    this.currentRole = (String) result.get("role");
                    this.currentEmail = (String) result.get("email");
                    this.currentFullName = (String) result.get("fullName");
                    this.authToken = (String) result.get("token");
                    
                    logger.info("✅ User authenticated via REST API: {} (role: {})", currentUser, currentRole);
                    return true;
                } else {
                    logger.warn("Login failed via REST API: HTTP {}", response.code());
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("Login failed - connection error: {}", e.getMessage());
            return false;
        }
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
        
        try {
            Map<String, String> registerRequest = new HashMap<>();
            registerRequest.put("username", username);
            registerRequest.put("password", password);
            registerRequest.put("email", email);
            registerRequest.put("fullName", fullName != null ? fullName : username);
            registerRequest.put("role", role != null ? role : "consumer");
            
            String jsonBody = objectMapper.writeValueAsString(registerRequest);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            Request request = new Request.Builder()
                    .url(baseUrl + "/auth/register")
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("✅ User registered via REST API: {} (role: {})", username, role);
                    return true;
                } else {
                    logger.warn("Registration failed via REST API: HTTP {}", response.code());
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("Registration failed - connection error: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public void logout() {
        if (currentUser != null) {
            logger.info("User logged out (REST mode): {}", currentUser);
        }
        this.currentUser = null;
        this.currentRole = null;
        this.currentEmail = null;
        this.currentFullName = null;
        this.authToken = null;
    }
    
    @Override
    public boolean isAuthenticated() {
        return currentUser != null && authToken != null;
    }
    
    @Override
    public String getCurrentUser() {
        return currentUser;
    }
    
    @Override
    public String getCurrentRole() {
        return currentRole;
    }
    
    @Override
    public String getCurrentEmail() {
        return currentEmail;
    }
    
    @Override
    public String getCurrentFullName() {
        return currentFullName;
    }
    
    /**
     * Get current authentication token
     * @return auth token or null if not authenticated
     */
    public String getAuthToken() {
        return authToken;
    }
    
    /**
     * Test if REST API is reachable
     * @return true if backend is available
     */
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/health")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            logger.debug("REST API not reachable: {}", e.getMessage());
            return false;
        }
    }
}
