package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Standardized simulation event payload containing GPS coordinates,
 * environmental data, and delivery status information.
 * Used for realtime updates to UI components and external clients.
 */
public class SimulationEvent {
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("latitude")
    private double latitude;
    
    @JsonProperty("longitude")
    private double longitude;
    
    @JsonProperty("location_name")
    private String locationName;
    
    @JsonProperty("temperature")
    private double temperature;
    
    @JsonProperty("humidity")
    private double humidity;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("eta")
    private long eta;
    
    @JsonProperty("progress_percent")
    private double progressPercent;
    
    @JsonProperty("current_waypoint")
    private int currentWaypoint;
    
    @JsonProperty("total_waypoints")
    private int totalWaypoints;
    
    @JsonProperty("farmer_id")
    private String farmerId;
    
    @JsonProperty("event_type")
    private EventType eventType;
    
    /**
     * Type of simulation event
     */
    public enum EventType {
        STARTED,
        GPS_UPDATE,
        TEMPERATURE_UPDATE,
        STATUS_CHANGE,
        ETA_UPDATE,
        COMPLETED,
        ERROR
    }
    
    /**
     * Default constructor for JSON deserialization
     */
    public SimulationEvent() {
    }
    
    /**
     * Full constructor
     */
    public SimulationEvent(String batchId, long timestamp, double latitude, double longitude,
                          String locationName, double temperature, double humidity,
                          String status, long eta, double progressPercent,
                          int currentWaypoint, int totalWaypoints, String farmerId,
                          EventType eventType) {
        this.batchId = batchId;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.temperature = temperature;
        this.humidity = humidity;
        this.status = status;
        this.eta = eta;
        this.progressPercent = progressPercent;
        this.currentWaypoint = currentWaypoint;
        this.totalWaypoints = totalWaypoints;
        this.farmerId = farmerId;
        this.eventType = eventType;
    }
    
    // Getters and setters
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public double getHumidity() {
        return humidity;
    }
    
    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public long getEta() {
        return eta;
    }
    
    public void setEta(long eta) {
        this.eta = eta;
    }
    
    public double getProgressPercent() {
        return progressPercent;
    }
    
    public void setProgressPercent(double progressPercent) {
        this.progressPercent = progressPercent;
    }
    
    public int getCurrentWaypoint() {
        return currentWaypoint;
    }
    
    public void setCurrentWaypoint(int currentWaypoint) {
        this.currentWaypoint = currentWaypoint;
    }
    
    public int getTotalWaypoints() {
        return totalWaypoints;
    }
    
    public void setTotalWaypoints(int totalWaypoints) {
        this.totalWaypoints = totalWaypoints;
    }
    
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimulationEvent that = (SimulationEvent) o;
        return timestamp == that.timestamp &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                Double.compare(that.temperature, temperature) == 0 &&
                Double.compare(that.humidity, humidity) == 0 &&
                eta == that.eta &&
                Double.compare(that.progressPercent, progressPercent) == 0 &&
                currentWaypoint == that.currentWaypoint &&
                totalWaypoints == that.totalWaypoints &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(locationName, that.locationName) &&
                Objects.equals(status, that.status) &&
                Objects.equals(farmerId, that.farmerId) &&
                eventType == that.eventType;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, timestamp, latitude, longitude, locationName,
                temperature, humidity, status, eta, progressPercent,
                currentWaypoint, totalWaypoints, farmerId, eventType);
    }
    
    @Override
    public String toString() {
        return "SimulationEvent{" +
                "batchId='" + batchId + '\'' +
                ", timestamp=" + timestamp +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", locationName='" + locationName + '\'' +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", status='" + status + '\'' +
                ", eta=" + eta +
                ", progressPercent=" + progressPercent +
                ", currentWaypoint=" + currentWaypoint +
                ", totalWaypoints=" + totalWaypoints +
                ", farmerId='" + farmerId + '\'' +
                ", eventType=" + eventType +
                '}';
    }
}
