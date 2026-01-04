package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Comprehensive delivery report containing route summary and quality metrics.
 */
public class DeliveryReport {
    private final String batchId;
    private final String farmerId;
    private final GeoCoordinate origin;
    private final GeoCoordinate destination;
    private final Scenario scenario;
    private final long startTime;
    private final long endTime;
    private final double totalDistance;
    private final double averageTemperature;
    private final double averageHumidity;
    private final double initialQualityScore;
    private final double finalQualityScore;
    private final double totalQualityDecay;
    private final double spoilageProbability;
    private final boolean deliveredOnTime;
    private final List<Alert> alerts;
    private final List<RouteWaypoint> route;
    private final List<TimelineEvent> timeline;
    
    public DeliveryReport(
            @JsonProperty("batchId") String batchId,
            @JsonProperty("farmerId") String farmerId,
            @JsonProperty("origin") GeoCoordinate origin,
            @JsonProperty("destination") GeoCoordinate destination,
            @JsonProperty("scenario") Scenario scenario,
            @JsonProperty("startTime") long startTime,
            @JsonProperty("endTime") long endTime,
            @JsonProperty("totalDistance") double totalDistance,
            @JsonProperty("averageTemperature") double averageTemperature,
            @JsonProperty("averageHumidity") double averageHumidity,
            @JsonProperty("initialQualityScore") double initialQualityScore,
            @JsonProperty("finalQualityScore") double finalQualityScore,
            @JsonProperty("totalQualityDecay") double totalQualityDecay,
            @JsonProperty("spoilageProbability") double spoilageProbability,
            @JsonProperty("deliveredOnTime") boolean deliveredOnTime,
            @JsonProperty("alerts") List<Alert> alerts,
            @JsonProperty("route") List<RouteWaypoint> route,
            @JsonProperty("timeline") List<TimelineEvent> timeline) {
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.origin = origin;
        this.destination = destination;
        this.scenario = scenario;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalDistance = totalDistance;
        this.averageTemperature = averageTemperature;
        this.averageHumidity = averageHumidity;
        this.initialQualityScore = initialQualityScore;
        this.finalQualityScore = finalQualityScore;
        this.totalQualityDecay = totalQualityDecay;
        this.spoilageProbability = spoilageProbability;
        this.deliveredOnTime = deliveredOnTime;
        this.alerts = alerts;
        this.route = route;
        this.timeline = timeline;
    }
    
    public String getBatchId() { return batchId; }
    public String getFarmerId() { return farmerId; }
    public GeoCoordinate getOrigin() { return origin; }
    public GeoCoordinate getDestination() { return destination; }
    public Scenario getScenario() { return scenario; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getTotalDistance() { return totalDistance; }
    public double getAverageTemperature() { return averageTemperature; }
    public double getAverageHumidity() { return averageHumidity; }
    public double getInitialQualityScore() { return initialQualityScore; }
    public double getFinalQualityScore() { return finalQualityScore; }
    public double getTotalQualityDecay() { return totalQualityDecay; }
    public double getSpoilageProbability() { return spoilageProbability; }
    
    @JsonProperty("isDeliveredOnTime")
    public boolean isDeliveredOnTime() { return deliveredOnTime; }
    
    public List<Alert> getAlerts() { return alerts; }
    public List<RouteWaypoint> getRoute() { return route; }
    public List<TimelineEvent> getTimeline() { return timeline; }
    
    /**
     * Timeline event during delivery.
     */
    public static class TimelineEvent {
        private final long timestamp;
        private final String event;
        private final String description;
        
        public TimelineEvent(
                @JsonProperty("timestamp") long timestamp,
                @JsonProperty("event") String event,
                @JsonProperty("description") String description) {
            this.timestamp = timestamp;
            this.event = event;
            this.description = description;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getEvent() { return event; }
        public String getDescription() { return description; }
    }
}
