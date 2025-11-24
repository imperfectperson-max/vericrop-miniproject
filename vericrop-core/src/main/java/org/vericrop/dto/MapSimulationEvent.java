package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Event published for map simulation updates during delivery
 */
public class MapSimulationEvent {
    private String batchId;
    private long timestamp;
    private double latitude;
    private double longitude;
    private String locationName;
    private double progress; // 0.0 to 1.0
    private String status;
    private double temperature; // Environmental data
    private double humidity; // Environmental data
    
    public MapSimulationEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public MapSimulationEvent(String batchId, double latitude, double longitude, 
                              String locationName, double progress, String status) {
        this();
        this.batchId = batchId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.progress = progress;
        this.status = status;
    }
    
    public MapSimulationEvent(String batchId, double latitude, double longitude, 
                              String locationName, double progress, String status,
                              double temperature, double humidity) {
        this(batchId, latitude, longitude, locationName, progress, status);
        this.temperature = temperature;
        this.humidity = humidity;
    }
    
    // Getters and setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    
    @JsonProperty("location_name")
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    
    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    
    @Override
    public String toString() {
        // Only include environmental data if it appears to be initialized (non-zero)
        if (temperature != 0.0 || humidity != 0.0) {
            return String.format("MapSimulationEvent{batchId='%s', location='%s', progress=%.1f%%, status='%s', temp=%.1f°C, humidity=%.1f%%}",
                    batchId, locationName, progress * 100, status, temperature, humidity);
        } else {
            return String.format("MapSimulationEvent{batchId='%s', location='%s', progress=%.1f%%, status='%s'}",
                    batchId, locationName, progress * 100, status);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapSimulationEvent that = (MapSimulationEvent) o;
        return timestamp == that.timestamp &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                Double.compare(that.progress, progress) == 0 &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(locationName, that.locationName) &&
                Objects.equals(status, that.status);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, timestamp, latitude, longitude, locationName, progress, status);
    }
}
