package org.vericrop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Scenario;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for loading scenario definitions from JSON files.
 * Loads scenarios from classpath resources (vericrop-gui/src/main/resources/scenarios/*.json)
 * and maps them to Scenario enum values.
 * 
 * Resilient to missing files - logs warnings and continues with defaults.
 */
public class ScenarioLoader {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Temperature mapping thresholds for scenario inference
    private static final double HOT_TRANSPORT_THRESHOLD = 7.0; // °C
    private static final double COLD_STORAGE_THRESHOLD = 0.0; // °C
    
    private final Map<String, ScenarioDefinition> loadedScenarios;
    
    /**
     * Scenario definition loaded from JSON.
     */
    public static class ScenarioDefinition {
        private String id;
        private String description;
        private TemperatureRange target;
        private int durationMinutes;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public TemperatureRange getTarget() { return target; }
        public void setTarget(TemperatureRange target) { this.target = target; }
        
        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    }
    
    /**
     * Temperature range definition.
     */
    public static class TemperatureRange {
        private double min;
        private double max;
        
        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
    }
    
    /**
     * Constructor - loads scenarios from classpath.
     */
    public ScenarioLoader() {
        this.loadedScenarios = new HashMap<>();
        loadScenarios();
    }
    
    /**
     * Load scenario JSON files from classpath.
     */
    private void loadScenarios() {
        logger.info("Loading scenario definitions from classpath...");
        
        // Try to load scenario files
        String[] scenarioFiles = {
            "/scenarios/example-01.json",
            "/scenarios/example-02.json",
            "/scenarios/example-03.json"
        };
        
        for (String scenarioFile : scenarioFiles) {
            try (InputStream is = getClass().getResourceAsStream(scenarioFile)) {
                if (is != null) {
                    JsonNode root = objectMapper.readTree(is);
                    ScenarioDefinition def = objectMapper.treeToValue(root, ScenarioDefinition.class);
                    loadedScenarios.put(def.getId(), def);
                    logger.info("Loaded scenario: {} - {}", def.getId(), def.getDescription());
                } else {
                    logger.warn("Scenario file not found: {} (continuing without it)", scenarioFile);
                }
            } catch (Exception e) {
                logger.warn("Failed to load scenario file {}: {} (continuing)", scenarioFile, e.getMessage());
            }
        }
        
        logger.info("Loaded {} scenario definitions", loadedScenarios.size());
    }
    
    /**
     * Get a scenario definition by ID.
     * Returns null if not found.
     */
    public ScenarioDefinition getScenarioDefinition(String scenarioId) {
        return loadedScenarios.get(scenarioId);
    }
    
    /**
     * Map a scenario ID to a Scenario enum value.
     * Uses heuristics to match JSON scenario IDs to enum values.
     * Returns NORMAL as fallback.
     */
    public Scenario mapToScenarioEnum(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return Scenario.NORMAL;
        }
        
        // Try direct enum match
        try {
            return Scenario.valueOf(scenarioId.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            // Not a direct match, try heuristics
        }
        
        // Check scenario definition for hints
        ScenarioDefinition def = loadedScenarios.get(scenarioId);
        if (def != null && def.getTarget() != null) {
            double avgTemp = (def.getTarget().getMin() + def.getTarget().getMax()) / 2.0;
            
            // Map temperature ranges to scenarios
            if (avgTemp < COLD_STORAGE_THRESHOLD) {
                logger.debug("Mapping scenario {} to COLD_STORAGE (avg temp: {})", scenarioId, avgTemp);
                return Scenario.COLD_STORAGE;
            } else if (avgTemp > HOT_TRANSPORT_THRESHOLD) {
                logger.debug("Mapping scenario {} to HOT_TRANSPORT (avg temp: {})", scenarioId, avgTemp);
                return Scenario.HOT_TRANSPORT;
            }
        }
        
        // Default to NORMAL
        logger.debug("Using NORMAL scenario for ID: {}", scenarioId);
        return Scenario.NORMAL;
    }
    
    /**
     * Get all loaded scenario IDs.
     */
    public Map<String, ScenarioDefinition> getAllScenarios() {
        return new HashMap<>(loadedScenarios);
    }
}
