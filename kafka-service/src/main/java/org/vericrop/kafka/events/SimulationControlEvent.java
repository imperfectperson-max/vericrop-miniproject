package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event for controlling simulations across application instances.
 * Published to the "simulation-control" topic when a simulation is started or stopped
 * from the ProducerController.
 * 
 * JSON payload format:
 * {"action":"START"|"STOP","simulationId":"<uuid>","timestamp":<epochMs>,"instanceId":"<instanceUuid>"}
 */
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
