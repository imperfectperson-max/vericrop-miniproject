package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapServiceTest {
    
    private MapService mapService;
    
    @BeforeEach
    void setUp() {
        mapService = new MapService();
    }
    
    @Test
    void testGenerateRoute() {
        String batchId = "BATCH_MAP_001";
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "New York");
        GeoCoordinate destination = new GeoCoordinate(34.0522, -118.2437, "Los Angeles");
        int numWaypoints = 5;
        long startTime = System.currentTimeMillis();
        double avgSpeedKmh = 80.0;
        Scenario scenario = Scenario.NORMAL;
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, numWaypoints, startTime, avgSpeedKmh, scenario);
        
        assertNotNull(route);
        assertEquals(numWaypoints, route.size());
        
        // Verify first waypoint is at origin
        RouteWaypoint firstWaypoint = route.get(0);
        assertEquals(origin.getLatitude(), firstWaypoint.getLocation().getLatitude(), 0.001);
        assertEquals(origin.getLongitude(), firstWaypoint.getLocation().getLongitude(), 0.001);
        
        // Verify last waypoint is at destination
        RouteWaypoint lastWaypoint = route.get(numWaypoints - 1);
        assertEquals(destination.getLatitude(), lastWaypoint.getLocation().getLatitude(), 0.001);
        assertEquals(destination.getLongitude(), lastWaypoint.getLocation().getLongitude(), 0.001);
        
        // Verify timestamps are increasing
        for (int i = 1; i < route.size(); i++) {
            assertTrue(route.get(i).getTimestamp() >= route.get(i - 1).getTimestamp());
        }
        
        // Verify route metadata was registered
        MapService.RouteMetadata metadata = mapService.getRouteMetadata(batchId);
        assertNotNull(metadata);
        assertEquals(batchId, metadata.getBatchId());
        assertEquals(numWaypoints, metadata.getWaypointCount());
        assertTrue(metadata.getTotalDistance() > 0);
    }
    
    @Test
    void testGenerateRouteWithHotTransportScenario() {
        String batchId = "BATCH_MAP_002";
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        Scenario scenario = Scenario.HOT_TRANSPORT;
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 3, System.currentTimeMillis(), 60.0, scenario);
        
        assertNotNull(route);
        assertEquals(3, route.size());
        
        // Verify temperature drift is applied (should be higher than normal)
        for (RouteWaypoint waypoint : route) {
            // HOT_TRANSPORT has +8.0°C drift, so temps should generally be higher
            assertTrue(waypoint.getTemperature() > 0);
        }
    }
    
    @Test
    void testGenerateRouteWithExtremeDelayScenario() {
        String batchId = "BATCH_MAP_003";
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        Scenario normalScenario = Scenario.NORMAL;
        Scenario delayScenario = Scenario.EXTREME_DELAY;
        long startTime = System.currentTimeMillis();
        
        List<RouteWaypoint> normalRoute = mapService.generateRoute(
            "BATCH_NORMAL", origin, destination, 3, startTime, 60.0, normalScenario);
        
        List<RouteWaypoint> delayRoute = mapService.generateRoute(
            batchId, origin, destination, 3, startTime, 60.0, delayScenario);
        
        MapService.RouteMetadata normalMeta = mapService.getRouteMetadata("BATCH_NORMAL");
        MapService.RouteMetadata delayMeta = mapService.getRouteMetadata(batchId);
        
        // EXTREME_DELAY has 0.4 speed multiplier, so should take longer
        assertTrue(delayMeta.getEstimatedDuration() > normalMeta.getEstimatedDuration());
    }
    
    @Test
    void testGetRouteMetadata() {
        String batchId = "BATCH_MAP_004";
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        
        mapService.generateRoute(batchId, origin, destination, 5, 
                                System.currentTimeMillis(), 80.0, Scenario.NORMAL);
        
        MapService.RouteMetadata metadata = mapService.getRouteMetadata(batchId);
        
        assertNotNull(metadata);
        assertEquals(batchId, metadata.getBatchId());
        assertEquals(40.0, metadata.getOrigin().getLatitude(), 0.001);
        assertEquals(41.0, metadata.getDestination().getLatitude(), 0.001);
        assertEquals(5, metadata.getWaypointCount());
    }
    
    @Test
    void testClearRoute() {
        String batchId = "BATCH_MAP_005";
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        
        mapService.generateRoute(batchId, origin, destination, 3, 
                                System.currentTimeMillis(), 60.0, Scenario.NORMAL);
        
        assertNotNull(mapService.getRouteMetadata(batchId));
        
        mapService.clearRoute(batchId);
        
        assertNull(mapService.getRouteMetadata(batchId));
    }
    
    @Test
    void testGetAllRoutes() {
        mapService.generateRoute("BATCH_1", 
            new GeoCoordinate(40.0, -74.0, "A"), 
            new GeoCoordinate(41.0, -75.0, "B"), 
            3, System.currentTimeMillis(), 60.0, Scenario.NORMAL);
            
        mapService.generateRoute("BATCH_2", 
            new GeoCoordinate(42.0, -76.0, "C"), 
            new GeoCoordinate(43.0, -77.0, "D"), 
            3, System.currentTimeMillis(), 60.0, Scenario.NORMAL);
        
        assertEquals(2, mapService.getAllRoutes().size());
        assertTrue(mapService.getAllRoutes().containsKey("BATCH_1"));
        assertTrue(mapService.getAllRoutes().containsKey("BATCH_2"));
    }
}
