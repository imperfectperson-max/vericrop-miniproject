package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Alert;
import org.vericrop.service.models.RouteWaypoint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for computing final quality assessments on delivery.
 * Aggregates sensor logs (temperature, humidity, time) and applies 
 * quality decay models to determine final quality scores.
 * 
 * <p>This service is used by ConsumerController to compute and display
 * final quality when a shipment is delivered.</p>
 * 
 * <p>Thread-safe: Can be used by multiple controllers concurrently.</p>
 */
public class QualityAssessmentService {
    private static final Logger logger = LoggerFactory.getLogger(QualityAssessmentService.class);
    
    // Quality thresholds
    private static final double PRIME_QUALITY_THRESHOLD = 80.0;
    private static final double ACCEPTABLE_QUALITY_THRESHOLD = 60.0;
    
    // Temperature violation penalties
    private static final double TEMP_VIOLATION_PENALTY_PER_READING = 0.5;
    private static final double CRITICAL_TEMP_VIOLATION_PENALTY = 2.0;
    
    // Humidity violation penalties
    private static final double HUMIDITY_VIOLATION_PENALTY_PER_READING = 0.3;
    
    // Temperature thresholds for cold chain (°C)
    private static final double TEMP_MIN = 2.0;
    private static final double TEMP_MAX = 8.0;
    private static final double CRITICAL_TEMP_MAX = 12.0;
    
    // Humidity thresholds (%)
    private static final double HUMIDITY_MIN = 65.0;
    private static final double HUMIDITY_MAX = 90.0;
    
    private final QualityDecayService qualityDecayService;
    private final TemperatureService temperatureService;
    private final AlertService alertService;
    
    // Cache of final quality assessments per batch
    private final Map<String, FinalQualityAssessment> assessmentCache;
    
    /**
     * Final quality assessment result.
     */
    public static class FinalQualityAssessment {
        private final String batchId;
        private final double initialQuality;
        private final double finalQuality;
        private final String qualityGrade;
        private final int temperatureViolations;
        private final int humidityViolations;
        private final double avgTemperature;
        private final double avgHumidity;
        private final long transitTimeMs;
        private final int alertCount;
        private final long assessmentTimestamp;
        
        public FinalQualityAssessment(String batchId, double initialQuality, double finalQuality,
                                      String qualityGrade, int temperatureViolations,
                                      int humidityViolations, double avgTemperature,
                                      double avgHumidity, long transitTimeMs, int alertCount) {
            this.batchId = batchId;
            this.initialQuality = initialQuality;
            this.finalQuality = finalQuality;
            this.qualityGrade = qualityGrade;
            this.temperatureViolations = temperatureViolations;
            this.humidityViolations = humidityViolations;
            this.avgTemperature = avgTemperature;
            this.avgHumidity = avgHumidity;
            this.transitTimeMs = transitTimeMs;
            this.alertCount = alertCount;
            this.assessmentTimestamp = System.currentTimeMillis();
        }
        
        public String getBatchId() { return batchId; }
        public double getInitialQuality() { return initialQuality; }
        public double getFinalQuality() { return finalQuality; }
        public String getQualityGrade() { return qualityGrade; }
        public int getTemperatureViolations() { return temperatureViolations; }
        public int getHumidityViolations() { return humidityViolations; }
        public double getAvgTemperature() { return avgTemperature; }
        public double getAvgHumidity() { return avgHumidity; }
        public long getTransitTimeMs() { return transitTimeMs; }
        public int getAlertCount() { return alertCount; }
        public long getAssessmentTimestamp() { return assessmentTimestamp; }
        
        public boolean isPrimeQuality() {
            return finalQuality >= PRIME_QUALITY_THRESHOLD;
        }
        
        public boolean isAcceptableQuality() {
            return finalQuality >= ACCEPTABLE_QUALITY_THRESHOLD;
        }
        
        @Override
        public String toString() {
            return String.format(
                "FinalQualityAssessment{batchId='%s', finalQuality=%.1f%%, grade='%s', " +
                "tempViolations=%d, humidityViolations=%d, avgTemp=%.1f°C, alerts=%d}",
                batchId, finalQuality, qualityGrade, temperatureViolations, 
                humidityViolations, avgTemperature, alertCount
            );
        }
    }
    
    /**
     * Create a new QualityAssessmentService with dependencies.
     * 
     * @param qualityDecayService Service for quality decay calculations
     * @param temperatureService Service for temperature monitoring data
     * @param alertService Service for alert data
     */
    public QualityAssessmentService(QualityDecayService qualityDecayService,
                                   TemperatureService temperatureService,
                                   AlertService alertService) {
        this.qualityDecayService = qualityDecayService;
        this.temperatureService = temperatureService;
        this.alertService = alertService;
        this.assessmentCache = new ConcurrentHashMap<>();
        
        logger.info("QualityAssessmentService initialized");
    }
    
    /**
     * Create a QualityAssessmentService with only QualityDecayService.
     * Temperature and Alert services will be null (standalone mode).
     */
    public QualityAssessmentService(QualityDecayService qualityDecayService) {
        this(qualityDecayService, null, null);
    }
    
    /**
     * Create a QualityAssessmentService with default services.
     */
    public QualityAssessmentService() {
        this(new QualityDecayService(), null, null);
    }
    
    /**
     * Compute final quality assessment for a delivered batch.
     * Aggregates all sensor data and applies quality decay model.
     * 
     * @param batchId The batch identifier
     * @param initialQuality Initial quality score (0-100)
     * @param routeWaypoints Route waypoints with environmental readings
     * @return Final quality assessment
     */
    public FinalQualityAssessment assessFinalQuality(String batchId, double initialQuality,
                                                     List<RouteWaypoint> routeWaypoints) {
        logger.info("Computing final quality assessment for batch: {}", batchId);
        
        if (routeWaypoints == null || routeWaypoints.isEmpty()) {
            logger.warn("No route waypoints provided for batch: {}", batchId);
            return createDefaultAssessment(batchId, initialQuality);
        }
        
        // Aggregate environmental readings
        double totalTemp = 0;
        double totalHumidity = 0;
        int tempViolations = 0;
        int criticalTempViolations = 0;
        int humidityViolations = 0;
        
        for (RouteWaypoint waypoint : routeWaypoints) {
            double temp = waypoint.getTemperature();
            double humidity = waypoint.getHumidity();
            
            totalTemp += temp;
            totalHumidity += humidity;
            
            // Check temperature violations
            if (temp < TEMP_MIN || temp > TEMP_MAX) {
                tempViolations++;
                if (temp > CRITICAL_TEMP_MAX) {
                    criticalTempViolations++;
                }
            }
            
            // Check humidity violations
            if (humidity < HUMIDITY_MIN || humidity > HUMIDITY_MAX) {
                humidityViolations++;
            }
        }
        
        int readingCount = routeWaypoints.size();
        double avgTemp = totalTemp / readingCount;
        double avgHumidity = totalHumidity / readingCount;
        
        // Calculate transit time
        long startTime = routeWaypoints.get(0).getTimestamp();
        long endTime = routeWaypoints.get(routeWaypoints.size() - 1).getTimestamp();
        long transitTimeMs = endTime - startTime;
        double transitHours = transitTimeMs / (1000.0 * 60 * 60);
        
        // Calculate quality decay using the decay service
        double decayedQuality = qualityDecayService.calculateQuality(
            initialQuality, avgTemp, avgHumidity, transitHours);
        
        // Apply violation penalties
        double penaltyFromTempViolations = tempViolations * TEMP_VIOLATION_PENALTY_PER_READING;
        double penaltyFromCriticalTemp = criticalTempViolations * CRITICAL_TEMP_VIOLATION_PENALTY;
        double penaltyFromHumidity = humidityViolations * HUMIDITY_VIOLATION_PENALTY_PER_READING;
        double totalPenalty = penaltyFromTempViolations + penaltyFromCriticalTemp + penaltyFromHumidity;
        
        double finalQuality = Math.max(0, decayedQuality - totalPenalty);
        
        // Get alert count from alert service if available
        int alertCount = 0;
        if (alertService != null) {
            alertCount = alertService.getAlertCount(batchId);
        }
        
        // Determine quality grade
        String grade = determineQualityGrade(finalQuality);
        
        // Create assessment
        FinalQualityAssessment assessment = new FinalQualityAssessment(
            batchId, initialQuality, finalQuality, grade,
            tempViolations, humidityViolations, avgTemp, avgHumidity,
            transitTimeMs, alertCount
        );
        
        // Cache the assessment
        assessmentCache.put(batchId, assessment);
        
        logger.info("Final quality assessment computed: {}", assessment);
        
        return assessment;
    }
    
    /**
     * Compute final quality using TemperatureService monitoring data.
     * This method is used when route waypoints are not directly available.
     * 
     * @param batchId The batch identifier
     * @param initialQuality Initial quality score (0-100)
     * @param startTimeMs Simulation start time in milliseconds
     * @return Final quality assessment
     */
    public FinalQualityAssessment assessFinalQualityFromMonitoring(String batchId, 
                                                                   double initialQuality,
                                                                   long startTimeMs) {
        logger.info("Computing final quality from monitoring data for batch: {}", batchId);
        
        if (temperatureService == null) {
            logger.warn("TemperatureService not available, using default assessment");
            return createDefaultAssessment(batchId, initialQuality);
        }
        
        TemperatureService.TemperatureMonitoring monitoring = 
            temperatureService.getMonitoring(batchId);
        
        if (monitoring == null) {
            logger.warn("No monitoring data found for batch: {}", batchId);
            return createDefaultAssessment(batchId, initialQuality);
        }
        
        // Calculate transit time
        long transitTimeMs = System.currentTimeMillis() - startTimeMs;
        double transitHours = transitTimeMs / (1000.0 * 60 * 60);
        
        // Calculate decay using average temperature and default humidity
        double avgTemp = monitoring.getAvgTemp();
        double avgHumidity = 75.0; // Default assumption
        
        double decayedQuality = qualityDecayService.calculateQuality(
            initialQuality, avgTemp, avgHumidity, transitHours);
        
        // Apply violation penalty
        int violations = monitoring.getViolationCount();
        double violationPenalty = violations * TEMP_VIOLATION_PENALTY_PER_READING;
        double finalQuality = Math.max(0, decayedQuality - violationPenalty);
        
        // Get alert count
        int alertCount = 0;
        if (alertService != null) {
            alertCount = alertService.getAlertCount(batchId);
        }
        
        String grade = determineQualityGrade(finalQuality);
        
        FinalQualityAssessment assessment = new FinalQualityAssessment(
            batchId, initialQuality, finalQuality, grade,
            violations, 0, avgTemp, avgHumidity, transitTimeMs, alertCount
        );
        
        assessmentCache.put(batchId, assessment);
        
        logger.info("Final quality assessment from monitoring: {}", assessment);
        
        return assessment;
    }
    
    /**
     * Get a cached final quality assessment for a batch.
     * 
     * @param batchId The batch identifier
     * @return Cached assessment or null if not available
     */
    public FinalQualityAssessment getCachedAssessment(String batchId) {
        return assessmentCache.get(batchId);
    }
    
    /**
     * Clear cached assessment for a batch.
     */
    public void clearCachedAssessment(String batchId) {
        assessmentCache.remove(batchId);
    }
    
    /**
     * Determine quality grade based on final quality score.
     */
    private String determineQualityGrade(double quality) {
        if (quality >= 90) {
            return "PRIME";
        } else if (quality >= 80) {
            return "EXCELLENT";
        } else if (quality >= 70) {
            return "GOOD";
        } else if (quality >= 60) {
            return "ACCEPTABLE";
        } else if (quality >= 40) {
            return "MARGINAL";
        } else {
            return "REJECTED";
        }
    }
    
    /**
     * Create a default assessment when sensor data is not available.
     */
    private FinalQualityAssessment createDefaultAssessment(String batchId, double initialQuality) {
        // Apply minimal decay for default case (assume 1 hour transit, ideal conditions)
        double finalQuality = qualityDecayService.calculateQuality(
            initialQuality, 5.0, 75.0, 1.0);
        String grade = determineQualityGrade(finalQuality);
        
        return new FinalQualityAssessment(
            batchId, initialQuality, finalQuality, grade,
            0, 0, 5.0, 75.0, 3600000L, 0
        );
    }
}
