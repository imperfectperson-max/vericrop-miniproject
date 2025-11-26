package org.vericrop.gui.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.services.SimulationAsyncService;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MapSimulator;
import org.vericrop.service.ScenarioManager;
import org.vericrop.service.simulation.SimulationManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for simulation and map state access.
 * Provides endpoints to retrieve current map simulation state, scenario information,
 * and active shipments tracking.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationRestController {
    private static final Logger logger = LoggerFactory.getLogger(SimulationRestController.class);
    
    private final MapSimulator mapSimulator;
    private final ScenarioManager scenarioManager;
    private final DeliverySimulator deliverySimulator;
    private final SimulationManager simulationManager;
    private final SimulationAsyncService simulationAsyncService;
    
    /**
     * Constructor with Spring dependency injection.
     */
    @Autowired
    public SimulationRestController(MapSimulator mapSimulator, 
                                   ScenarioManager scenarioManager,
                                   DeliverySimulator deliverySimulator,
                                   SimulationManager simulationManager,
                                   SimulationAsyncService simulationAsyncService) {
        this.mapSimulator = mapSimulator;
        this.scenarioManager = scenarioManager;
        this.deliverySimulator = deliverySimulator;
        this.simulationManager = simulationManager;
        this.simulationAsyncService = simulationAsyncService;
        logger.info("SimulationRestController initialized with all dependencies including async service");
    }
    
    /**
     * Get current map state snapshot.
     * 
     * @return Map snapshot with entity positions and metadata
     */
    @GetMapping("/map")
    public ResponseEntity<Map<String, Object>> getMapSnapshot() {
        try {
            MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
            
            if (snapshot == null) {
                return ResponseEntity.ok(createErrorResponse("No active simulation"));
            }
            
            Map<String, Object> response = snapshot.toMap();
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving map snapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve map snapshot: " + e.getMessage()));
        }
    }
    
    /**
     * Get information about all available scenarios.
     * 
     * @return List of scenarios with their configurations
     */
    @GetMapping("/scenarios")
    public ResponseEntity<Map<String, Object>> getScenarios() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("scenarios", scenarioManager.getAllScenarioInfo());
            response.put("default_scenario", scenarioManager.getDefaultScenarioId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving scenarios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve scenarios: " + e.getMessage()));
        }
    }
    
    /**
     * Get information about a specific scenario.
     * 
     * @param scenarioId Scenario ID
     * @return Scenario information
     */
    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<Map<String, Object>> getScenarioInfo(@PathVariable String scenarioId) {
        try {
            if (!scenarioManager.isValidScenario(scenarioId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Scenario not found: " + scenarioId));
            }
            
            Map<String, Object> info = scenarioManager.getScenarioInfo(scenarioId);
            return ResponseEntity.ok(info);
            
        } catch (Exception e) {
            logger.error("Error retrieving scenario info for {}", scenarioId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve scenario info: " + e.getMessage()));
        }
    }
    
    /**
     * Get current simulation status including map state.
     * 
     * @return Combined simulation status with map snapshot
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Get map snapshot
            MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
            if (snapshot != null) {
                status.put("active", true);
                status.put("current_step", snapshot.getSimulationStep());
                status.put("scenario_id", snapshot.getScenarioId());
                status.put("entity_count", snapshot.getEntities().size());
                status.put("map_state", snapshot.toMap());
            } else {
                status.put("active", false);
                status.put("message", "No active simulation");
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving simulation status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve status: " + e.getMessage()));
        }
    }
    
    /**
     * Health check endpoint for the simulation API.
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "simulation-api");
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get all active shipments currently in simulation.
     * Returns a list of active deliveries with their current status and location.
     * 
     * @return List of active shipments with status information
     */
    @GetMapping("/active-shipments")
    public ResponseEntity<Map<String, Object>> getActiveShipments() {
        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> activeShipments = new ArrayList<>();
            
            // Check if SimulationManager has an active simulation
            if (SimulationManager.isInitialized() && simulationManager.isRunning()) {
                String batchId = simulationManager.getSimulationId();
                
                try {
                    DeliverySimulator.SimulationStatus status = 
                        deliverySimulator.getSimulationStatus(batchId);
                    
                    if (status != null && status.isRunning()) {
                        Map<String, Object> shipment = new HashMap<>();
                        shipment.put("batch_id", batchId);
                        shipment.put("running", status.isRunning());
                        shipment.put("current_waypoint", status.getCurrentWaypoint());
                        shipment.put("total_waypoints", status.getTotalWaypoints());
                        
                        // Calculate progress percentage
                        double progress = status.getTotalWaypoints() > 0 ? 
                            (double) status.getCurrentWaypoint() / status.getTotalWaypoints() * 100.0 : 0.0;
                        shipment.put("progress_percent", Math.round(progress * 10.0) / 10.0);
                        
                        // Add current location info if available
                        if (status.getCurrentLocation() != null) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("name", status.getCurrentLocation().getLocation().getName());
                            location.put("temperature", status.getCurrentLocation().getTemperature());
                            location.put("humidity", status.getCurrentLocation().getHumidity());
                            location.put("timestamp", status.getCurrentLocation().getTimestamp());
                            shipment.put("current_location", location);
                        }
                        
                        // Add scenario and progress info from SimulationManager
                        shipment.put("farmer_id", simulationManager.getCurrentProducer());
                        shipment.put("progress_manager", simulationManager.getProgress());
                        shipment.put("current_location_name", simulationManager.getCurrentLocation());
                        
                        activeShipments.add(shipment);
                    }
                } catch (Exception e) {
                    logger.warn("Error getting status for batch {}: {}", batchId, e.getMessage());
                }
            }
            
            response.put("active_shipments", activeShipments);
            response.put("count", activeShipments.size());
            response.put("timestamp", System.currentTimeMillis());
            response.put("simulation_running", SimulationManager.isInitialized() && simulationManager.isRunning());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving active shipments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve active shipments: " + e.getMessage()));
        }
    }
    
    /**
     * Start a new simulation with a selected scenario.
     * 
     * @param request Simulation start request with scenario selection
     * @return Simulation start response with batch ID
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSimulation(@RequestBody Map<String, Object> request) {
        try {
            // Extract parameters
            String scenarioId = (String) request.getOrDefault("scenario_id", "scenario-01");
            String batchId = (String) request.getOrDefault("batch_id", "BATCH_" + System.currentTimeMillis());
            String farmerId = (String) request.getOrDefault("farmer_id", "FARMER_DEFAULT");
            
            // Validate scenario
            if (!scenarioManager.isValidScenario(scenarioId)) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid scenario ID: " + scenarioId));
            }
            
            // Check if simulation is already running
            if (SimulationManager.isInitialized() && simulationManager.isRunning()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse("Simulation already running: " + 
                        simulationManager.getSimulationId()));
            }
            
            // Start simulation (will be handled by SimulationManager if used from GUI)
            // For REST API, we return instructions
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Simulation start requested");
            response.put("batch_id", batchId);
            response.put("scenario_id", scenarioId);
            response.put("farmer_id", farmerId);
            response.put("note", "Use GUI producer screen or LogisticsController to start full simulation");
            response.put("scenario_info", scenarioManager.getScenarioInfo(scenarioId));
            
            logger.info("Simulation start requested: batch={}, scenario={}", batchId, scenarioId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to start simulation: " + e.getMessage()));
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
    
    /**
     * Start a new simulation asynchronously.
     * Returns HTTP 202 Accepted immediately with a simulation ID.
     * The actual simulation creation runs in the background.
     * 
     * <h3>Async Simulation Flow</h3>
     * <ol>
     *   <li>Client calls POST /api/simulation/start-async</li>
     *   <li>Server immediately returns HTTP 202 with simulation_id</li>
     *   <li>Simulation creation runs in background thread</li>
     *   <li>Client polls GET /api/simulation/{id}/status for progress</li>
     * </ol>
     * 
     * @param request Simulation start request with parameters
     * @return HTTP 202 Accepted with simulation ID for polling
     */
    @PostMapping("/start-async")
    public ResponseEntity<Map<String, Object>> startSimulationAsync(@RequestBody Map<String, Object> request) {
        try {
            // Generate unique simulation ID
            String simulationId = "SIM_" + System.currentTimeMillis() + "_" + 
                                  UUID.randomUUID().toString().substring(0, 8);
            
            // Extract parameters with defaults
            String batchId = (String) request.getOrDefault("batch_id", "BATCH_" + System.currentTimeMillis());
            String farmerId = (String) request.getOrDefault("farmer_id", "FARMER_DEFAULT");
            
            // Origin coordinates (default: Sunny Valley Farm)
            double originLat = getDoubleValue(request, "origin_lat", 42.3601);
            double originLon = getDoubleValue(request, "origin_lon", -71.0589);
            String originName = (String) request.getOrDefault("origin_name", "Sunny Valley Farm");
            
            // Destination coordinates (default: Metro Fresh Warehouse)
            double destLat = getDoubleValue(request, "dest_lat", 42.3736);
            double destLon = getDoubleValue(request, "dest_lon", -71.1097);
            String destName = (String) request.getOrDefault("dest_name", "Metro Fresh Warehouse");
            
            // Simulation parameters
            int numWaypoints = getIntValue(request, "num_waypoints", 20);
            double avgSpeedKmh = getDoubleValue(request, "avg_speed_kmh", 50.0);
            long updateIntervalMs = getLongValue(request, "update_interval_ms", 10000);
            
            // Quick validation - check for existing running simulation
            if (SimulationManager.isInitialized() && simulationManager.isRunning()) {
                String runningId = simulationManager.getSimulationId();
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createConflictResponse(runningId));
            }
            
            // Start async simulation creation (returns immediately)
            simulationAsyncService.createSimulationAsync(
                simulationId, batchId, farmerId,
                originLat, originLon, originName,
                destLat, destLon, destName,
                numWaypoints, avgSpeedKmh, updateIntervalMs
            );
            
            logger.info("✅ Async simulation creation scheduled: {} for batch {}", simulationId, batchId);
            
            // Build response with simulation ID for polling
            Map<String, Object> response = new HashMap<>();
            response.put("accepted", true);
            response.put("simulation_id", simulationId);
            response.put("batch_id", batchId);
            response.put("farmer_id", farmerId);
            response.put("message", "Simulation creation started. Poll /api/simulation/" + simulationId + "/status for progress.");
            response.put("status_url", "/api/simulation/" + simulationId + "/status");
            response.put("timestamp", System.currentTimeMillis());
            
            // Return HTTP 202 Accepted immediately
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error scheduling async simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to schedule simulation: " + e.getMessage()));
        }
    }
    
    /**
     * Get the status of an async simulation task.
     * Use this endpoint to poll for simulation progress after calling start-async.
     * 
     * @param simulationId The simulation ID returned from start-async
     * @return Current status of the simulation
     */
    @GetMapping("/{simulationId}/status")
    public ResponseEntity<Map<String, Object>> getSimulationTaskStatus(@PathVariable String simulationId) {
        try {
            SimulationAsyncService.SimulationTaskStatus taskStatus = 
                simulationAsyncService.getTaskStatus(simulationId);
            
            if (taskStatus == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Simulation not found: " + simulationId));
            }
            
            return ResponseEntity.ok(taskStatus.toMap());
            
        } catch (Exception e) {
            logger.error("Error getting simulation status for {}", simulationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get simulation status: " + e.getMessage()));
        }
    }
    
    /**
     * Create a conflict response for when a simulation is already running.
     */
    private Map<String, Object> createConflictResponse(String runningSimulationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", true);
        response.put("message", "Another simulation is already running");
        response.put("running_simulation_id", runningSimulationId);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Safely extract a double value from a map with default.
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Safely extract an int value from a map with default.
     */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Safely extract a long value from a map with default.
     */
    private long getLongValue(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
