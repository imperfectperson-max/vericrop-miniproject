package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing routes and geographic mapping for deliveries.
 * Generates routes with waypoints and tracks route metadata.
 */
public class MapService {
    private static final Logger logger = LoggerFactory.getLogger(MapService.class);
    
    // Humidity thresholds (%)
    private static final double HUMIDITY_MIN = 65.0;
    private static final double HUMIDITY_MAX = 95.0;
    private static final double HUMIDITY_IDEAL = 80.0;
    
    // Temperature thresholds for fresh produce (°C)
    private static final double TEMP_MIN = 2.0;
    private static final double TEMP_MAX = 8.0;
    private static final double TEMP_IDEAL = 5.0;
    
    private final Map<String, RouteMetadata> routeRegistry;
    private final Random random;
    
    /**
     * Route metadata tracking.
     */
    public static class RouteMetadata {
        private final String batchId;
        private final GeoCoordinate origin;
        private final GeoCoordinate destination;
        private final int waypointCount;
        private final double totalDistance;
        private final long estimatedDuration;
        
        public RouteMetadata(String batchId, GeoCoordinate origin, GeoCoordinate destination,
                           int waypointCount, double totalDistance, long estimatedDuration) {
            this.batchId = batchId;
            this.origin = origin;
            this.destination = destination;
            this.waypointCount = waypointCount;
            this.totalDistance = totalDistance;
            this.estimatedDuration = estimatedDuration;
        }
        
        public String getBatchId() { return batchId; }
        public GeoCoordinate getOrigin() { return origin; }
        public GeoCoordinate getDestination() { return destination; }
        public int getWaypointCount() { return waypointCount; }
        public double getTotalDistance() { return totalDistance; }
        public long getEstimatedDuration() { return estimatedDuration; }
    }
    
    public MapService() {
        this.routeRegistry = new ConcurrentHashMap<>();
        this.random = new Random();
    }
    
    /**
     * Generate a route with waypoints between origin and destination.
     */
    public List<RouteWaypoint> generateRoute(String batchId, GeoCoordinate origin, 
                                             GeoCoordinate destination, int numWaypoints,
                                             long startTime, double avgSpeedKmh, Scenario scenario) {
        List<RouteWaypoint> waypoints = new ArrayList<>();
        
        double latDiff = destination.getLatitude() - origin.getLatitude();
        double lonDiff = destination.getLongitude() - origin.getLongitude();
        double distance = calculateDistance(origin, destination);
        
        // Calculate time interval between waypoints based on speed
        double speedMultiplier = scenario != null ? scenario.getSpeedMultiplier() : 1.0;
        double effectiveSpeed = avgSpeedKmh * speedMultiplier;
        long totalDurationMs = (long) ((distance / effectiveSpeed) * 3600 * 1000);
        long timeInterval = totalDurationMs / (numWaypoints - 1);
        
        // Generate waypoints
        for (int i = 0; i < numWaypoints; i++) {
            double fraction = (double) i / (numWaypoints - 1);
            double lat = origin.getLatitude() + (latDiff * fraction);
            double lon = origin.getLongitude() + (lonDiff * fraction);
            long timestamp = startTime + (timeInterval * i);
            
            // Calculate environmental conditions with scenario effects
            double temperature = calculateTemperature(scenario);
            double humidity = calculateHumidity(scenario);
            
            GeoCoordinate location = new GeoCoordinate(lat, lon, String.format("Waypoint %d", i));
            RouteWaypoint waypoint = new RouteWaypoint(location, timestamp, temperature, humidity);
            waypoints.add(waypoint);
        }
        
        // Register route metadata
        RouteMetadata metadata = new RouteMetadata(batchId, origin, destination, 
                                                   numWaypoints, distance, totalDurationMs);
        routeRegistry.put(batchId, metadata);
        
        logger.info("Generated route for batch {} with {} waypoints, distance: {:.2f} km, duration: {} ms",
                   batchId, numWaypoints, distance, totalDurationMs);
        
        return waypoints;
    }
    
    /**
     * Calculate distance between two coordinates (simplified Haversine formula).
     */
    private double calculateDistance(GeoCoordinate from, GeoCoordinate to) {
        double earthRadiusKm = 6371.0;
        
        double dLat = Math.toRadians(to.getLatitude() - from.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadiusKm * c;
    }
    
    /**
     * Calculate temperature with scenario effects.
     */
    private double calculateTemperature(Scenario scenario) {
        double baseTemp = TEMP_IDEAL + (random.nextGaussian() * 1.5);
        if (scenario != null) {
            baseTemp += scenario.getTemperatureDrift();
        }
        return baseTemp;
    }
    
    /**
     * Calculate humidity with scenario effects.
     */
    private double calculateHumidity(Scenario scenario) {
        double baseHumidity = HUMIDITY_IDEAL + (random.nextGaussian() * 5.0);
        if (scenario != null) {
            baseHumidity += scenario.getHumidityDrift();
        }
        return Math.max(HUMIDITY_MIN, Math.min(HUMIDITY_MAX, baseHumidity));
    }
    
    /**
     * Get route metadata for a batch.
     */
    public RouteMetadata getRouteMetadata(String batchId) {
        return routeRegistry.get(batchId);
    }
    
    /**
     * Get all registered routes.
     */
    public Map<String, RouteMetadata> getAllRoutes() {
        return new ConcurrentHashMap<>(routeRegistry);
    }
    
    /**
     * Clear route metadata for a batch.
     */
    public void clearRoute(String batchId) {
        routeRegistry.remove(batchId);
        logger.debug("Cleared route metadata for batch: {}", batchId);
    }
}
