package org.vericrop.gui.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * REST controller for consumer endpoints.
 * Provides API to verify batches and check compliance status.
 * 
 * <h2>Endpoint Summary</h2>
 * <ul>
 *   <li>POST /consumer/verify - Verify a batch object</li>
 *   <li>GET /consumer/batches/{batchId}/compliance - Get compliance status for a batch</li>
 *   <li>POST /consumer/batches/{batchId}/compliance - Update compliance status</li>
 *   <li>GET /consumer/batches/compliance - Get all batch compliance statuses</li>
 * </ul>
 */
@RestController
@RequestMapping("/consumer")
public class ConsumerRestController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerRestController.class);
    
    // In-memory cache for batch compliance status
    private final Map<String, ComplianceStatus> batchCompliance = new ConcurrentHashMap<>();
    
    // UUID pattern for validation (standard UUID format: 8-4-4-4-12 hex digits)
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    
    // Base64 PNG data URL pattern
    private static final Pattern BASE64_PNG_DATA_URL_PATTERN = Pattern.compile(
        "^data:image/png;base64,[A-Za-z0-9+/]+=*$");
    
    // Simple base64 pattern (for QR code payloads without data URL prefix)
    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+/]+=*$");
    
    // Minimum quality score
    private static final int MIN_QUALITY_SCORE = 0;
    
    // Maximum quality score
    private static final int MAX_QUALITY_SCORE = 100;
    
    /**
     * POST /consumer/verify
     * 
     * Verify a batch object and return verification result.
     * Checks id (UUID format), qualityScore (0-100), and qrCode (valid format).
     * 
     * @param request The batch object to verify
     * @return Verification result with verified status, reasons, and details
     */
    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verifyBatch(@RequestBody BatchVerificationRequest request) {
        logger.info("Received batch verification request for id: {}", 
                   request != null ? request.getId() : "null");
        
        List<String> reasons = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();
        
        // Handle null request
        if (request == null) {
            reasons.add("Request body is required");
            return ResponseEntity.badRequest().body(
                new VerificationResponse(false, reasons, null, details));
        }
        
        String batchId = request.getId();
        
        // Validate id (present and UUID format)
        if (!isValidUUID(request.getId())) {
            if (request.getId() == null || request.getId().trim().isEmpty()) {
                reasons.add("id is required");
            } else {
                reasons.add("id must be a valid UUID format (e.g., 550e8400-e29b-41d4-a716-446655440000)");
            }
        }
        
        // Validate qualityScore (present and in range 0-100)
        if (!isValidQualityScore(request.getQualityScore())) {
            if (request.getQualityScore() == null) {
                reasons.add("qualityScore is required");
            } else {
                reasons.add("qualityScore must be a number between " + MIN_QUALITY_SCORE + " and " + MAX_QUALITY_SCORE);
            }
        }
        
        // Validate qrCode (present and valid format)
        String qrCodePayload = null;
        if (!isValidQRCode(request.getQrCode())) {
            if (request.getQrCode() == null || request.getQrCode().trim().isEmpty()) {
                reasons.add("qrCode is required");
            } else {
                reasons.add("qrCode must be a valid base64 PNG data URL or base64 encoded string");
            }
        } else {
            // Try to decode and parse QR code payload
            qrCodePayload = decodeQRCodePayload(request.getQrCode());
            if (qrCodePayload != null) {
                details.put("qrPayload", qrCodePayload);
            }
        }
        
        // Add parsed details to response
        if (request.getQualityScore() != null) {
            details.put("qualityScore", request.getQualityScore());
            details.put("qualityLabel", getQualityLabel(request.getQualityScore()));
        }
        if (request.getProductType() != null) {
            details.put("productType", request.getProductType());
        }
        if (request.getProducerId() != null) {
            details.put("producerId", request.getProducerId());
        }
        
        boolean verified = reasons.isEmpty();
        
        if (verified) {
            logger.info("✅ Batch verification passed for id: {}", batchId);
        } else {
            logger.warn("❌ Batch verification failed for id: {}. Reasons: {}", batchId, reasons);
        }
        
        return ResponseEntity.ok(new VerificationResponse(verified, reasons, batchId, details));
    }
    
    /**
     * Check if a string is a valid UUID format.
     * 
     * @param id The string to validate
     * @return true if the string is a valid UUID format
     */
    public static boolean isValidUUID(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(id.trim()).matches();
    }
    
    /**
     * Check if a quality score is valid (between 0 and 100 inclusive).
     * 
     * @param qualityScore The quality score to validate
     * @return true if the quality score is valid
     */
    public static boolean isValidQualityScore(Integer qualityScore) {
        if (qualityScore == null) {
            return false;
        }
        return qualityScore >= MIN_QUALITY_SCORE && qualityScore <= MAX_QUALITY_SCORE;
    }
    
    /**
     * Check if a QR code string is valid.
     * Accepts either a base64 PNG data URL or a plain base64 encoded string.
     * 
     * @param qrCode The QR code string to validate
     * @return true if the QR code format is valid
     */
    public static boolean isValidQRCode(String qrCode) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            return false;
        }
        String trimmed = qrCode.trim();
        // Check for base64 PNG data URL format
        if (BASE64_PNG_DATA_URL_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        // Check for plain base64 format (minimum length for meaningful content)
        if (trimmed.length() >= 4 && BASE64_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        return false;
    }
    
    /**
     * Attempt to decode the payload from a QR code string.
     * If the QR code is a data URL, this returns a placeholder indicating it's image data.
     * If it's base64, attempts to decode it.
     * 
     * @param qrCode The QR code string
     * @return Decoded payload or null if decoding fails
     */
    private String decodeQRCodePayload(String qrCode) {
        if (qrCode == null) {
            return null;
        }
        try {
            if (qrCode.startsWith("data:image/png;base64,")) {
                // It's a PNG data URL - return indication that it's image data
                return "[PNG Image Data]";
            }
            // Try to decode as base64 text
            byte[] decoded = Base64.getDecoder().decode(qrCode.trim());
            return new String(decoded);
        } catch (Exception e) {
            logger.debug("Could not decode QR payload: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get a human-readable quality label based on quality score.
     * 
     * @param score The quality score (0-100)
     * @return Quality label string
     */
    private String getQualityLabel(Integer score) {
        if (score == null) return "Unknown";
        if (score >= 90) return "Fresh";
        if (score >= 70) return "Good";
        if (score >= 50) return "Fair";
        return "Poor";
    }
    
    /**
     * Get compliance status for a batch
     */
    @GetMapping("/batches/{batchId}/compliance")
    public ResponseEntity<ComplianceStatus> getBatchCompliance(@PathVariable String batchId) {
        ComplianceStatus status = batchCompliance.get(batchId);
        if (status != null) {
            return ResponseEntity.ok(status);
        }
        // Return default status if not found
        return ResponseEntity.ok(new ComplianceStatus(batchId, null, 0, "No compliance data available"));
    }
    
    /**
     * Update compliance status for a batch - called by Kafka consumers
     */
    @PostMapping("/batches/{batchId}/compliance")
    public ResponseEntity<Void> updateBatchCompliance(
            @PathVariable String batchId,
            @RequestBody ComplianceStatus status) {
        status.setBatchId(batchId);
        status.setTimestamp(System.currentTimeMillis());
        batchCompliance.put(batchId, status);
        System.out.println("🛒 Updated compliance for batch: " + batchId + 
            " - compliant: " + status.getCompliant());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get all batches with compliance status
     */
    @GetMapping("/batches/compliance")
    public ResponseEntity<List<ComplianceStatus>> getAllBatchCompliance() {
        List<ComplianceStatus> statuses = new ArrayList<>(batchCompliance.values());
        // Sort by timestamp descending
        statuses.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return ResponseEntity.ok(statuses);
    }
    
    /**
     * ComplianceStatus model
     */
    public static class ComplianceStatus {
        private String batchId;
        private Boolean compliant;
        private int violationCount;
        private String details;
        private long timestamp;
        private List<ViolationDetail> violations;
        
        public ComplianceStatus() {
            this.timestamp = System.currentTimeMillis();
            this.violations = new ArrayList<>();
        }
        
        public ComplianceStatus(String batchId, Boolean compliant, int violationCount, String details) {
            this();
            this.batchId = batchId;
            this.compliant = compliant;
            this.violationCount = violationCount;
            this.details = details;
        }
        
        // Getters and setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public Boolean getCompliant() { return compliant; }
        public void setCompliant(Boolean compliant) { this.compliant = compliant; }
        
        public int getViolationCount() { return violationCount; }
        public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
        
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public List<ViolationDetail> getViolations() { return violations; }
        public void setViolations(List<ViolationDetail> violations) { this.violations = violations; }
    }
    
    /**
     * ViolationDetail model
     */
    public static class ViolationDetail {
        private long timestamp;
        private double temperature;
        private int durationSeconds;
        private String description;
        
        public ViolationDetail() {}
        
        public ViolationDetail(long timestamp, double temperature, int durationSeconds, String description) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.durationSeconds = durationSeconds;
            this.description = description;
        }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * Request body for batch verification.
     * Contains the batch data to verify.
     */
    public static class BatchVerificationRequest {
        private String id;
        private Integer qualityScore;
        private String qrCode;
        private String productType;
        private String producerId;
        private String batchName;
        private Integer quantity;
        private String location;
        private Map<String, Object> additionalData;
        
        public BatchVerificationRequest() {}
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public Integer getQualityScore() { return qualityScore; }
        public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
        
        public String getQrCode() { return qrCode; }
        public void setQrCode(String qrCode) { this.qrCode = qrCode; }
        
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        
        public String getProducerId() { return producerId; }
        public void setProducerId(String producerId) { this.producerId = producerId; }
        
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
    }
    
    /**
     * Response body for batch verification.
     * Contains the verification result, reasons for failure, and parsed details.
     */
    public static class VerificationResponse {
        private boolean verified;
        private List<String> reasons;
        private String batchId;
        private Map<String, Object> details;
        private long timestamp;
        
        public VerificationResponse() {
            this.timestamp = System.currentTimeMillis();
            this.reasons = new ArrayList<>();
            this.details = new HashMap<>();
        }
        
        public VerificationResponse(boolean verified, List<String> reasons, String batchId, Map<String, Object> details) {
            this();
            this.verified = verified;
            this.reasons = reasons != null ? reasons : new ArrayList<>();
            this.batchId = batchId;
            this.details = details != null ? details : new HashMap<>();
        }
        
        // Getters and setters
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
