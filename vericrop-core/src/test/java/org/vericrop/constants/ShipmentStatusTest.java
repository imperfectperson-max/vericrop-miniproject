package org.vericrop.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShipmentStatus constants and utility methods.
 */
class ShipmentStatusTest {
    
    @Test
    void testFromProgress_delivered() {
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.fromProgress(100.0));
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.fromProgress(100.1)); // Above 100
    }
    
    @Test
    void testFromProgress_atWarehouse() {
        assertEquals(ShipmentStatus.AT_WAREHOUSE, ShipmentStatus.fromProgress(90.0));
        assertEquals(ShipmentStatus.AT_WAREHOUSE, ShipmentStatus.fromProgress(95.0));
        assertEquals(ShipmentStatus.AT_WAREHOUSE, ShipmentStatus.fromProgress(99.9));
    }
    
    @Test
    void testFromProgress_approaching() {
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.fromProgress(70.0));
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.fromProgress(80.0));
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.fromProgress(89.9));
    }
    
    @Test
    void testFromProgress_inTransit() {
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.fromProgress(30.0));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.fromProgress(50.0));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.fromProgress(69.9));
    }
    
    @Test
    void testFromProgress_created() {
        assertEquals(ShipmentStatus.CREATED, ShipmentStatus.fromProgress(0.0));
        assertEquals(ShipmentStatus.CREATED, ShipmentStatus.fromProgress(5.0));
        assertEquals(ShipmentStatus.CREATED, ShipmentStatus.fromProgress(9.9));
    }
    
    @Test
    void testIsDelivered() {
        assertTrue(ShipmentStatus.isDelivered("DELIVERED"));
        assertTrue(ShipmentStatus.isDelivered("delivered"));
        assertTrue(ShipmentStatus.isDelivered("Delivered"));
        
        assertFalse(ShipmentStatus.isDelivered("IN_TRANSIT"));
        assertFalse(ShipmentStatus.isDelivered("APPROACHING"));
        assertFalse(ShipmentStatus.isDelivered(null));
        assertFalse(ShipmentStatus.isDelivered(""));
    }
    
    @Test
    void testIsInTransit() {
        assertTrue(ShipmentStatus.isInTransit("IN_TRANSIT"));
        assertTrue(ShipmentStatus.isInTransit("in_transit"));
        assertTrue(ShipmentStatus.isInTransit("DISPATCHED"));
        assertTrue(ShipmentStatus.isInTransit("En Route"));
        assertTrue(ShipmentStatus.isInTransit("Departing"));
        
        assertFalse(ShipmentStatus.isInTransit("DELIVERED"));
        assertFalse(ShipmentStatus.isInTransit("APPROACHING"));
    }
    
    @Test
    void testIsApproaching() {
        assertTrue(ShipmentStatus.isApproaching("APPROACHING"));
        assertTrue(ShipmentStatus.isApproaching("approaching"));
        assertTrue(ShipmentStatus.isApproaching("APPROACHING_DESTINATION"));
        assertTrue(ShipmentStatus.isApproaching("AT_WAREHOUSE"));
        
        assertFalse(ShipmentStatus.isApproaching("IN_TRANSIT"));
        assertFalse(ShipmentStatus.isApproaching("DELIVERED"));
    }
    
    @Test
    void testNormalize_deliveredVariants() {
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.normalize("DELIVERED"));
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.normalize("delivered"));
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.normalize("Complete"));
        assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.normalize("completed"));
    }
    
    @Test
    void testNormalize_inTransitVariants() {
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.normalize("IN_TRANSIT"));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.normalize("in transit"));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.normalize("en-route"));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.normalize("ENROUTE"));
        assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.normalize("Departing"));
    }
    
    @Test
    void testNormalize_approachingVariants() {
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.normalize("APPROACHING"));
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.normalize("approaching_destination"));
        assertEquals(ShipmentStatus.APPROACHING_DESTINATION, ShipmentStatus.normalize("near destination"));
    }
    
    @Test
    void testNormalize_warehouseVariants() {
        assertEquals(ShipmentStatus.AT_WAREHOUSE, ShipmentStatus.normalize("AT_WAREHOUSE"));
        assertEquals(ShipmentStatus.AT_WAREHOUSE, ShipmentStatus.normalize("warehouse"));
    }
    
    @Test
    void testNormalize_nullAndEmpty() {
        assertEquals(ShipmentStatus.AWAITING_SHIPMENT, ShipmentStatus.normalize(null));
        assertEquals(ShipmentStatus.AWAITING_SHIPMENT, ShipmentStatus.normalize(""));
        assertEquals(ShipmentStatus.AWAITING_SHIPMENT, ShipmentStatus.normalize("   "));
    }
    
    @Test
    void testNormalize_unknownStatus() {
        // Unknown statuses are returned as-is (original string) - no uppercase conversion for unrecognized values
        String originalInput = "custom_status";
        assertEquals(originalInput, ShipmentStatus.normalize(originalInput));
    }
    
    @Test
    void testConstants() {
        // Verify constants are not null
        assertNotNull(ShipmentStatus.AWAITING_SHIPMENT);
        assertNotNull(ShipmentStatus.CREATED);
        assertNotNull(ShipmentStatus.DISPATCHED);
        assertNotNull(ShipmentStatus.IN_TRANSIT);
        assertNotNull(ShipmentStatus.APPROACHING_DESTINATION);
        assertNotNull(ShipmentStatus.AT_WAREHOUSE);
        assertNotNull(ShipmentStatus.DELIVERED);
        
        // Verify progress thresholds are in correct order
        assertTrue(ShipmentStatus.PROGRESS_DEPARTING_THRESHOLD < ShipmentStatus.PROGRESS_EN_ROUTE_THRESHOLD);
        assertTrue(ShipmentStatus.PROGRESS_EN_ROUTE_THRESHOLD < ShipmentStatus.PROGRESS_APPROACHING_THRESHOLD);
        assertTrue(ShipmentStatus.PROGRESS_APPROACHING_THRESHOLD < ShipmentStatus.PROGRESS_AT_WAREHOUSE_THRESHOLD);
        assertTrue(ShipmentStatus.PROGRESS_AT_WAREHOUSE_THRESHOLD < ShipmentStatus.PROGRESS_COMPLETE);
    }
}
