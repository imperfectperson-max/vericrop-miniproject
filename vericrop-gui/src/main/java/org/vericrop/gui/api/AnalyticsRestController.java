package org.vericrop.gui.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for analytics endpoints
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsRestController {
    
    // In-memory cache for recent batches
    private final Map<String, BatchInfo> recentBatches = new ConcurrentHashMap<>();
    
    /**
     * Get recent batches
     */
    @GetMapping("/recent-batches")
    public ResponseEntity<List<BatchInfo>> getRecentBatches() {
        List<BatchInfo> batches = new ArrayList<>(recentBatches.values());
        // Sort by timestamp descending (most recent first)
        batches.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return ResponseEntity.ok(batches);
    }
    
    /**
     * Update recent batches - called by Kafka consumers or other controllers
     */
    @PostMapping("/recent-batches")
    public ResponseEntity<Void> updateRecentBatches(@RequestBody BatchInfo batchInfo) {
        recentBatches.put(batchInfo.getBatchId(), batchInfo);
        System.out.println("📊 Updated recent batches cache: " + batchInfo.getBatchId());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Update batch status - convenience endpoint
     */
    @PutMapping("/batches/{batchId}/status")
    public ResponseEntity<Void> updateBatchStatus(
            @PathVariable String batchId,
            @RequestParam String status) {
        BatchInfo batch = recentBatches.get(batchId);
        if (batch != null) {
            batch.setStatus(status);
            batch.setTimestamp(System.currentTimeMillis());
            System.out.println("📊 Updated batch status: " + batchId + " -> " + status);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Update temperature compliance for a batch
     */
    @PutMapping("/batches/{batchId}/compliance")
    public ResponseEntity<Void> updateComplianceStatus(
            @PathVariable String batchId,
            @RequestBody ComplianceInfo compliance) {
        BatchInfo batch = recentBatches.get(batchId);
        if (batch != null) {
            batch.setCompliant(compliance.isCompliant());
            batch.setViolationCount(compliance.getViolationCount());
            batch.setTimestamp(System.currentTimeMillis());
            System.out.println("📊 Updated compliance for batch: " + batchId + 
                " - compliant: " + compliance.isCompliant());
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
            System.out.println("📊 Created new batch with compliance: " + batchId);
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
