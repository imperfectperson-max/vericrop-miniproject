package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Data Transfer Object for fruit quality evaluation requests.
 * Contains image data and metadata for ML-based quality assessment.
 */
public class EvaluationRequest {
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("image_path")
    private String imagePath;
    
    @JsonProperty("image_base64")
    private String imageBase64;
    
    @JsonProperty("product_type")
    private String productType;
    
    @JsonProperty("farmer_id")
    private String farmerId;
    
    private long timestamp;

    public EvaluationRequest() {
        this.timestamp = System.currentTimeMillis();
    }

    public EvaluationRequest(String batchId, String imagePath, String productType) {
        this();
        this.batchId = batchId;
        this.imagePath = imagePath;
        this.productType = productType;
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
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
        EvaluationRequest that = (EvaluationRequest) o;
        return timestamp == that.timestamp &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(imagePath, that.imagePath) &&
                Objects.equals(productType, that.productType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, imagePath, productType, timestamp);
    }

    @Override
    public String toString() {
        return String.format("EvaluationRequest{batchId='%s', productType='%s', farmerId='%s'}",
                batchId, productType, farmerId);
    }
}
