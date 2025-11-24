package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureServiceTest {
    
    private TemperatureService temperatureService;
    
    @BeforeEach
    void setUp() {
        temperatureService = new TemperatureService();
    }
    
    @Test
    void testStartMonitoring() {
        String batchId = "BATCH_TEST_001";
        
        temperatureService.startMonitoring(batchId);
        
        TemperatureService.TemperatureMonitoring monitoring = temperatureService.getMonitoring(batchId);
        assertNotNull(monitoring);
        assertEquals(batchId, monitoring.getBatchId());
        assertEquals(0, monitoring.getReadingCount());
        assertTrue(monitoring.isCompliant());
    }
    
    @Test
    void testRecordRouteWithCompliantTemperatures() {
        String batchId = "BATCH_TEST_002";
        temperatureService.startMonitoring(batchId);
        
        // Create route with compliant temperatures (2-8°C range)
        List<RouteWaypoint> waypoints = createTestRoute(batchId, 5.0, 6.0, 5.5);
        
        temperatureService.recordRoute(batchId, waypoints);
        
        TemperatureService.TemperatureMonitoring monitoring = temperatureService.getMonitoring(batchId);
        assertEquals(3, monitoring.getReadingCount());
        assertEquals(0, monitoring.getViolationCount());
        assertTrue(monitoring.isCompliant());
        assertEquals(5.0, monitoring.getMinTemp(), 0.01);
        assertEquals(6.0, monitoring.getMaxTemp(), 0.01);
    }
    
    @Test
    void testRecordRouteWithViolations() {
        String batchId = "BATCH_TEST_003";
        temperatureService.startMonitoring(batchId);
        
        // Create route with temperature violations (outside 2-8°C range)
        List<RouteWaypoint> waypoints = createTestRoute(batchId, 1.0, 10.0, 5.0);
        
        temperatureService.recordRoute(batchId, waypoints);
        
        TemperatureService.TemperatureMonitoring monitoring = temperatureService.getMonitoring(batchId);
        assertEquals(3, monitoring.getReadingCount());
        assertEquals(2, monitoring.getViolationCount()); // 1.0 and 10.0 are violations
        assertFalse(monitoring.isCompliant());
    }
    
    @Test
    void testStopMonitoring() {
        String batchId = "BATCH_TEST_004";
        temperatureService.startMonitoring(batchId);
        
        List<RouteWaypoint> waypoints = createTestRoute(batchId, 5.0, 6.0, 5.5);
        temperatureService.recordRoute(batchId, waypoints);
        
        TemperatureService.TemperatureMonitoring monitoring = temperatureService.stopMonitoring(batchId);
        
        assertNotNull(monitoring);
        assertEquals(3, monitoring.getReadingCount());
        assertNull(temperatureService.getMonitoring(batchId));
    }
    
    @Test
    void testGetAllActiveMonitoring() {
        String batchId1 = "BATCH_TEST_005";
        String batchId2 = "BATCH_TEST_006";
        
        temperatureService.startMonitoring(batchId1);
        temperatureService.startMonitoring(batchId2);
        
        assertEquals(2, temperatureService.getAllActiveMonitoring().size());
        assertTrue(temperatureService.getAllActiveMonitoring().containsKey(batchId1));
        assertTrue(temperatureService.getAllActiveMonitoring().containsKey(batchId2));
    }
    
    @Test
    void testRecordRouteWithoutMonitoring() {
        String batchId = "BATCH_TEST_007";
        List<RouteWaypoint> waypoints = createTestRoute(batchId, 5.0, 6.0, 5.5);
        
        // Should handle gracefully without throwing exception
        assertDoesNotThrow(() -> temperatureService.recordRoute(batchId, waypoints));
    }
    
    private List<RouteWaypoint> createTestRoute(String batchId, double temp1, double temp2, double temp3) {
        List<RouteWaypoint> waypoints = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        
        waypoints.add(new RouteWaypoint(
            new GeoCoordinate(40.0, -74.0, "Point 1"),
            timestamp,
            temp1,
            80.0
        ));
        
        waypoints.add(new RouteWaypoint(
            new GeoCoordinate(40.5, -74.5, "Point 2"),
            timestamp + 1000,
            temp2,
            80.0
        ));
        
        waypoints.add(new RouteWaypoint(
            new GeoCoordinate(41.0, -75.0, "Point 3"),
            timestamp + 2000,
            temp3,
            80.0
        ));
        
        return waypoints;
    }
}
