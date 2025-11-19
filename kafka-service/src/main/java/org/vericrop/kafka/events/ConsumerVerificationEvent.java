package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ConsumerVerificationEvent {
    private String batchId;
    private String productType;
    private String verificationResult; // "VERIFIED", "REJECTED", "PENDING"
    private String consumerId;
    private String qrCode;
    private long timestamp;
    private String verificationData;
    private String location;

    public ConsumerVerificationEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public ConsumerVerificationEvent(String batchId, String productType,
                                     String verificationResult, String consumerId) {
        this();
        this.batchId = batchId;
        this.productType = productType;
        this.verificationResult = verificationResult;
        this.consumerId = consumerId;
    }

    // Getters and Setters
    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    @JsonProperty("product_type")
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    @JsonProperty("verification_result")
    public String getVerificationResult() { return verificationResult; }
    public void setVerificationResult(String verificationResult) { this.verificationResult = verificationResult; }

    @JsonProperty("consumer_id")
    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    @JsonProperty("qr_code")
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @JsonProperty("verification_data")
    public String getVerificationData() { return verificationData; }
    public void setVerificationData(String verificationData) { this.verificationData = verificationData; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Override
    public String toString() {
        return String.format("ConsumerVerificationEvent{batchId='%s', result='%s', consumer='%s'}",
                batchId, verificationResult, consumerId);
    }
}