package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.simulation.SimulationManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for asynchronous simulation creation.
 * Performs heavy simulation work on background threads while allowing
 * HTTP requests to return immediately with HTTP 202 Accepted.
 * 
 * <h2>Async Simulation Flow</h2>
 * <ol>
 *   <li>Client calls POST /api/simulation/start-async</li>
 *   <li>Controller generates simulation ID and calls this service</li>
 *   <li>Controller immediately returns HTTP 202 with the simulation ID</li>
 *   <li>This service performs simulation creation in background</li>
 *   <li>Client can poll GET /api/simulation/{id}/status for progress</li>
 * </ol>
 * 
 * <h2>Status Tracking</h2>
 * <p>Each simulation task has a status that can be queried:</p>
 * <ul>
 *   <li>CREATING - Simulation is being set up in background</li>
 *   <li>RUNNING - Simulation started successfully and is in progress</li>
 *   <li>COMPLETED - Simulation finished successfully</li>
 *   <li>FAILED - Simulation creation or execution failed</li>
 * </ul>
 */
@Service
public class SimulationAsyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationAsyncService.class);
    
    /**
     * Status of a simulation task.
     */
    public enum SimulationStatus {
        CREATING("Simulation is being created in background"),
        RUNNING("Simulation is running"),
        COMPLETED("Simulation completed successfully"),
        FAILED("Simulation failed");
        
        private final String description;
        
        SimulationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Holds the status and details of a simulation task.
     */
    public static class SimulationTaskStatus {
        private final String simulationId;
        private volatile SimulationStatus status;
        private volatile String message;
        private volatile double progress;
        private volatile String currentLocation;
        private volatile long startTime;
        private volatile long lastUpdateTime;
        
        public SimulationTaskStatus(String simulationId) {
            this.simulationId = simulationId;
            this.status = SimulationStatus.CREATING;
            this.message = "Simulation creation started";
            this.progress = 0.0;
            this.currentLocation = "Initializing...";
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = this.startTime;
        }
        
        public String getSimulationId() { return simulationId; }
        public SimulationStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public double getProgress() { return progress; }
        public String getCurrentLocation() { return currentLocation; }
        public long getStartTime() { return startTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        void setStatus(SimulationStatus status) { 
            this.status = status; 
            this.lastUpdateTime = System.currentTimeMillis();
        }
        void setMessage(String message) { 
            this.message = message; 
            this.lastUpdateTime = System.currentTimeMillis();
        }
        void setProgress(double progress) { 
            this.progress = progress;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        void setCurrentLocation(String location) { 
            this.currentLocation = location;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        /**
         * Convert to map for JSON response.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("simulation_id", simulationId);
            map.put("status", status.name());
            map.put("status_description", status.getDescription());
            map.put("message", message);
            map.put("progress", progress);
            map.put("current_location", currentLocation);
            map.put("start_time", startTime);
            map.put("last_update_time", lastUpdateTime);
            map.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return map;
        }
    }
    
    // In-memory store for simulation task statuses
    private final Map<String, SimulationTaskStatus> taskStatuses = new ConcurrentHashMap<>();
    
    /**
     * Asynchronously create and start a simulation.
     * This method runs on a background thread and returns immediately.
     * The status can be queried via getTaskStatus().
     * 
     * @param simulationId Unique simulation identifier (generated by controller)
     * @param batchId Batch identifier for the simulation
     * @param farmerId Farmer/producer identifier
     * @param originLat Origin latitude
     * @param originLon Origin longitude
     * @param originName Origin location name
     * @param destLat Destination latitude
     * @param destLon Destination longitude
     * @param destName Destination location name
     * @param numWaypoints Number of waypoints for route
     * @param avgSpeedKmh Average speed in km/h
     * @param updateIntervalMs Update interval in milliseconds
     * @return CompletableFuture that completes when simulation creation finishes
     */
    @Async("simulationAsyncExecutor")
    public CompletableFuture<SimulationTaskStatus> createSimulationAsync(
            String simulationId,
            String batchId,
            String farmerId,
            double originLat,
            double originLon,
            String originName,
            double destLat,
            double destLon,
            String destName,
            int numWaypoints,
            double avgSpeedKmh,
            long updateIntervalMs) {
        
        logger.info("🚀 Async simulation creation started: {} (batchId: {})", simulationId, batchId);
        
        // Create and register task status
        SimulationTaskStatus taskStatus = new SimulationTaskStatus(simulationId);
        taskStatuses.put(simulationId, taskStatus);
        
        try {
            // Check if SimulationManager is initialized
            if (!SimulationManager.isInitialized()) {
                throw new IllegalStateException("SimulationManager not initialized. Application may not be fully started.");
            }
            
            SimulationManager manager = SimulationManager.getInstance();
            
            // Check if another simulation is already running
            if (manager.isRunning()) {
                String runningId = manager.getSimulationId();
                taskStatus.setStatus(SimulationStatus.FAILED);
                taskStatus.setMessage("Another simulation is already running: " + runningId);
                logger.warn("⚠️ Cannot start simulation {}: another simulation {} is running", 
                           simulationId, runningId);
                return CompletableFuture.completedFuture(taskStatus);
            }
            
            // Update status to indicate creation in progress
            taskStatus.setMessage("Setting up simulation parameters...");
            
            // Create coordinates
            DeliverySimulator.GeoCoordinate origin = new DeliverySimulator.GeoCoordinate(
                originLat, originLon, originName);
            DeliverySimulator.GeoCoordinate destination = new DeliverySimulator.GeoCoordinate(
                destLat, destLon, destName);
            
            // Start the simulation (this is the potentially slow operation)
            logger.info("⏳ Starting simulation for batch: {}", batchId);
            manager.startSimulation(batchId, farmerId, origin, destination, 
                                   numWaypoints, avgSpeedKmh, updateIntervalMs);
            
            // Update status to running
            taskStatus.setStatus(SimulationStatus.RUNNING);
            taskStatus.setMessage("Simulation started successfully");
            taskStatus.setProgress(0.0);
            taskStatus.setCurrentLocation("Starting...");
            
            logger.info("✅ Async simulation created successfully: {} -> {}", simulationId, batchId);
            
            return CompletableFuture.completedFuture(taskStatus);
            
        } catch (Exception e) {
            logger.error("❌ Failed to create simulation {}: {}", simulationId, e.getMessage(), e);
            taskStatus.setStatus(SimulationStatus.FAILED);
            taskStatus.setMessage("Failed to create simulation: " + e.getMessage());
            return CompletableFuture.completedFuture(taskStatus);
        }
    }
    
    /**
     * Get the status of a simulation task.
     * 
     * @param simulationId The simulation ID
     * @return Task status or null if not found
     */
    public SimulationTaskStatus getTaskStatus(String simulationId) {
        SimulationTaskStatus taskStatus = taskStatuses.get(simulationId);
        
        if (taskStatus != null && taskStatus.getStatus() == SimulationStatus.RUNNING) {
            // Update with live data from SimulationManager if running
            try {
                if (SimulationManager.isInitialized()) {
                    SimulationManager manager = SimulationManager.getInstance();
                    if (manager.isRunning()) {
                        taskStatus.setProgress(manager.getProgress());
                        String location = manager.getCurrentLocation();
                        if (location != null) {
                            taskStatus.setCurrentLocation(location);
                        }
                    } else {
                        // Simulation stopped
                        taskStatus.setStatus(SimulationStatus.COMPLETED);
                        taskStatus.setMessage("Simulation completed");
                        taskStatus.setProgress(100.0);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error updating task status from SimulationManager: {}", e.getMessage());
            }
        }
        
        return taskStatus;
    }
    
    /**
     * Check if a simulation task exists.
     * 
     * @param simulationId The simulation ID
     * @return true if the task exists
     */
    public boolean hasTask(String simulationId) {
        return taskStatuses.containsKey(simulationId);
    }
    
    /**
     * Remove a completed or failed task from tracking.
     * This helps prevent memory leaks for long-running applications.
     * 
     * @param simulationId The simulation ID to remove
     */
    public void removeTask(String simulationId) {
        taskStatuses.remove(simulationId);
        logger.debug("Removed task status for: {}", simulationId);
    }
    
    /**
     * Clean up old completed/failed tasks.
     * Tasks older than the specified age are removed.
     * 
     * @param maxAgeMs Maximum age in milliseconds for tasks to keep
     * @return Number of tasks removed
     */
    public int cleanupOldTasks(long maxAgeMs) {
        long cutoffTime = System.currentTimeMillis() - maxAgeMs;
        
        // Use removeIf to safely modify ConcurrentHashMap during iteration
        int initialSize = taskStatuses.size();
        taskStatuses.entrySet().removeIf(entry -> {
            SimulationTaskStatus status = entry.getValue();
            // Only remove completed or failed tasks that are old
            return (status.getStatus() == SimulationStatus.COMPLETED || 
                    status.getStatus() == SimulationStatus.FAILED) &&
                   status.getLastUpdateTime() < cutoffTime;
        });
        int removed = initialSize - taskStatuses.size();
        
        if (removed > 0) {
            logger.info("Cleaned up {} old simulation tasks", removed);
        }
        
        return removed;
    }
}
