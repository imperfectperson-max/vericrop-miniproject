package org.vericrop.service.orchestration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Request object for concurrent scenario orchestration.
 */
public class ScenarioOrchestrationRequest {
    private final String requestId;
    private final String userId;
    private final Set<ScenarioGroup> scenarioGroups;
    private final Map<String, Object> context;
    private final long timeoutMs;
    
    public ScenarioOrchestrationRequest(
            String requestId,
            String userId,
            Set<ScenarioGroup> scenarioGroups,
            Map<String, Object> context,
            long timeoutMs) {
        this.requestId = requestId;
        this.userId = userId;
        this.scenarioGroups = scenarioGroups;
        this.context = context != null ? context : new HashMap<>();
        this.timeoutMs = timeoutMs;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public Set<ScenarioGroup> getScenarioGroups() {
        return scenarioGroups;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}
