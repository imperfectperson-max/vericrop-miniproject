package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.RouteWaypoint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for monitoring and reporting temperature conditions during delivery.
 * Tracks temperature violations, trends, and compliance with cold chain requirements.
 */
public class TemperatureService {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureService.class);
    
    // Temperature thresholds for fresh produce (°C)
    private static final double TEMP_MIN = 2.0;
    private static final double TEMP_MAX = 8.0;
    private static final double TEMP_IDEAL = 5.0;
    
    private final Map<String, TemperatureMonitoring> activeMonitoring;
    
    /**
     * Temperature monitoring state for a batch.
     */
    public static class TemperatureMonitoring {
        private final String batchId;
        private double minTemp = Double.MAX_VALUE;
        private double maxTemp = Double.MIN_VALUE;
        private double avgTemp = 0.0;
        private int violationCount = 0;
        private int readingCount = 0;
        
        public TemperatureMonitoring(String batchId) {
            this.batchId = batchId;
        }
        
        public void recordReading(double temperature) {
            readingCount++;
            minTemp = Math.min(minTemp, temperature);
            maxTemp = Math.max(maxTemp, temperature);
            avgTemp = ((avgTemp * (readingCount - 1)) + temperature) / readingCount;
            
            if (temperature < TEMP_MIN || temperature > TEMP_MAX) {
                violationCount++;
            }
        }
        
        public String getBatchId() { return batchId; }
        public double getMinTemp() { return minTemp; }
        public double getMaxTemp() { return maxTemp; }
        public double getAvgTemp() { return avgTemp; }
        public int getViolationCount() { return violationCount; }
        public int getReadingCount() { return readingCount; }
        public boolean isCompliant() { return violationCount == 0; }
    }
    
    public TemperatureService() {
        this.activeMonitoring = new ConcurrentHashMap<>();
    }
    
    /**
     * Start monitoring temperature for a batch.
     */
    public void startMonitoring(String batchId) {
        TemperatureMonitoring monitoring = new TemperatureMonitoring(batchId);
        activeMonitoring.put(batchId, monitoring);
        logger.info("Started temperature monitoring for batch: {}", batchId);
    }
    
    /**
     * Record temperature readings from route waypoints.
     */
    public void recordRoute(String batchId, List<RouteWaypoint> waypoints) {
        TemperatureMonitoring monitoring = activeMonitoring.get(batchId);
        if (monitoring == null) {
            logger.warn("No active monitoring for batch: {}", batchId);
            return;
        }
        
        for (RouteWaypoint waypoint : waypoints) {
            monitoring.recordReading(waypoint.getTemperature());
        }
        
        logger.debug("Recorded {} temperature readings for batch: {}", 
                    waypoints.size(), batchId);
    }
    
    /**
     * Get monitoring status for a batch.
     */
    public TemperatureMonitoring getMonitoring(String batchId) {
        return activeMonitoring.get(batchId);
    }
    
    /**
     * Stop monitoring and return final report.
     */
    public TemperatureMonitoring stopMonitoring(String batchId) {
        TemperatureMonitoring monitoring = activeMonitoring.remove(batchId);
        if (monitoring != null) {
            logger.info("Stopped temperature monitoring for batch: {} - {} violations in {} readings",
                       batchId, monitoring.getViolationCount(), monitoring.getReadingCount());
        }
        return monitoring;
    }
    
    /**
     * Get all active monitoring sessions.
     */
    public Map<String, TemperatureMonitoring> getAllActiveMonitoring() {
        return new ConcurrentHashMap<>(activeMonitoring);
    }
}
