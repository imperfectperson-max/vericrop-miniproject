package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Event to signal the start of coordinated simulations.
 * Published to simulations.start topic.
 */
public class SimulationStartEvent {
    
    @JsonProperty("runId")
    private String runId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("triggerSource")
    private String triggerSource;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    public SimulationStartEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SimulationStartEvent(String runId, String triggerSource) {
        this.runId = runId;
        this.triggerSource = triggerSource;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRunId() {
        return runId;
    }
    
    public void setRunId(String runId) {
        this.runId = runId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getTriggerSource() {
        return triggerSource;
    }
    
    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    @Override
    public String toString() {
        return "SimulationStartEvent{" +
                "runId='" + runId + '\'' +
                ", timestamp=" + timestamp +
                ", triggerSource='" + triggerSource + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
