package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event representing batch creation in the supply chain.
 * Published to Kafka when a new batch is created with quality assessment.
 */
public class BatchCreatedEvent {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("batch_name")
    private String batchName;
    
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
    
    @JsonProperty("prime_rate")
    private Double primeRate;
    
    @JsonProperty("rejection_rate")
    private Double rejectionRate;
    
    @JsonProperty("image_path")
    private String imagePath;
    
    @JsonProperty("qr_code_path")
    private String qrCodePath;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("status")
    private String status;
    
    // Constructors
    public BatchCreatedEvent() {
    }
    
    public BatchCreatedEvent(String batchId, String batchName, String farmer, String productType,
                           Integer quantity, Double qualityScore, String qualityLabel,
                           Double primeRate, Double rejectionRate, String timestamp) {
        this.batchId = batchId;
        this.batchName = batchName;
        this.farmer = farmer;
        this.productType = productType;
        this.quantity = quantity;
        this.qualityScore = qualityScore;
        this.qualityLabel = qualityLabel;
        this.primeRate = primeRate;
        this.rejectionRate = rejectionRate;
        this.timestamp = timestamp;
        this.status = "created";
        this.eventId = "BATCH_EVENT_" + System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getBatchName() {
        return batchName;
    }
    
    public void setBatchName(String batchName) {
        this.batchName = batchName;
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
    
    @Override
    public String toString() {
        return "BatchCreatedEvent{" +
                "eventId='" + eventId + '\'' +
                ", batchId='" + batchId + '\'' +
                ", batchName='" + batchName + '\'' +
                ", farmer='" + farmer + '\'' +
                ", productType='" + productType + '\'' +
                ", qualityScore=" + qualityScore +
                ", qualityLabel='" + qualityLabel + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
