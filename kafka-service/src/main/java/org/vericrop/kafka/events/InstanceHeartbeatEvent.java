package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event representing an instance heartbeat for the instance registry.
 * Used to track how many application instances are currently running.
 * Instances send heartbeats periodically to indicate they are alive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceHeartbeatEvent {
    
    @JsonProperty("instanceId")
    private String instanceId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("status")
    private String status;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public InstanceHeartbeatEvent() {
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
    }
    
    /**
     * Create a new heartbeat event.
     * 
     * @param instanceId Unique identifier for the instance
     */
    public InstanceHeartbeatEvent(String instanceId) {
        this.instanceId = instanceId;
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
    }
    
    /**
     * Create a shutdown event to indicate instance is stopping.
     * 
     * @param instanceId Unique identifier for the instance
     * @return InstanceHeartbeatEvent with SHUTDOWN status
     */
    public static InstanceHeartbeatEvent shutdown(String instanceId) {
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId);
        event.setStatus("SHUTDOWN");
        return event;
    }
    
    // Getters and Setters
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public boolean isAlive() {
        return "ALIVE".equals(status);
    }
    
    public boolean isShutdown() {
        return "SHUTDOWN".equals(status);
    }
    
    @Override
    public String toString() {
        return "InstanceHeartbeatEvent{" +
                "instanceId='" + instanceId + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
