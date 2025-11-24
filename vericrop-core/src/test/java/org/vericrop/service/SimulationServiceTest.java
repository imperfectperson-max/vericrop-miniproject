package org.vericrop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.SimulationEvent;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SimulationService.
 * Validates event emission, listener notifications, and lifecycle management.
 */
public class SimulationServiceTest {
    
    private SimulationService simulationService;
    private DeliverySimulator deliverySimulator;
    private MapService mapService;
    private TemperatureService temperatureService;
    
    @BeforeEach
    public void setUp() {
        // Initialize dependencies
        MessageService messageService = new MessageService();
        AlertService alertService = new AlertService();
        deliverySimulator = new DeliverySimulator(messageService, alertService);
        mapService = new MapService();
        temperatureService = new TemperatureService();
        
        // Initialize SimulationService with 100ms interval for fast testing
        simulationService = new SimulationService(deliverySimulator, mapService, 
                                                  temperatureService, 100L);
    }
    
    @AfterEach
    public void tearDown() {
        if (simulationService != null) {
            simulationService.shutdown();
        }
    }
    
    @Test
    public void testStartSimulation_EmitsStartedEvent() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_001";
        String farmerId = "FARMER_001";
        List<RouteWaypoint> route = createTestRoute(5);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger eventCount = new AtomicInteger(0);
        
        // Act
        boolean started = simulationService.startSimulation(batchId, farmerId, route, event -> {
            eventCount.incrementAndGet();
            if (event.getEventType() == SimulationEvent.EventType.STARTED) {
                assertEquals(batchId, event.getBatchId());
                assertEquals(farmerId, event.getFarmerId());
                assertEquals(0.0, event.getProgressPercent(), 0.01);
                latch.countDown();
            }
        });
        
        // Assert
        assertTrue(started, "Simulation should start successfully");
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive STARTED event");
        assertTrue(eventCount.get() >= 1, "Should receive at least one event");
    }
    
    @Test
    public void testStartSimulation_EmitsGPSUpdates() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_002";
        String farmerId = "FARMER_002";
        List<RouteWaypoint> route = createTestRoute(3);
        
        CountDownLatch latch = new CountDownLatch(2); // Wait for at least 2 GPS updates
        List<SimulationEvent> gpsEvents = new ArrayList<>();
        
        // Act
        simulationService.startSimulation(batchId, farmerId, route, event -> {
            if (event.getEventType() == SimulationEvent.EventType.GPS_UPDATE) {
                gpsEvents.add(event);
                latch.countDown();
            }
        }, 100L);
        
        // Assert
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Should receive GPS update events");
        assertTrue(gpsEvents.size() >= 2, "Should receive multiple GPS updates");
        
        // Verify GPS coordinates are populated
        for (SimulationEvent event : gpsEvents) {
            assertNotNull(event.getBatchId());
            assertTrue(event.getLatitude() != 0.0 || event.getLongitude() != 0.0);
            assertTrue(event.getCurrentWaypoint() >= 0);
            assertTrue(event.getTotalWaypoints() > 0);
        }
    }
    
    @Test
    public void testStartSimulation_WithNullRoute_ReturnsFalse() {
        // Act
        boolean started = simulationService.startSimulation("BATCH_003", "FARMER_003", 
                                                           null, event -> {});
        
        // Assert
        assertFalse(started, "Should not start simulation with null route");
    }
    
    @Test
    public void testStartSimulation_WithEmptyRoute_ReturnsFalse() {
        // Act
        boolean started = simulationService.startSimulation("BATCH_004", "FARMER_004", 
                                                           new ArrayList<>(), event -> {});
        
        // Assert
        assertFalse(started, "Should not start simulation with empty route");
    }
    
    @Test
    public void testStartSimulation_AlreadyRunning_ReturnsFalse() {
        // Arrange
        String batchId = "TEST_BATCH_005";
        List<RouteWaypoint> route = createTestRoute(3);
        
        // Act
        boolean firstStart = simulationService.startSimulation(batchId, "FARMER_005", 
                                                              route, event -> {});
        boolean secondStart = simulationService.startSimulation(batchId, "FARMER_005", 
                                                               route, event -> {});
        
        // Assert
        assertTrue(firstStart, "First start should succeed");
        assertFalse(secondStart, "Second start should fail (already running)");
    }
    
    @Test
    public void testStopSimulation_StopsEventEmission() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_006";
        List<RouteWaypoint> route = createTestRoute(10);
        
        AtomicInteger eventCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Act
        simulationService.startSimulation(batchId, "FARMER_006", route, event -> {
            eventCount.incrementAndGet();
            startLatch.countDown();
        }, 50L);
        
        // Wait for simulation to start
        assertTrue(startLatch.await(1, TimeUnit.SECONDS), "Simulation should start");
        
        // Stop after a short delay
        Thread.sleep(200);
        int countBeforeStop = eventCount.get();
        simulationService.stopSimulation(batchId);
        
        // Wait a bit more
        Thread.sleep(200);
        int countAfterStop = eventCount.get();
        
        // Assert
        assertTrue(countBeforeStop > 0, "Should have emitted events before stop");
        assertEquals(countBeforeStop, countAfterStop, 
                    "Should not emit events after stop");
    }
    
    @Test
    public void testAddListener_ReceivesEvents() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_007";
        List<RouteWaypoint> route = createTestRoute(3);
        
        CountDownLatch firstListenerLatch = new CountDownLatch(1);
        CountDownLatch secondListenerLatch = new CountDownLatch(1);
        
        // Start simulation with first listener
        simulationService.startSimulation(batchId, "FARMER_007", route, 
            event -> firstListenerLatch.countDown(), 100L);
        
        // Add second listener after simulation has started
        Thread.sleep(100);
        simulationService.addListener(batchId, event -> secondListenerLatch.countDown());
        
        // Assert
        assertTrue(firstListenerLatch.await(1, TimeUnit.SECONDS), 
                  "First listener should receive events");
        assertTrue(secondListenerLatch.await(1, TimeUnit.SECONDS), 
                  "Second listener should receive events");
    }
    
    @Test
    public void testIsSimulationRunning() {
        // Arrange
        String batchId = "TEST_BATCH_008";
        List<RouteWaypoint> route = createTestRoute(3);
        
        // Act & Assert
        assertFalse(simulationService.isSimulationRunning(batchId), 
                   "Simulation should not be running initially");
        
        simulationService.startSimulation(batchId, "FARMER_008", route, event -> {});
        
        assertTrue(simulationService.isSimulationRunning(batchId), 
                  "Simulation should be running after start");
        
        simulationService.stopSimulation(batchId);
        
        assertFalse(simulationService.isSimulationRunning(batchId), 
                   "Simulation should not be running after stop");
    }
    
    @Test
    public void testGetActiveSimulationCount() {
        // Arrange
        List<RouteWaypoint> route = createTestRoute(3);
        
        // Act & Assert
        assertEquals(0, simulationService.getActiveSimulationCount(), 
                    "Should have no active simulations initially");
        
        simulationService.startSimulation("BATCH_A", "FARMER_A", route, event -> {});
        assertEquals(1, simulationService.getActiveSimulationCount(), 
                    "Should have 1 active simulation");
        
        simulationService.startSimulation("BATCH_B", "FARMER_B", route, event -> {});
        assertEquals(2, simulationService.getActiveSimulationCount(), 
                    "Should have 2 active simulations");
        
        simulationService.stopSimulation("BATCH_A");
        assertEquals(1, simulationService.getActiveSimulationCount(), 
                    "Should have 1 active simulation after stopping one");
    }
    
    @Test
    public void testSubscribeToTemperatureUpdates_Success() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_TEMP_001";
        List<RouteWaypoint> route = createTestRoute(5);
        
        CountDownLatch latch = new CountDownLatch(2); // Wait for initial + update
        List<SimulationEvent> temperatureEvents = new ArrayList<>();
        
        // Start simulation
        simulationService.startSimulation(batchId, "FARMER_001", route, event -> {}, 100L);
        Thread.sleep(50); // Let simulation start
        
        // Act - Subscribe to temperature updates
        boolean subscribed = simulationService.subscribeToTemperatureUpdates(batchId, event -> {
            temperatureEvents.add(event);
            latch.countDown();
        });
        
        // Assert
        assertTrue(subscribed, "Should successfully subscribe to temperature updates");
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive temperature events");
        assertFalse(temperatureEvents.isEmpty(), "Should have received temperature events");
    }
    
    @Test
    public void testSubscribeToTemperatureUpdates_NonExistentSimulation() {
        // Arrange
        String batchId = "NON_EXISTENT_BATCH";
        
        // Act
        boolean subscribed = simulationService.subscribeToTemperatureUpdates(batchId, event -> {});
        
        // Assert
        assertFalse(subscribed, "Should not subscribe to non-existent simulation");
    }
    
    @Test
    public void testUnsubscribeFromTemperatureUpdates() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_TEMP_002";
        List<RouteWaypoint> route = createTestRoute(5);
        
        AtomicInteger eventCount = new AtomicInteger(0);
        java.util.function.Consumer<SimulationEvent> listener = event -> eventCount.incrementAndGet();
        
        // Start simulation and subscribe
        simulationService.startSimulation(batchId, "FARMER_002", route, event -> {}, 100L);
        Thread.sleep(50);
        simulationService.subscribeToTemperatureUpdates(batchId, listener);
        
        // Wait for some events
        Thread.sleep(200);
        int countBeforeUnsubscribe = eventCount.get();
        
        // Act - Unsubscribe
        boolean unsubscribed = simulationService.unsubscribeFromTemperatureUpdates(batchId, listener);
        
        // Wait and verify no more events
        Thread.sleep(200);
        int countAfterUnsubscribe = eventCount.get();
        
        // Assert
        assertTrue(unsubscribed, "Should successfully unsubscribe");
        assertTrue(countBeforeUnsubscribe > 0, "Should have received events before unsubscribe");
        assertEquals(countBeforeUnsubscribe, countAfterUnsubscribe, 
                    "Should not receive events after unsubscribe");
    }
    
    @Test
    public void testGetHistoricalTemperatureReadings_ActiveSimulation() throws InterruptedException {
        // Arrange
        String batchId = "TEST_BATCH_HIST_001";
        List<RouteWaypoint> route = createTestRoute(10);
        
        // Start simulation
        simulationService.startSimulation(batchId, "FARMER_001", route, event -> {}, 50L);
        
        // Wait for some waypoints to be traversed
        Thread.sleep(300);
        
        // Act
        List<SimulationEvent> historicalReadings = simulationService.getHistoricalTemperatureReadings(batchId);
        
        // Assert
        assertNotNull(historicalReadings, "Historical readings should not be null");
        assertFalse(historicalReadings.isEmpty(), "Should have historical readings");
        assertTrue(historicalReadings.size() > 0, "Should have at least one historical reading");
        
        // Verify readings are properly formatted
        SimulationEvent firstReading = historicalReadings.get(0);
        assertEquals(batchId, firstReading.getBatchId());
        assertEquals(SimulationEvent.EventType.TEMPERATURE_UPDATE, firstReading.getEventType());
        assertTrue(firstReading.getTemperature() > 0, "Temperature should be positive");
    }
    
    @Test
    public void testGetHistoricalTemperatureReadings_NonExistentSimulation() {
        // Arrange
        String batchId = "NON_EXISTENT_BATCH";
        
        // Act
        List<SimulationEvent> historicalReadings = simulationService.getHistoricalTemperatureReadings(batchId);
        
        // Assert
        assertNotNull(historicalReadings, "Should return non-null list");
        assertTrue(historicalReadings.isEmpty(), "Should return empty list for non-existent simulation");
    }
    
    /**
     * Helper method to create a test route with specified number of waypoints.
     */
    private List<RouteWaypoint> createTestRoute(int numWaypoints) {
        List<RouteWaypoint> route = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < numWaypoints; i++) {
            double lat = 40.0 + (i * 0.01);
            double lon = -74.0 + (i * 0.01);
            GeoCoordinate location = new GeoCoordinate(lat, lon, "Waypoint_" + i);
            
            RouteWaypoint waypoint = new RouteWaypoint(
                location,
                currentTime + (i * 1000L), // 1 second apart
                5.0 + (i * 0.1), // temperature
                80.0 - (i * 0.5)  // humidity
            );
            route.add(waypoint);
        }
        
        return route;
    }
}
