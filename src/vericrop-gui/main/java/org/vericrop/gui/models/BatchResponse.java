package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for Batch Creation Response.
 * Represents the response from the ML service /batches POST endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchResponse {
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("quality_score")
    private Double qualityScore;
    
    @JsonProperty("prime_rate")
    private Double primeRate;
    
    @JsonProperty("rejection_rate")
    private Double rejectionRate;
    
    @JsonProperty("quality_label")
    private String qualityLabel;
    
    @JsonProperty("data_hash")
    private String dataHash;

    // Constructors
    public BatchResponse() {
    }

    public BatchResponse(String batchId, String status) {
        this.batchId = batchId;
        this.status = status;
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Double getPrimeRate() {
        return primeRate;
    }

    public void setPrimeRate(Double primeRate) {
        this.primeRate = primeRate;
    }

    public Double getRejectionRate() {
        return rejectionRate;
    }

    public void setRejectionRate(Double rejectionRate) {
        this.rejectionRate = rejectionRate;
    }

    public String getQualityLabel() {
        return qualityLabel;
    }

    public void setQualityLabel(String qualityLabel) {
        this.qualityLabel = qualityLabel;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    @Override
    public String toString() {
        return "BatchResponse{" +
                "batchId='" + batchId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", qualityScore=" + qualityScore +
                '}';
    }
}
