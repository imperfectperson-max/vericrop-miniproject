package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status information for an active delivery simulation.
 */
public class SimulationStatus {
    private final String batchId;
    private final boolean running;
    private final int currentWaypoint;
    private final int totalWaypoints;
    private final RouteWaypoint currentLocation;
    private final double currentQualityScore;
    private final double spoilageProbability;
    
    public SimulationStatus(
            String batchId,
            boolean running,
            int currentWaypoint,
            int totalWaypoints,
            RouteWaypoint currentLocation,
            double currentQualityScore,
            double spoilageProbability) {
        this.batchId = batchId;
        this.running = running;
        this.currentWaypoint = currentWaypoint;
        this.totalWaypoints = totalWaypoints;
        this.currentLocation = currentLocation;
        this.currentQualityScore = currentQualityScore;
        this.spoilageProbability = spoilageProbability;
    }
    
    // Constructor for backwards compatibility
    public SimulationStatus(String batchId, boolean running, int currentWaypoint,
                          int totalWaypoints, RouteWaypoint currentLocation) {
        this(batchId, running, currentWaypoint, totalWaypoints, currentLocation, 100.0, 0.0);
    }
    
    @JsonProperty("batchId")
    public String getBatchId() { return batchId; }
    
    @JsonProperty("isRunning")
    public boolean isRunning() { return running; }
    
    @JsonProperty("currentWaypoint")
    public int getCurrentWaypoint() { return currentWaypoint; }
    
    @JsonProperty("totalWaypoints")
    public int getTotalWaypoints() { return totalWaypoints; }
    
    @JsonProperty("currentLocation")
    public RouteWaypoint getCurrentLocation() { return currentLocation; }
    
    @JsonProperty("currentQualityScore")
    public double getCurrentQualityScore() { return currentQualityScore; }
    
    @JsonProperty("spoilageProbability")
    public double getSpoilageProbability() { return spoilageProbability; }
    
    // Legacy compatibility
    public String getShipmentId() { return batchId; }
}
