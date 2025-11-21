package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Service for simulating delivery routes and environmental conditions.
 * Emits location updates and environmental readings during transit.
 */
public class DeliverySimulator {
    private static final Logger logger = LoggerFactory.getLogger(DeliverySimulator.class);
    
    private final MessageService messageService;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, SimulationState> activeSimulations;
    private final Random random;
    
    /**
     * Geographic coordinate.
     */
    public static class GeoCoordinate {
        private final double latitude;
        private final double longitude;
        private final String name;
        
        public GeoCoordinate(double latitude, double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }
        
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getName() { return name; }
        
        @Override
        public String toString() {
            return String.format("%s (%.4f, %.4f)", name, latitude, longitude);
        }
    }
    
    /**
     * Route waypoint with timestamp.
     */
    public static class RouteWaypoint {
        private final GeoCoordinate location;
        private final long timestamp;
        private final double temperature;
        private final double humidity;
        
        public RouteWaypoint(GeoCoordinate location, long timestamp, 
                           double temperature, double humidity) {
            this.location = location;
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.humidity = humidity;
        }
        
        public GeoCoordinate getLocation() { return location; }
        public long getTimestamp() { return timestamp; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
    }
    
    /**
     * Simulation state.
     */
    private static class SimulationState {
        final String shipmentId;
        final List<RouteWaypoint> route;
        int currentWaypointIndex;
        boolean running;
        Future<?> task;
        
        SimulationState(String shipmentId, List<RouteWaypoint> route) {
            this.shipmentId = shipmentId;
            this.route = route;
            this.currentWaypointIndex = 0;
            this.running = false;
        }
    }
    
    public DeliverySimulator(MessageService messageService) {
        this.messageService = messageService;
        this.executor = Executors.newCachedThreadPool();
        this.activeSimulations = new ConcurrentHashMap<>();
        this.random = new Random();
        
        logger.info("DeliverySimulator initialized");
    }
    
    /**
     * Generate a simulated route between two locations.
     */
    public List<RouteWaypoint> generateRoute(GeoCoordinate origin, GeoCoordinate destination,
                                            int numWaypoints, long startTime, 
                                            double avgSpeedKmh) {
        if (numWaypoints < 2) {
            throw new IllegalArgumentException("Must have at least 2 waypoints");
        }
        
        List<RouteWaypoint> route = new ArrayList<>();
        
        // Calculate distance (simplified)
        double distance = calculateDistance(origin, destination);
        long totalTravelTimeMs = (long) ((distance / avgSpeedKmh) * 3600 * 1000);
        long intervalMs = totalTravelTimeMs / (numWaypoints - 1);
        
        // Generate waypoints
        for (int i = 0; i < numWaypoints; i++) {
            double fraction = (double) i / (numWaypoints - 1);
            
            // Interpolate position
            double lat = origin.getLatitude() + 
                        (destination.getLatitude() - origin.getLatitude()) * fraction;
            double lon = origin.getLongitude() + 
                        (destination.getLongitude() - origin.getLongitude()) * fraction;
            
            String name = (i == 0) ? origin.getName() : 
                         (i == numWaypoints - 1) ? destination.getName() :
                         String.format("Waypoint %d", i);
            
            GeoCoordinate location = new GeoCoordinate(lat, lon, name);
            long timestamp = startTime + (i * intervalMs);
            
            // Generate environmental readings with some variation
            double temp = 5.0 + random.nextGaussian() * 2.0;  // Mean 5°C, std dev 2°C
            double humidity = 75.0 + random.nextGaussian() * 10.0;  // Mean 75%, std dev 10%
            // Clamp values to realistic ranges
            temp = Math.max(-50.0, Math.min(50.0, temp));  // Temperature range [-50, 50]°C
            humidity = Math.max(0.0, Math.min(100.0, humidity));  // Humidity range [0, 100]%
            
            route.add(new RouteWaypoint(location, timestamp, temp, humidity));
        }
        
        double durationHours = totalTravelTimeMs / 3600000.0;
        String distanceStr = String.format("%.1f", distance);
        String durationStr = String.format("%.1f", durationHours);
        
        logger.info("Generated route: {} waypoints, distance: {} km, duration: {} hours",
                    numWaypoints, distanceStr, durationStr);
        
        return route;
    }
    
    /**
     * Start a delivery simulation.
     */
    public void startSimulation(String shipmentId, List<RouteWaypoint> route, 
                               long updateIntervalMs) {
        if (activeSimulations.containsKey(shipmentId)) {
            logger.warn("Simulation already running for shipment: {}", shipmentId);
            return;
        }
        
        SimulationState state = new SimulationState(shipmentId, new ArrayList<>(route));
        state.running = true;
        
        // Start simulation task
        state.task = executor.submit(() -> runSimulation(state, updateIntervalMs));
        
        activeSimulations.put(shipmentId, state);
        
        logger.info("Started simulation for shipment: {}", shipmentId);
        
        // Send initial message
        sendSimulationMessage(shipmentId, "Delivery simulation started", 
                            route.get(0).getLocation().toString());
    }
    
    /**
     * Stop a delivery simulation.
     */
    public void stopSimulation(String shipmentId) {
        SimulationState state = activeSimulations.get(shipmentId);
        if (state == null) {
            logger.warn("No active simulation for shipment: {}", shipmentId);
            return;
        }
        
        state.running = false;
        if (state.task != null) {
            state.task.cancel(true);
        }
        
        activeSimulations.remove(shipmentId);
        
        logger.info("Stopped simulation for shipment: {}", shipmentId);
        
        sendSimulationMessage(shipmentId, "Delivery simulation stopped", "");
    }
    
    /**
     * Get status of a simulation.
     */
    public SimulationStatus getSimulationStatus(String shipmentId) {
        SimulationState state = activeSimulations.get(shipmentId);
        if (state == null) {
            return new SimulationStatus(shipmentId, false, 0, 0, null);
        }
        
        RouteWaypoint current = state.route.get(state.currentWaypointIndex);
        return new SimulationStatus(shipmentId, state.running, 
                                   state.currentWaypointIndex, state.route.size(), current);
    }
    
    /**
     * Run the simulation.
     */
    private void runSimulation(SimulationState state, long updateIntervalMs) {
        try {
            while (state.running && state.currentWaypointIndex < state.route.size()) {
                RouteWaypoint waypoint = state.route.get(state.currentWaypointIndex);
                
                // Send location update message
                String content = String.format(
                    "Location: %s | Temp: %.1f°C | Humidity: %.1f%%",
                    waypoint.getLocation().toString(),
                    waypoint.getTemperature(),
                    waypoint.getHumidity()
                );
                
                sendSimulationMessage(state.shipmentId, "Location Update", content);
                
                // Check for environmental alerts
                if (waypoint.getTemperature() < 4.0 || waypoint.getTemperature() > 8.0) {
                    sendSimulationMessage(state.shipmentId, "Temperature Alert",
                        String.format("Temperature outside range: %.1f°C", waypoint.getTemperature()));
                }
                
                state.currentWaypointIndex++;
                
                // Check if delivery is complete
                if (state.currentWaypointIndex >= state.route.size()) {
                    sendSimulationMessage(state.shipmentId, "Delivery Complete",
                        "Shipment arrived at destination");
                    state.running = false;
                    break;
                }
                
                // Wait for next update
                Thread.sleep(updateIntervalMs);
            }
            
        } catch (InterruptedException e) {
            logger.info("Simulation interrupted for shipment: {}", state.shipmentId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Simulation error for shipment: {}", state.shipmentId, e);
        } finally {
            activeSimulations.remove(state.shipmentId);
        }
    }
    
    /**
     * Send a message via the messaging system.
     */
    private void sendSimulationMessage(String shipmentId, String subject, String content) {
        if (messageService != null) {
            try {
                Message message = new Message(
                    "logistics",
                    "delivery_simulator",
                    "all",  // Broadcast to all roles
                    null,
                    subject,
                    content
                );
                message.setShipmentId(shipmentId);
                messageService.sendMessage(message);
            } catch (Exception e) {
                logger.error("Failed to send simulation message", e);
            }
        }
    }
    
    /**
     * Calculate distance between two coordinates (simplified Haversine).
     */
    private double calculateDistance(GeoCoordinate c1, GeoCoordinate c2) {
        double lat1 = Math.toRadians(c1.getLatitude());
        double lat2 = Math.toRadians(c2.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(c2.getLongitude() - c1.getLongitude());
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(lat1) * Math.cos(lat2) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371 * c;  // Earth radius in km
    }
    
    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        activeSimulations.values().forEach(state -> state.running = false);
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("DeliverySimulator shut down");
    }
    
    /**
     * Simulation status response.
     */
    public static class SimulationStatus {
        private final String shipmentId;
        private final boolean running;
        private final int currentWaypoint;
        private final int totalWaypoints;
        private final RouteWaypoint currentLocation;
        
        public SimulationStatus(String shipmentId, boolean running, int currentWaypoint,
                              int totalWaypoints, RouteWaypoint currentLocation) {
            this.shipmentId = shipmentId;
            this.running = running;
            this.currentWaypoint = currentWaypoint;
            this.totalWaypoints = totalWaypoints;
            this.currentLocation = currentLocation;
        }
        
        public String getShipmentId() { return shipmentId; }
        public boolean isRunning() { return running; }
        public int getCurrentWaypoint() { return currentWaypoint; }
        public int getTotalWaypoints() { return totalWaypoints; }
        public RouteWaypoint getCurrentLocation() { return currentLocation; }
    }
}
