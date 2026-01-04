package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a waypoint along a delivery route with environmental readings.
 */
public class RouteWaypoint {
    private final GeoCoordinate location;
    private final long timestamp;
    private final double temperature;
    private final double humidity;
    
    public RouteWaypoint(
            @JsonProperty("location") GeoCoordinate location,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("temperature") double temperature,
            @JsonProperty("humidity") double humidity) {
        this.location = location;
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.humidity = humidity;
    }
    
    public GeoCoordinate getLocation() { return location; }
    public long getTimestamp() { return timestamp; }
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    
    @Override
    public String toString() {
        return String.format("Waypoint[%s, temp=%.1f°C, humidity=%.1f%%]",
                location.toString(), temperature, humidity);
    }
}
