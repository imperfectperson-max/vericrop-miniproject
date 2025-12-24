package org.vericrop.gui.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.services.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for analytics endpoints.
 * All analytics endpoints require admin authentication.
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsRestController {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsRestController.class);
    
    // In-memory cache for recent batches
    private final Map<String, BatchInfo> recentBatches = new ConcurrentHashMap<>();
    private final JwtService jwtService;
    
    /**
     * Constructor with dependency injection
     */
    public AnalyticsRestController(JwtService jwtService) {
        this.jwtService = jwtService;
        logger.info("AnalyticsRestController initialized with admin authentication");
    }
    
    /**
     * Package-private constructor for testing.
     * Skips authentication when jwtService is null.
     * DO NOT use in production code.
     */
    AnalyticsRestController() {
        this.jwtService = null;
        logger.info("AnalyticsRestController initialized in TEST MODE (authentication disabled)");
    }
    
    /**
     * Verify that the request has admin authentication
     */
    private ResponseEntity<Map<String, Object>> verifyAdminAuth(String authHeader) {
        if (jwtService == null) {
            // Test mode - skip authentication
            return null;
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createAuthError("Authentication required", "Please provide a valid Bearer token"));
        }
        
        String token = authHeader.substring(7);
        
        if (!jwtService.isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createAuthError("Invalid token", "Token is invalid or expired"));
        }
        
        String role = jwtService.extractRole(token);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            logger.warn("Non-admin user attempted to access analytics: role={}", role);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(createAuthError("Access denied", "Admin role required to access analytics"));
        }
        
        return null; // Auth successful
    }
    
    /**
     * Create authentication error response
     */
    private Map<String, Object> createAuthError(String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Get recent batches - Admin only
     */
    @GetMapping("/recent-batches")
    public ResponseEntity<?> getRecentBatches(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Verify admin authentication
        ResponseEntity<Map<String, Object>> authError = verifyAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        
        List<BatchInfo> batches = new ArrayList<>(recentBatches.values());
        // Sort by timestamp descending (most recent first)
        batches.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return ResponseEntity.ok(batches);
    }
    
    /**
     * Update recent batches - Admin only
     */
    @PostMapping("/recent-batches")
    public ResponseEntity<?> updateRecentBatches(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BatchInfo batchInfo) {
        
        // Verify admin authentication
        ResponseEntity<Map<String, Object>> authError = verifyAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        
        recentBatches.put(batchInfo.getBatchId(), batchInfo);
        logger.info("📊 Updated recent batches cache: {}", batchInfo.getBatchId());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Update batch status - Admin only
     */
    @PutMapping("/batches/{batchId}/status")
    public ResponseEntity<?> updateBatchStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String batchId,
            @RequestParam String status) {
        
        // Verify admin authentication
        ResponseEntity<Map<String, Object>> authError = verifyAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        
        BatchInfo batch = recentBatches.get(batchId);
        if (batch != null) {
            batch.setStatus(status);
            batch.setTimestamp(System.currentTimeMillis());
            logger.info("📊 Updated batch status: {} -> {}", batchId, status);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Update temperature compliance for a batch - Admin only
     */
    @PutMapping("/batches/{batchId}/compliance")
    public ResponseEntity<?> updateComplianceStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String batchId,
            @RequestBody ComplianceInfo compliance) {
        
        // Verify admin authentication
        ResponseEntity<Map<String, Object>> authError = verifyAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        
        BatchInfo batch = recentBatches.get(batchId);
        if (batch != null) {
            batch.setCompliant(compliance.isCompliant());
            batch.setViolationCount(compliance.getViolationCount());
            batch.setTimestamp(System.currentTimeMillis());
            logger.info("📊 Updated compliance for batch: {} - compliant: {}", 
                batchId, compliance.isCompliant());
            return ResponseEntity.ok().build();
        } else {
            // Create new batch info if not exists
            BatchInfo newBatch = new BatchInfo();
            newBatch.setBatchId(batchId);
            newBatch.setStatus("IN_TRANSIT");
            newBatch.setCompliant(compliance.isCompliant());
            newBatch.setViolationCount(compliance.getViolationCount());
            newBatch.setTimestamp(System.currentTimeMillis());
            recentBatches.put(batchId, newBatch);
            logger.info("📊 Created new batch with compliance: {}", batchId);
            return ResponseEntity.ok().build();
        }
    }
    
    /**
     * BatchInfo model
     */
    public static class BatchInfo {
        private String batchId;
        private String status;
        private long timestamp;
        private Boolean compliant;
        private Integer violationCount;
        private String location;
        private Double qualityScore;
        
        public BatchInfo() {
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public Boolean getCompliant() { return compliant; }
        public void setCompliant(Boolean compliant) { this.compliant = compliant; }
        
        public Integer getViolationCount() { return violationCount; }
        public void setViolationCount(Integer violationCount) { this.violationCount = violationCount; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public Double getQualityScore() { return qualityScore; }
        public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    }
    
    /**
     * ComplianceInfo model
     */
    public static class ComplianceInfo {
        private boolean compliant;
        private int violationCount;
        
        public ComplianceInfo() {}
        
        public ComplianceInfo(boolean compliant, int violationCount) {
            this.compliant = compliant;
            this.violationCount = violationCount;
        }
        
        public boolean isCompliant() { return compliant; }
        public void setCompliant(boolean compliant) { this.compliant = compliant; }
        
        public int getViolationCount() { return violationCount; }
        public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    }
}
