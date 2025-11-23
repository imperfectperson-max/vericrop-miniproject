package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Data Transfer Object for Batch Records.
 * Represents a batch of produce in the supply chain with quality and metadata.
 * 
 * This DTO matches the structure expected by the ML service API
 * and the database schema for batch metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchRecord {
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("farmer")
    private String farmer;
    
    @JsonProperty("product_type")
    private String productType;
    
    @JsonProperty("quantity")
    private Integer quantity;
    
    @JsonProperty("quality_score")
    private Double qualityScore;
    
    @JsonProperty("quality_label")
    private String qualityLabel;
    
    @JsonProperty("data_hash")
    private String dataHash;
    
    @JsonProperty("timestamp")
    private String timestamp;  // ISO 8601 format
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("prime_rate")
    private Double primeRate;
    
    @JsonProperty("rejection_rate")
    private Double rejectionRate;
    
    @JsonProperty("image_path")
    private String imagePath;
    
    @JsonProperty("qr_code_path")
    private String qrCodePath;

    // Database-specific fields (not sent to ML service)
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public BatchRecord() {
    }

    public BatchRecord(String batchId, String name, String farmer, String productType, Integer quantity) {
        this.batchId = batchId;
        this.name = name;
        this.farmer = farmer;
        this.productType = productType;
        this.quantity = quantity;
    }

    // Builder pattern for convenient object creation
    public static class Builder {
        private final BatchRecord batch = new BatchRecord();

        public Builder batchId(String batchId) {
            batch.batchId = batchId;
            return this;
        }

        public Builder name(String name) {
            batch.name = name;
            return this;
        }

        public Builder farmer(String farmer) {
            batch.farmer = farmer;
            return this;
        }

        public Builder productType(String productType) {
            batch.productType = productType;
            return this;
        }

        public Builder quantity(Integer quantity) {
            batch.quantity = quantity;
            return this;
        }

        public Builder qualityScore(Double qualityScore) {
            batch.qualityScore = qualityScore;
            return this;
        }

        public Builder qualityLabel(String qualityLabel) {
            batch.qualityLabel = qualityLabel;
            return this;
        }

        public Builder dataHash(String dataHash) {
            batch.dataHash = dataHash;
            return this;
        }

        public Builder timestamp(String timestamp) {
            batch.timestamp = timestamp;
            return this;
        }

        public Builder status(String status) {
            batch.status = status;
            return this;
        }

        public Builder primeRate(Double primeRate) {
            batch.primeRate = primeRate;
            return this;
        }

        public Builder rejectionRate(Double rejectionRate) {
            batch.rejectionRate = rejectionRate;
            return this;
        }

        public BatchRecord build() {
            return batch;
        }
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFarmer() {
        return farmer;
    }

    public void setFarmer(String farmer) {
        this.farmer = farmer;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getQrCodePath() {
        return qrCodePath;
    }

    public void setQrCodePath(String qrCodePath) {
        this.qrCodePath = qrCodePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchRecord that = (BatchRecord) o;
        return Objects.equals(batchId, that.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId);
    }

    @Override
    public String toString() {
        return "BatchRecord{" +
                "batchId='" + batchId + '\'' +
                ", name='" + name + '\'' +
                ", farmer='" + farmer + '\'' +
                ", productType='" + productType + '\'' +
                ", quantity=" + quantity +
                ", qualityScore=" + qualityScore +
                ", qualityLabel='" + qualityLabel + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
