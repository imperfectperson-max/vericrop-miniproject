package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event for controlling simulations across application instances.
 * Published to the "simulation-control" topic when a simulation is started or stopped
 * from the ProducerController.
 * 
 * Supports two JSON payload formats for backward compatibility:
 * 1. Action-based format (preferred):
 *    {"action":"START"|"STOP","simulationId":"<uuid>","timestamp":<epochMs>,"instanceId":"<instanceUuid>"}
 * 
 * 2. Boolean-based format (legacy clients):
 *    {"start":true,"simulationId":"<uuid>",...} or {"stop":true,...}
 * 
 * When deserializing, boolean "start" or "stop" fields are mapped to the action field.
 * The @JsonIgnoreProperties annotation ensures forward compatibility with new fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationControlEvent {
    
    /**
     * Simulation control action types.
     */
    public enum Action {
        START,
        STOP
    }
    
    @JsonProperty("action")
    private Action action;
    
    @JsonProperty("simulationId")
    private String simulationId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("instanceId")
    private String instanceId;
    
    @JsonProperty("batchId")
    private String batchId;
    
    @JsonProperty("farmerId")
    private String farmerId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public SimulationControlEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a new simulation control event.
     * 
     * @param action The action (START or STOP)
     * @param simulationId Unique identifier for the simulation
     * @param instanceId Unique identifier for the sending instance
     */
    public SimulationControlEvent(Action action, String simulationId, String instanceId) {
        this.action = action;
        this.simulationId = simulationId;
        this.instanceId = instanceId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a START simulation control event with batch and farmer details.
     * 
     * @param simulationId Unique identifier for the simulation
     * @param instanceId Unique identifier for the sending instance
     * @param batchId Batch ID being simulated
     * @param farmerId Farmer ID associated with the batch
     * @return SimulationControlEvent for starting a simulation
     */
    public static SimulationControlEvent start(String simulationId, String instanceId, 
                                                String batchId, String farmerId) {
        SimulationControlEvent event = new SimulationControlEvent(Action.START, simulationId, instanceId);
        event.setBatchId(batchId);
        event.setFarmerId(farmerId);
        return event;
    }
    
    /**
     * Create a STOP simulation control event.
     * 
     * @param simulationId Unique identifier for the simulation
     * @param instanceId Unique identifier for the sending instance
     * @return SimulationControlEvent for stopping a simulation
     */
    public static SimulationControlEvent stop(String simulationId, String instanceId) {
        return new SimulationControlEvent(Action.STOP, simulationId, instanceId);
    }
    
    // Getters and Setters
    
    public Action getAction() {
        return action;
    }
    
    public void setAction(Action action) {
        this.action = action;
    }
    
    public String getSimulationId() {
        return simulationId;
    }
    
    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    public boolean isStart() {
        return action == Action.START;
    }
    
    public boolean isStop() {
        return action == Action.STOP;
    }
    
    /**
     * Setter for the "start" boolean field from JSON.
     * When set to true, derives action = START for backward compatibility
     * with clients that send {"start": true, ...} instead of {"action": "START", ...}.
     * 
     * <p>Note: When {@code start} is null or false, the existing action is not modified.
     * This means if both "action" and "start" fields are present in JSON, the final
     * action depends on the order of deserialization (typically the last setter wins).
     * For predictable behavior, clients should use either "action" OR "start"/"stop", not both.</p>
     * 
     * @param start If true, sets action to START; if false or null, action is unchanged
     */
    @JsonProperty("start")
    public void setStart(Boolean start) {
        if (Boolean.TRUE.equals(start)) {
            this.action = Action.START;
        }
    }
    
    /**
     * Setter for the "stop" boolean field from JSON.
     * When set to true, derives action = STOP for backward compatibility
     * with clients that send {"stop": true, ...} instead of {"action": "STOP", ...}.
     * 
     * <p>Note: When {@code stop} is null or false, the existing action is not modified.
     * This means if both "action" and "stop" fields are present in JSON, the final
     * action depends on the order of deserialization (typically the last setter wins).
     * For predictable behavior, clients should use either "action" OR "start"/"stop", not both.</p>
     * 
     * @param stop If true, sets action to STOP; if false or null, action is unchanged
     */
    @JsonProperty("stop")
    public void setStop(Boolean stop) {
        if (Boolean.TRUE.equals(stop)) {
            this.action = Action.STOP;
        }
    }
    
    @Override
    public String toString() {
        return "SimulationControlEvent{" +
                "action=" + action +
                ", simulationId='" + simulationId + '\'' +
                ", batchId='" + batchId + '\'' +
                ", farmerId='" + farmerId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
