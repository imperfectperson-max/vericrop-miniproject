package org.vericrop.constants;

/**
 * Shared status constants for shipment lifecycle.
 * Used across controllers (Producer, Logistics, Consumer) to ensure consistent
 * status string comparison in cross-process/cross-instance scenarios.
 * 
 * <p>Status progression: AWAITING_SHIPMENT → IN_TRANSIT → APPROACHING_DESTINATION → DELIVERED</p>
 */
public final class ShipmentStatus {
    
    // Prevent instantiation
    private ShipmentStatus() {}
    
    /** Initial status when batch is created but not yet dispatched */
    public static final String AWAITING_SHIPMENT = "AWAITING_SHIPMENT";
    
    /** Shipment is created and ready for dispatch */
    public static final String CREATED = "CREATED";
    
    /** Shipment has been dispatched from origin */
    public static final String DISPATCHED = "DISPATCHED";
    
    /** Shipment is actively in transit */
    public static final String IN_TRANSIT = "IN_TRANSIT";
    
    /** Shipment is approaching the destination */
    public static final String APPROACHING_DESTINATION = "APPROACHING_DESTINATION";
    
    /** Alternative naming: approaching */
    public static final String APPROACHING = "APPROACHING";
    
    /** Shipment has arrived at warehouse */
    public static final String AT_WAREHOUSE = "AT_WAREHOUSE";
    
    /** Shipment has been delivered to final destination */
    public static final String DELIVERED = "DELIVERED";
    
    /** Shipment was stopped manually */
    public static final String STOPPED = "STOPPED";
    
    // Progress thresholds (percentage) for status determination
    /** Progress threshold for departing status */
    public static final double PROGRESS_DEPARTING_THRESHOLD = 10.0;
    
    /** Progress threshold for en route status */
    public static final double PROGRESS_EN_ROUTE_THRESHOLD = 30.0;
    
    /** Progress threshold for approaching status */
    public static final double PROGRESS_APPROACHING_THRESHOLD = 70.0;
    
    /** Progress threshold for at warehouse status */
    public static final double PROGRESS_AT_WAREHOUSE_THRESHOLD = 90.0;
    
    /** Progress threshold for completed/delivered status */
    public static final double PROGRESS_COMPLETE = 100.0;
    
    /**
     * Determine status based on progress percentage.
     * 
     * @param progressPercent Progress as percentage (0-100)
     * @return Status string
     */
    public static String fromProgress(double progressPercent) {
        if (progressPercent >= PROGRESS_COMPLETE) {
            return DELIVERED;
        } else if (progressPercent >= PROGRESS_AT_WAREHOUSE_THRESHOLD) {
            return AT_WAREHOUSE;
        } else if (progressPercent >= PROGRESS_APPROACHING_THRESHOLD) {
            return APPROACHING_DESTINATION;
        } else if (progressPercent >= PROGRESS_DEPARTING_THRESHOLD) {
            // From 10% to 70% - both departing and en route phases are IN_TRANSIT
            return IN_TRANSIT;
        } else {
            return CREATED;
        }
    }
    
    /**
     * Check if a status string represents a delivered state (case-insensitive).
     * 
     * @param status Status string to check
     * @return true if status represents delivered state
     */
    public static boolean isDelivered(String status) {
        return DELIVERED.equalsIgnoreCase(status);
    }
    
    /**
     * Check if a status string represents an in-transit state (case-insensitive).
     * 
     * @param status Status string to check
     * @return true if status represents in-transit state
     */
    public static boolean isInTransit(String status) {
        return IN_TRANSIT.equalsIgnoreCase(status) || 
               DISPATCHED.equalsIgnoreCase(status) ||
               "En Route".equalsIgnoreCase(status) ||
               "Departing".equalsIgnoreCase(status);
    }
    
    /**
     * Check if a status string represents an approaching state (case-insensitive).
     * 
     * @param status Status string to check
     * @return true if status represents approaching state
     */
    public static boolean isApproaching(String status) {
        return APPROACHING.equalsIgnoreCase(status) || 
               APPROACHING_DESTINATION.equalsIgnoreCase(status) ||
               AT_WAREHOUSE.equalsIgnoreCase(status);
    }
    
    /**
     * Normalize a status string to a canonical form.
     * Handles various formats from different sources (Kafka events, UI, etc.).
     * 
     * <p>For recognized statuses, returns the canonical uppercase constant.
     * For unrecognized statuses, returns the original string as-is to preserve
     * new/unknown statuses from future features or external systems.</p>
     * 
     * @param status Status string to normalize
     * @return Normalized status string (canonical constant or original if not recognized)
     */
    public static String normalize(String status) {
        if (status == null || status.trim().isEmpty()) {
            return AWAITING_SHIPMENT;
        }
        
        String normalized = status.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        
        // Map common variations to canonical constants
        switch (normalized) {
            case "DELIVERED":
            case "COMPLETE":
            case "COMPLETED":
                return DELIVERED;
            case "IN_TRANSIT":
            case "INTRANSIT":
            case "EN_ROUTE":
            case "ENROUTE":
            case "DEPARTING":
                return IN_TRANSIT;
            case "APPROACHING":
            case "APPROACHING_DESTINATION":
            case "APPROACHING_DEST":
            case "NEAR_DESTINATION":
                return APPROACHING_DESTINATION;
            case "AT_WAREHOUSE":
            case "ATWAREHOUSE":
            case "WAREHOUSE":
                return AT_WAREHOUSE;
            case "CREATED":
            case "NEW":
            case "STARTED":
                return CREATED;
            case "DISPATCHED":
            case "SHIPPED":
                return DISPATCHED;
            case "AWAITING_SHIPMENT":
            case "AWAITING":
            case "PENDING":
                return AWAITING_SHIPMENT;
            case "STOPPED":
            case "CANCELLED":
            case "ABORTED":
                return STOPPED;
            default:
                // Return original string as-is to preserve unknown statuses
                // from new features or external systems
                return status;
        }
    }
}
