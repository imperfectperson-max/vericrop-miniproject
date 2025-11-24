package org.vericrop.gui.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.integration.AnalyticsAirflowClient;
import org.vericrop.gui.integration.AnalyticsKafkaProducer;
import org.vericrop.gui.models.AnalyticsJob;
import org.vericrop.gui.services.AnalyticsWorkflowService;
import org.vericrop.gui.services.impl.AnalyticsWorkflowServiceImpl;
import org.vericrop.gui.store.InMemoryJobStore;
import org.vericrop.gui.store.JobStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for analytics endpoints.
 * Provides both legacy batch tracking and new analytics workflow orchestration.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {
    
    // In-memory cache for recent batches (legacy)
    private final Map<String, BatchInfo> recentBatches = new ConcurrentHashMap<>();
    
    // Analytics workflow service
    private final AnalyticsWorkflowService analyticsService;
    
    /**
     * Default constructor - initializes with default configuration
     */
    public AnalyticsRestController() {
        ConfigService config = ConfigService.getInstance();
        JobStore jobStore = new InMemoryJobStore();
        AnalyticsAirflowClient airflowClient = new AnalyticsAirflowClient(config);
        AnalyticsKafkaProducer kafkaProducer = new AnalyticsKafkaProducer(config);
        
        this.analyticsService = new AnalyticsWorkflowServiceImpl(jobStore, airflowClient, kafkaProducer);
    }
    
    /**
     * Constructor for dependency injection (testing)
     */
    public AnalyticsRestController(AnalyticsWorkflowService analyticsService) {
        this.analyticsService = analyticsService;
    }
    
    // ============================================================================
    // New Analytics Workflow Endpoints
    // ============================================================================
    
    /**
     * Trigger an analytics job
     * POST /api/analytics/trigger
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerAnalyticsJob(@RequestBody TriggerRequest request) {
        try {
            AnalyticsJob job = analyticsService.triggerAnalyticsJob(
                    request.getDatasetId(),
                    request.getParameters(),
                    request.getCallbackUrl()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("job_id", job.getJobId());
            response.put("airflow_run_id", job.getAirflowRunId());
            response.put("status", job.getStatus().name());
            response.put("created_at", job.getCreatedAt().toString());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to trigger analytics job");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get job status
     * GET /api/analytics/{jobId}/status
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        try {
            AnalyticsJob job = analyticsService.getJobStatus(jobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("job_id", job.getJobId());
            response.put("status", job.getStatus().name().toLowerCase());
            response.put("created_at", job.getCreatedAt().toString());
            response.put("updated_at", job.getUpdatedAt().toString());
            
            if (job.getAirflowRunId() != null) {
                response.put("airflow_run_id", job.getAirflowRunId());
            }
            
            if (job.getErrorMessage() != null) {
                response.put("error_message", job.getErrorMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * Get job results
     * GET /api/analytics/{jobId}/results
     */
    @GetMapping("/{jobId}/results")
    public ResponseEntity<Map<String, Object>> getJobResults(@PathVariable String jobId) {
        try {
            AnalyticsJob job = analyticsService.getJobResults(jobId);
            
            if (job.getStatus() != AnalyticsJob.JobStatus.SUCCESS) {
                Map<String, Object> response = new HashMap<>();
                response.put("job_id", job.getJobId());
                response.put("status", job.getStatus().name().toLowerCase());
                response.put("message", "Results not yet available");
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("job_id", job.getJobId());
            response.put("status", job.getStatus().name().toLowerCase());
            response.put("results", job.getResults());
            
            if (job.getResultsLink() != null) {
                response.put("results_link", job.getResultsLink());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * Callback endpoint for async job updates
     * POST /api/analytics/{jobId}/callback
     */
    @PostMapping("/{jobId}/callback")
    public ResponseEntity<Map<String, Object>> jobCallback(
            @PathVariable String jobId,
            @RequestBody CallbackRequest request) {
        try {
            analyticsService.updateJobViaCallback(jobId, request.getStatus(), request.getResults());
            
            Map<String, Object> response = new HashMap<>();
            response.put("job_id", jobId);
            response.put("message", "Job updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Job not found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    // ============================================================================
    // Legacy Batch Tracking Endpoints
    // ============================================================================
    
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
    
    // ============================================================================
    // Analytics Workflow Request/Response Models
    // ============================================================================
    
    /**
     * Request model for triggering analytics job
     */
    public static class TriggerRequest {
        private String datasetId;
        private Map<String, Object> parameters;
        private String callbackUrl;
        
        public TriggerRequest() {}
        
        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    }
    
    /**
     * Request model for job callback
     */
    public static class CallbackRequest {
        private String status;
        private Map<String, Object> results;
        
        public CallbackRequest() {}
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Map<String, Object> getResults() { return results; }
        public void setResults(Map<String, Object> results) { this.results = results; }
    }
}
