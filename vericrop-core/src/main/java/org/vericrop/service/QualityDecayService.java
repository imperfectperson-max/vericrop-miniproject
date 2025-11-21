package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating quality decay based on temperature and humidity.
 * Implements a deterministic decay model influenced by environmental conditions.
 */
public class QualityDecayService {
    private static final Logger logger = LoggerFactory.getLogger(QualityDecayService.class);
    
    // Ideal ranges for cold chain produce
    private static final double IDEAL_TEMP_MIN = 4.0;  // °C
    private static final double IDEAL_TEMP_MAX = 8.0;  // °C
    private static final double IDEAL_HUMIDITY_MIN = 70.0;  // %
    private static final double IDEAL_HUMIDITY_MAX = 85.0;  // %
    
    // Decay factors per hour outside ideal range
    private static final double BASE_DECAY_PER_HOUR = 0.5;  // Base decay in ideal conditions
    private static final double TEMP_PENALTY_PER_DEGREE = 0.3;  // Additional decay per degree outside range
    private static final double HUMIDITY_PENALTY_PER_PERCENT = 0.1;  // Additional decay per % outside range
    
    /**
     * Environmental reading at a specific time.
     */
    public static class EnvironmentalReading {
        private final long timestamp;
        private final double temperature;  // °C
        private final double humidity;     // %
        
        public EnvironmentalReading(long timestamp, double temperature, double humidity) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.humidity = humidity;
        }
        
        public long getTimestamp() { return timestamp; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
    }
    
    /**
     * Quality trace point with timestamp and quality score.
     */
    public static class QualityTracePoint {
        private final long timestamp;
        private final double quality;
        private final double temperature;
        private final double humidity;
        private final double decayRate;
        
        public QualityTracePoint(long timestamp, double quality, double temperature, 
                                double humidity, double decayRate) {
            this.timestamp = timestamp;
            this.quality = quality;
            this.temperature = temperature;
            this.humidity = humidity;
            this.decayRate = decayRate;
        }
        
        public long getTimestamp() { return timestamp; }
        public double getQuality() { return quality; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
        public double getDecayRate() { return decayRate; }
        
        @Override
        public String toString() {
            return String.format("QualityTrace{time=%d, quality=%.2f, temp=%.1f°C, humidity=%.1f%%, decay=%.2f}",
                    timestamp, quality, temperature, humidity, decayRate);
        }
    }
    
    /**
     * Calculate current quality based on initial quality and time under given conditions.
     */
    public double calculateQuality(double initialQuality, double temperature, double humidity, 
                                  double hoursElapsed) {
        if (initialQuality < 0 || initialQuality > 100) {
            throw new IllegalArgumentException("Initial quality must be between 0 and 100");
        }
        
        if (hoursElapsed < 0) {
            throw new IllegalArgumentException("Hours elapsed cannot be negative");
        }
        
        double decayRate = calculateDecayRate(temperature, humidity);
        double quality = initialQuality - (decayRate * hoursElapsed);
        
        return Math.max(0, Math.min(100, quality));
    }
    
    /**
     * Calculate decay rate per hour based on temperature and humidity.
     */
    public double calculateDecayRate(double temperature, double humidity) {
        double tempPenalty = calculateTemperaturePenalty(temperature);
        double humidityPenalty = calculateHumidityPenalty(humidity);
        
        return BASE_DECAY_PER_HOUR + tempPenalty + humidityPenalty;
    }
    
    /**
     * Calculate temperature penalty (additional decay per hour).
     */
    private double calculateTemperaturePenalty(double temperature) {
        if (temperature >= IDEAL_TEMP_MIN && temperature <= IDEAL_TEMP_MAX) {
            return 0.0;  // No penalty in ideal range
        }
        
        double deviation;
        if (temperature < IDEAL_TEMP_MIN) {
            deviation = IDEAL_TEMP_MIN - temperature;
        } else {
            deviation = temperature - IDEAL_TEMP_MAX;
        }
        
        return deviation * TEMP_PENALTY_PER_DEGREE;
    }
    
    /**
     * Calculate humidity penalty (additional decay per hour).
     */
    private double calculateHumidityPenalty(double humidity) {
        if (humidity >= IDEAL_HUMIDITY_MIN && humidity <= IDEAL_HUMIDITY_MAX) {
            return 0.0;  // No penalty in ideal range
        }
        
        double deviation;
        if (humidity < IDEAL_HUMIDITY_MIN) {
            deviation = IDEAL_HUMIDITY_MIN - humidity;
        } else {
            deviation = humidity - IDEAL_HUMIDITY_MAX;
        }
        
        return deviation * HUMIDITY_PENALTY_PER_PERCENT;
    }
    
    /**
     * Simulate quality degradation over a route with varying environmental conditions.
     */
    public List<QualityTracePoint> simulateQualityTrace(double initialQuality, 
                                                       List<EnvironmentalReading> readings) {
        if (readings == null || readings.isEmpty()) {
            throw new IllegalArgumentException("Environmental readings cannot be empty");
        }
        
        List<QualityTracePoint> trace = new ArrayList<>();
        double currentQuality = initialQuality;
        
        // Sort readings by timestamp
        readings.sort((r1, r2) -> Long.compare(r1.timestamp, r2.timestamp));
        
        // Add initial point
        EnvironmentalReading firstReading = readings.get(0);
        double initialDecayRate = calculateDecayRate(firstReading.temperature, firstReading.humidity);
        trace.add(new QualityTracePoint(firstReading.timestamp, currentQuality, 
                 firstReading.temperature, firstReading.humidity, initialDecayRate));
        
        // Process each subsequent reading
        for (int i = 1; i < readings.size(); i++) {
            EnvironmentalReading prevReading = readings.get(i - 1);
            EnvironmentalReading currentReading = readings.get(i);
            
            // Calculate hours elapsed
            double hoursElapsed = (currentReading.timestamp - prevReading.timestamp) / (1000.0 * 60 * 60);
            
            // Calculate decay rate for this period (use previous reading conditions)
            double decayRate = calculateDecayRate(prevReading.temperature, prevReading.humidity);
            
            // Apply decay
            currentQuality = currentQuality - (decayRate * hoursElapsed);
            currentQuality = Math.max(0, Math.min(100, currentQuality));
            
            // Add trace point
            trace.add(new QualityTracePoint(currentReading.timestamp, currentQuality,
                     currentReading.temperature, currentReading.humidity, decayRate));
        }
        
        logger.info(String.format("Quality trace simulated: %d points, final quality: %.2f", 
                   trace.size(), currentQuality));
        
        return trace;
    }
    
    /**
     * Predict quality at a future time given current conditions.
     */
    public double predictQuality(double currentQuality, double temperature, double humidity,
                                double hoursInFuture) {
        return calculateQuality(currentQuality, temperature, humidity, hoursInFuture);
    }
    
    /**
     * Get ideal temperature range.
     */
    public static double[] getIdealTemperatureRange() {
        return new double[]{IDEAL_TEMP_MIN, IDEAL_TEMP_MAX};
    }
    
    /**
     * Get ideal humidity range.
     */
    public static double[] getIdealHumidityRange() {
        return new double[]{IDEAL_HUMIDITY_MIN, IDEAL_HUMIDITY_MAX};
    }
    
    /**
     * Check if temperature is in ideal range.
     */
    public boolean isTemperatureIdeal(double temperature) {
        return temperature >= IDEAL_TEMP_MIN && temperature <= IDEAL_TEMP_MAX;
    }
    
    /**
     * Check if humidity is in ideal range.
     */
    public boolean isHumidityIdeal(double humidity) {
        return humidity >= IDEAL_HUMIDITY_MIN && humidity <= IDEAL_HUMIDITY_MAX;
    }
}
