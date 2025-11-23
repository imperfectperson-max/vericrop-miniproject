package org.vericrop.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Performance metrics for a supplier (farmer).
 */
public class SupplierMetrics {
    private final String farmerId;
    private final int totalDeliveries;
    private final int successfulDeliveries;
    private final int spoiledShipments;
    private final double averageQualityDecay;
    private final double averageDeliveryTime;
    private final double successRate;
    
    public SupplierMetrics(
            @JsonProperty("farmerId") String farmerId,
            @JsonProperty("totalDeliveries") int totalDeliveries,
            @JsonProperty("successfulDeliveries") int successfulDeliveries,
            @JsonProperty("spoiledShipments") int spoiledShipments,
            @JsonProperty("averageQualityDecay") double averageQualityDecay,
            @JsonProperty("averageDeliveryTime") double averageDeliveryTime) {
        this.farmerId = farmerId;
        this.totalDeliveries = totalDeliveries;
        this.successfulDeliveries = successfulDeliveries;
        this.spoiledShipments = spoiledShipments;
        this.averageQualityDecay = averageQualityDecay;
        this.averageDeliveryTime = averageDeliveryTime;
        this.successRate = totalDeliveries > 0 
            ? (double) successfulDeliveries / totalDeliveries 
            : 0.0;
    }
    
    public String getFarmerId() { return farmerId; }
    public int getTotalDeliveries() { return totalDeliveries; }
    public int getSuccessfulDeliveries() { return successfulDeliveries; }
    public int getSpoiledShipments() { return spoiledShipments; }
    public double getAverageQualityDecay() { return averageQualityDecay; }
    public double getAverageDeliveryTime() { return averageDeliveryTime; }
    
    @JsonProperty("successRate")
    public double getSuccessRate() { return successRate; }
}
