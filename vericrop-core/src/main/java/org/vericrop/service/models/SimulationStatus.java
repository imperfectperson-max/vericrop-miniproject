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
            @JsonProperty("batchId") String batchId,
            @JsonProperty("running") boolean running,
            @JsonProperty("currentWaypoint") int currentWaypoint,
            @JsonProperty("totalWaypoints") int totalWaypoints,
            @JsonProperty("currentLocation") RouteWaypoint currentLocation,
            @JsonProperty("currentQualityScore") double currentQualityScore,
            @JsonProperty("spoilageProbability") double spoilageProbability) {
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
    
    public String getBatchId() { return batchId; }
    
    @JsonProperty("isRunning")
    public boolean isRunning() { return running; }
    
    public int getCurrentWaypoint() { return currentWaypoint; }
    public int getTotalWaypoints() { return totalWaypoints; }
    public RouteWaypoint getCurrentLocation() { return currentLocation; }
    public double getCurrentQualityScore() { return currentQualityScore; }
    public double getSpoilageProbability() { return spoilageProbability; }
    
    // Legacy compatibility
    public String getShipmentId() { return batchId; }
}
