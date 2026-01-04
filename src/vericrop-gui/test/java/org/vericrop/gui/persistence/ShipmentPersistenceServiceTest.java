package org.vericrop.gui.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShipmentPersistenceService.
 */
class ShipmentPersistenceServiceTest {
    
    @TempDir
    Path tempDir;
    
    private ShipmentPersistenceService service;
    
    @BeforeEach
    void setUp() {
        service = new ShipmentPersistenceService(tempDir.toString());
    }
    
    @AfterEach
    void tearDown() {
        if (service != null) {
            service.clearAllData();
        }
    }
    
    // ==================== SHIPMENT TESTS ====================
    
    @Test
    void testSaveAndGetShipment() {
        PersistedShipment shipment = new PersistedShipment(
                "BATCH_001", "IN_TRANSIT", "Highway Mile 10",
                4.5, 65.0, "30 min", "TRUCK_001"
        );
        
        service.saveShipment(shipment);
        
        assertTrue(service.getShipment("BATCH_001").isPresent());
        PersistedShipment retrieved = service.getShipment("BATCH_001").get();
        assertEquals("BATCH_001", retrieved.getBatchId());
        assertEquals("IN_TRANSIT", retrieved.getStatus());
        assertEquals(4.5, retrieved.getTemperature(), 0.01);
        assertEquals(65.0, retrieved.getHumidity(), 0.01);
    }
    
    @Test
    void testUpdateShipment() {
        PersistedShipment shipment = new PersistedShipment(
                "BATCH_002", "IN_TRANSIT", "Origin",
                4.0, 60.0, "60 min", "TRUCK_002"
        );
        service.saveShipment(shipment);
        
        // Update the shipment
        PersistedShipment updated = service.getShipment("BATCH_002").get();
        updated.setStatus("DELIVERED");
        updated.setLocation("Destination");
        updated.setTemperature(5.0);
        service.saveShipment(updated);
        
        PersistedShipment retrieved = service.getShipment("BATCH_002").get();
        assertEquals("DELIVERED", retrieved.getStatus());
        assertEquals("Destination", retrieved.getLocation());
        assertEquals(5.0, retrieved.getTemperature(), 0.01);
    }
    
    @Test
    void testGetAllShipments() {
        service.saveShipment(new PersistedShipment("BATCH_A", "IN_TRANSIT", "Loc A", 4.0, 60.0, "30 min", "TRUCK_A"));
        service.saveShipment(new PersistedShipment("BATCH_B", "DELIVERED", "Loc B", 4.5, 65.0, "DELIVERED", "TRUCK_B"));
        service.saveShipment(new PersistedShipment("BATCH_C", "IN_TRANSIT", "Loc C", 5.0, 70.0, "45 min", "TRUCK_C"));
        
        List<PersistedShipment> all = service.getAllShipments();
        assertEquals(3, all.size());
    }
    
    @Test
    void testGetShipmentsByStatus() {
        service.saveShipment(new PersistedShipment("BATCH_1", "IN_TRANSIT", "Loc 1", 4.0, 60.0, "30 min", "TRUCK_1"));
        service.saveShipment(new PersistedShipment("BATCH_2", "DELIVERED", "Loc 2", 4.5, 65.0, "DELIVERED", "TRUCK_2"));
        service.saveShipment(new PersistedShipment("BATCH_3", "IN_TRANSIT", "Loc 3", 5.0, 70.0, "45 min", "TRUCK_3"));
        
        List<PersistedShipment> inTransit = service.getShipmentsByStatus("IN_TRANSIT");
        assertEquals(2, inTransit.size());
        
        List<PersistedShipment> delivered = service.getShipmentsByStatus("DELIVERED");
        assertEquals(1, delivered.size());
    }
    
    @Test
    void testGetShipmentsByDateRange() {
        // Create shipments - the service sets createdAt to current time
        service.saveShipment(new PersistedShipment("BATCH_TODAY", "IN_TRANSIT", "Loc", 4.0, 60.0, "30 min", "TRUCK"));
        
        LocalDate today = LocalDate.now();
        List<PersistedShipment> todayShipments = service.getShipmentsByDateRange(today.minusDays(1), today.plusDays(1));
        
        assertFalse(todayShipments.isEmpty());
        assertEquals("BATCH_TODAY", todayShipments.get(0).getBatchId());
    }
    
    @Test
    void testDeleteShipment() {
        service.saveShipment(new PersistedShipment("BATCH_DELETE", "IN_TRANSIT", "Loc", 4.0, 60.0, "30 min", "TRUCK"));
        assertTrue(service.getShipment("BATCH_DELETE").isPresent());
        
        boolean deleted = service.deleteShipment("BATCH_DELETE");
        assertTrue(deleted);
        assertFalse(service.getShipment("BATCH_DELETE").isPresent());
    }
    
    // ==================== SIMULATION TESTS ====================
    
    @Test
    void testSaveAndGetSimulation() {
        PersistedSimulation simulation = new PersistedSimulation("BATCH_SIM_001", "FARMER_001", "NORMAL");
        simulation.setStatus("COMPLETED");
        simulation.setCompleted(true);
        simulation.setFinalQuality(95.0);
        simulation.setAvgTemperature(4.5);
        simulation.setViolationsCount(0);
        simulation.setComplianceStatus("COMPLIANT");
        
        service.saveSimulation(simulation);
        
        List<PersistedSimulation> simulations = service.getSimulationsByBatchId("BATCH_SIM_001");
        assertFalse(simulations.isEmpty());
        
        PersistedSimulation retrieved = simulations.get(0);
        assertEquals("BATCH_SIM_001", retrieved.getBatchId());
        assertEquals("FARMER_001", retrieved.getFarmerId());
        assertEquals("COMPLETED", retrieved.getStatus());
        assertTrue(retrieved.isCompleted());
        assertEquals(95.0, retrieved.getFinalQuality(), 0.01);
    }
    
    @Test
    void testGetAllSimulations() {
        service.saveSimulation(new PersistedSimulation("BATCH_A", "FARMER_A", "NORMAL"));
        service.saveSimulation(new PersistedSimulation("BATCH_B", "FARMER_B", "HOT_WEATHER"));
        service.saveSimulation(new PersistedSimulation("BATCH_C", "FARMER_C", "TRAFFIC_DELAY"));
        
        List<PersistedSimulation> all = service.getAllSimulations();
        assertEquals(3, all.size());
    }
    
    @Test
    void testGetSimulationsByDateRange() {
        service.saveSimulation(new PersistedSimulation("BATCH_TODAY_SIM", "FARMER_X", "NORMAL"));
        
        LocalDate today = LocalDate.now();
        List<PersistedSimulation> todaySimulations = service.getSimulationsByDateRange(today.minusDays(1), today.plusDays(1));
        
        assertFalse(todaySimulations.isEmpty());
        assertEquals("BATCH_TODAY_SIM", todaySimulations.get(0).getBatchId());
    }
    
    @Test
    void testGetCompletedSimulations() {
        PersistedSimulation completed1 = new PersistedSimulation("BATCH_COMP_1", "FARMER_1", "NORMAL");
        completed1.setCompleted(true);
        service.saveSimulation(completed1);
        
        PersistedSimulation incomplete = new PersistedSimulation("BATCH_INC", "FARMER_2", "NORMAL");
        incomplete.setCompleted(false);
        service.saveSimulation(incomplete);
        
        PersistedSimulation completed2 = new PersistedSimulation("BATCH_COMP_2", "FARMER_3", "NORMAL");
        completed2.setCompleted(true);
        service.saveSimulation(completed2);
        
        List<PersistedSimulation> completedSims = service.getCompletedSimulations();
        assertEquals(2, completedSims.size());
        assertTrue(completedSims.stream().allMatch(PersistedSimulation::isCompleted));
    }
    
    @Test
    void testDeleteSimulation() {
        PersistedSimulation simulation = new PersistedSimulation("BATCH_DELETE_SIM", "FARMER_X", "NORMAL");
        service.saveSimulation(simulation);
        String id = simulation.getId();
        
        assertTrue(service.getSimulation(id).isPresent());
        
        boolean deleted = service.deleteSimulation(id);
        assertTrue(deleted);
        assertFalse(service.getSimulation(id).isPresent());
    }
    
    // ==================== UTILITY TESTS ====================
    
    @Test
    void testGetCounts() {
        service.saveShipment(new PersistedShipment("S1", "IN_TRANSIT", "Loc", 4.0, 60.0, "30 min", "TRUCK"));
        service.saveShipment(new PersistedShipment("S2", "DELIVERED", "Loc", 4.5, 65.0, "DELIVERED", "TRUCK"));
        service.saveSimulation(new PersistedSimulation("SIM1", "F1", "NORMAL"));
        
        assertEquals(2, service.getShipmentsCount());
        assertEquals(1, service.getSimulationsCount());
    }
    
    @Test
    void testClearAllData() {
        service.saveShipment(new PersistedShipment("S1", "IN_TRANSIT", "Loc", 4.0, 60.0, "30 min", "TRUCK"));
        service.saveSimulation(new PersistedSimulation("SIM1", "F1", "NORMAL"));
        
        service.clearAllData();
        
        assertEquals(0, service.getShipmentsCount());
        assertEquals(0, service.getSimulationsCount());
    }
    
    @Test
    void testDataDirectoryPath() {
        assertNotNull(service.getDataDirectoryPath());
        assertTrue(service.getDataDirectoryPath().contains(tempDir.toString()));
    }
}
