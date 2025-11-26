package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DTO representing the complete result of a simulation run.
 * Includes temperature series data for rendering the temperature monitoring graph.
 */
public class SimulationResult {

    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("farmer_id")
    private String farmerId;

    @JsonProperty("start_time")
    private long startTime;

    @JsonProperty("end_time")
    private long endTime;

    @JsonProperty("status")
    private String status;

    @JsonProperty("final_quality")
    private double finalQuality;

    @JsonProperty("temperature_series")
    private List<TemperatureDataPoint> temperatureSeries;

    @JsonProperty("humidity_series")
    private List<HumidityDataPoint> humiditySeries;

    @JsonProperty("waypoints_count")
    private int waypointsCount;

    @JsonProperty("avg_temperature")
    private double avgTemperature;

    @JsonProperty("min_temperature")
    private double minTemperature;

    @JsonProperty("max_temperature")
    private double maxTemperature;

    @JsonProperty("avg_humidity")
    private double avgHumidity;

    @JsonProperty("compliance_status")
    private String complianceStatus;

    @JsonProperty("violations_count")
    private int violationsCount;

    /**
     * Default constructor for JSON deserialization
     */
    public SimulationResult() {
        this.temperatureSeries = new ArrayList<>();
        this.humiditySeries = new ArrayList<>();
    }

    /**
     * Constructor with basic info
     */
    public SimulationResult(String batchId, String farmerId) {
        this();
        this.batchId = batchId;
        this.farmerId = farmerId;
    }

    /**
     * Full constructor
     */
    public SimulationResult(String batchId, String farmerId, long startTime, long endTime,
                            String status, double finalQuality,
                            List<TemperatureDataPoint> temperatureSeries,
                            List<HumidityDataPoint> humiditySeries) {
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.finalQuality = finalQuality;
        this.temperatureSeries = temperatureSeries != null ? temperatureSeries : new ArrayList<>();
        this.humiditySeries = humiditySeries != null ? humiditySeries : new ArrayList<>();
        calculateStatistics();
    }

    /**
     * Add a temperature data point
     */
    public void addTemperaturePoint(long timestamp, double temperature, String location) {
        this.temperatureSeries.add(new TemperatureDataPoint(timestamp, temperature, location));
    }

    /**
     * Add a humidity data point
     */
    public void addHumidityPoint(long timestamp, double humidity, String location) {
        this.humiditySeries.add(new HumidityDataPoint(timestamp, humidity, location));
    }

    /**
     * Calculate statistics from the temperature series
     */
    public void calculateStatistics() {
        if (temperatureSeries == null || temperatureSeries.isEmpty()) {
            return;
        }

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int violationCount = 0;

        for (TemperatureDataPoint point : temperatureSeries) {
            double temp = point.getTemperature();
            sum += temp;
            min = Math.min(min, temp);
            max = Math.max(max, temp);
            
            // Temperature compliance check (2-8°C for cold chain)
            if (temp < 2.0 || temp > 8.0) {
                violationCount++;
            }
        }

        this.avgTemperature = sum / temperatureSeries.size();
        this.minTemperature = min;
        this.maxTemperature = max;
        this.violationsCount = violationCount;
        this.complianceStatus = violationCount == 0 ? "COMPLIANT" : "NON_COMPLIANT";
        this.waypointsCount = temperatureSeries.size();

        // Calculate average humidity
        if (humiditySeries != null && !humiditySeries.isEmpty()) {
            double humiditySum = 0;
            for (HumidityDataPoint point : humiditySeries) {
                humiditySum += point.getHumidity();
            }
            this.avgHumidity = humiditySum / humiditySeries.size();
        }
    }

    // Getters and setters

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

    public double getFinalQuality() {
        return finalQuality;
    }

    public void setFinalQuality(double finalQuality) {
        this.finalQuality = finalQuality;
    }

    public List<TemperatureDataPoint> getTemperatureSeries() {
        return temperatureSeries;
    }

    public void setTemperatureSeries(List<TemperatureDataPoint> temperatureSeries) {
        this.temperatureSeries = temperatureSeries;
    }

    public List<HumidityDataPoint> getHumiditySeries() {
        return humiditySeries;
    }

    public void setHumiditySeries(List<HumidityDataPoint> humiditySeries) {
        this.humiditySeries = humiditySeries;
    }

    public int getWaypointsCount() {
        return waypointsCount;
    }

    public void setWaypointsCount(int waypointsCount) {
        this.waypointsCount = waypointsCount;
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

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
    }

    public int getViolationsCount() {
        return violationsCount;
    }

    public void setViolationsCount(int violationsCount) {
        this.violationsCount = violationsCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimulationResult that = (SimulationResult) o;
        return startTime == that.startTime &&
                endTime == that.endTime &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(farmerId, that.farmerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, farmerId, startTime, endTime);
    }

    @Override
    public String toString() {
        return "SimulationResult{" +
                "batchId='" + batchId + '\'' +
                ", farmerId='" + farmerId + '\'' +
                ", status='" + status + '\'' +
                ", finalQuality=" + finalQuality +
                ", temperaturePoints=" + (temperatureSeries != null ? temperatureSeries.size() : 0) +
                ", avgTemperature=" + avgTemperature +
                ", complianceStatus='" + complianceStatus + '\'' +
                '}';
    }

    /**
     * Nested class for temperature data points
     */
    public static class TemperatureDataPoint {
        @JsonProperty("timestamp")
        private long timestamp;

        @JsonProperty("temperature")
        private double temperature;

        @JsonProperty("location")
        private String location;

        public TemperatureDataPoint() {
        }

        public TemperatureDataPoint(long timestamp, double temperature, String location) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.location = location;
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

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return "TemperatureDataPoint{" +
                    "timestamp=" + timestamp +
                    ", temperature=" + temperature +
                    ", location='" + location + '\'' +
                    '}';
        }
    }

    /**
     * Nested class for humidity data points
     */
    public static class HumidityDataPoint {
        @JsonProperty("timestamp")
        private long timestamp;

        @JsonProperty("humidity")
        private double humidity;

        @JsonProperty("location")
        private String location;

        public HumidityDataPoint() {
        }

        public HumidityDataPoint(long timestamp, double humidity, String location) {
            this.timestamp = timestamp;
            this.humidity = humidity;
            this.location = location;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public double getHumidity() {
            return humidity;
        }

        public void setHumidity(double humidity) {
            this.humidity = humidity;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return "HumidityDataPoint{" +
                    "timestamp=" + timestamp +
                    ", humidity=" + humidity +
                    ", location='" + location + '\'' +
                    '}';
        }
    }
}
