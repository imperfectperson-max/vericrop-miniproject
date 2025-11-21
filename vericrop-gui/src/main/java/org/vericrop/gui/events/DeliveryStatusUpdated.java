package org.vericrop.gui.events;

/**
 * Event published when a delivery status changes.
 */
public class DeliveryStatusUpdated extends DomainEvent {
    private final String shipmentId;
    private final String status;
    private final String location;
    private final String details;
    
    public DeliveryStatusUpdated(String shipmentId, String status, String location, String details) {
        super();
        this.shipmentId = shipmentId;
        this.status = status;
        this.location = location;
        this.details = details;
    }
    
    public String getShipmentId() {
        return shipmentId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getLocation() {
        return location;
    }
    
    public String getDetails() {
        return details;
    }
    
    @Override
    public String toString() {
        return "DeliveryStatusUpdated{" +
               "shipmentId='" + shipmentId + '\'' +
               ", status='" + status + '\'' +
               ", location='" + location + '\'' +
               ", details='" + details + '\'' +
               ", " + super.toString() +
               '}';
    }
}
