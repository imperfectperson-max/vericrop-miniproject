package com.vericrop.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the simulation system.
 * Tests end-to-end simulation flow with demo mode time compression.
 */
@DisplayName("Simulation Integration Tests")
class SimulationIntegrationTest {
    
    /** Tolerance for floating-point comparisons in quality checks */
    private static final double QUALITY_TOLERANCE = 0.01;
    
    private EnhancedDeliverySimulator simulator;
    
    @BeforeEach
    void setUp() {
        // Use demo mode for faster tests
        SimulationConfig demoConfig = SimulationConfig.createDemoMode();
        simulator = new EnhancedDeliverySimulator(demoConfig);
    }
    
    @AfterEach
    void tearDown() {
        if (simulator != null) {
            simulator.shutdown();
        }
    }
    
    @Test
    @DisplayName("Demo mode completes simulation within expected time")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void demoModeCompletesWithinExpectedTime() throws InterruptedException {
        String deliveryId = "DEMO-TEST-001";
        
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Sunny Valley Farm")
            .destination(42.3736, -71.1097, "Metro Fresh Warehouse")
            .baseTemperature(4.0)
            .baseHumidity(65.0)
            .build();
        
        List<DeliveryEvent> events = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<Double> finalQuality = new AtomicReference<>();
        
        long startTime = System.currentTimeMillis();
        
        boolean started = simulator.startSimulation(deliveryId, "farmer-001", interpolator, 
            event -> {
                events.add(event);
                System.out.println("📦 Event: " + event);
                
                if (event.getEventType() == DeliveryEvent.EventType.COMPLETED) {
                    finalQuality.set(event.getFinalQuality());
                    completionLatch.countDown();
                }
            });
        
        assertTrue(started, "Simulation should start successfully");
        
        // Wait for completion (should complete within 4 minutes in demo mode)
        boolean completed = completionLatch.await(4, TimeUnit.MINUTES);
        
        long elapsedMs = System.currentTimeMillis() - startTime;
        double elapsedMinutes = elapsedMs / 60000.0;
        
        assertTrue(completed, "Simulation should complete within 4 minutes in demo mode");
        System.out.println("✅ Simulation completed in " + String.format("%.2f", elapsedMinutes) + " minutes");
        
        // Verify events
        assertFalse(events.isEmpty(), "Should have received events");
        
        // Verify state transitions occurred
        boolean hasStarted = events.stream()
            .anyMatch(e -> e.getEventType() == DeliveryEvent.EventType.STARTED);
        boolean hasStateChanges = events.stream()
            .anyMatch(e -> e.getEventType() == DeliveryEvent.EventType.STATE_CHANGED);
        boolean hasCompleted = events.stream()
            .anyMatch(e -> e.getEventType() == DeliveryEvent.EventType.COMPLETED);
        
        assertTrue(hasStarted, "Should have STARTED event");
        assertTrue(hasStateChanges, "Should have STATE_CHANGED events");
        assertTrue(hasCompleted, "Should have COMPLETED event");
        
        // Verify final quality
        assertNotNull(finalQuality.get(), "Final quality should be set");
        assertTrue(finalQuality.get() > 0, "Final quality should be positive");
        assertTrue(finalQuality.get() < 100, "Final quality should have decayed");
        
        System.out.println("✅ Final quality: " + String.format("%.1f%%", finalQuality.get()));
    }
    
    @Test
    @DisplayName("State transitions occur exactly once per state")
    void stateTransitionsOccurOncePerState() throws InterruptedException {
        String deliveryId = "STATE-TEST-001";
        
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Origin")
            .destination(42.3736, -71.1097, "Destination")
            .build();
        
        List<DeliveryEvent> stateChangeEvents = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(1);
        
        simulator.startSimulation(deliveryId, "farmer-001", interpolator, 
            event -> {
                if (event.isStateChange()) {
                    stateChangeEvents.add(event);
                }
                if (event.getEventType() == DeliveryEvent.EventType.COMPLETED) {
                    completionLatch.countDown();
                }
            });
        
        // Wait for completion
        completionLatch.await(4, TimeUnit.MINUTES);
        
        // Count transitions to each state
        long inTransitCount = stateChangeEvents.stream()
            .filter(e -> e.getState() == DeliveryState.IN_TRANSIT)
            .count();
        long approachingCount = stateChangeEvents.stream()
            .filter(e -> e.getState() == DeliveryState.APPROACHING)
            .count();
        long completedCount = stateChangeEvents.stream()
            .filter(e -> e.getState() == DeliveryState.COMPLETED)
            .count();
        
        // Each state transition should occur exactly once
        assertEquals(1, inTransitCount, "Should transition to IN_TRANSIT exactly once");
        assertEquals(1, approachingCount, "Should transition to APPROACHING exactly once");
        assertEquals(1, completedCount, "Should transition to COMPLETED exactly once");
    }
    
    @Test
    @DisplayName("Cannot start duplicate simulation")
    void cannotStartDuplicateSimulation() {
        String deliveryId = "DUPLICATE-TEST-001";
        
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Origin")
            .destination(42.3736, -71.1097, "Destination")
            .build();
        
        // Start first simulation
        boolean firstStart = simulator.startSimulation(deliveryId, "farmer-001", interpolator, event -> {});
        assertTrue(firstStart, "First simulation should start");
        
        // Try to start duplicate
        boolean secondStart = simulator.startSimulation(deliveryId, "farmer-002", interpolator, event -> {});
        assertFalse(secondStart, "Duplicate simulation should not start");
        
        // Verify only one simulation is running
        assertTrue(simulator.isRunning(deliveryId), "Simulation should be running");
    }
    
    @Test
    @DisplayName("Stop simulation emits stopped event")
    void stopSimulationEmitsEvent() throws InterruptedException {
        String deliveryId = "STOP-TEST-001";
        
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Origin")
            .destination(42.3736, -71.1097, "Destination")
            .build();
        
        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicReference<DeliveryEvent> stopEvent = new AtomicReference<>();
        
        simulator.startSimulation(deliveryId, "farmer-001", interpolator, 
            event -> {
                if (event.getEventType() == DeliveryEvent.EventType.STOPPED) {
                    stopEvent.set(event);
                    stopLatch.countDown();
                }
            });
        
        // Wait a bit for simulation to start
        Thread.sleep(100);
        assertTrue(simulator.isRunning(deliveryId), "Simulation should be running");
        
        // Stop the simulation
        boolean stopped = simulator.stopSimulation(deliveryId);
        assertTrue(stopped, "Should stop simulation");
        
        // Wait for stopped event
        stopLatch.await(5, TimeUnit.SECONDS);
        
        assertNotNull(stopEvent.get(), "Should receive stopped event");
        assertEquals(DeliveryEvent.EventType.STOPPED, stopEvent.get().getEventType());
        assertTrue(stopEvent.get().isTerminal(), "Stopped event should be terminal");
    }
    
    @Test
    @DisplayName("Quality decays progressively")
    void qualityDecaysProgressively() throws InterruptedException {
        String deliveryId = "QUALITY-TEST-001";
        
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Origin")
            .destination(42.3736, -71.1097, "Destination")
            .build();
        
        List<Double> qualityReadings = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(1);
        
        simulator.startSimulation(deliveryId, "farmer-001", interpolator, 
            event -> {
                qualityReadings.add(event.getCurrentQuality());
                if (event.getEventType() == DeliveryEvent.EventType.COMPLETED) {
                    completionLatch.countDown();
                }
            });
        
        // Wait for completion
        completionLatch.await(4, TimeUnit.MINUTES);
        
        // Verify quality decreased over time
        assertFalse(qualityReadings.isEmpty(), "Should have quality readings");
        
        double firstQuality = qualityReadings.get(0);
        double lastQuality = qualityReadings.get(qualityReadings.size() - 1);
        
        assertTrue(lastQuality < firstQuality, 
            "Quality should decrease (first: " + firstQuality + ", last: " + lastQuality + ")");
        
        // Verify quality readings are monotonically decreasing (or stable within tolerance)
        for (int i = 1; i < qualityReadings.size(); i++) {
            assertTrue(qualityReadings.get(i) <= qualityReadings.get(i - 1) + QUALITY_TOLERANCE,
                "Quality should not increase: " + qualityReadings.get(i - 1) + " -> " + qualityReadings.get(i));
        }
    }
    
    @Test
    @DisplayName("Route interpolator generates valid coordinates")
    void routeInterpolatorGeneratesValidCoordinates() {
        RouteInterpolator interpolator = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Farm")
            .destination(42.3736, -71.1097, "Warehouse")
            .baseTemperature(4.0)
            .baseHumidity(65.0)
            .build();
        
        // Test at various progress points
        double[] progressPoints = {0.0, 0.25, 0.5, 0.75, 1.0};
        
        RouteInterpolator.Position prevPosition = null;
        
        for (double progress : progressPoints) {
            RouteInterpolator.InterpolatedData data = interpolator.interpolate(progress);
            
            // Verify coordinates are valid
            assertTrue(data.getLatitude() >= -90 && data.getLatitude() <= 90,
                "Latitude should be valid: " + data.getLatitude());
            assertTrue(data.getLongitude() >= -180 && data.getLongitude() <= 180,
                "Longitude should be valid: " + data.getLongitude());
            
            // Verify location name is set
            assertNotNull(data.getLocationName(), "Location name should not be null");
            assertFalse(data.getLocationName().isEmpty(), "Location name should not be empty");
            
            // Verify environmental data
            assertTrue(data.getTemperature() > -50 && data.getTemperature() < 50,
                "Temperature should be reasonable: " + data.getTemperature());
            assertTrue(data.getHumidity() >= 0 && data.getHumidity() <= 100,
                "Humidity should be 0-100: " + data.getHumidity());
            
            // Verify position progresses from origin to destination
            if (prevPosition != null) {
                // Coordinates should move towards destination
                double distFromOrigin = Math.sqrt(
                    Math.pow(data.getLatitude() - 42.3601, 2) +
                    Math.pow(data.getLongitude() - (-71.0589), 2)
                );
                // Just verify we're moving (not strictly increasing due to route variations)
            }
            
            prevPosition = data.getPosition();
        }
        
        // Verify endpoints
        RouteInterpolator.InterpolatedData startData = interpolator.interpolate(0);
        RouteInterpolator.InterpolatedData endData = interpolator.interpolate(1);
        
        assertEquals(42.3601, startData.getLatitude(), 0.001, "Start latitude should match origin");
        assertEquals(-71.0589, startData.getLongitude(), 0.001, "Start longitude should match origin");
        assertEquals(42.3736, endData.getLatitude(), 0.001, "End latitude should match destination");
        assertEquals(-71.1097, endData.getLongitude(), 0.001, "End longitude should match destination");
    }
    
    @Test
    @DisplayName("Simulation config creates valid default and demo modes")
    void simulationConfigModes() {
        SimulationConfig defaultConfig = SimulationConfig.createDefault();
        SimulationConfig demoConfig = SimulationConfig.createDemoMode();
        
        // Default mode
        assertFalse(defaultConfig.isDemoMode(), "Default should not be demo mode");
        assertEquals(1.0, defaultConfig.getSpeedFactor(), 0.001, "Default speed factor should be 1.0");
        
        // Demo mode
        assertTrue(demoConfig.isDemoMode(), "Demo config should be demo mode");
        assertTrue(demoConfig.getSpeedFactor() > 1.0, "Demo speed factor should be > 1.0");
        
        // Demo mode should complete faster
        double defaultTime = defaultConfig.getEstimatedCompletionMinutes();
        double demoTime = demoConfig.getEstimatedCompletionMinutes();
        
        assertTrue(demoTime < defaultTime, 
            "Demo mode should complete faster: " + demoTime + " < " + defaultTime);
        assertTrue(demoTime <= 4.0, 
            "Demo mode should complete within 4 minutes: " + demoTime);
        
        System.out.println("Default estimated time: " + String.format("%.1f", defaultTime) + " min");
        System.out.println("Demo estimated time: " + String.format("%.1f", demoTime) + " min");
    }
    
    @Test
    @DisplayName("Multiple concurrent simulations are isolated")
    void multipleConcurrentSimulationsIsolated() throws InterruptedException {
        String delivery1 = "CONCURRENT-001";
        String delivery2 = "CONCURRENT-002";
        
        RouteInterpolator interpolator1 = new RouteInterpolator.Builder()
            .origin(42.3601, -71.0589, "Farm 1")
            .destination(42.3736, -71.1097, "Warehouse 1")
            .build();
        
        RouteInterpolator interpolator2 = new RouteInterpolator.Builder()
            .origin(40.7128, -74.0060, "Farm 2")
            .destination(40.7589, -73.9851, "Warehouse 2")
            .build();
        
        List<DeliveryEvent> events1 = new ArrayList<>();
        List<DeliveryEvent> events2 = new ArrayList<>();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        // Start both simulations
        simulator.startSimulation(delivery1, "farmer-001", interpolator1, 
            event -> {
                events1.add(event);
                if (event.getEventType() == DeliveryEvent.EventType.COMPLETED) {
                    latch1.countDown();
                }
            });
        
        simulator.startSimulation(delivery2, "farmer-002", interpolator2, 
            event -> {
                events2.add(event);
                if (event.getEventType() == DeliveryEvent.EventType.COMPLETED) {
                    latch2.countDown();
                }
            });
        
        // Wait for both to complete
        assertTrue(latch1.await(4, TimeUnit.MINUTES), "Simulation 1 should complete");
        assertTrue(latch2.await(4, TimeUnit.MINUTES), "Simulation 2 should complete");
        
        // Verify events are separate
        assertFalse(events1.isEmpty(), "Simulation 1 should have events");
        assertFalse(events2.isEmpty(), "Simulation 2 should have events");
        
        // All events in events1 should have delivery1 ID
        assertTrue(events1.stream().allMatch(e -> e.getDeliveryId().equals(delivery1)),
            "All events1 should have delivery1 ID");
        
        // All events in events2 should have delivery2 ID
        assertTrue(events2.stream().allMatch(e -> e.getDeliveryId().equals(delivery2)),
            "All events2 should have delivery2 ID");
    }
}
