package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Command event sent to controllers to start their tasks.
 */
public class ControllerCommandEvent {
    
    @JsonProperty("orchestration_id")
    private String orchestrationId;
    
    @JsonProperty("controller_name")
    private String controllerName;
    
    @JsonProperty("command")
    private String command;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("parameters")
    private java.util.Map<String, Object> parameters;
    
    public ControllerCommandEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public ControllerCommandEvent(String orchestrationId, String controllerName, String command) {
        this();
        this.orchestrationId = orchestrationId;
        this.controllerName = controllerName;
        this.command = command;
    }
    
    public ControllerCommandEvent(String orchestrationId, String controllerName, String command, 
                                  java.util.Map<String, Object> parameters) {
        this(orchestrationId, controllerName, command);
        this.parameters = parameters;
    }
    
    // Getters and setters
    public String getOrchestrationId() {
        return orchestrationId;
    }
    
    public void setOrchestrationId(String orchestrationId) {
        this.orchestrationId = orchestrationId;
    }
    
    public String getControllerName() {
        return controllerName;
    }
    
    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public java.util.Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(java.util.Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    @Override
    public String toString() {
        return String.format("ControllerCommandEvent{orchestrationId='%s', controllerName='%s', command='%s', timestamp=%d}",
                orchestrationId, controllerName, command, timestamp);
    }
}
