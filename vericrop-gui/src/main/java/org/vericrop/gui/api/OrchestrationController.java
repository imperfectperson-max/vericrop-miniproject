package org.vericrop.gui.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.service.orchestration.*;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ScenarioExecutionStatus;

import java.util.*;

/**
 * REST API controller for scenario orchestration.
 * 
 * Provides endpoints to trigger concurrent execution of scenario groups
 * and retrieve orchestration status.
 */
@RestController
@RequestMapping("/api/orchestration")
public class OrchestrationController {
    
    private final OrchestrationService orchestrationService;
    
    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }
    
    /**
     * Triggers concurrent execution of specified scenario groups.
     * 
     * Example request:
     * POST /api/orchestration/execute
     * {
     *   "userId": "user-123",
     *   "scenarioGroups": ["SCENARIOS", "DELIVERY", "MAP"],
     *   "context": {
     *     "batchId": "BATCH-001",
     *     "farmerId": "FARM-123"
     *   },
     *   "timeoutMs": 30000
     * }
     * 
     * @param request The orchestration request
     * @return Orchestration result with per-scenario status
     */
    @PostMapping("/execute")
    public ResponseEntity<OrchestrationResponse> executeScenarios(@RequestBody OrchestrationRequest request) {
        // Validate request
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            return ResponseEntity.badRequest().body(new OrchestrationResponse(
                    false,
                    "userId is required",
                    Collections.emptyMap()
            ));
        }
        
        if (request.getScenarioGroups() == null || request.getScenarioGroups().isEmpty()) {
            return ResponseEntity.badRequest().body(new OrchestrationResponse(
                    false,
                    "At least one scenario group is required",
                    Collections.emptyMap()
            ));
        }
        
        // Convert scenario group strings to enums
        Set<ScenarioGroup> scenarioGroups = new HashSet<>();
        for (String groupName : request.getScenarioGroups()) {
            try {
                scenarioGroups.add(ScenarioGroup.valueOf(groupName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new OrchestrationResponse(
                        false,
                        "Invalid scenario group: " + groupName,
                        Collections.emptyMap()
                ));
            }
        }
        
        // Create orchestration request
        String requestId = UUID.randomUUID().toString();
        long timeoutMs = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : 30000; // Default 30s
        
        ScenarioOrchestrationRequest orchestrationRequest = new ScenarioOrchestrationRequest(
                requestId,
                request.getUserId(),
                scenarioGroups,
                request.getContext() != null ? request.getContext() : new HashMap<>(),
                timeoutMs
        );
        
        // Execute scenarios
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(orchestrationRequest);
        
        // Convert to response format
        Map<String, ScenarioStatusResponse> scenarioStatuses = new HashMap<>();
        for (Map.Entry<ScenarioGroup, ScenarioExecutionStatus> entry : result.getScenarioStatuses().entrySet()) {
            ScenarioExecutionStatus status = entry.getValue();
            scenarioStatuses.put(
                    entry.getKey().name(),
                    new ScenarioStatusResponse(
                            status.getStatus().name(),
                            status.getMessage(),
                            status.getTimestamp().toString(),
                            status.getErrorDetails()
                    )
            );
        }
        
        OrchestrationResponse response = new OrchestrationResponse(
                result.isOverallSuccess(),
                result.getMessage(),
                scenarioStatuses
        );
        response.setRequestId(result.getRequestId());
        response.setStartTime(result.getStartTime().toString());
        response.setEndTime(result.getEndTime().toString());
        response.setDurationMs(result.getDurationMs());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Executes all six scenario groups concurrently.
     * 
     * Convenience endpoint that runs all scenario groups without requiring
     * them to be specified individually.
     * 
     * @param request The orchestration request (scenarioGroups will be ignored)
     * @return Orchestration result with per-scenario status
     */
    @PostMapping("/execute-all")
    public ResponseEntity<OrchestrationResponse> executeAllScenarios(@RequestBody OrchestrationRequest request) {
        request.setScenarioGroups(Arrays.asList(
                "SCENARIOS", "DELIVERY", "MAP", "TEMPERATURE", "SUPPLIER_COMPLIANCE", "SIMULATIONS"
        ));
        return executeScenarios(request);
    }
    
    /**
     * Request DTO for orchestration.
     */
    public static class OrchestrationRequest {
        private String userId;
        private List<String> scenarioGroups;
        private Map<String, Object> context;
        private long timeoutMs;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public List<String> getScenarioGroups() { return scenarioGroups; }
        public void setScenarioGroups(List<String> scenarioGroups) { this.scenarioGroups = scenarioGroups; }
        
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }
    
    /**
     * Response DTO for orchestration.
     */
    public static class OrchestrationResponse {
        private String requestId;
        private boolean success;
        private String message;
        private String startTime;
        private String endTime;
        private long durationMs;
        private Map<String, ScenarioStatusResponse> scenarioStatuses;
        
        public OrchestrationResponse(boolean success, String message, Map<String, ScenarioStatusResponse> scenarioStatuses) {
            this.success = success;
            this.message = message;
            this.scenarioStatuses = scenarioStatuses;
        }
        
        // Getters and setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        
        public Map<String, ScenarioStatusResponse> getScenarioStatuses() { return scenarioStatuses; }
        public void setScenarioStatuses(Map<String, ScenarioStatusResponse> scenarioStatuses) { 
            this.scenarioStatuses = scenarioStatuses; 
        }
    }
    
    /**
     * Response DTO for individual scenario status.
     */
    public static class ScenarioStatusResponse {
        private final String status;
        private final String message;
        private final String timestamp;
        private final String errorDetails;
        
        public ScenarioStatusResponse(String status, String message, String timestamp, String errorDetails) {
            this.status = status;
            this.message = message;
            this.timestamp = timestamp;
            this.errorDetails = errorDetails;
        }
        
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getTimestamp() { return timestamp; }
        public String getErrorDetails() { return errorDetails; }
    }
}
