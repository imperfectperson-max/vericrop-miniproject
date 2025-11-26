package org.vericrop.gui.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.vericrop.dto.SimulationResult;

import java.util.Objects;

/**
 * Represents a persisted simulation run record for historical data and reports.
 */
public class PersistedSimulation {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("farmer_id")
    private String farmerId;
    
    @JsonProperty("scenario_id")
    private String scenarioId;
    
    @JsonProperty("start_time")
    private long startTime;
    
    @JsonProperty("end_time")
    private long endTime;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("completed")
    private boolean completed;
    
    @JsonProperty("final_quality")
    private double finalQuality;
    
    @JsonProperty("initial_quality")
    private double initialQuality;
    
    @JsonProperty("avg_temperature")
    private double avgTemperature;
    
    @JsonProperty("min_temperature")
    private double minTemperature;
    
    @JsonProperty("max_temperature")
    private double maxTemperature;
    
    @JsonProperty("avg_humidity")
    private double avgHumidity;
    
    @JsonProperty("waypoints_count")
    private int waypointsCount;
    
    @JsonProperty("violations_count")
    private int violationsCount;
    
    @JsonProperty("compliance_status")
    private String complianceStatus;
    
    @JsonProperty("origin")
    private String origin;
    
    @JsonProperty("destination")
    private String destination;
    
    @JsonProperty("result_json")
    private String resultJson;
    
    /**
     * Default constructor for JSON deserialization
     */
    public PersistedSimulation() {
        this.id = java.util.UUID.randomUUID().toString();
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Constructor with basic fields
     */
    public PersistedSimulation(String batchId, String farmerId, String scenarioId) {
        this();
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.scenarioId = scenarioId;
        this.status = "STARTED";
        this.completed = false;
    }
    
    /**
     * Create from SimulationResult
     */
    public static PersistedSimulation fromSimulationResult(SimulationResult result, String scenarioId) {
        PersistedSimulation simulation = new PersistedSimulation();
        simulation.setBatchId(result.getBatchId());
        simulation.setFarmerId(result.getFarmerId());
        simulation.setScenarioId(scenarioId);
        simulation.setStartTime(result.getStartTime());
        simulation.setEndTime(result.getEndTime());
        simulation.setStatus(result.getStatus());
        simulation.setCompleted("COMPLETED".equals(result.getStatus()) || "Delivered".equals(result.getStatus()));
        simulation.setFinalQuality(result.getFinalQuality());
        simulation.setAvgTemperature(result.getAvgTemperature());
        simulation.setMinTemperature(result.getMinTemperature());
        simulation.setMaxTemperature(result.getMaxTemperature());
        simulation.setAvgHumidity(result.getAvgHumidity());
        simulation.setWaypointsCount(result.getWaypointsCount());
        simulation.setViolationsCount(result.getViolationsCount());
        simulation.setComplianceStatus(result.getComplianceStatus());
        return simulation;
    }

    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public String getScenarioId() {
        return scenarioId;
    }
    
    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
    
    public double getFinalQuality() {
        return finalQuality;
    }
    
    public void setFinalQuality(double finalQuality) {
        this.finalQuality = finalQuality;
    }
    
    public double getInitialQuality() {
        return initialQuality;
    }
    
    public void setInitialQuality(double initialQuality) {
        this.initialQuality = initialQuality;
    }
    
    public double getAvgTemperature() {
        return avgTemperature;
    }
    
    public void setAvgTemperature(double avgTemperature) {
        this.avgTemperature = avgTemperature;
    }
    
    public double getMinTemperature() {
        return minTemperature;
    }
    
    public void setMinTemperature(double minTemperature) {
        this.minTemperature = minTemperature;
    }
    
    public double getMaxTemperature() {
        return maxTemperature;
    }
    
    public void setMaxTemperature(double maxTemperature) {
        this.maxTemperature = maxTemperature;
    }
    
    public double getAvgHumidity() {
        return avgHumidity;
    }
    
    public void setAvgHumidity(double avgHumidity) {
        this.avgHumidity = avgHumidity;
    }
    
    public int getWaypointsCount() {
        return waypointsCount;
    }
    
    public void setWaypointsCount(int waypointsCount) {
        this.waypointsCount = waypointsCount;
    }
    
    public int getViolationsCount() {
        return violationsCount;
    }
    
    public void setViolationsCount(int violationsCount) {
        this.violationsCount = violationsCount;
    }
    
    public String getComplianceStatus() {
        return complianceStatus;
    }
    
    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
    }
    
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    public String getResultJson() {
        return resultJson;
    }
    
    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistedSimulation that = (PersistedSimulation) o;
        return Objects.equals(id, that.id) && Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, batchId);
    }
    
    @Override
    public String toString() {
        return "PersistedSimulation{" +
                "id='" + id + '\'' +
                ", batchId='" + batchId + '\'' +
                ", farmerId='" + farmerId + '\'' +
                ", status='" + status + '\'' +
                ", completed=" + completed +
                ", finalQuality=" + finalQuality +
                ", startTime=" + startTime +
                '}';
    }
}
