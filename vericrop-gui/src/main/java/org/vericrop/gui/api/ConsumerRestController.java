package org.vericrop.gui.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for consumer endpoints
 */
@RestController
@RequestMapping("/consumer")
public class ConsumerRestController {
    
    // In-memory cache for batch compliance status
    private final Map<String, ComplianceStatus> batchCompliance = new ConcurrentHashMap<>();
    
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
}
