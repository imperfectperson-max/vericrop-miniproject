package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a geographic coordinate with latitude, longitude, and name.
 */
public class GeoCoordinate {
    private final double latitude;
    private final double longitude;
    private final String name;
    
    public GeoCoordinate(
            @JsonProperty("latitude") double latitude,
            @JsonProperty("longitude") double longitude,
            @JsonProperty("name") String name) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
    }
    
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getName() { return name; }
    
    @Override
    public String toString() {
        return String.format("%s (%.4f, %.4f)", name, latitude, longitude);
    }
}
