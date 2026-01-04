package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event representing an order placed by a consumer/buyer.
 * Published to Kafka when an order is created, and triggers batch quality disclosure.
 */
public class OrderEvent {
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("order_id")
    private String orderId;
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("buyer")
    private String buyer;
    
    @JsonProperty("seller")
    private String seller;
    
    @JsonProperty("quantity_ordered")
    private Integer quantityOrdered;
    
    @JsonProperty("price_per_unit")
    private Double pricePerUnit;
    
    @JsonProperty("total_price")
    private Double totalPrice;
    
    @JsonProperty("quality_disclosed")
    private Boolean qualityDisclosed;
    
    @JsonProperty("quality_score")
    private Double qualityScore;
    
    @JsonProperty("prime_rate")
    private Double primeRate;
    
    @JsonProperty("rejection_rate")
    private Double rejectionRate;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("delivery_address")
    private String deliveryAddress;
    
    // Constructors
    public OrderEvent() {
    }
    
    public OrderEvent(String orderId, String batchId, String buyer, String seller,
                     Integer quantityOrdered, Double pricePerUnit, String timestamp) {
        this.orderId = orderId;
        this.batchId = batchId;
        this.buyer = buyer;
        this.seller = seller;
        this.quantityOrdered = quantityOrdered;
        this.pricePerUnit = pricePerUnit;
        this.totalPrice = quantityOrdered * pricePerUnit;
        this.timestamp = timestamp;
        this.status = "pending";
        this.qualityDisclosed = false;
        this.eventId = "ORDER_EVENT_" + System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getBuyer() {
        return buyer;
    }
    
    public void setBuyer(String buyer) {
        this.buyer = buyer;
    }
    
    public String getSeller() {
        return seller;
    }
    
    public void setSeller(String seller) {
        this.seller = seller;
    }
    
    public Integer getQuantityOrdered() {
        return quantityOrdered;
    }
    
    public void setQuantityOrdered(Integer quantityOrdered) {
        this.quantityOrdered = quantityOrdered;
    }
    
    public Double getPricePerUnit() {
        return pricePerUnit;
    }
    
    public void setPricePerUnit(Double pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
    
    public Double getTotalPrice() {
        return totalPrice;
    }
    
    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }
    
    public Boolean getQualityDisclosed() {
        return qualityDisclosed;
    }
    
    public void setQualityDisclosed(Boolean qualityDisclosed) {
        this.qualityDisclosed = qualityDisclosed;
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
    
    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
    
    /**
     * Disclose quality information to the buyer for price negotiation
     */
    public void discloseQuality(Double qualityScore, Double primeRate, Double rejectionRate) {
        this.qualityScore = qualityScore;
        this.primeRate = primeRate;
        this.rejectionRate = rejectionRate;
        this.qualityDisclosed = true;
    }
    
    @Override
    public String toString() {
        return "OrderEvent{" +
                "eventId='" + eventId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", batchId='" + batchId + '\'' +
                ", buyer='" + buyer + '\'' +
                ", seller='" + seller + '\'' +
                ", quantityOrdered=" + quantityOrdered +
                ", totalPrice=" + totalPrice +
                ", qualityDisclosed=" + qualityDisclosed +
                ", status='" + status + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
