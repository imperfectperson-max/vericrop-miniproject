package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for quality evaluation results.
 * Contains the ML model's assessment of fruit quality.
 */
public class EvaluationResult {
    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("quality_score")
    private double qualityScore;

    @JsonProperty("pass_fail")
    private String passFail;

    private String prediction;

    private double confidence;

    @JsonProperty("data_hash")
    private String dataHash;

    private Map<String, Object> metadata;

    private long timestamp;

    public EvaluationResult() {
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }

    public EvaluationResult(String batchId, double qualityScore, String passFail, String dataHash) {
        this();
        this.batchId = batchId;
        this.qualityScore = qualityScore;
        this.passFail = passFail;
        this.dataHash = dataHash;
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getPassFail() {
        return passFail;
    }

    public void setPassFail(String passFail) {
        this.passFail = passFail;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvaluationResult that = (EvaluationResult) o;
        return Double.compare(that.qualityScore, qualityScore) == 0 &&
                timestamp == that.timestamp &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(passFail, that.passFail) &&
                Objects.equals(dataHash, that.dataHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, qualityScore, passFail, dataHash, timestamp);
    }

    @Override
    public String toString() {
        return String.format("EvaluationResult{batchId='%s', score=%.2f, passFail='%s'}",
                batchId, qualityScore, passFail);
    }
}