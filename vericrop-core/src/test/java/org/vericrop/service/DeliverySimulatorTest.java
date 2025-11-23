package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.DeliverySimulator.GeoCoordinate;
import org.vericrop.service.DeliverySimulator.RouteWaypoint;
import org.vericrop.service.DeliverySimulator.SimulationStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliverySimulator.
 */
public class DeliverySimulatorTest {
    
    private DeliverySimulator simulator;
    private MessageService messageService;
    
    @BeforeEach
    public void setUp() {
        messageService = new MessageService(false);  // In-memory for tests
        simulator = new DeliverySimulator(messageService);
    }
    
    @AfterEach
    public void tearDown() {
        simulator.shutdown();
    }
    
    @Test
    public void testGenerateRoute() {
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "New York");
        GeoCoordinate destination = new GeoCoordinate(34.0522, -118.2437, "Los Angeles");
        
        int numWaypoints = 10;
        long startTime = System.currentTimeMillis();
        double avgSpeed = 60.0;
        
        List<RouteWaypoint> route = simulator.generateRoute(
            origin, destination, numWaypoints, startTime, avgSpeed);
        
        assertNotNull(route);
        assertEquals(numWaypoints, route.size());
        
        // First waypoint should be at origin
        RouteWaypoint first = route.get(0);
        assertEquals(origin.getLatitude(), first.getLocation().getLatitude(), 0.0001);
        assertEquals(origin.getLongitude(), first.getLocation().getLongitude(), 0.0001);
        
        // Last waypoint should be at destination
        RouteWaypoint last = route.get(route.size() - 1);
        assertEquals(destination.getLatitude(), last.getLocation().getLatitude(), 0.0001);
        assertEquals(destination.getLongitude(), last.getLocation().getLongitude(), 0.0001);
        
        // Timestamps should be increasing
        for (int i = 1; i < route.size(); i++) {
            assertTrue(route.get(i).getTimestamp() > route.get(i - 1).getTimestamp());
        }
        
        // Environmental readings should be present
        for (RouteWaypoint waypoint : route) {
            assertTrue(waypoint.getTemperature() > -50 && waypoint.getTemperature() < 50);
            assertTrue(waypoint.getHumidity() >= 0 && waypoint.getHumidity() <= 100);
        }
    }
    
    @Test
    public void testGenerateRoute_MinimumWaypoints() {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -73.0, "End");
        
        List<RouteWaypoint> route = simulator.generateRoute(
            origin, destination, 2, System.currentTimeMillis(), 60.0);
        
        assertEquals(2, route.size());
    }
    
    @Test
    public void testGenerateRoute_InvalidWaypoints() {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -73.0, "End");
        
        assertThrows(IllegalArgumentException.class, () -> {
            simulator.generateRoute(origin, destination, 1, System.currentTimeMillis(), 60.0);
        });
    }
    
    @Test
    public void testStartStopSimulation() throws InterruptedException {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -73.0, "End");
        
        List<RouteWaypoint> route = simulator.generateRoute(
            origin, destination, 5, System.currentTimeMillis(), 60.0);
        
        String shipmentId = "TEST_SHIP_001";
        
        // Start simulation
        simulator.startSimulation(shipmentId, route, 100);  // 100ms intervals for fast test
        
        // Check status
        Thread.sleep(200);  // Let it run a bit
        SimulationStatus status = simulator.getSimulationStatus(shipmentId);
        
        assertNotNull(status);
        assertEquals(shipmentId, status.getShipmentId());
        assertTrue(status.isRunning() || status.getCurrentWaypoint() > 0);
        
        // Stop simulation
        simulator.stopSimulation(shipmentId);
        
        // Check status after stop
        Thread.sleep(100);
        status = simulator.getSimulationStatus(shipmentId);
        assertFalse(status.isRunning());
    }
    
    @Test
    public void testSimulationEmitsMessages() throws InterruptedException {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -73.0, "End");
        
        List<RouteWaypoint> route = simulator.generateRoute(
            origin, destination, 3, System.currentTimeMillis(), 60.0);
        
        String shipmentId = "TEST_SHIP_002";
        
        // Clear any existing messages
        messageService.clearAll();
        
        // Start simulation
        simulator.startSimulation(shipmentId, route, 100);  // Fast updates
        
        // Wait for some messages
        Thread.sleep(500);
        
        // Check messages were sent
        List<org.vericrop.dto.Message> messages = messageService.getAllMessages();
        assertTrue(messages.size() > 0, "Simulation should emit messages");
        
        // Verify message content
        org.vericrop.dto.Message firstMsg = messages.get(0);
        assertEquals("logistics", firstMsg.getSenderRole());
        assertEquals("delivery_simulator", firstMsg.getSenderId());
        assertEquals(shipmentId, firstMsg.getShipmentId());
        
        // Stop simulation
        simulator.stopSimulation(shipmentId);
    }
    
    @Test
    public void testGetSimulationStatus_NotRunning() {
        SimulationStatus status = simulator.getSimulationStatus("NON_EXISTENT");
        
        assertNotNull(status);
        assertFalse(status.isRunning());
        assertEquals(0, status.getTotalWaypoints());
    }
    
    @Test
    public void testMultipleSimulations() throws InterruptedException {
        GeoCoordinate origin1 = new GeoCoordinate(40.0, -74.0, "Start1");
        GeoCoordinate dest1 = new GeoCoordinate(41.0, -73.0, "End1");
        List<RouteWaypoint> route1 = simulator.generateRoute(
            origin1, dest1, 5, System.currentTimeMillis(), 60.0);  // Increased waypoints for longer simulation
        
        GeoCoordinate origin2 = new GeoCoordinate(42.0, -75.0, "Start2");
        GeoCoordinate dest2 = new GeoCoordinate(43.0, -74.0, "End2");
        List<RouteWaypoint> route2 = simulator.generateRoute(
            origin2, dest2, 5, System.currentTimeMillis(), 60.0);  // Increased waypoints for longer simulation
        
        // Start two simulations with longer intervals to ensure they're running during test
        simulator.startSimulation("SHIP_A", route1, 200);
        simulator.startSimulation("SHIP_B", route2, 200);
        
        // Wait briefly for simulations to start
        // Initial delay is updateInterval/10 = 20ms
        Thread.sleep(50);
        
        // Verify both simulations have started (should have non-zero total waypoints)
        SimulationStatus statusA = simulator.getSimulationStatus("SHIP_A");
        SimulationStatus statusB = simulator.getSimulationStatus("SHIP_B");
        
        assertTrue(statusA.getTotalWaypoints() > 0, "Simulation A should be registered");
        assertTrue(statusB.getTotalWaypoints() > 0, "Simulation B should be registered");
        assertTrue(statusA.isRunning(), "Simulation A should be running");
        assertTrue(statusB.isRunning(), "Simulation B should be running");
        
        // Wait for some progress to be made
        // With 200ms intervals and 5 waypoints, first waypoint at ~20ms, second at ~220ms
        Thread.sleep(250);
        
        // Check that simulations are still running or have made progress
        statusA = simulator.getSimulationStatus("SHIP_A");
        statusB = simulator.getSimulationStatus("SHIP_B");
        
        // At this point, simulations should either:
        // 1. Still be running (most likely with longer intervals)
        // 2. Have advanced to waypoint 1 or beyond if they progressed
        // Note: If simulation completed, it will be removed and status will show totalWaypoints=0
        // So we check: either still registered (totalWaypoints > 0) OR was running earlier (which we verified above)
        assertSimulationActiveOrProgressed("A", statusA);
        assertSimulationActiveOrProgressed("B", statusB);
        
        // Stop both (safe even if already completed)
        simulator.stopSimulation("SHIP_A");
        simulator.stopSimulation("SHIP_B");
    }
    
    /**
     * Helper method to assert that a simulation is either still active or has made progress.
     */
    private void assertSimulationActiveOrProgressed(String simulationName, SimulationStatus status) {
        boolean isValid = (status.getTotalWaypoints() > 0 && (status.isRunning() || status.getCurrentWaypoint() > 0));
        assertTrue(isValid, 
            String.format("Simulation %s should still be active or have made progress (running=%s, waypoint=%d/%d)", 
                simulationName, status.isRunning(), status.getCurrentWaypoint(), status.getTotalWaypoints()));
    }
}
