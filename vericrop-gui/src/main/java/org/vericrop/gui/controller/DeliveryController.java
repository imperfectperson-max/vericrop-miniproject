package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.DeliverySimulator.GeoCoordinate;
import org.vericrop.service.DeliverySimulator.RouteWaypoint;
import org.vericrop.service.DeliverySimulator.SimulationStatus;
import org.vericrop.service.MessageService;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for delivery simulation and route management.
 */
@RestController
@RequestMapping("/api/v1/delivery")
@CrossOrigin(origins = "*")
public class DeliveryController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    
    private final DeliverySimulator deliverySimulator;
    private final MessageService messageService;
    
    public DeliveryController() {
        this.messageService = new MessageService(true);
        this.deliverySimulator = new DeliverySimulator(messageService);
        logger.info("DeliveryController initialized");
    }
    
    /**
     * POST /api/v1/delivery/generate-route
     * Generate a simulated route between two locations.
     */
    @PostMapping("/generate-route")
    public ResponseEntity<Map<String, Object>> generateRoute(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> originData = (Map<String, Object>) request.get("origin");
            @SuppressWarnings("unchecked")
            Map<String, Object> destinationData = (Map<String, Object>) request.get("destination");
            
            GeoCoordinate origin = new GeoCoordinate(
                ((Number) originData.get("latitude")).doubleValue(),
                ((Number) originData.get("longitude")).doubleValue(),
                (String) originData.get("name")
            );
            
            GeoCoordinate destination = new GeoCoordinate(
                ((Number) destinationData.get("latitude")).doubleValue(),
                ((Number) destinationData.get("longitude")).doubleValue(),
                (String) destinationData.get("name")
            );
            
            int numWaypoints = request.containsKey("num_waypoints") ? 
                              ((Number) request.get("num_waypoints")).intValue() : 10;
            
            long startTime = request.containsKey("start_time") ?
                           ((Number) request.get("start_time")).longValue() :
                           Instant.now().toEpochMilli();
            
            double avgSpeed = request.containsKey("avg_speed_kmh") ?
                            ((Number) request.get("avg_speed_kmh")).doubleValue() : 60.0;
            
            List<RouteWaypoint> route = deliverySimulator.generateRoute(
                origin, destination, numWaypoints, startTime, avgSpeed);
            
            // Convert route to response format
            List<Map<String, Object>> routeData = new ArrayList<>();
            for (RouteWaypoint waypoint : route) {
                Map<String, Object> waypointData = new HashMap<>();
                waypointData.put("latitude", waypoint.getLocation().getLatitude());
                waypointData.put("longitude", waypoint.getLocation().getLongitude());
                waypointData.put("name", waypoint.getLocation().getName());
                waypointData.put("timestamp", waypoint.getTimestamp());
                waypointData.put("temperature", waypoint.getTemperature());
                waypointData.put("humidity", waypoint.getHumidity());
                routeData.add(waypointData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("route", routeData);
            response.put("num_waypoints", route.size());
            response.put("origin", origin.toString());
            response.put("destination", destination.toString());
            
            logger.info("Generated route: {} waypoints from {} to {}", 
                       route.size(), origin.getName(), destination.getName());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to generate route", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * POST /api/v1/delivery/start-simulation
     * Start a delivery simulation for a shipment.
     */
    @PostMapping("/start-simulation")
    public ResponseEntity<Map<String, Object>> startSimulation(@RequestBody Map<String, Object> request) {
        try {
            String shipmentId = (String) request.get("shipment_id");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> routeData = (List<Map<String, Object>>) request.get("route");
            
            long updateInterval = request.containsKey("update_interval_ms") ?
                                ((Number) request.get("update_interval_ms")).longValue() : 5000L;
            
            // Convert route data to RouteWaypoint objects
            List<RouteWaypoint> route = new ArrayList<>();
            for (Map<String, Object> waypointData : routeData) {
                GeoCoordinate location = new GeoCoordinate(
                    ((Number) waypointData.get("latitude")).doubleValue(),
                    ((Number) waypointData.get("longitude")).doubleValue(),
                    (String) waypointData.get("name")
                );
                
                long timestamp = ((Number) waypointData.get("timestamp")).longValue();
                double temp = ((Number) waypointData.get("temperature")).doubleValue();
                double humidity = ((Number) waypointData.get("humidity")).doubleValue();
                
                route.add(new RouteWaypoint(location, timestamp, temp, humidity));
            }
            
            deliverySimulator.startSimulation(shipmentId, route, updateInterval);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("shipment_id", shipmentId);
            response.put("num_waypoints", route.size());
            response.put("update_interval_ms", updateInterval);
            response.put("message", "Simulation started");
            
            logger.info("Started simulation for shipment: {}", shipmentId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to start simulation", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * POST /api/v1/delivery/stop-simulation
     * Stop a delivery simulation.
     */
    @PostMapping("/stop-simulation")
    public ResponseEntity<Map<String, Object>> stopSimulation(@RequestBody Map<String, Object> request) {
        try {
            String shipmentId = (String) request.get("shipment_id");
            
            deliverySimulator.stopSimulation(shipmentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("shipment_id", shipmentId);
            response.put("message", "Simulation stopped");
            
            logger.info("Stopped simulation for shipment: {}", shipmentId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to stop simulation", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/delivery/simulation-status/{shipmentId}
     * Get status of a delivery simulation.
     */
    @GetMapping("/simulation-status/{shipmentId}")
    public ResponseEntity<Map<String, Object>> getSimulationStatus(@PathVariable String shipmentId) {
        try {
            SimulationStatus status = deliverySimulator.getSimulationStatus(shipmentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("shipment_id", status.getShipmentId());
            response.put("running", status.isRunning());
            response.put("current_waypoint", status.getCurrentWaypoint());
            response.put("total_waypoints", status.getTotalWaypoints());
            
            if (status.getCurrentLocation() != null) {
                RouteWaypoint current = status.getCurrentLocation();
                Map<String, Object> currentLocation = new HashMap<>();
                currentLocation.put("latitude", current.getLocation().getLatitude());
                currentLocation.put("longitude", current.getLocation().getLongitude());
                currentLocation.put("name", current.getLocation().getName());
                currentLocation.put("temperature", current.getTemperature());
                currentLocation.put("humidity", current.getHumidity());
                response.put("current_location", currentLocation);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get simulation status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Cleanup when controller is destroyed.
     */
    @PreDestroy
    public void cleanup() {
        deliverySimulator.shutdown();
        logger.info("DeliveryController cleaned up");
    }
}
