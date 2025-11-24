package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Temperature reading data for a specific shipment/batch at a point in time.
 * Used for historical data retrieval and live streaming.
 */
public class TemperatureReading {
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("temperature")
    private double temperature;
    
    @JsonProperty("sensor_id")
    private String sensorId;
    
    @JsonProperty("location_name")
    private String locationName;
    
    /**
     * Default constructor for JSON deserialization
     */
    public TemperatureReading() {
    }
    
    /**
     * Full constructor
     */
    public TemperatureReading(String batchId, long timestamp, double temperature, 
                             String sensorId, String locationName) {
        this.batchId = batchId;
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.sensorId = sensorId;
        this.locationName = locationName;
    }
    
    /**
     * Convenience constructor without sensor ID
     */
    public TemperatureReading(String batchId, long timestamp, double temperature, String locationName) {
        this(batchId, timestamp, temperature, null, locationName);
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
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public String getSensorId() {
        return sensorId;
    }
    
    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemperatureReading that = (TemperatureReading) o;
        return timestamp == that.timestamp &&
                Double.compare(that.temperature, temperature) == 0 &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(sensorId, that.sensorId) &&
                Objects.equals(locationName, that.locationName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(batchId, timestamp, temperature, sensorId, locationName);
    }
    
    @Override
    public String toString() {
        return "TemperatureReading{" +
                "batchId='" + batchId + '\'' +
                ", timestamp=" + timestamp +
                ", temperature=" + temperature +
                ", sensorId='" + sensorId + '\'' +
                ", locationName='" + locationName + '\'' +
                '}';
    }
}
