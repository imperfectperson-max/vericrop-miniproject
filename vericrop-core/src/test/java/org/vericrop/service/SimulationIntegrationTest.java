package org.vericrop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.Alert;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;
import org.vericrop.service.simulation.SimulationManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for end-to-end simulation flow.
 * Tests the integrated functionality of MapService, TemperatureService, AlertService,
 * and SimulationManager working together.
 */
public class SimulationIntegrationTest {
    
    // Test constants - use scenario temperature drift value
    private static final double HOT_TRANSPORT_MIN_TEMP = 5.0 + Scenario.HOT_TRANSPORT.getTemperatureDrift() - 2.0; // Expected: > 11°C avg, allow margin
    
    private MessageService messageService;
    private AlertService alertService;
    private DeliverySimulator deliverySimulator;
    private MapService mapService;
    private TemperatureService temperatureService;
    private SimulationManager simulationManager;
    
    @BeforeEach
    public void setup() {
        // Reset the singleton to ensure clean state for each test
        SimulationManager.resetForTesting();
        
        messageService = new MessageService(false); // No persistence for tests
        alertService = new AlertService();
        deliverySimulator = new DeliverySimulator(messageService, alertService);
        mapService = new MapService();
        temperatureService = new TemperatureService();
        
        // Initialize SimulationManager with all services
        SimulationManager.initialize(deliverySimulator, mapService, temperatureService, alertService);
        simulationManager = SimulationManager.getInstance();
    }
    
    @AfterEach
    public void teardown() {
        if (simulationManager != null && simulationManager.isRunning()) {
            simulationManager.stopSimulation();
        }
        if (deliverySimulator != null) {
            deliverySimulator.shutdown();
        }
        // Reset to clean up the singleton after each test
        SimulationManager.resetForTesting();
    }
    
    @Test
    public void testScenarioLoader() {
        ScenarioLoader loader = new ScenarioLoader();
        assertNotNull(loader);
        
        // Should handle missing files gracefully
        ScenarioLoader.ScenarioDefinition def = loader.getScenarioDefinition("non-existent");
        assertNull(def);
        
        // Should map to enum values
        Scenario normal = loader.mapToScenarioEnum(null);
        assertEquals(Scenario.NORMAL, normal);
    }
    
    @Test
    public void testMultiLegRouteGeneration() {
        String batchId = "TEST_BATCH_001";
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm Location");
        GeoCoordinate warehouse = new GeoCoordinate(40.7580, -73.9855, "Distribution Center");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Consumer Location");
        
        List<RouteWaypoint> route = mapService.generateMultiLegRoute(
            batchId, origin, warehouse, destination, 
            5, System.currentTimeMillis(), 50.0, Scenario.NORMAL
        );
        
        assertNotNull(route);
        assertFalse(route.isEmpty());
        assertTrue(route.size() >= 9); // At least 5 + 5 - 1 (excluding duplicate waypoint)
        
        // Verify first and last waypoints
        RouteWaypoint first = route.get(0);
        RouteWaypoint last = route.get(route.size() - 1);
        
        assertEquals(origin.getLatitude(), first.getLocation().getLatitude(), 0.0001);
        assertEquals(destination.getLatitude(), last.getLocation().getLatitude(), 0.0001);
        
        // Verify route metadata was registered
        MapService.RouteMetadata metadata = mapService.getRouteMetadata(batchId);
        assertNotNull(metadata);
        assertEquals(batchId, metadata.getBatchId());
        assertEquals(route.size(), metadata.getWaypointCount());
    }
    
    @Test
    public void testTemperatureMonitoringAndRecording() {
        String batchId = "TEST_BATCH_002";
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Origin");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Destination");
        
        // Generate route
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 10, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL
        );
        
        // Start monitoring
        temperatureService.startMonitoring(batchId);
        
        // Record route
        temperatureService.recordRoute(batchId, route);
        
        // Verify monitoring data
        TemperatureService.TemperatureMonitoring monitoring = 
            temperatureService.getMonitoring(batchId);
        
        assertNotNull(monitoring);
        assertEquals(batchId, monitoring.getBatchId());
        assertEquals(10, monitoring.getReadingCount());
        assertTrue(monitoring.getAvgTemp() > 0);
        assertTrue(monitoring.getMinTemp() <= monitoring.getMaxTemp());
        
        // Stop monitoring
        TemperatureService.TemperatureMonitoring finalReport = 
            temperatureService.stopMonitoring(batchId);
        
        assertNotNull(finalReport);
        assertEquals(monitoring.getReadingCount(), finalReport.getReadingCount());
    }
    
    @Test
    public void testAlertGeneration() throws InterruptedException {
        String batchId = "TEST_BATCH_003";
        
        // Create an alert
        Alert alert = new Alert(
            "TEST_ALERT_001",
            batchId,
            Alert.AlertType.TEMPERATURE_HIGH,
            Alert.Severity.HIGH,
            "Temperature exceeded threshold",
            10.5,
            8.0,
            System.currentTimeMillis(),
            "Test Location"
        );
        
        // Record alert
        alertService.recordAlert(alert);
        
        // Give it a moment to process
        TimeUnit.MILLISECONDS.sleep(100);
        
        // Verify alert was recorded
        List<Alert> alerts = alertService.getAlertsByBatch(batchId);
        assertFalse(alerts.isEmpty());
        assertEquals(1, alerts.size());
        assertEquals(batchId, alerts.get(0).getBatchId());
        assertEquals(Alert.AlertType.TEMPERATURE_HIGH, alerts.get(0).getType());
    }
    
    @Test
    public void testHotTransportScenarioGeneratesHigherTemperatures() {
        String batchId = "TEST_BATCH_004";
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Origin");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Destination");
        
        // Generate route with HOT_TRANSPORT scenario
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 20, 
            System.currentTimeMillis(), 50.0, Scenario.HOT_TRANSPORT
        );
        
        assertNotNull(route);
        assertFalse(route.isEmpty());
        
        // Calculate average temperature
        double avgTemp = route.stream()
            .mapToDouble(RouteWaypoint::getTemperature)
            .average()
            .orElse(0.0);
        
        // HOT_TRANSPORT should have higher average temperature than normal (5°C)
        // With +8°C drift, expect average around 13°C
        assertTrue(avgTemp > HOT_TRANSPORT_MIN_TEMP, 
                  "HOT_TRANSPORT scenario should produce higher temperatures");
    }
    
    @Test
    public void testRouteConcatenation() {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Origin");
        GeoCoordinate midpoint = new GeoCoordinate(41.0, -74.0, "Midpoint");
        GeoCoordinate destination = new GeoCoordinate(42.0, -74.0, "Destination");
        
        long startTime = System.currentTimeMillis();
        
        List<RouteWaypoint> segment1 = mapService.generateRoute(
            "seg1", origin, midpoint, 5, startTime, 50.0, Scenario.NORMAL
        );
        
        List<RouteWaypoint> segment2 = mapService.generateRoute(
            "seg2", midpoint, destination, 5, 
            segment1.get(segment1.size() - 1).getTimestamp(), 50.0, Scenario.NORMAL
        );
        
        List<List<RouteWaypoint>> segments = List.of(segment1, segment2);
        List<RouteWaypoint> combined = mapService.concatenateRoutes(segments);
        
        assertNotNull(combined);
        assertEquals(9, combined.size()); // 5 + 5 - 1 (duplicate removed)
        
        // Verify first and last points
        assertEquals(origin.getLatitude(), combined.get(0).getLocation().getLatitude(), 0.0001);
        assertEquals(destination.getLatitude(), combined.get(combined.size() - 1).getLocation().getLatitude(), 0.0001);
    }
}
