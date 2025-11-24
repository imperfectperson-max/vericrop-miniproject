package org.vericrop.gui.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.vericrop.dto.SimulationEvent;
import org.vericrop.dto.TemperatureReading;
import org.vericrop.service.SimulationService;
import org.vericrop.service.TemperatureService;
import org.vericrop.service.simulation.SimulationManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * REST API controller for logistics monitoring and temperature tracking.
 * Provides endpoints for real-time temperature monitoring via Server-Sent Events (SSE)
 * and historical temperature data retrieval.
 */
@RestController
@RequestMapping("/api/logistics")
public class LogisticsRestController {
    private static final Logger logger = LoggerFactory.getLogger(LogisticsRestController.class);
    
    private static final long SSE_TIMEOUT = 30 * 60 * 1000; // 30 minutes
    private static final int MAX_HISTORICAL_READINGS = 100;
    
    private final SimulationService simulationService;
    private final TemperatureService temperatureService;
    private final SimulationManager simulationManager;
    private final ObjectMapper objectMapper;
    
    // Track active SSE emitters for cleanup
    private final Map<String, Set<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();
    
    /**
     * Constructor with Spring dependency injection.
     */
    @Autowired
    public LogisticsRestController(SimulationService simulationService,
                                   TemperatureService temperatureService,
                                   SimulationManager simulationManager) {
        this.simulationService = simulationService;
        this.temperatureService = temperatureService;
        this.simulationManager = simulationManager;
        this.objectMapper = new ObjectMapper();
        logger.info("LogisticsRestController initialized");
    }
    
    /**
     * Stream temperature updates for a shipment via Server-Sent Events.
     * 
     * Returns initial historical readings followed by live updates when simulation is active.
     * Client should handle SSE connection and reconnect on disconnect.
     * 
     * Example usage:
     * const eventSource = new EventSource('/api/logistics/shipments/BATCH_123/temperature/stream');
     * eventSource.onmessage = (event) => {
     *   const data = JSON.parse(event.data);
     *   console.log('Temperature:', data.temperature);
     * };
     * 
     * @param shipmentId Shipment/batch identifier
     * @return SSE emitter for streaming temperature data
     */
    @GetMapping(path = "/shipments/{shipmentId}/temperature/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTemperature(@PathVariable String shipmentId) {
        logger.info("SSE stream requested for shipment: {}", shipmentId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Track emitter for cleanup
        activeEmitters.computeIfAbsent(shipmentId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        
        // Store listener reference for cleanup
        final Consumer<SimulationEvent>[] listenerRef = new Consumer[1];
        
        // Update completion handler to cleanup subscription
        emitter.onCompletion(() -> {
            logger.debug("SSE stream completed for shipment: {}", shipmentId);
            if (listenerRef[0] != null) {
                simulationService.unsubscribeFromTemperatureUpdates(shipmentId, listenerRef[0]);
            }
            cleanupEmitter(shipmentId, emitter);
        });
        
        emitter.onTimeout(() -> {
            logger.debug("SSE stream timed out for shipment: {}", shipmentId);
            if (listenerRef[0] != null) {
                simulationService.unsubscribeFromTemperatureUpdates(shipmentId, listenerRef[0]);
            }
            cleanupEmitter(shipmentId, emitter);
        });
        
        emitter.onError((ex) -> {
            logger.error("SSE stream error for shipment: {}", shipmentId, ex);
            if (listenerRef[0] != null) {
                simulationService.unsubscribeFromTemperatureUpdates(shipmentId, listenerRef[0]);
            }
            cleanupEmitter(shipmentId, emitter);
        });
        
        // Start streaming using Spring's async capabilities
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // Send initial historical readings
                List<SimulationEvent> historicalReadings = simulationService.getHistoricalTemperatureReadings(shipmentId);
                
                if (!historicalReadings.isEmpty()) {
                    logger.debug("Sending {} historical readings for shipment: {}", historicalReadings.size(), shipmentId);
                    
                    for (SimulationEvent event : historicalReadings) {
                        TemperatureReading reading = convertToTemperatureReading(event);
                        sendEvent(emitter, "temperature", reading);
                    }
                }
                
                // Check if simulation is active
                boolean simulationActive = simulationService.isSimulationRunning(shipmentId);
                
                if (simulationActive) {
                    // Subscribe to live updates
                    Consumer<SimulationEvent> listener = event -> {
                        try {
                            TemperatureReading reading = convertToTemperatureReading(event);
                            sendEvent(emitter, "temperature", reading);
                        } catch (Exception e) {
                            logger.error("Error sending temperature event", e);
                        }
                    };
                    
                    listenerRef[0] = listener; // Store for cleanup
                    
                    boolean subscribed = simulationService.subscribeToTemperatureUpdates(shipmentId, listener);
                    
                    if (subscribed) {
                        logger.info("Subscribed to live temperature updates for shipment: {}", shipmentId);
                        
                        // Send status event
                        Map<String, Object> status = new HashMap<>();
                        status.put("simulation_active", true);
                        status.put("message", "Streaming live temperature data");
                        status.put("shipment_id", shipmentId);
                        sendEvent(emitter, "status", status);
                    } else {
                        logger.warn("Failed to subscribe to temperature updates for shipment: {}", shipmentId);
                        sendErrorEvent(emitter, "Failed to subscribe to live updates");
                    }
                } else {
                    // Simulation not active, send status and close
                    logger.info("No active simulation for shipment: {}", shipmentId);
                    
                    Map<String, Object> status = new HashMap<>();
                    status.put("simulation_active", false);
                    status.put("message", "No active simulation. Showing historical data only.");
                    status.put("shipment_id", shipmentId);
                    status.put("historical_count", historicalReadings.size());
                    sendEvent(emitter, "status", status);
                    
                    // Keep connection open for potential future updates
                    // Client can close if they don't want to wait
                }
                
            } catch (Exception e) {
                logger.error("Error in temperature stream for shipment: {}", shipmentId, e);
                sendErrorEvent(emitter, "Error streaming temperature data: " + e.getMessage());
                emitter.complete();
            }
        });
        
        return emitter;
    }
    
    /**
     * Get historical temperature readings for a shipment.
     * Returns temperature data from database and/or active simulation.
     * 
     * @param shipmentId Shipment/batch identifier
     * @param limit Maximum number of readings to return (default: 100)
     * @return Historical temperature readings
     */
    @GetMapping("/shipments/{shipmentId}/temperature-history")
    public ResponseEntity<Map<String, Object>> getTemperatureHistory(
            @PathVariable String shipmentId,
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            logger.debug("Temperature history requested for shipment: {}, limit: {}", shipmentId, limit);
            
            Map<String, Object> response = new HashMap<>();
            List<TemperatureReading> readings = new ArrayList<>();
            
            // Get historical readings from active simulation
            List<SimulationEvent> simEvents = simulationService.getHistoricalTemperatureReadings(shipmentId);
            
            for (SimulationEvent event : simEvents) {
                readings.add(convertToTemperatureReading(event));
                
                // Respect limit
                if (readings.size() >= limit) {
                    break;
                }
            }
            
            // Check if simulation is currently active
            boolean simulationActive = simulationService.isSimulationRunning(shipmentId);
            
            response.put("shipment_id", shipmentId);
            response.put("readings", readings);
            response.put("count", readings.size());
            response.put("simulation_active", simulationActive);
            response.put("timestamp", System.currentTimeMillis());
            
            if (simulationActive) {
                response.put("message", "Simulation active. Use /stream endpoint for live updates.");
            } else {
                response.put("message", "No active simulation. Showing historical data.");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving temperature history for shipment: {}", shipmentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve temperature history: " + e.getMessage()));
        }
    }
    
    /**
     * Get all active shipments with temperature monitoring.
     * 
     * @return List of active shipments
     */
    @GetMapping("/shipments/active")
    public ResponseEntity<Map<String, Object>> getActiveShipments() {
        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> shipments = new ArrayList<>();
            
            // Get active simulations
            if (SimulationManager.isInitialized() && simulationManager.isRunning()) {
                String batchId = simulationManager.getSimulationId();
                
                if (simulationService.isSimulationRunning(batchId)) {
                    Map<String, Object> shipment = new HashMap<>();
                    shipment.put("shipment_id", batchId);
                    shipment.put("simulation_active", true);
                    shipment.put("farmer_id", simulationManager.getCurrentProducer());
                    shipment.put("progress", simulationManager.getProgress());
                    shipment.put("current_location", simulationManager.getCurrentLocation());
                    
                    shipments.add(shipment);
                }
            }
            
            response.put("shipments", shipments);
            response.put("count", shipments.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving active shipments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve active shipments: " + e.getMessage()));
        }
    }
    
    /**
     * Health check endpoint.
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "logistics-api");
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }
    
    // Helper methods
    
    /**
     * Convert SimulationEvent to TemperatureReading.
     */
    private TemperatureReading convertToTemperatureReading(SimulationEvent event) {
        return new TemperatureReading(
            event.getBatchId(),
            event.getTimestamp(),
            event.getTemperature(),
            "SIM-SENSOR", // Sensor ID for simulated data
            event.getLocationName()
        );
    }
    
    /**
     * Send an SSE event with data.
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(json));
        } catch (IOException e) {
            logger.error("Error sending SSE event", e);
            emitter.completeWithError(e);
        }
    }
    
    /**
     * Send an error event.
     */
    private void sendErrorEvent(SseEmitter emitter, String message) {
        Map<String, Object> error = createErrorResponse(message);
        sendEvent(emitter, "error", error);
    }
    
    /**
     * Clean up emitter from tracking map.
     */
    private void cleanupEmitter(String shipmentId, SseEmitter emitter) {
        Set<SseEmitter> emitters = activeEmitters.get(shipmentId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                activeEmitters.remove(shipmentId);
            }
        }
    }
    
    /**
     * Create an error response map.
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
