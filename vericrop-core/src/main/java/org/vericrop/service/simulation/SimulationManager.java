package org.vericrop.service.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.*;
import org.vericrop.service.models.Alert;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton manager for simulation lifecycle that persists across GUI navigation.
 * Ensures simulation state is independent of any single controller instance.
 */
public class SimulationManager {
    private static final Logger logger = LoggerFactory.getLogger(SimulationManager.class);
    private static volatile SimulationManager instance;
    
    // Compliance monitoring constants
    private static final double VIOLATION_RATE_THRESHOLD = 10.0; // Percent
    private static final double CRITICAL_VIOLATION_RATE_THRESHOLD = 30.0; // Percent
    private static final double TEMPERATURE_MAX_THRESHOLD = 8.0; // °C
    private static final int COMPLIANCE_INITIAL_DELAY_SECONDS = 10;
    private static final int COMPLIANCE_CHECK_INTERVAL_SECONDS = 15;
    
    // Route generation constants
    private static final double WAREHOUSE_LOCATION_OFFSET = 0.05; // Degrees lat/lon offset for warehouse
    
    private final DeliverySimulator deliverySimulator;
    private final MapService mapService;
    private final TemperatureService temperatureService;
    private final AlertService alertService;
    private final MapSimulator mapSimulator;
    private final ScenarioManager scenarioManager;
    private final List<SimulationListener> listeners;
    private final AtomicReference<SimulationState> currentSimulation;
    private final AtomicBoolean running;
    private final ScheduledExecutorService complianceCheckExecutor;
    
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
     * Private constructor for singleton pattern (legacy - backward compatibility).
     */
    private SimulationManager(DeliverySimulator deliverySimulator) {
        this(deliverySimulator, null, null, null, null, null);
    }
    
    /**
     * Private constructor with full dependencies.
     */
    private SimulationManager(DeliverySimulator deliverySimulator, MapService mapService,
                             TemperatureService temperatureService, AlertService alertService) {
        this(deliverySimulator, mapService, temperatureService, alertService, null, null);
    }
    
    /**
     * Private constructor with all dependencies including map simulation.
     */
    private SimulationManager(DeliverySimulator deliverySimulator, MapService mapService,
                             TemperatureService temperatureService, AlertService alertService,
                             MapSimulator mapSimulator, ScenarioManager scenarioManager) {
        this.deliverySimulator = deliverySimulator;
        this.mapService = mapService != null ? mapService : new MapService();
        this.temperatureService = temperatureService != null ? temperatureService : new TemperatureService();
        this.alertService = alertService != null ? alertService : new AlertService();
        this.mapSimulator = mapSimulator != null ? mapSimulator : new MapSimulator();
        this.scenarioManager = scenarioManager != null ? scenarioManager : new ScenarioManager();
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentSimulation = new AtomicReference<>();
        this.running = new AtomicBoolean(false);
        this.complianceCheckExecutor = Executors.newScheduledThreadPool(1, 
            r -> new Thread(r, "SimulationManager-ComplianceCheck"));
        logger.info("SimulationManager initialized with integrated services including map simulation");
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
     * Initialize the SimulationManager singleton (legacy - backward compatibility).
     * Should be called once during application startup.
     */
    public static synchronized void initialize(DeliverySimulator deliverySimulator) {
        if (instance == null) {
            instance = new SimulationManager(deliverySimulator);
            logger.info("SimulationManager singleton created (legacy mode)");
        }
    }
    
    /**
     * Initialize the SimulationManager singleton with full dependencies.
     * Should be called once during application startup.
     * 
     * @param deliverySimulator Delivery simulator instance
     * @param mapService Map service for route generation
     * @param temperatureService Temperature monitoring service
     * @param alertService Alert service for compliance violations
     */
    public static synchronized void initialize(DeliverySimulator deliverySimulator, MapService mapService,
                                              TemperatureService temperatureService, AlertService alertService) {
        if (instance == null) {
            instance = new SimulationManager(deliverySimulator, mapService, temperatureService, alertService);
            logger.info("SimulationManager singleton created with integrated services");
        }
    }
    
    /**
     * Initialize the SimulationManager singleton with all dependencies including map simulation.
     * Should be called once during application startup.
     * 
     * @param deliverySimulator Delivery simulator instance
     * @param mapService Map service for route generation
     * @param temperatureService Temperature monitoring service
     * @param alertService Alert service for compliance violations
     * @param mapSimulator Map simulator for grid-based visualization
     * @param scenarioManager Scenario manager for scenario selection
     */
    public static synchronized void initialize(DeliverySimulator deliverySimulator, MapService mapService,
                                              TemperatureService temperatureService, AlertService alertService,
                                              MapSimulator mapSimulator, ScenarioManager scenarioManager) {
        if (instance == null) {
            instance = new SimulationManager(deliverySimulator, mapService, temperatureService, 
                                           alertService, mapSimulator, scenarioManager);
            logger.info("SimulationManager singleton created with full services including map simulation");
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
     * Start a new simulation (legacy method - backward compatibility).
     */
    public void startSimulation(String batchId, String farmerId, 
                               DeliverySimulator.GeoCoordinate origin,
                               DeliverySimulator.GeoCoordinate destination,
                               int numWaypoints, double avgSpeedKmh, long updateIntervalMs) {
        startSimulation(batchId, farmerId, origin, destination, numWaypoints, avgSpeedKmh, 
                       updateIntervalMs, null);
    }
    
    /**
     * Start a new simulation with scenario support.
     * 
     * @param batchId Batch identifier
     * @param farmerId Farmer/producer identifier
     * @param origin Starting location
     * @param destination Final destination
     * @param numWaypoints Number of waypoints per route segment
     * @param avgSpeedKmh Average speed in km/h
     * @param updateIntervalMs Update interval for simulation
     * @param scenario Delivery scenario (null = NORMAL)
     */
    public void startSimulation(String batchId, String farmerId, 
                               DeliverySimulator.GeoCoordinate origin,
                               DeliverySimulator.GeoCoordinate destination,
                               int numWaypoints, double avgSpeedKmh, long updateIntervalMs,
                               Scenario scenario) {
        if (running.get()) {
            logger.warn("Simulation already running for batch: {}", currentSimulation.get().batchId);
            notifyError(batchId, "Another simulation is already running");
            return;
        }
        
        // Use NORMAL scenario if none provided
        Scenario effectiveScenario = scenario != null ? scenario : Scenario.NORMAL;
        
        try {
            logger.info("=== Starting End-to-End Simulation ===");
            logger.info("Batch: {}, Farmer: {}, Scenario: {}", batchId, farmerId, effectiveScenario.getDisplayName());
            
            long startTime = System.currentTimeMillis();
            
            // Step 1: Generate multi-leg route (Farmer -> Warehouse -> Consumer)
            logger.info("Step 1: Generating multi-leg route...");
            GeoCoordinate originModel = new GeoCoordinate(origin.getLatitude(), origin.getLongitude(), origin.getName());
            GeoCoordinate destModel = new GeoCoordinate(destination.getLatitude(), destination.getLongitude(), destination.getName());
            
            // Generate intermediate warehouse location (midpoint with slight offset)
            double warehouseLat = (origin.getLatitude() + destination.getLatitude()) / 2.0 + WAREHOUSE_LOCATION_OFFSET;
            double warehouseLon = (origin.getLongitude() + destination.getLongitude()) / 2.0 + WAREHOUSE_LOCATION_OFFSET;
            GeoCoordinate warehouse = new GeoCoordinate(warehouseLat, warehouseLon, "Distribution Warehouse");
            
            List<RouteWaypoint> route = mapService.generateMultiLegRoute(
                batchId, originModel, warehouse, destModel, 
                numWaypoints, startTime, avgSpeedKmh, effectiveScenario);
            
            logger.info("Generated route with {} waypoints", route.size());
            
            // Step 2: Start temperature monitoring
            logger.info("Step 2: Starting temperature monitoring...");
            temperatureService.startMonitoring(batchId);
            
            // Step 3: Record route temperatures
            logger.info("Step 3: Recording route environmental data...");
            temperatureService.recordRoute(batchId, route);
            
            // Step 4: Start DeliverySimulator
            logger.info("Step 4: Starting delivery simulation...");
            // Convert model waypoints to legacy format
            List<DeliverySimulator.RouteWaypoint> legacyRoute = new java.util.ArrayList<>();
            for (RouteWaypoint wp : route) {
                DeliverySimulator.GeoCoordinate legacyCoord = new DeliverySimulator.GeoCoordinate(
                    wp.getLocation().getLatitude(),
                    wp.getLocation().getLongitude(),
                    wp.getLocation().getName()
                );
                legacyRoute.add(new DeliverySimulator.RouteWaypoint(
                    legacyCoord, wp.getTimestamp(), wp.getTemperature(), wp.getHumidity()
                ));
            }
            
            deliverySimulator.startSimulation(batchId, farmerId, legacyRoute, updateIntervalMs, effectiveScenario);
            
            // Step 5: Initialize MapSimulator for this scenario
            logger.info("Step 5: Initializing map simulation...");
            mapSimulator.initializeForScenario(effectiveScenario, batchId, route.size());
            
            // Step 6: Update state
            SimulationState state = new SimulationState(batchId, farmerId);
            currentSimulation.set(state);
            running.set(true);
            
            // Step 7: Start temperature compliance checking
            logger.info("Step 6: Starting temperature compliance monitoring...");
            startComplianceChecking(batchId);
            
            // Notify listeners
            notifyStarted(batchId, farmerId);
            
            logger.info("=== Simulation Started Successfully ===");
            logger.info("Batch: {}, Scenario: {}, Waypoints: {}", 
                       batchId, effectiveScenario.getDisplayName(), route.size());
            
            // Start progress monitoring
            startProgressMonitoring(batchId);
            
        } catch (Exception e) {
            logger.error("Failed to start simulation for batch: {}", batchId, e);
            notifyError(batchId, "Failed to start simulation: " + e.getMessage());
        }
    }
    
    /**
     * Start periodic temperature compliance checking.
     * Checks temperature monitoring data and generates alerts for violations.
     */
    private void startComplianceChecking(String batchId) {
        ScheduledFuture<?> complianceTask = complianceCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!running.get() || currentSimulation.get() == null || 
                    !currentSimulation.get().batchId.equals(batchId)) {
                    return; // Simulation stopped or different batch
                }
                
                TemperatureService.TemperatureMonitoring monitoring = 
                    temperatureService.getMonitoring(batchId);
                
                if (monitoring != null && monitoring.getReadingCount() > 0) {
                    // Check for temperature violations
                    if (monitoring.getViolationCount() > 0) {
                        double violationRate = (double) monitoring.getViolationCount() / 
                                              monitoring.getReadingCount() * 100.0;
                        
                        if (violationRate > VIOLATION_RATE_THRESHOLD) {
                            String message = String.format(
                                "Temperature compliance violation: %.1f%% of readings out of range " +
                                "(min: %.1f°C, max: %.1f°C, avg: %.1f°C)",
                                violationRate, monitoring.getMinTemp(), 
                                monitoring.getMaxTemp(), monitoring.getAvgTemp()
                            );
                            
                            Alert.Severity severity = violationRate > CRITICAL_VIOLATION_RATE_THRESHOLD ? 
                                Alert.Severity.CRITICAL : Alert.Severity.HIGH;
                            
                            Alert alert = new Alert(
                                java.util.UUID.randomUUID().toString(),
                                batchId,
                                Alert.AlertType.TEMPERATURE_HIGH,
                                severity,
                                message,
                                monitoring.getAvgTemp(),
                                TEMPERATURE_MAX_THRESHOLD,
                                System.currentTimeMillis(),
                                "Compliance Monitor"
                            );
                            
                            alertService.recordAlert(alert);
                            logger.warn("Compliance alert generated for batch {}: {}", batchId, message);
                        }
                    }
                    
                    // Log compliance status
                    if (monitoring.getReadingCount() % 10 == 0) { // Log every 10 readings
                        logger.debug("Compliance check - Batch: {}, Violations: {}/{}, Avg Temp: {:.1f}°C",
                                   batchId, monitoring.getViolationCount(), 
                                   monitoring.getReadingCount(), monitoring.getAvgTemp());
                    }
                }
            } catch (Exception e) {
                logger.error("Error in compliance checking for batch: {}", batchId, e);
            }
        }, COMPLIANCE_INITIAL_DELAY_SECONDS, COMPLIANCE_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        logger.info("Started compliance checking for batch: {}", batchId);
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
                        
                        // Step the map simulation to update entity positions
                        try {
                            mapSimulator.step(state.progress);
                            logger.trace("Map simulation stepped at progress {}%", state.progress);
                        } catch (Exception e) {
                            logger.error("Error stepping map simulation", e);
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
     * Get the MapSimulator instance.
     * 
     * @return MapSimulator instance
     */
    public MapSimulator getMapSimulator() {
        return mapSimulator;
    }
    
    /**
     * Get the ScenarioManager instance.
     * 
     * @return ScenarioManager instance
     */
    public ScenarioManager getScenarioManager() {
        return scenarioManager;
    }
    
    /**
     * Shutdown the manager and clean up resources.
     */
    public void shutdown() {
        if (running.get()) {
            stopSimulation();
        }
        
        // Shutdown compliance check executor
        if (complianceCheckExecutor != null) {
            complianceCheckExecutor.shutdown();
            try {
                if (!complianceCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    complianceCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                complianceCheckExecutor.shutdownNow();
            }
        }
        
        // Reset map simulator
        if (mapSimulator != null) {
            mapSimulator.reset();
        }
        
        listeners.clear();
        logger.info("SimulationManager shutdown complete");
    }
}
