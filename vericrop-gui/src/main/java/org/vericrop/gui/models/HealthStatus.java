package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for Health Check Status.
 * Represents the response from the ML service /health endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthStatus {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("time")
    private Long time;
    
    @JsonProperty("model_loaded")
    private Boolean modelLoaded;
    
    @JsonProperty("model_accuracy")
    private String modelAccuracy;
    
    @JsonProperty("classes_loaded")
    private Integer classesLoaded;

    // Constructors
    public HealthStatus() {
    }

    public HealthStatus(String status) {
        this.status = status;
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Boolean getModelLoaded() {
        return modelLoaded;
    }

    public void setModelLoaded(Boolean modelLoaded) {
        this.modelLoaded = modelLoaded;
    }

    public String getModelAccuracy() {
        return modelAccuracy;
    }

    public void setModelAccuracy(String modelAccuracy) {
        this.modelAccuracy = modelAccuracy;
    }

    public Integer getClassesLoaded() {
        return classesLoaded;
    }

    public void setClassesLoaded(Integer classesLoaded) {
        this.classesLoaded = classesLoaded;
    }

    /**
     * Check if the service is healthy
     */
    public boolean isHealthy() {
        return "ok".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
                "status='" + status + '\'' +
                ", modelLoaded=" + modelLoaded +
                ", modelAccuracy='" + modelAccuracy + '\'' +
                ", classesLoaded=" + classesLoaded +
                '}';
    }
}
