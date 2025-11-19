package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class QualityAlertEvent {
    private String batchId;
    private String alertType; // "TEMPERATURE_BREACH", "QUALITY_DROP", "HUMIDITY_BREACH"
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private String message;
    private double currentValue;
    private double thresholdValue;
    private long timestamp;
    private String sensorId;
    private String location;

    public QualityAlertEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public QualityAlertEvent(String batchId, String alertType, String severity,
                             String message, double currentValue, double thresholdValue) {
        this();
        this.batchId = batchId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.currentValue = currentValue;
        this.thresholdValue = thresholdValue;
    }

    // Getters and Setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    @JsonProperty("alert_type")
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @JsonProperty("current_value")
    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }

    @JsonProperty("threshold_value")
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @JsonProperty("sensor_id")
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Override
    public String toString() {
        return String.format("QualityAlertEvent{batchId='%s', type='%s', severity='%s', message='%s'}",
                batchId, alertType, severity, message);
    }
}