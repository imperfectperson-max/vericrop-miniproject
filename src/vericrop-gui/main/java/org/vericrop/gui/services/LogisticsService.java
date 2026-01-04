package org.vericrop.gui.services;

import org.vericrop.dto.MapSimulationEvent;
import org.vericrop.kafka.producers.MapSimulationEventProducer;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Service for logistics and map simulation
 */
public class LogisticsService {
    
    private final MapSimulationEventProducer producer;
    private final ExecutorService executor;
    private final Random random;
    private final ConcurrentHashMap<String, Future<?>> activeSimulations;
    
    // Default origin and destination coordinates
    private static final double ORIGIN_LAT = 42.3601;
    private static final double ORIGIN_LON = -71.0589;
    private static final double DEST_LAT = 42.3736;
    private static final double DEST_LON = -71.1097;
    
    // Update interval for map position (milliseconds) - default 3 seconds for 2-minute presentations
    // This provides ~40 updates for a 2-minute simulation, giving smooth map animation
    private static final int DEFAULT_UPDATE_INTERVAL_MS = 3000;
    
    // Minimum number of updates for any simulation (ensures smooth animation)
    private static final int MIN_UPDATES = 40;
    
    public LogisticsService() {
        this.producer = new MapSimulationEventProducer();
        this.executor = Executors.newCachedThreadPool();
        this.random = new Random();
        this.activeSimulations = new ConcurrentHashMap<>();
    }
    
    /**
     * Start map simulation and temperature compliance together
     * @param batchId Batch identifier
     * @param duration Simulation duration
     * @param tempService Temperature compliance service (nullable)
     * @param scenarioId Scenario ID for temperature compliance
     */
    public void startMapAndCompliance(String batchId, Duration duration, 
                                      TemperatureComplianceService tempService, String scenarioId) {
        try {
            System.out.println("🚀 Starting map and compliance simulations for batch: " + batchId);
            
            // Start map simulation (logs its own success message)
            startMapSimulation(batchId, duration);
            
            // Start temperature compliance if service is provided
            if (tempService != null) {
                try {
                    tempService.startComplianceSimulation(batchId, scenarioId, duration);
                    System.out.println("✅ Temperature compliance simulation started for batch: " + batchId);
                } catch (Exception e) {
                    System.err.println("⚠️ Temperature compliance simulation failed for " + batchId + ": " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the entire operation if temperature compliance fails
                }
            } else {
                System.out.println("ℹ️ No temperature compliance service provided for batch: " + batchId);
            }
        } catch (Exception e) {
            System.err.println("❌ Error starting simulations for " + batchId + ": " + e.getMessage());
            e.printStackTrace();
            // Don't throw - handle gracefully to prevent UI crashes
        }
    }
    
    /**
     * Start map simulation for a batch
     * @param batchId Batch identifier
     * @param duration Simulation duration
     */
    public void startMapSimulation(String batchId, Duration duration) {
        // Cancel existing simulation for this batch if any
        stopSimulation(batchId);
        
        Future<?> future = executor.submit(() -> {
            try {
                runMapSimulation(batchId, duration);
            } catch (Exception e) {
                System.err.println("Map simulation error for " + batchId + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        activeSimulations.put(batchId, future);
        System.out.println("🗺️ Map simulation started for batch: " + batchId);
    }
    
    /**
     * Stop an active simulation
     */
    public void stopSimulation(String batchId) {
        Future<?> future = activeSimulations.remove(batchId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            System.out.println("🛑 Map simulation stopped for batch: " + batchId);
        }
    }
    
    /**
     * Run the map simulation
     */
    private void runMapSimulation(String batchId, Duration duration) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + duration.toMillis();
        
        // Calculate update interval to ensure smooth animation
        // For 2-minute simulation: 120000ms / 40 updates = 3000ms per update
        long updateIntervalMs = Math.min(DEFAULT_UPDATE_INTERVAL_MS, 
                                          duration.toMillis() / MIN_UPDATES);
        // Ensure at least 1 second between updates
        updateIntervalMs = Math.max(updateIntervalMs, 1000);
        
        System.out.println("🗺️ Map simulation config: duration=" + duration.toSeconds() + "s, updateInterval=" + updateIntervalMs + "ms");
        
        // Publish start event with initial environmental data
        publishMapEvent(batchId, ORIGIN_LAT, ORIGIN_LON, "Farm Location", 0.0, "STARTED", 4.0, 65.0);
        
        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();
            double progress = (double) (currentTime - startTime) / duration.toMillis();
            
            // Interpolate position between origin and destination
            double currentLat = ORIGIN_LAT + (DEST_LAT - ORIGIN_LAT) * progress;
            double currentLon = ORIGIN_LON + (DEST_LON - ORIGIN_LON) * progress;
            
            // Add small random variation to simulate realistic movement
            currentLat += (random.nextDouble() - 0.5) * 0.001;
            currentLon += (random.nextDouble() - 0.5) * 0.001;
            
            // Simulate environmental conditions (temperature and humidity)
            // Temperature: baseline 4.0°C with small variations
            double temperature = 4.0 + (random.nextDouble() - 0.5) * 2.0; // 3.0-5.0°C range
            // Humidity: baseline 65% with variations
            double humidity = 65.0 + (random.nextDouble() - 0.5) * 10.0; // 60-70% range
            
            // Determine location name based on progress
            String locationName = getLocationName(progress);
            String status = progress >= 1.0 ? "DELIVERED" : "IN_TRANSIT";
            
            // Publish position update with environmental data
            publishMapEvent(batchId, currentLat, currentLon, locationName, progress, status, temperature, humidity);
            
            // Wait for next update
            Thread.sleep(updateIntervalMs);
        }
        
        // Publish final event
        publishMapEvent(batchId, DEST_LAT, DEST_LON, "Warehouse", 1.0, "DELIVERED", 3.8, 62.0);
        
        activeSimulations.remove(batchId);
        System.out.println("✅ Map simulation completed for batch: " + batchId);
    }
    
    /**
     * Publish a map simulation event with environmental data
     */
    private void publishMapEvent(String batchId, double lat, double lon, 
                                  String locationName, double progress, String status,
                                  double temperature, double humidity) {
        MapSimulationEvent event = new MapSimulationEvent(
            batchId, lat, lon, locationName, progress, status, temperature, humidity
        );
        producer.sendMapSimulationEvent(event);
    }
    
    /**
     * Get location name based on progress.
     * Provides detailed waypoint names suitable for 2-minute presentation scenarios.
     */
    private String getLocationName(double progress) {
        if (progress < 0.05) {
            return "Farm Packaging Center";
        } else if (progress < 0.10) {
            return "Quality Check Station";
        } else if (progress < 0.15) {
            return "Farm Exit Gate";
        } else if (progress < 0.25) {
            return "County Road Section";
        } else if (progress < 0.35) {
            return "Highway Entry Point";
        } else if (progress < 0.45) {
            return "Highway - Mile 5";
        } else if (progress < 0.55) {
            return "Cold Hub Approach";
        } else if (progress < 0.65) {
            return "Cold Hub Intake";
        } else if (progress < 0.75) {
            return "Hub Quality Rescan";
        } else if (progress < 0.85) {
            return "City Highway Entry";
        } else if (progress < 0.95) {
            return "Downtown Approach";
        } else {
            return "Warehouse Receiving Dock";
        }
    }
    
    /**
     * Check if a simulation is active for a batch
     */
    public boolean isSimulationActive(String batchId) {
        Future<?> future = activeSimulations.get(batchId);
        return future != null && !future.isDone();
    }
    
    /**
     * Shutdown the service
     */
    public void shutdown() {
        // Cancel all active simulations
        for (String batchId : activeSimulations.keySet()) {
            stopSimulation(batchId);
        }
        
        // Shutdown executor
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Logistics service executor did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Close producer
        producer.close();
        System.out.println("🔴 Logistics service shutdown complete");
    }
}
