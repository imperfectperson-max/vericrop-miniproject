package org.vericrop.service.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.DeliverySimulator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton manager for simulation lifecycle that persists across GUI navigation.
 * Ensures simulation state is independent of any single controller instance.
 */
public class SimulationManager {
    private static final Logger logger = LoggerFactory.getLogger(SimulationManager.class);
    private static volatile SimulationManager instance;
    
    private final DeliverySimulator deliverySimulator;
    private final List<SimulationListener> listeners;
    private final AtomicReference<SimulationState> currentSimulation;
    private final AtomicBoolean running;
    
    /**
     * Internal state holder for current simulation.
     */
    private static class SimulationState {
        final String batchId;
        final String farmerId;
        final long startTime;
        double progress;
        String currentLocation;
        
        SimulationState(String batchId, String farmerId) {
            this.batchId = batchId;
            this.farmerId = farmerId;
            this.startTime = System.currentTimeMillis();
            this.progress = 0.0;
            this.currentLocation = "Starting...";
        }
    }
    
    /**
     * Private constructor for singleton pattern.
     */
    private SimulationManager(DeliverySimulator deliverySimulator) {
        this.deliverySimulator = deliverySimulator;
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentSimulation = new AtomicReference<>();
        this.running = new AtomicBoolean(false);
        logger.info("SimulationManager initialized");
    }
    
    /**
     * Get singleton instance of SimulationManager.
     * Must be initialized first with initialize().
     */
    public static SimulationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SimulationManager not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Initialize the SimulationManager singleton.
     * Should be called once during application startup.
     */
    public static synchronized void initialize(DeliverySimulator deliverySimulator) {
        if (instance == null) {
            instance = new SimulationManager(deliverySimulator);
            logger.info("SimulationManager singleton created");
        }
    }
    
    /**
     * Check if SimulationManager has been initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * Register a listener to receive simulation events.
     */
    public void registerListener(SimulationListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            logger.debug("Registered simulation listener: {}", listener.getClass().getSimpleName());
            
            // If simulation is running, immediately notify the new listener
            if (isRunning()) {
                SimulationState state = currentSimulation.get();
                if (state != null) {
                    notifyStarted(state.batchId, state.farmerId);
                    notifyProgress(state.batchId, state.progress, state.currentLocation);
                }
            }
        }
    }
    
    /**
     * Unregister a listener from receiving simulation events.
     */
    public void unregisterListener(SimulationListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            logger.debug("Unregistered simulation listener: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * Start a new simulation.
     */
    public void startSimulation(String batchId, String farmerId, 
                               DeliverySimulator.GeoCoordinate origin,
                               DeliverySimulator.GeoCoordinate destination,
                               int numWaypoints, double avgSpeedKmh, long updateIntervalMs) {
        if (running.get()) {
            logger.warn("Simulation already running for batch: {}", currentSimulation.get().batchId);
            notifyError(batchId, "Another simulation is already running");
            return;
        }
        
        try {
            // Generate route
            long startTime = System.currentTimeMillis();
            var route = deliverySimulator.generateRoute(origin, destination, numWaypoints, startTime, avgSpeedKmh);
            
            // Start simulation in DeliverySimulator
            deliverySimulator.startSimulation(batchId, route, updateIntervalMs);
            
            // Update state
            SimulationState state = new SimulationState(batchId, farmerId);
            currentSimulation.set(state);
            running.set(true);
            
            // Notify listeners
            notifyStarted(batchId, farmerId);
            
            logger.info("Started simulation for batch: {}, farmer: {}", batchId, farmerId);
            
            // Start progress monitoring (simplified - in real implementation would track actual progress)
            startProgressMonitoring(batchId);
            
        } catch (Exception e) {
            logger.error("Failed to start simulation for batch: {}", batchId, e);
            notifyError(batchId, "Failed to start simulation: " + e.getMessage());
        }
    }
    
    /**
     * Stop the current simulation.
     */
    public void stopSimulation() {
        SimulationState state = currentSimulation.get();
        if (state == null || !running.get()) {
            logger.warn("No active simulation to stop");
            return;
        }
        
        try {
            // Stop simulation in DeliverySimulator
            deliverySimulator.stopSimulation(state.batchId);
            
            // Update state
            running.set(false);
            String stoppedBatchId = state.batchId;
            currentSimulation.set(null);
            
            // Notify listeners
            notifyStopped(stoppedBatchId, false);
            
            logger.info("Stopped simulation for batch: {}", stoppedBatchId);
            
        } catch (Exception e) {
            logger.error("Error stopping simulation", e);
            if (state != null) {
                notifyError(state.batchId, "Error stopping simulation: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if a simulation is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Get current simulation progress (0.0 to 100.0).
     */
    public double getProgress() {
        SimulationState state = currentSimulation.get();
        return state != null ? state.progress : 0.0;
    }
    
    /**
     * Get current simulation ID (batch ID).
     */
    public String getSimulationId() {
        SimulationState state = currentSimulation.get();
        return state != null ? state.batchId : null;
    }
    
    /**
     * Get current producer/farmer ID.
     */
    public String getCurrentProducer() {
        SimulationState state = currentSimulation.get();
        return state != null ? state.farmerId : null;
    }
    
    /**
     * Get current location description.
     */
    public String getCurrentLocation() {
        SimulationState state = currentSimulation.get();
        return state != null ? state.currentLocation : null;
    }
    
    /**
     * Start monitoring progress (simplified implementation).
     * In a real implementation, this would poll the DeliverySimulator for actual progress.
     */
    private void startProgressMonitoring(String batchId) {
        // This is a simplified implementation
        // In production, would poll DeliverySimulator.getSimulationStatus() periodically
        Thread monitorThread = new Thread(() -> {
            try {
                while (running.get()) {
                    SimulationState state = currentSimulation.get();
                    if (state != null && state.batchId.equals(batchId)) {
                        // Simulate progress (in real implementation, would get from DeliverySimulator)
                        state.progress = Math.min(100.0, state.progress + 2.0);
                        
                        // Update location based on progress
                        if (state.progress < 30) {
                            state.currentLocation = "En route from origin";
                        } else if (state.progress < 70) {
                            state.currentLocation = "In transit - midpoint";
                        } else if (state.progress < 100) {
                            state.currentLocation = "Approaching destination";
                        } else if (state.progress >= 100) {
                            state.currentLocation = "Delivered";
                            // Auto-stop when complete
                            // Stop simulation (will notify listeners with completed=true)
                            running.set(false); // Stop the monitoring loop
                            String completedBatchId = state.batchId;
                            currentSimulation.set(null);
                            
                            // Stop in DeliverySimulator
                            try {
                                deliverySimulator.stopSimulation(completedBatchId);
                            } catch (Exception e) {
                                logger.error("Error stopping delivery simulator", e);
                            }
                            
                            // Notify listeners with completed=true
                            notifyStopped(completedBatchId, true);
                            logger.info("Simulation completed for batch: {}", completedBatchId);
                            break; // Exit monitoring loop
                        }
                        
                        // Notify listeners
                        notifyProgress(batchId, state.progress, state.currentLocation);
                    }
                    
                    Thread.sleep(5000); // Update every 5 seconds
                }
            } catch (InterruptedException e) {
                logger.debug("Progress monitoring interrupted for batch: {}", batchId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error in progress monitoring for batch: {}", batchId, e);
            }
        });
        monitorThread.setName("SimulationProgressMonitor-" + batchId);
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Notify all listeners that simulation started.
     */
    private void notifyStarted(String batchId, String farmerId) {
        for (SimulationListener listener : listeners) {
            try {
                listener.onSimulationStarted(batchId, farmerId);
            } catch (Exception e) {
                logger.error("Error notifying listener of simulation start", e);
            }
        }
    }
    
    /**
     * Notify all listeners of progress update.
     */
    private void notifyProgress(String batchId, double progress, String location) {
        for (SimulationListener listener : listeners) {
            try {
                listener.onProgressUpdate(batchId, progress, location);
            } catch (Exception e) {
                logger.error("Error notifying listener of progress update", e);
            }
        }
    }
    
    /**
     * Notify all listeners that simulation stopped.
     */
    private void notifyStopped(String batchId, boolean completed) {
        for (SimulationListener listener : listeners) {
            try {
                listener.onSimulationStopped(batchId, completed);
            } catch (Exception e) {
                logger.error("Error notifying listener of simulation stop", e);
            }
        }
    }
    
    /**
     * Notify all listeners of an error.
     */
    private void notifyError(String batchId, String error) {
        for (SimulationListener listener : listeners) {
            try {
                listener.onSimulationError(batchId, error);
            } catch (Exception e) {
                logger.error("Error notifying listener of simulation error", e);
            }
        }
    }
    
    /**
     * Shutdown the manager and clean up resources.
     */
    public void shutdown() {
        if (running.get()) {
            stopSimulation();
        }
        listeners.clear();
        logger.info("SimulationManager shutdown complete");
    }
}
