package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Data Transfer Object for ML Service Prediction Results.
 * Represents the response from the ML service /predict endpoint.
 * 
 * Maps to the structure returned by docker/ml-service/app.py predict endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PredictionResult {
    
    @JsonProperty("quality_score")
    private Double qualityScore;
    
    @JsonProperty("label")
    private String label;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("report")
    private String report;
    
    @JsonProperty("data_hash")
    private String dataHash;
    
    @JsonProperty("model_accuracy")
    private String modelAccuracy;
    
    @JsonProperty("all_predictions")
    private Map<String, Double> allPredictions;
    
    @JsonProperty("class_id")
    private Integer classId;

    // Constructors
    public PredictionResult() {
    }

    public PredictionResult(Double qualityScore, String label, Double confidence) {
        this.qualityScore = qualityScore;
        this.label = label;
        this.confidence = confidence;
    }

    // Getters and Setters
    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    public String getModelAccuracy() {
        return modelAccuracy;
    }

    public void setModelAccuracy(String modelAccuracy) {
        this.modelAccuracy = modelAccuracy;
    }

    public Map<String, Double> getAllPredictions() {
        return allPredictions;
    }

    public void setAllPredictions(Map<String, Double> allPredictions) {
        this.allPredictions = allPredictions;
    }

    public Integer getClassId() {
        return classId;
    }

    public void setClassId(Integer classId) {
        this.classId = classId;
    }

    @Override
    public String toString() {
        return "PredictionResult{" +
                "qualityScore=" + qualityScore +
                ", label='" + label + '\'' +
                ", confidence=" + confidence +
                ", modelAccuracy='" + modelAccuracy + '\'' +
                '}';
    }
}
