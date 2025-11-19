package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class LogisticsEvent {
    private String batchId;
    private String status; // "CREATED", "IN_TRANSIT", "AT_WAREHOUSE", "DELIVERED"
    private double temperature;
    private double humidity;
    private String location;
    private String route;
    private String vehicleId;
    private String driverId;
    private long timestamp;
    private double latitude;
    private double longitude;

    public LogisticsEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public LogisticsEvent(String batchId, String status, double temperature,
                          double humidity, String location) {
        this();
        this.batchId = batchId;
        this.status = status;
        this.temperature = temperature;
        this.humidity = humidity;
        this.location = location;
    }

    // Getters and Setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    @JsonProperty("vehicle_id")
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    @JsonProperty("driver_id")
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @Override
    public String toString() {
        return String.format("LogisticsEvent{batchId='%s', status='%s', temp=%.1f°C, location='%s'}",
                batchId, status, temperature, location);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogisticsEvent that = (LogisticsEvent) o;
        return Double.compare(that.temperature, temperature) == 0 &&
                Double.compare(that.humidity, humidity) == 0 &&
                timestamp == that.timestamp &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(status, that.status) &&
                Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, status, temperature, humidity, location, timestamp);
    }
}