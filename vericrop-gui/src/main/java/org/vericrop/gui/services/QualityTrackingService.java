package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.BatchRecord;
import org.vericrop.gui.persistence.PostgresBatchRepository;
import org.vericrop.gui.util.ReportGenerator;
import org.vericrop.service.QualityDecayService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for tracking batch quality changes over time.
 * Applies quality decay during supplier handling and transit.
 */
public class QualityTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(QualityTrackingService.class);
    
    private final QualityDecayService decayService;
    private final PostgresBatchRepository batchRepository;
    
    public QualityTrackingService(QualityDecayService decayService, 
                                 PostgresBatchRepository batchRepository) {
        this.decayService = decayService;
        this.batchRepository = batchRepository;
        logger.info("✅ QualityTrackingService initialized");
    }
    
    /**
     * Apply quality decay during supplier handling.
     * Updates batch quality based on storage conditions and time elapsed.
     * 
     * @param batchId The batch ID
     * @param temperature Storage temperature in Celsius
     * @param humidity Storage humidity percentage
     * @param hoursElapsed Hours in storage
     * @return Updated batch with new quality
     * @throws SQLException if database update fails
     */
    public BatchRecord applySupplierHandlingDecay(String batchId, double temperature, 
                                                  double humidity, double hoursElapsed) 
            throws SQLException {
        logger.info("Applying supplier handling decay for batch: {}", batchId);
        
        Optional<BatchRecord> batchOpt = batchRepository.findByBatchId(batchId);
        if (batchOpt.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        
        BatchRecord batch = batchOpt.get();
        if (batch.getQualityScore() == null) {
            logger.warn("Cannot apply decay - batch has no quality score");
            return batch;
        }
        
        double initialQuality = batch.getQualityScore() * 100; // Convert to percentage
        double newQuality = decayService.calculateQuality(initialQuality, temperature, 
                                                         humidity, hoursElapsed);
        
        // Update batch quality
        batch.setQualityScore(newQuality / 100.0); // Convert back to 0-1 scale
        batch.setStatus("in_storage");
        
        // Recalculate prime and rejection rates based on new quality
        recomputeQualityMetrics(batch);
        
        // Save to database
        batch = batchRepository.update(batch);
        
        logger.info("Applied supplier handling decay: batchId={}, initial={}, final={}, decay={}", 
                   batchId, initialQuality, newQuality, initialQuality - newQuality);
        
        // Log the decay event
        logQualityChangeEvent(batchId, initialQuality / 100.0, newQuality / 100.0, 
                             "supplier_handling", temperature, humidity);
        
        return batch;
    }
    
    /**
     * Apply quality decay during transit.
     * Simulates quality degradation with environmental readings.
     * 
     * @param batchId The batch ID
     * @param readings List of environmental readings during transit
     * @return Updated batch with final quality
     * @throws SQLException if database update fails
     */
    public BatchRecord applyTransitDecay(String batchId, 
                                        List<QualityDecayService.EnvironmentalReading> readings) 
            throws SQLException {
        logger.info("Applying transit decay for batch: {}", batchId);
        
        Optional<BatchRecord> batchOpt = batchRepository.findByBatchId(batchId);
        if (batchOpt.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        
        BatchRecord batch = batchOpt.get();
        if (batch.getQualityScore() == null) {
            logger.warn("Cannot apply decay - batch has no quality score");
            return batch;
        }
        
        double initialQuality = batch.getQualityScore() * 100;
        
        // Simulate quality trace through transit
        List<QualityDecayService.QualityTracePoint> trace = 
            decayService.simulateQualityTrace(initialQuality, readings);
        
        // Get final quality from last trace point
        QualityDecayService.QualityTracePoint finalPoint = trace.get(trace.size() - 1);
        double finalQuality = finalPoint.getQuality();
        
        // Update batch quality
        batch.setQualityScore(finalQuality / 100.0);
        batch.setStatus("in_transit");
        
        // Recalculate prime and rejection rates
        recomputeQualityMetrics(batch);
        
        // Save to database
        batch = batchRepository.update(batch);
        
        logger.info("Applied transit decay: batchId={}, initial={}, final={}, waypoints={}", 
                   batchId, initialQuality, finalQuality, readings.size());
        
        // Log quality trace events
        for (QualityDecayService.QualityTracePoint point : trace) {
            logQualityChangeEvent(batchId, initialQuality / 100.0, point.getQuality() / 100.0,
                                 "transit", point.getTemperature(), point.getHumidity());
        }
        
        return batch;
    }
    
    /**
     * Get quality prediction for future time given current conditions.
     * 
     * @param batchId The batch ID
     * @param temperature Expected temperature
     * @param humidity Expected humidity
     * @param hoursInFuture Hours to predict ahead
     * @return Predicted quality score (0-1 scale)
     * @throws SQLException if batch not found
     */
    public double predictFutureQuality(String batchId, double temperature, 
                                      double humidity, double hoursInFuture) 
            throws SQLException {
        Optional<BatchRecord> batchOpt = batchRepository.findByBatchId(batchId);
        if (batchOpt.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        
        BatchRecord batch = batchOpt.get();
        if (batch.getQualityScore() == null) {
            throw new IllegalStateException("Batch has no quality score");
        }
        
        double currentQuality = batch.getQualityScore() * 100;
        double predictedQuality = decayService.predictQuality(currentQuality, temperature, 
                                                             humidity, hoursInFuture);
        
        logger.info("Predicted quality for batch {}: current={}, predicted={} (in {} hours)", 
                   batchId, currentQuality, predictedQuality, hoursInFuture);
        
        return predictedQuality / 100.0;
    }
    
    /**
     * Recompute batch quality metrics (prime rate, rejection rate) based on current quality.
     * Uses the same algorithm as batch creation.
     */
    private void recomputeQualityMetrics(BatchRecord batch) {
        if (batch.getQualityScore() == null || batch.getQualityLabel() == null) {
            return;
        }
        
        double qualityScore = batch.getQualityScore();
        double qualityPercent = qualityScore * 100.0;
        String classification = batch.getQualityLabel().toUpperCase();
        
        double primeRate, rejectionRate;
        
        switch (classification) {
            case "FRESH":
                primeRate = Math.min(80 + (qualityPercent * 0.2), 100.0) / 100.0;
                double freshRemainder = 100.0 - (primeRate * 100.0);
                rejectionRate = (freshRemainder * 0.2) / 100.0;
                break;
                
            case "LOW_QUALITY":
            case "LOW QUALITY":
                double lowQualityRate = Math.min(80 + (qualityPercent * 0.2), 100.0);
                double lowQualityRemainder = 100.0 - lowQualityRate;
                primeRate = (lowQualityRemainder * 0.8) / 100.0;
                rejectionRate = (lowQualityRemainder * 0.2) / 100.0;
                break;
                
            case "ROTTEN":
                rejectionRate = Math.min(80 + (qualityPercent * 0.2), 100.0) / 100.0;
                double rottenRemainder = 100.0 - (rejectionRate * 100.0);
                primeRate = (rottenRemainder * 0.2) / 100.0;
                break;
                
            default:
                primeRate = qualityScore;
                rejectionRate = (1.0 - qualityScore) * 0.3;
        }
        
        batch.setPrimeRate(primeRate);
        batch.setRejectionRate(rejectionRate);
    }
    
    /**
     * Log a quality change event to supply chain timeline.
     */
    private void logQualityChangeEvent(String batchId, double initialQuality, 
                                      double finalQuality, String eventType,
                                      double temperature, double humidity) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("batch_id", batchId);
            event.put("event_type", eventType);
            event.put("initial_quality", initialQuality);
            event.put("final_quality", finalQuality);
            event.put("quality_change", finalQuality - initialQuality);
            event.put("temperature", temperature);
            event.put("humidity", humidity);
            
            List<Map<String, Object>> events = new ArrayList<>();
            events.add(event);
            
            // Export to supply chain events directory
            Path eventPath = ReportGenerator.generateSupplyChainTimelineJSON(
                "quality_change_" + eventType, events);
            
            logger.debug("Logged quality change event: {}", eventPath.getFileName());
        } catch (IOException e) {
            logger.error("Failed to log quality change event", e);
        }
    }
    
    /**
     * Get quality decay rate for given conditions.
     */
    public double getDecayRate(double temperature, double humidity) {
        return decayService.calculateDecayRate(temperature, humidity);
    }
    
    /**
     * Check if environmental conditions are ideal.
     */
    public Map<String, Boolean> checkIdealConditions(double temperature, double humidity) {
        Map<String, Boolean> conditions = new HashMap<>();
        conditions.put("temperature_ideal", decayService.isTemperatureIdeal(temperature));
        conditions.put("humidity_ideal", decayService.isHumidityIdeal(humidity));
        conditions.put("all_ideal", decayService.isTemperatureIdeal(temperature) && 
                                    decayService.isHumidityIdeal(humidity));
        return conditions;
    }
}
