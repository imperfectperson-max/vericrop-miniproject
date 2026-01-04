package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.SimulationEvent;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Service responsible for running GPS & temperature scenarios for a batch.
 * Emits detailed SimulationEvent objects at configurable intervals to listeners.
 * Coordinates with DeliverySimulator for actual simulation logic.
 */
public class SimulationService {
    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);
    
    private final DeliverySimulator deliverySimulator;
    private final MapService mapService;
    private final TemperatureService temperatureService;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, SimulationTask> activeTasks;
    private final long defaultEmitIntervalMs;
    
    /**
     * Holds information about an active simulation task
     */
    private static class SimulationTask {
        final String batchId;
        final String farmerId;
        final List<RouteWaypoint> route;
        final List<Consumer<SimulationEvent>> listeners;
        final ScheduledFuture<?> scheduledFuture;
        volatile boolean running;
        volatile int currentWaypointIndex;
        
        SimulationTask(String batchId, String farmerId, List<RouteWaypoint> route,
                      ScheduledFuture<?> scheduledFuture) {
            this.batchId = batchId;
            this.farmerId = farmerId;
            this.route = route;
            this.listeners = new CopyOnWriteArrayList<>();
            this.scheduledFuture = scheduledFuture;
            this.running = true;
            this.currentWaypointIndex = 0;
        }
    }
    
    /**
     * Constructor with dependencies
     * 
     * @param deliverySimulator The delivery simulator instance
     * @param mapService Map service for route generation
     * @param temperatureService Temperature monitoring service
     * @param defaultEmitIntervalMs Default interval in milliseconds between events
     */
    public SimulationService(DeliverySimulator deliverySimulator,
                            MapService mapService,
                            TemperatureService temperatureService,
                            long defaultEmitIntervalMs) {
        this.deliverySimulator = deliverySimulator;
        this.mapService = mapService;
        this.temperatureService = temperatureService;
        this.defaultEmitIntervalMs = defaultEmitIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(5, r -> {
            Thread t = new Thread(r, "SimulationService-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.activeTasks = new ConcurrentHashMap<>();
        logger.info("SimulationService initialized with emit interval: {}ms", defaultEmitIntervalMs);
    }
    
    /**
     * Start a simulation for a batch with a route.
     * Emits SimulationEvent objects at the configured interval.
     * 
     * @param batchId Batch identifier
     * @param farmerId Farmer/producer identifier
     * @param route Route with waypoints containing GPS coordinates and environmental data
     * @param eventListener Listener that receives SimulationEvent objects
     * @return true if simulation started successfully, false otherwise
     */
    public boolean startSimulation(String batchId, String farmerId, List<RouteWaypoint> route,
                                  Consumer<SimulationEvent> eventListener) {
        return startSimulation(batchId, farmerId, route, eventListener, defaultEmitIntervalMs);
    }
    
    /**
     * Start a simulation for a batch with a route and custom emit interval.
     * 
     * @param batchId Batch identifier
     * @param farmerId Farmer/producer identifier
     * @param route Route with waypoints
     * @param eventListener Listener that receives SimulationEvent objects
     * @param emitIntervalMs Custom emit interval in milliseconds
     * @return true if simulation started successfully, false otherwise
     */
    public boolean startSimulation(String batchId, String farmerId, List<RouteWaypoint> route,
                                  Consumer<SimulationEvent> eventListener, long emitIntervalMs) {
        if (activeTasks.containsKey(batchId)) {
            logger.warn("Simulation already running for batch: {}", batchId);
            return false;
        }
        
        if (route == null || route.isEmpty()) {
            logger.error("Cannot start simulation: route is null or empty");
            return false;
        }
        
        logger.info("Starting simulation for batch: {}, {} waypoints, interval: {}ms", 
                   batchId, route.size(), emitIntervalMs);
        
        // Get total waypoint count
        int totalWaypoints = route.size();
        
        // Create scheduled task
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                SimulationTask task = activeTasks.get(batchId);
                if (task == null || !task.running) {
                    return;
                }
                
                // Get current waypoint
                if (task.currentWaypointIndex >= task.route.size()) {
                    // Simulation complete
                    emitCompletionEvent(task);
                    stopSimulation(batchId);
                    return;
                }
                
                RouteWaypoint waypoint = task.route.get(task.currentWaypointIndex);
                GeoCoordinate location = waypoint.getLocation();
                
                // Calculate progress
                double progressPercent = (double) task.currentWaypointIndex / task.route.size() * 100.0;
                
                // Calculate ETA (time remaining) based on progress
                int remainingWaypoints = task.route.size() - task.currentWaypointIndex;
                long estimatedTimePerWaypoint = emitIntervalMs; // Approximate time per waypoint
                long eta = remainingWaypoints * estimatedTimePerWaypoint;
                if (eta < 0) eta = 0;
                
                // Determine status based on progress
                String status;
                if (progressPercent < 10) {
                    status = "Created";
                } else if (progressPercent < 30) {
                    status = "In Transit - Departing Origin";
                } else if (progressPercent < 70) {
                    status = "In Transit - En Route";
                } else if (progressPercent < 90) {
                    status = "In Transit - Approaching Destination";
                } else if (progressPercent < 100) {
                    status = "At Warehouse";
                } else {
                    status = "Delivered";
                }
                
                // Create and emit event
                SimulationEvent event = new SimulationEvent(
                    batchId,
                    System.currentTimeMillis(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getName(),
                    waypoint.getTemperature(),
                    waypoint.getHumidity(),
                    status,
                    eta,
                    progressPercent,
                    task.currentWaypointIndex,
                    totalWaypoints,
                    farmerId,
                    SimulationEvent.EventType.GPS_UPDATE
                );
                
                // Notify all listeners
                for (Consumer<SimulationEvent> listener : task.listeners) {
                    try {
                        listener.accept(event);
                    } catch (Exception e) {
                        logger.error("Error notifying listener of event", e);
                    }
                }
                
                // Move to next waypoint
                task.currentWaypointIndex++;
                
            } catch (Exception e) {
                logger.error("Error in simulation task for batch: {}", batchId, e);
            }
        }, 0, emitIntervalMs, TimeUnit.MILLISECONDS);
        
        // Create and store task
        SimulationTask task = new SimulationTask(batchId, farmerId, route, future);
        if (eventListener != null) {
            task.listeners.add(eventListener);
        }
        activeTasks.put(batchId, task);
        
        // Emit started event
        emitStartedEvent(task);
        
        return true;
    }
    
    /**
     * Add an additional listener to an active simulation
     * 
     * @param batchId Batch identifier
     * @param eventListener Listener to add
     * @return true if listener was added, false if simulation not found
     */
    public boolean addListener(String batchId, Consumer<SimulationEvent> eventListener) {
        SimulationTask task = activeTasks.get(batchId);
        if (task != null && eventListener != null) {
            task.listeners.add(eventListener);
            logger.debug("Added listener to simulation: {}", batchId);
            return true;
        }
        return false;
    }
    
    /**
     * Stop a simulation
     * 
     * @param batchId Batch identifier
     * @return true if simulation was stopped, false if not found
     */
    public boolean stopSimulation(String batchId) {
        SimulationTask task = activeTasks.remove(batchId);
        if (task != null) {
            task.running = false;
            if (task.scheduledFuture != null && !task.scheduledFuture.isCancelled()) {
                task.scheduledFuture.cancel(false);
            }
            logger.info("Stopped simulation for batch: {}", batchId);
            return true;
        }
        logger.warn("No active simulation found for batch: {}", batchId);
        return false;
    }
    
    /**
     * Check if a simulation is running
     */
    public boolean isSimulationRunning(String batchId) {
        SimulationTask task = activeTasks.get(batchId);
        return task != null && task.running;
    }
    
    /**
     * Get the number of active simulations
     */
    public int getActiveSimulationCount() {
        return activeTasks.size();
    }
    
    /**
     * Emit a simulation started event
     */
    private void emitStartedEvent(SimulationTask task) {
        RouteWaypoint firstWaypoint = task.route.get(0);
        GeoCoordinate location = firstWaypoint.getLocation();
        
        SimulationEvent event = new SimulationEvent(
            task.batchId,
            System.currentTimeMillis(),
            location.getLatitude(),
            location.getLongitude(),
            location.getName(),
            firstWaypoint.getTemperature(),
            firstWaypoint.getHumidity(),
            "Created",
            task.route.get(task.route.size() - 1).getTimestamp() - System.currentTimeMillis(),
            0.0,
            0,
            task.route.size(),
            task.farmerId,
            SimulationEvent.EventType.STARTED
        );
        
        for (Consumer<SimulationEvent> listener : task.listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("Error notifying listener of start event", e);
            }
        }
    }
    
    /**
     * Emit a simulation completion event
     */
    private void emitCompletionEvent(SimulationTask task) {
        RouteWaypoint lastWaypoint = task.route.get(task.route.size() - 1);
        GeoCoordinate location = lastWaypoint.getLocation();
        
        SimulationEvent event = new SimulationEvent(
            task.batchId,
            System.currentTimeMillis(),
            location.getLatitude(),
            location.getLongitude(),
            location.getName(),
            lastWaypoint.getTemperature(),
            lastWaypoint.getHumidity(),
            "Delivered",
            0,
            100.0,
            task.route.size() - 1,
            task.route.size(),
            task.farmerId,
            SimulationEvent.EventType.COMPLETED
        );
        
        for (Consumer<SimulationEvent> listener : task.listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("Error notifying listener of completion event", e);
            }
        }
    }
    
    /**
     * Shutdown the service and cancel all active simulations
     */
    public void shutdown() {
        logger.info("Shutting down SimulationService...");
        
        // Stop all active simulations
        for (String batchId : activeTasks.keySet()) {
            stopSimulation(batchId);
        }
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        
        logger.info("SimulationService shutdown complete");
    }
}
