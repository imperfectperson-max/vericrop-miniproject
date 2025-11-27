package org.vericrop.gui.services;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Service for generating and validating JSON Web Tokens.
 * Used for secure authentication in REST API endpoints.
 */
@Service
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    // Default expiration: 24 hours in milliseconds
    private static final long DEFAULT_EXPIRATION_MS = 24 * 60 * 60 * 1000;
    
    // Minimum secret key length for HS256 (256 bits = 32 bytes)
    private static final int MIN_SECRET_LENGTH = 32;
    
    private final SecretKey secretKey;
    private final long expirationMs;
    
    /**
     * Constructor with injected configuration.
     * Falls back to secure defaults if not configured.
     */
    public JwtService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs) {
        
        // Use provided secret or generate a secure default
        if (secret == null || secret.trim().isEmpty() || secret.length() < MIN_SECRET_LENGTH) {
            // Generate a secure random key for development
            this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            logger.warn("JWT secret not configured or too short. Using auto-generated secure key. " +
                       "Set jwt.secret in application.yml for production use (min 32 chars).");
        } else {
            // Pad or use the configured secret
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            logger.info("JWT service initialized with configured secret");
        }
        
        this.expirationMs = expirationMs > 0 ? expirationMs : DEFAULT_EXPIRATION_MS;
        logger.info("JWT expiration set to {} ms ({} hours)", 
                   this.expirationMs, this.expirationMs / (1000 * 60 * 60));
    }
    
    /**
     * Default constructor for testing.
     */
    public JwtService() {
        this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        this.expirationMs = DEFAULT_EXPIRATION_MS;
        logger.info("JWT service initialized with auto-generated key (test mode)");
    }
    
    /**
     * Generate a JWT token for the given username and role.
     *
     * @param username The username to include in the token
     * @param role The user's role
     * @param email The user's email (optional)
     * @return The generated JWT token string
     */
    public String generateToken(String username, String role, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (email != null) {
            claims.put("email", email);
        }
        
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        
        logger.debug("Generated JWT token for user: {} (role: {})", username, role);
        return token;
    }
    
    /**
     * Generate a JWT token with only username and role.
     */
    public String generateToken(String username, String role) {
        return generateToken(username, role, null);
    }
    
    /**
     * Extract the username from a JWT token.
     *
     * @param token The JWT token
     * @return The username (subject) from the token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extract the role from a JWT token.
     *
     * @param token The JWT token
     * @return The role from the token claims
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }
    
    /**
     * Extract the email from a JWT token.
     *
     * @param token The JWT token
     * @return The email from the token claims, or null if not present
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }
    
    /**
     * Extract the expiration date from a JWT token.
     *
     * @param token The JWT token
     * @return The expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * Extract a specific claim from a JWT token.
     *
     * @param token The JWT token
     * @param claimsResolver Function to extract the desired claim
     * @return The extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Validate a JWT token for a given username.
     *
     * @param token The JWT token to validate
     * @param username The expected username
     * @return true if the token is valid for the user and not expired
     */
    public boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            boolean valid = extractedUsername.equals(username) && !isTokenExpired(token);
            
            if (!valid) {
                logger.debug("Token validation failed for user: {} (extracted: {}, expired: {})", 
                           username, extractedUsername, isTokenExpired(token));
            }
            
            return valid;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a JWT token is valid (not expired and properly signed).
     *
     * @param token The JWT token to validate
     * @return true if the token is structurally valid
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Token is invalid: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a JWT token is expired.
     *
     * @param token The JWT token
     * @return true if the token is expired
     */
    private boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
    
    /**
     * Extract all claims from a JWT token.
     *
     * @param token The JWT token
     * @return All claims from the token
     * @throws JwtException if the token is invalid
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
