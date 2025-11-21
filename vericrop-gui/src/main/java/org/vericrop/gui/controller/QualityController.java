package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.service.QualityDecayService;
import org.vericrop.service.QualityDecayService.EnvironmentalReading;
import org.vericrop.service.QualityDecayService.QualityTracePoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for quality decay predictions and simulations.
 */
@RestController
@RequestMapping("/api/v1/quality")
@CrossOrigin(origins = "*")
public class QualityController {
    private static final Logger logger = LoggerFactory.getLogger(QualityController.class);
    
    private final QualityDecayService qualityDecayService;
    
    public QualityController() {
        this.qualityDecayService = new QualityDecayService();
        logger.info("QualityController initialized");
    }
    
    /**
     * POST /api/v1/quality/predict
     * Predict quality at a future time given current conditions.
     */
    @PostMapping("/predict")
    public ResponseEntity<Map<String, Object>> predictQuality(@RequestBody Map<String, Object> request) {
        try {
            double currentQuality = ((Number) request.get("current_quality")).doubleValue();
            double temperature = ((Number) request.get("temperature")).doubleValue();
            double humidity = ((Number) request.get("humidity")).doubleValue();
            double hoursInFuture = ((Number) request.get("hours_in_future")).doubleValue();
            
            double predictedQuality = qualityDecayService.predictQuality(
                currentQuality, temperature, humidity, hoursInFuture);
            
            double decayRate = qualityDecayService.calculateDecayRate(temperature, humidity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("current_quality", currentQuality);
            response.put("predicted_quality", predictedQuality);
            response.put("decay_rate", decayRate);
            response.put("hours_in_future", hoursInFuture);
            response.put("temperature", temperature);
            response.put("humidity", humidity);
            response.put("temperature_ideal", qualityDecayService.isTemperatureIdeal(temperature));
            response.put("humidity_ideal", qualityDecayService.isHumidityIdeal(humidity));
            
            logger.info("Quality prediction: {} -> {} over {} hours", 
                       String.format("%.2f", currentQuality), String.format("%.2f", predictedQuality), String.format("%.1f", hoursInFuture));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to predict quality", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * POST /api/v1/quality/simulate
     * Simulate quality degradation over a route with environmental readings.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateQuality(@RequestBody Map<String, Object> request) {
        try {
            double initialQuality = ((Number) request.get("initial_quality")).doubleValue();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> readingsData = (List<Map<String, Object>>) request.get("readings");
            
            List<EnvironmentalReading> readings = new ArrayList<>();
            for (Map<String, Object> reading : readingsData) {
                long timestamp = ((Number) reading.get("timestamp")).longValue();
                double temp = ((Number) reading.get("temperature")).doubleValue();
                double humidity = ((Number) reading.get("humidity")).doubleValue();
                readings.add(new EnvironmentalReading(timestamp, temp, humidity));
            }
            
            List<QualityTracePoint> trace = qualityDecayService.simulateQualityTrace(
                initialQuality, readings);
            
            // Convert trace to response format
            List<Map<String, Object>> traceData = new ArrayList<>();
            for (QualityTracePoint point : trace) {
                Map<String, Object> pointData = new HashMap<>();
                pointData.put("timestamp", point.getTimestamp());
                pointData.put("quality", point.getQuality());
                pointData.put("temperature", point.getTemperature());
                pointData.put("humidity", point.getHumidity());
                pointData.put("decay_rate", point.getDecayRate());
                traceData.add(pointData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("initial_quality", initialQuality);
            response.put("final_quality", trace.get(trace.size() - 1).getQuality());
            response.put("trace", traceData);
            response.put("num_points", trace.size());
            
            logger.info("Quality simulation: {} -> {} over {} readings", 
                       String.format("%.2f", initialQuality), String.format("%.2f", trace.get(trace.size() - 1).getQuality()), readings.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to simulate quality", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/quality/decay-rate
     * Calculate decay rate for given conditions.
     */
    @GetMapping("/decay-rate")
    public ResponseEntity<Map<String, Object>> getDecayRate(
            @RequestParam double temperature,
            @RequestParam double humidity) {
        
        try {
            double decayRate = qualityDecayService.calculateDecayRate(temperature, humidity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("temperature", temperature);
            response.put("humidity", humidity);
            response.put("decay_rate", decayRate);
            response.put("temperature_ideal", qualityDecayService.isTemperatureIdeal(temperature));
            response.put("humidity_ideal", qualityDecayService.isHumidityIdeal(humidity));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to calculate decay rate", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/quality/ideal-ranges
     * Get ideal temperature and humidity ranges.
     */
    @GetMapping("/ideal-ranges")
    public ResponseEntity<Map<String, Object>> getIdealRanges() {
        double[] tempRange = QualityDecayService.getIdealTemperatureRange();
        double[] humidityRange = QualityDecayService.getIdealHumidityRange();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("temperature_range", Map.of(
            "min", tempRange[0],
            "max", tempRange[1],
            "unit", "°C"
        ));
        response.put("humidity_range", Map.of(
            "min", humidityRange[0],
            "max", humidityRange[1],
            "unit", "%"
        ));
        
        return ResponseEntity.ok(response);
    }
}
