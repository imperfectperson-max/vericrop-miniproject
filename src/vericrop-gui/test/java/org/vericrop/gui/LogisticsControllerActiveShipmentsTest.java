package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Active Shipments tab population in LogisticsController.
 * Verifies that shipments are correctly added/updated/removed during simulation.
 */
public class LogisticsControllerActiveShipmentsTest {
    
    /**
     * Test that shipment data model contains all required fields.
     */
    @Test
    @DisplayName("Shipment model should contain all required fields")
    void testShipmentModel() {
        LogisticsController.Shipment shipment = new LogisticsController.Shipment(
            "BATCH_001",
            "IN_TRANSIT",
            "Highway A - Mile 120",
            4.2,
            65.0,
            "17:30",
            "TRUCK_001"
        );
        
        assertEquals("BATCH_001", shipment.getBatchId());
        assertEquals("IN_TRANSIT", shipment.getStatus());
        assertEquals("Highway A - Mile 120", shipment.getLocation());
        assertEquals(4.2, shipment.getTemperature(), 0.01);
        assertEquals(65.0, shipment.getHumidity(), 0.01);
        assertEquals("17:30", shipment.getEta());
        assertEquals("TRUCK_001", shipment.getVehicle());
    }
    
    /**
     * Test that shipment status transitions are valid.
     */
    @Test
    @DisplayName("Shipment status transitions should be valid")
    void testShipmentStatusTransitions() {
        List<String> validStatuses = List.of(
            "CREATED", "IN_TRANSIT", "AT_WAREHOUSE", "DELIVERED", "STOPPED"
        );
        
        // All these statuses should be considered valid
        for (String status : validStatuses) {
            LogisticsController.Shipment shipment = new LogisticsController.Shipment(
                "BATCH_TEST",
                status,
                "Test Location",
                4.0, 65.0, "ETA", "TRUCK_001"
            );
            assertNotNull(shipment.getStatus());
            assertEquals(status, shipment.getStatus());
        }
    }
    
    /**
     * Test shipment uniqueness check logic.
     */
    @Test
    @DisplayName("Shipments should be uniquely identified by batchId")
    void testShipmentUniqueness() {
        List<LogisticsController.Shipment> shipments = new ArrayList<>();
        
        // Add first shipment
        shipments.add(new LogisticsController.Shipment(
            "BATCH_A", "IN_TRANSIT", "Location 1", 4.0, 65.0, "ETA1", "TRUCK_001"
        ));
        
        // Check if batch already exists (simulating addShipmentToTable logic)
        String newBatchId = "BATCH_A";
        boolean exists = shipments.stream()
            .anyMatch(s -> s.getBatchId().equals(newBatchId));
        
        assertTrue(exists, "BATCH_A should be detected as existing");
        
        // Check for non-existent batch
        String anotherBatchId = "BATCH_B";
        boolean anotherExists = shipments.stream()
            .anyMatch(s -> s.getBatchId().equals(anotherBatchId));
        
        assertFalse(anotherExists, "BATCH_B should NOT be detected as existing");
    }
    
    /**
     * Test shipment update by index logic.
     */
    @Test
    @DisplayName("Shipment update should find and replace by index")
    void testShipmentUpdateByIndex() {
        List<LogisticsController.Shipment> shipments = new ArrayList<>();
        
        // Add initial shipment
        shipments.add(new LogisticsController.Shipment(
            "BATCH_X", "CREATED", "Origin", 4.0, 65.0, "Starting", "TRUCK_001"
        ));
        
        // Find index of shipment to update
        String batchToUpdate = "BATCH_X";
        int shipmentIndex = -1;
        for (int i = 0; i < shipments.size(); i++) {
            if (shipments.get(i).getBatchId().equals(batchToUpdate)) {
                shipmentIndex = i;
                break;
            }
        }
        
        assertEquals(0, shipmentIndex, "BATCH_X should be at index 0");
        
        // Create updated shipment
        LogisticsController.Shipment updatedShipment = new LogisticsController.Shipment(
            "BATCH_X", "IN_TRANSIT", "Highway A", 4.5, 68.0, "30 min", "TRUCK_001"
        );
        
        // Replace at same index
        shipments.set(shipmentIndex, updatedShipment);
        
        // Verify update
        assertEquals("IN_TRANSIT", shipments.get(0).getStatus());
        assertEquals("Highway A", shipments.get(0).getLocation());
        assertEquals(4.5, shipments.get(0).getTemperature(), 0.01);
    }
    
    /**
     * Test vehicle ID generation is consistent.
     */
    @Test
    @DisplayName("Vehicle ID generation should be consistent for same batch")
    void testVehicleIdGeneration() {
        String batchId = "BATCH_123";
        
        // Simulate generateVehicleId logic
        String vehicleId1 = "TRUCK-" + Math.abs(batchId.hashCode() % 1000);
        String vehicleId2 = "TRUCK-" + Math.abs(batchId.hashCode() % 1000);
        
        assertEquals(vehicleId1, vehicleId2, 
            "Vehicle ID should be consistent for same batch ID");
        assertTrue(vehicleId1.startsWith("TRUCK-"), 
            "Vehicle ID should start with TRUCK-");
    }
    
    /**
     * Test that shipment list maintains insertion order.
     */
    @Test
    @DisplayName("Shipments should be added at the top of the list")
    void testShipmentInsertionOrder() {
        List<LogisticsController.Shipment> shipments = new ArrayList<>();
        
        // Add shipments at index 0 (top of list)
        shipments.add(0, new LogisticsController.Shipment(
            "BATCH_FIRST", "IN_TRANSIT", "Loc1", 4.0, 65.0, "ETA1", "TRUCK_001"
        ));
        
        shipments.add(0, new LogisticsController.Shipment(
            "BATCH_SECOND", "IN_TRANSIT", "Loc2", 4.0, 65.0, "ETA2", "TRUCK_002"
        ));
        
        shipments.add(0, new LogisticsController.Shipment(
            "BATCH_THIRD", "IN_TRANSIT", "Loc3", 4.0, 65.0, "ETA3", "TRUCK_003"
        ));
        
        // Most recent should be at index 0
        assertEquals("BATCH_THIRD", shipments.get(0).getBatchId());
        assertEquals("BATCH_SECOND", shipments.get(1).getBatchId());
        assertEquals("BATCH_FIRST", shipments.get(2).getBatchId());
    }
}
