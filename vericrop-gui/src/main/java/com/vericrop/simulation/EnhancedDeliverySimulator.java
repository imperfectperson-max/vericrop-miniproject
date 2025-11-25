package com.vericrop.simulation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Enhanced delivery simulator with deterministic, smooth animation and robust lifecycle management.
 * Features:
 * - Single Timeline/node per delivery ID (no duplicate spawning)
 * - Robust state machine: AVAILABLE -> IN_TRANSIT -> APPROACHING -> COMPLETED
 * - Quality decay with exponential formula
 * - Demo mode support for time compression
 * - Thread-safe operations
 */
public class EnhancedDeliverySimulator {
    
    private final Map<String, SimulationInstance> activeSimulations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final SimulationConfig config;
    
    /**
     * Holds the state of a single simulation instance.
     */
    private static class SimulationInstance {
        final String deliveryId;
        final String farmerId;
        final RouteInterpolator interpolator;
        final double initialQuality;
        final long startTime;
        final Consumer<DeliveryEvent> eventHandler;
        
        volatile DeliveryState currentState;
        volatile double currentProgress;
        volatile double currentQuality;
        volatile long lastUpdateTime;
        volatile ScheduledFuture<?> scheduledTask;
        volatile boolean stopped;
        
        SimulationInstance(String deliveryId, String farmerId, RouteInterpolator interpolator,
                          double initialQuality, Consumer<DeliveryEvent> eventHandler) {
            this.deliveryId = deliveryId;
            this.farmerId = farmerId;
            this.interpolator = interpolator;
            this.initialQuality = initialQuality;
            this.eventHandler = eventHandler;
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = startTime;
            this.currentState = DeliveryState.AVAILABLE;
            this.currentProgress = 0;
            this.currentQuality = initialQuality;
            this.stopped = false;
        }
    }
    
    /**
     * Create simulator with default configuration.
     */
    public EnhancedDeliverySimulator() {
        this(SimulationConfig.fromEnvironment());
    }
    
    /**
     * Create simulator with custom configuration.
     */
    public EnhancedDeliverySimulator(SimulationConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "DeliverySimulator-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        System.out.println("🚀 EnhancedDeliverySimulator initialized with config: " + config);
    }
    
    /**
     * Start a new delivery simulation.
     * Only one simulation per deliveryId is allowed.
     * 
     * @param deliveryId Unique identifier for the delivery
     * @param farmerId Farmer/producer identifier
     * @param interpolator Route interpolator for position calculation
     * @param eventHandler Callback for delivery events
     * @return true if simulation started, false if already running
     */
    public boolean startSimulation(String deliveryId, String farmerId,
                                   RouteInterpolator interpolator,
                                   Consumer<DeliveryEvent> eventHandler) {
        
        // Prevent duplicate simulations
        if (activeSimulations.containsKey(deliveryId)) {
            System.err.println("⚠️ Simulation already running for delivery: " + deliveryId);
            return false;
        }
        
        SimulationInstance instance = new SimulationInstance(
            deliveryId, farmerId, interpolator, config.getInitialQuality(), eventHandler
        );
        
        activeSimulations.put(deliveryId, instance);
        
        // Emit started event
        RouteInterpolator.InterpolatedData startData = interpolator.interpolate(0);
        DeliveryEvent startEvent = DeliveryEvent.started(
            deliveryId,
            startData.getLatitude(),
            startData.getLongitude(),
            startData.getLocationName(),
            instance.currentQuality
        );
        emitEvent(instance, startEvent);
        
        // Schedule periodic updates
        int intervalMs = config.getEffectiveUpdateIntervalMs();
        instance.scheduledTask = scheduler.scheduleAtFixedRate(
            () -> updateSimulation(instance),
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("✅ Started simulation for delivery: " + deliveryId + 
                          " with interval " + intervalMs + "ms");
        
        return true;
    }
    
    /**
     * Stop a running simulation.
     * 
     * @param deliveryId The delivery to stop
     * @return true if stopped, false if not found
     */
    public boolean stopSimulation(String deliveryId) {
        SimulationInstance instance = activeSimulations.remove(deliveryId);
        if (instance == null) {
            return false;
        }
        
        instance.stopped = true;
        
        if (instance.scheduledTask != null) {
            instance.scheduledTask.cancel(false);
        }
        
        // Emit stopped event
        DeliveryEvent stopEvent = DeliveryEvent.stopped(
            deliveryId, instance.currentState, instance.currentProgress, instance.currentQuality
        );
        emitEvent(instance, stopEvent);
        
        System.out.println("⏹️ Stopped simulation for delivery: " + deliveryId);
        
        return true;
    }
    
    /**
     * Check if a simulation is running.
     */
    public boolean isRunning(String deliveryId) {
        return activeSimulations.containsKey(deliveryId);
    }
    
    /**
     * Get current progress for a simulation.
     */
    public double getProgress(String deliveryId) {
        SimulationInstance instance = activeSimulations.get(deliveryId);
        return instance != null ? instance.currentProgress : -1;
    }
    
    /**
     * Get current state for a simulation.
     */
    public DeliveryState getState(String deliveryId) {
        SimulationInstance instance = activeSimulations.get(deliveryId);
        return instance != null ? instance.currentState : null;
    }
    
    /**
     * Get current quality for a simulation.
     */
    public double getQuality(String deliveryId) {
        SimulationInstance instance = activeSimulations.get(deliveryId);
        return instance != null ? instance.currentQuality : -1;
    }
    
    /**
     * Get list of all active delivery IDs.
     */
    public List<String> getActiveDeliveryIds() {
        return List.copyOf(activeSimulations.keySet());
    }
    
    /**
     * Main update loop for a simulation instance.
     */
    private void updateSimulation(SimulationInstance instance) {
        if (instance.stopped) {
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            long elapsedMs = currentTime - instance.startTime;
            
            // Calculate progress based on elapsed time and waypoint count
            // Each waypoint represents 1/waypointCount of total progress
            int waypointCount = config.getWaypointCount();
            int effectiveIntervalMs = config.getEffectiveUpdateIntervalMs();
            long totalDurationMs = (long) waypointCount * effectiveIntervalMs;
            
            double progressFraction = Math.min(1.0, (double) elapsedMs / totalDurationMs);
            double progressPercent = progressFraction * 100.0;
            
            // Update progress
            instance.currentProgress = progressPercent;
            
            // Calculate quality decay using exponential formula
            // finalQuality = initialQuality * exp(-decayRate * exposureSeconds)
            double elapsedSeconds = elapsedMs / 1000.0;
            double decayedQuality = calculateQualityDecay(
                instance.initialQuality, 
                config.getDecayRate(),
                elapsedSeconds
            );
            instance.currentQuality = decayedQuality;
            
            // Get interpolated position and environment
            RouteInterpolator.InterpolatedData data = instance.interpolator.interpolate(progressFraction);
            
            // Determine new state based on progress
            DeliveryState newState = DeliveryState.fromProgress(progressPercent);
            DeliveryState previousState = instance.currentState;
            
            // Check for state transition
            if (newState != previousState && previousState.canTransitionTo(newState)) {
                instance.currentState = newState;
                
                // If transitioning to COMPLETED, handle via completeSimulation instead
                if (newState == DeliveryState.COMPLETED) {
                    completeSimulation(instance, data);
                    return; // Exit early, completion event will be emitted by completeSimulation
                }
                
                // Emit state change event for non-terminal states
                DeliveryEvent stateEvent = DeliveryEvent.stateChanged(
                    instance.deliveryId,
                    newState,
                    previousState,
                    progressPercent,
                    data.getLatitude(),
                    data.getLongitude(),
                    data.getLocationName(),
                    data.getTemperature(),
                    data.getHumidity(),
                    decayedQuality
                );
                emitEvent(instance, stateEvent);
                
                System.out.println("🔄 State transition for " + instance.deliveryId + 
                                  ": " + previousState + " -> " + newState);
            } else {
                // Emit progress update
                DeliveryEvent progressEvent = DeliveryEvent.progressUpdate(
                    instance.deliveryId,
                    instance.currentState,
                    progressPercent,
                    data.getLatitude(),
                    data.getLongitude(),
                    data.getLocationName(),
                    data.getTemperature(),
                    data.getHumidity(),
                    decayedQuality
                );
                emitEvent(instance, progressEvent);
            }
            
            instance.lastUpdateTime = currentTime;
            
        } catch (Exception e) {
            System.err.println("❌ Error updating simulation " + instance.deliveryId + ": " + e.getMessage());
            e.printStackTrace();
            
            // Emit error event
            DeliveryEvent errorEvent = DeliveryEvent.error(
                instance.deliveryId,
                instance.currentState,
                instance.currentProgress,
                e.getMessage()
            );
            emitEvent(instance, errorEvent);
        }
    }
    
    /**
     * Complete a simulation and emit completion event.
     */
    private void completeSimulation(SimulationInstance instance, 
                                    RouteInterpolator.InterpolatedData finalData) {
        
        instance.stopped = true;
        
        if (instance.scheduledTask != null) {
            instance.scheduledTask.cancel(false);
        }
        
        // Emit completion event with final quality
        DeliveryEvent completionEvent = DeliveryEvent.completed(
            instance.deliveryId,
            finalData.getLatitude(),
            finalData.getLongitude(),
            finalData.getLocationName(),
            finalData.getTemperature(),
            finalData.getHumidity(),
            instance.currentQuality
        );
        emitEvent(instance, completionEvent);
        
        // Remove from active simulations
        activeSimulations.remove(instance.deliveryId);
        
        System.out.println("✅ Completed simulation for " + instance.deliveryId + 
                          " with final quality: " + String.format("%.1f%%", instance.currentQuality));
    }
    
    /**
     * Calculate quality decay using exponential formula.
     * finalQuality = initialQuality * exp(-decayRate * exposureSeconds)
     * 
     * @param initialQuality Starting quality (0-100)
     * @param decayRate Decay rate per second
     * @param exposureSeconds Time elapsed in seconds
     * @return Decayed quality (0-100)
     */
    public static double calculateQualityDecay(double initialQuality, double decayRate, 
                                               double exposureSeconds) {
        if (exposureSeconds < 0) {
            throw new IllegalArgumentException("exposureSeconds cannot be negative");
        }
        if (decayRate < 0) {
            throw new IllegalArgumentException("decayRate cannot be negative");
        }
        
        double decayFactor = Math.exp(-decayRate * exposureSeconds);
        double decayedQuality = initialQuality * decayFactor;
        
        // Ensure quality stays within bounds
        return Math.max(0, Math.min(100, decayedQuality));
    }
    
    /**
     * Emit an event to the handler safely.
     */
    private void emitEvent(SimulationInstance instance, DeliveryEvent event) {
        if (instance.eventHandler != null) {
            try {
                instance.eventHandler.accept(event);
            } catch (Exception e) {
                System.err.println("⚠️ Error in event handler for " + instance.deliveryId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Shutdown the simulator and stop all active simulations.
     */
    public void shutdown() {
        // Stop all active simulations
        for (String deliveryId : List.copyOf(activeSimulations.keySet())) {
            stopSimulation(deliveryId);
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
        
        System.out.println("🔴 EnhancedDeliverySimulator shutdown complete");
    }
    
    /**
     * Get the configuration.
     */
    public SimulationConfig getConfig() {
        return config;
    }
}
