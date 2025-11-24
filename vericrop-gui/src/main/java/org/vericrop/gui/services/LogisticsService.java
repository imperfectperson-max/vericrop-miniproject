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
    
    // Update interval for map position (seconds)
    private static final int UPDATE_INTERVAL_SECONDS = 10;
    
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
            
            // Start map simulation
            startMapSimulation(batchId, duration);
            System.out.println("✅ Map simulation started for batch: " + batchId);
            
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
        
        // Publish start event
        publishMapEvent(batchId, ORIGIN_LAT, ORIGIN_LON, "Farm Location", 0.0, "STARTED");
        
        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();
            double progress = (double) (currentTime - startTime) / duration.toMillis();
            
            // Interpolate position between origin and destination
            double currentLat = ORIGIN_LAT + (DEST_LAT - ORIGIN_LAT) * progress;
            double currentLon = ORIGIN_LON + (DEST_LON - ORIGIN_LON) * progress;
            
            // Add small random variation to simulate realistic movement
            currentLat += (random.nextDouble() - 0.5) * 0.001;
            currentLon += (random.nextDouble() - 0.5) * 0.001;
            
            // Determine location name based on progress
            String locationName = getLocationName(progress);
            String status = progress >= 1.0 ? "DELIVERED" : "IN_TRANSIT";
            
            // Publish position update
            publishMapEvent(batchId, currentLat, currentLon, locationName, progress, status);
            
            // Wait for next update
            Thread.sleep(UPDATE_INTERVAL_SECONDS * 1000);
        }
        
        // Publish final event
        publishMapEvent(batchId, DEST_LAT, DEST_LON, "Warehouse", 1.0, "DELIVERED");
        
        activeSimulations.remove(batchId);
        System.out.println("✅ Map simulation completed for batch: " + batchId);
    }
    
    /**
     * Publish a map simulation event
     */
    private void publishMapEvent(String batchId, double lat, double lon, 
                                  String locationName, double progress, String status) {
        MapSimulationEvent event = new MapSimulationEvent(
            batchId, lat, lon, locationName, progress, status
        );
        producer.sendMapSimulationEvent(event);
    }
    
    /**
     * Get location name based on progress
     */
    private String getLocationName(double progress) {
        if (progress < 0.1) {
            return "Farm Location";
        } else if (progress < 0.3) {
            return "Highway - Mile 10";
        } else if (progress < 0.5) {
            return "Highway - Mile 30";
        } else if (progress < 0.7) {
            return "Highway - Mile 50";
        } else if (progress < 0.9) {
            return "Distribution Center Entrance";
        } else {
            return "Warehouse";
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
