package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event representing an instance heartbeat for the instance registry.
 * Used to track how many application instances are currently running.
 * Instances send heartbeats periodically to indicate they are alive.
 * 
 * Each heartbeat includes the instance's role (e.g., PRODUCER, LOGISTICS, CONSUMER)
 * to enable role-based discovery and coordination across multiple controller instances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceHeartbeatEvent {
    
    /**
     * Enumeration of controller roles for instance tracking.
     */
    public enum Role {
        /** Producer controller - creates batches and starts simulations */
        PRODUCER,
        /** Logistics controller - tracks deliveries and routes */
        LOGISTICS,
        /** Consumer controller - verifies and receives batches */
        CONSUMER,
        /** Unknown or unspecified role (for backward compatibility) */
        UNKNOWN
    }
    
    @JsonProperty("instanceId")
    private String instanceId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("host")
    private String host;
    
    @JsonProperty("port")
    private int port;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public InstanceHeartbeatEvent() {
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
        this.role = Role.UNKNOWN.name();
    }
    
    /**
     * Create a new heartbeat event with just an instance ID.
     * Role defaults to UNKNOWN for backward compatibility.
     * 
     * @param instanceId Unique identifier for the instance
     */
    public InstanceHeartbeatEvent(String instanceId) {
        this.instanceId = instanceId;
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
        this.role = Role.UNKNOWN.name();
    }
    
    /**
     * Create a new heartbeat event with instance ID and role.
     * 
     * @param instanceId Unique identifier for the instance
     * @param role The role of this instance (PRODUCER, LOGISTICS, CONSUMER)
     */
    public InstanceHeartbeatEvent(String instanceId, Role role) {
        this.instanceId = instanceId;
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
        this.role = role != null ? role.name() : Role.UNKNOWN.name();
    }
    
    /**
     * Create a new heartbeat event with full instance details.
     * 
     * @param instanceId Unique identifier for the instance
     * @param role The role of this instance
     * @param host The host/IP address of this instance
     * @param port The port number of this instance
     */
    public InstanceHeartbeatEvent(String instanceId, Role role, String host, int port) {
        this.instanceId = instanceId;
        this.timestamp = System.currentTimeMillis();
        this.status = "ALIVE";
        this.role = role != null ? role.name() : Role.UNKNOWN.name();
        this.host = host;
        this.port = port;
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
    
    /**
     * Create a shutdown event with role information.
     * 
     * @param instanceId Unique identifier for the instance
     * @param role The role of the shutting down instance
     * @return InstanceHeartbeatEvent with SHUTDOWN status
     */
    public static InstanceHeartbeatEvent shutdown(String instanceId, Role role) {
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId, role);
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
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    /**
     * Get the role as an enum value.
     * 
     * @return Role enum, or UNKNOWN if the role string is invalid or null
     */
    public Role getRoleEnum() {
        if (role == null) {
            return Role.UNKNOWN;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return Role.UNKNOWN;
        }
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
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
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", timestamp=" + timestamp +
                '}';
    }
}
