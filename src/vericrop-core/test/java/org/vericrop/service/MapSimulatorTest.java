package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.MapSimulator.EntityType;
import org.vericrop.service.MapSimulator.MapEntity;
import org.vericrop.service.MapSimulator.MapSnapshot;
import org.vericrop.service.models.Scenario;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MapSimulator.
 */
public class MapSimulatorTest {
    
    private MapSimulator mapSimulator;
    
    @BeforeEach
    public void setUp() {
        mapSimulator = new MapSimulator();
    }
    
    @Test
    public void testInitialization() {
        assertNotNull(mapSimulator);
        assertEquals(0, mapSimulator.getCurrentStep());
        assertEquals(0, mapSimulator.getEntityCount());
    }
    
    @Test
    public void testInitializeForScenario() {
        String batchId = "TEST_BATCH_001";
        Scenario scenario = Scenario.NORMAL;
        int numWaypoints = 10;
        
        mapSimulator.initializeForScenario(scenario, batchId, numWaypoints);
        
        // Verify entities are created
        assertEquals(5, mapSimulator.getEntityCount()); // Producer, consumer, warehouse, vehicle, resource
        assertEquals(0, mapSimulator.getCurrentStep());
        
        // Get snapshot and verify
        MapSnapshot snapshot = mapSimulator.getSnapshot();
        assertNotNull(snapshot);
        assertEquals(5, snapshot.getEntities().size());
        assertEquals(scenario.name(), snapshot.getScenarioId());
    }
    
    @Test
    public void testStepAdvancesSimulation() {
        String batchId = "TEST_BATCH_002";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        // Step the simulation
        MapSnapshot snapshot1 = mapSimulator.step(25.0);
        assertEquals(1, snapshot1.getSimulationStep());
        
        MapSnapshot snapshot2 = mapSimulator.step(50.0);
        assertEquals(2, snapshot2.getSimulationStep());
        
        assertEquals(2, mapSimulator.getCurrentStep());
    }
    
    @Test
    public void testVehiclePositionUpdates() {
        String batchId = "TEST_BATCH_003";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        MapSnapshot initialSnapshot = mapSimulator.getSnapshot();
        MapEntity initialVehicle = findEntityByType(initialSnapshot.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertNotNull(initialVehicle);
        int initialX = initialVehicle.getX();
        
        // Step simulation with progress
        MapSnapshot snapshot = mapSimulator.step(50.0);
        MapEntity vehicle = findEntityByType(snapshot.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertNotNull(vehicle);
        
        // Vehicle should have moved right
        assertTrue(vehicle.getX() > initialX, "Vehicle should move right as progress increases");
    }
    
    @Test
    public void testResourceFollowsVehicle() {
        String batchId = "TEST_BATCH_004";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        MapSnapshot snapshot = mapSimulator.step(30.0);
        
        MapEntity vehicle = findEntityByType(snapshot.getEntities(), EntityType.DELIVERY_VEHICLE);
        MapEntity resource = findEntityByType(snapshot.getEntities(), EntityType.RESOURCE);
        
        assertNotNull(vehicle);
        assertNotNull(resource);
        
        // Resource should be at same position as vehicle
        assertEquals(vehicle.getX(), resource.getX());
        assertEquals(vehicle.getY(), resource.getY());
    }
    
    @Test
    public void testResourceQualityDegrades() {
        String batchId = "TEST_BATCH_005";
        mapSimulator.initializeForScenario(Scenario.HOT_TRANSPORT, batchId, 10);
        
        MapSnapshot initialSnapshot = mapSimulator.getSnapshot();
        MapEntity initialResource = findEntityByType(initialSnapshot.getEntities(), EntityType.RESOURCE);
        Double initialQuality = (Double) initialResource.getMetadata().get("quality");
        assertNotNull(initialQuality);
        assertEquals(1.0, initialQuality, 0.01);
        
        // Step simulation to 100%
        MapSnapshot finalSnapshot = mapSimulator.step(100.0);
        MapEntity finalResource = findEntityByType(finalSnapshot.getEntities(), EntityType.RESOURCE);
        Double finalQuality = (Double) finalResource.getMetadata().get("quality");
        
        assertNotNull(finalQuality);
        assertTrue(finalQuality < initialQuality, "Resource quality should degrade over time");
    }
    
    @Test
    public void testMapSnapshotToMap() {
        String batchId = "TEST_BATCH_006";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        MapSnapshot snapshot = mapSimulator.step(25.0);
        Map<String, Object> snapshotMap = snapshot.toMap();
        
        assertNotNull(snapshotMap);
        assertTrue(snapshotMap.containsKey("timestamp"));
        assertTrue(snapshotMap.containsKey("simulation_step"));
        assertTrue(snapshotMap.containsKey("grid_width"));
        assertTrue(snapshotMap.containsKey("grid_height"));
        assertTrue(snapshotMap.containsKey("scenario_id"));
        assertTrue(snapshotMap.containsKey("entities"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) snapshotMap.get("entities");
        assertEquals(5, entities.size());
    }
    
    @Test
    public void testDifferentScenarios() {
        String batchId = "TEST_BATCH_007";
        
        // Test NORMAL scenario
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        MapSnapshot normalSnapshot = mapSimulator.getSnapshot();
        assertEquals("NORMAL", normalSnapshot.getScenarioId());
        
        // Reset and test HOT_TRANSPORT
        mapSimulator.reset();
        mapSimulator.initializeForScenario(Scenario.HOT_TRANSPORT, batchId, 10);
        MapSnapshot hotSnapshot = mapSimulator.getSnapshot();
        assertEquals("HOT_TRANSPORT", hotSnapshot.getScenarioId());
    }
    
    @Test
    public void testReset() {
        String batchId = "TEST_BATCH_008";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        mapSimulator.step(50.0);
        
        assertEquals(1, mapSimulator.getCurrentStep());
        assertEquals(5, mapSimulator.getEntityCount());
        
        mapSimulator.reset();
        
        assertEquals(0, mapSimulator.getCurrentStep());
        assertEquals(0, mapSimulator.getEntityCount());
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        String batchId = "TEST_BATCH_009";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        // Create multiple threads that read/write concurrently
        Thread stepThread = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                mapSimulator.step(i * 10.0);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        
        Thread snapshotThread = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                MapSnapshot snapshot = mapSimulator.getSnapshot();
                assertNotNull(snapshot);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        
        stepThread.start();
        snapshotThread.start();
        
        stepThread.join(2000);
        snapshotThread.join(2000);
        
        // Should not throw any exceptions
        assertTrue(mapSimulator.getCurrentStep() > 0);
    }
    
    @Test
    public void testPhaseTransitions() {
        String batchId = "TEST_BATCH_010";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        // Test different progress phases
        MapSnapshot snapshot1 = mapSimulator.step(20.0);
        MapEntity vehicle1 = findEntityByType(snapshot1.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertEquals("En route from producer", vehicle1.getMetadata().get("phase"));
        
        MapSnapshot snapshot2 = mapSimulator.step(40.0);
        MapEntity vehicle2 = findEntityByType(snapshot2.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertEquals("Approaching warehouse", vehicle2.getMetadata().get("phase"));
        
        MapSnapshot snapshot3 = mapSimulator.step(60.0);
        MapEntity vehicle3 = findEntityByType(snapshot3.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertEquals("Departed warehouse", vehicle3.getMetadata().get("phase"));
        
        MapSnapshot snapshot4 = mapSimulator.step(80.0);
        MapEntity vehicle4 = findEntityByType(snapshot4.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertEquals("Approaching consumer", vehicle4.getMetadata().get("phase"));
        
        MapSnapshot snapshot5 = mapSimulator.step(100.0);
        MapEntity vehicle5 = findEntityByType(snapshot5.getEntities(), EntityType.DELIVERY_VEHICLE);
        assertEquals("Delivered", vehicle5.getMetadata().get("phase"));
    }
    
    // Helper method to find entity by type
    private MapEntity findEntityByType(List<MapEntity> entities, EntityType type) {
        for (MapEntity entity : entities) {
            if (entity.getType() == type) {
                return entity;
            }
        }
        return null;
    }
}
