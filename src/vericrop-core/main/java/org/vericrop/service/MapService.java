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
    
    /**
     * Generate a multi-leg route: origin -> warehouse -> destination.
     * Creates a realistic route that goes through an intermediate warehouse location.
     * 
     * @param batchId Batch identifier
     * @param origin Starting location (farmer)
     * @param warehouse Intermediate warehouse location
     * @param destination Final destination (consumer)
     * @param waypointsPerLeg Number of waypoints per leg
     * @param startTime Start timestamp
     * @param avgSpeedKmh Average speed in km/h
     * @param scenario Delivery scenario
     * @return Combined list of waypoints for the full route
     */
    public List<RouteWaypoint> generateMultiLegRoute(String batchId, GeoCoordinate origin,
                                                     GeoCoordinate warehouse, GeoCoordinate destination,
                                                     int waypointsPerLeg, long startTime,
                                                     double avgSpeedKmh, Scenario scenario) {
        logger.info("Generating multi-leg route: {} -> {} -> {}", 
                   origin.getName(), warehouse.getName(), destination.getName());
        
        // Generate first leg: origin -> warehouse
        List<RouteWaypoint> leg1 = generateRoute(
            batchId + "_leg1", origin, warehouse, waypointsPerLeg, 
            startTime, avgSpeedKmh, scenario);
        
        // Calculate start time for second leg
        long leg1EndTime = leg1.isEmpty() ? startTime : leg1.get(leg1.size() - 1).getTimestamp();
        
        // Generate second leg: warehouse -> destination
        List<RouteWaypoint> leg2 = generateRoute(
            batchId + "_leg2", warehouse, destination, waypointsPerLeg,
            leg1EndTime, avgSpeedKmh, scenario);
        
        // Combine legs (exclude duplicate warehouse waypoint)
        List<RouteWaypoint> fullRoute = new ArrayList<>(leg1);
        if (!leg2.isEmpty()) {
            // Skip first waypoint of leg2 as it's the same as last of leg1
            fullRoute.addAll(leg2.subList(1, leg2.size()));
        }
        
        // Calculate total distance and duration
        double totalDistance = calculateDistance(origin, warehouse) + 
                              calculateDistance(warehouse, destination);
        long totalDuration = fullRoute.isEmpty() ? 0 : 
                           fullRoute.get(fullRoute.size() - 1).getTimestamp() - startTime;
        
        // Register combined route metadata
        RouteMetadata metadata = new RouteMetadata(batchId, origin, destination,
                                                   fullRoute.size(), totalDistance, totalDuration);
        routeRegistry.put(batchId, metadata);
        
        logger.info("Generated multi-leg route for batch {}: {} waypoints, {:.2f} km, {} ms",
                   batchId, fullRoute.size(), totalDistance, totalDuration);
        
        return fullRoute;
    }
    
    /**
     * Concatenate multiple route segments into a single route with proper timestamp progression.
     * 
     * @param routeSegments List of route segments to concatenate
     * @return Combined route with sequential timestamps
     */
    public List<RouteWaypoint> concatenateRoutes(List<List<RouteWaypoint>> routeSegments) {
        if (routeSegments == null || routeSegments.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<RouteWaypoint> combined = new ArrayList<>();
        
        for (int i = 0; i < routeSegments.size(); i++) {
            List<RouteWaypoint> segment = routeSegments.get(i);
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            
            if (i == 0) {
                // First segment - add all waypoints
                combined.addAll(segment);
            } else {
                // Subsequent segments - skip first waypoint to avoid duplication
                combined.addAll(segment.subList(1, segment.size()));
            }
        }
        
        logger.debug("Concatenated {} route segments into {} waypoints", 
                    routeSegments.size(), combined.size());
        
        return combined;
    }
}
