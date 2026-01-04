package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Event for supplier compliance status updates.
 */
public class SupplierComplianceEvent {
    private String farmerId;
    private String eventType; // "DELIVERY_RECORDED", "COMPLIANCE_UPDATED", "WARNING", "NON_COMPLIANT"
    private String complianceStatus; // "COMPLIANT", "WARNING", "NON_COMPLIANT", "NEW"
    private boolean compliant;
    private int totalDeliveries;
    private int successfulDeliveries;
    private int spoiledShipments;
    private double successRate;
    private double spoilageRate;
    private double averageQualityDecay;
    private long timestamp;
    
    public SupplierComplianceEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SupplierComplianceEvent(String farmerId, String eventType, String complianceStatus, boolean compliant) {
        this();
        this.farmerId = farmerId;
        this.eventType = eventType;
        this.complianceStatus = complianceStatus;
        this.compliant = compliant;
    }
    
    // Getters and Setters
    @JsonProperty("farmer_id")
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    
    @JsonProperty("event_type")
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    @JsonProperty("compliance_status")
    public String getComplianceStatus() { return complianceStatus; }
    public void setComplianceStatus(String complianceStatus) { this.complianceStatus = complianceStatus; }
    
    public boolean isCompliant() { return compliant; }
    public void setCompliant(boolean compliant) { this.compliant = compliant; }
    
    @JsonProperty("total_deliveries")
    public int getTotalDeliveries() { return totalDeliveries; }
    public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }
    
    @JsonProperty("successful_deliveries")
    public int getSuccessfulDeliveries() { return successfulDeliveries; }
    public void setSuccessfulDeliveries(int successfulDeliveries) { this.successfulDeliveries = successfulDeliveries; }
    
    @JsonProperty("spoiled_shipments")
    public int getSpoiledShipments() { return spoiledShipments; }
    public void setSpoiledShipments(int spoiledShipments) { this.spoiledShipments = spoiledShipments; }
    
    @JsonProperty("success_rate")
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    
    @JsonProperty("spoilage_rate")
    public double getSpoilageRate() { return spoilageRate; }
    public void setSpoilageRate(double spoilageRate) { this.spoilageRate = spoilageRate; }
    
    @JsonProperty("average_quality_decay")
    public double getAverageQualityDecay() { return averageQualityDecay; }
    public void setAverageQualityDecay(double averageQualityDecay) { this.averageQualityDecay = averageQualityDecay; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return String.format("SupplierComplianceEvent{farmerId='%s', eventType='%s', status='%s', compliant=%b}",
                           farmerId, eventType, complianceStatus, compliant);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierComplianceEvent that = (SupplierComplianceEvent) o;
        return compliant == that.compliant &&
               timestamp == that.timestamp &&
               Objects.equals(farmerId, that.farmerId) &&
               Objects.equals(eventType, that.eventType) &&
               Objects.equals(complianceStatus, that.complianceStatus);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(farmerId, eventType, complianceStatus, compliant, timestamp);
    }
}
