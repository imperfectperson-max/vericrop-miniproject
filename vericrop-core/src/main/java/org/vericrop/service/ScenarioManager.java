package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Scenario;

import java.util.*;

/**
 * Unified manager for scenario selection and configuration.
 * Consolidates the three existing scenarios (example-01, example-02, example-03)
 * and provides an API to select and configure scenarios for simulations.
 */
public class ScenarioManager {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioManager.class);
    
    // Scenario ID to Scenario enum mapping
    private static final Map<String, Scenario> SCENARIO_MAPPING = new HashMap<>();
    
    static {
        // Map JSON scenario IDs to Scenario enum values based on their temperature profiles
        // scenario-01: target 2-5°C (avg 3.5°C) -> NORMAL (within ideal range)
        // scenario-02: target 1-4°C (avg 2.5°C) -> COLD_STORAGE (below normal, strict)
        // scenario-03: target 0-6°C (avg 3.0°C) with spikes to 9°C -> HOT_TRANSPORT (has hot spikes)
        SCENARIO_MAPPING.put("scenario-01", Scenario.NORMAL);       // Normal cold-chain (2-5°C)
        SCENARIO_MAPPING.put("scenario-02", Scenario.COLD_STORAGE); // Strict cold-chain (1-4°C, colder)
        SCENARIO_MAPPING.put("scenario-03", Scenario.HOT_TRANSPORT); // High-risk with temp spikes (0-6°C + spikes)
        
        // Also support direct enum names (case insensitive via getScenario)
        SCENARIO_MAPPING.put("NORMAL", Scenario.NORMAL);
        SCENARIO_MAPPING.put("HOT_TRANSPORT", Scenario.HOT_TRANSPORT);
        SCENARIO_MAPPING.put("COLD_STORAGE", Scenario.COLD_STORAGE);
        SCENARIO_MAPPING.put("HUMID_ROUTE", Scenario.HUMID_ROUTE);
        SCENARIO_MAPPING.put("EXTREME_DELAY", Scenario.EXTREME_DELAY);
        
        // Backward compatibility aliases (match scenario-XX patterns)
        SCENARIO_MAPPING.put("example-01", Scenario.NORMAL);
        SCENARIO_MAPPING.put("example-02", Scenario.COLD_STORAGE);
        SCENARIO_MAPPING.put("example-03", Scenario.HOT_TRANSPORT);
    }
    
    /**
     * Configuration for a scenario including initial map setup and parameters.
     */
    public static class ScenarioConfig {
        private final Scenario scenario;
        private final String scenarioId;
        private final String description;
        private final Map<String, Object> parameters;
        private final Map<String, Object> initialMapSetup;
        
        public ScenarioConfig(Scenario scenario, String scenarioId, String description) {
            this.scenario = scenario;
            this.scenarioId = scenarioId;
            this.description = description;
            this.parameters = new HashMap<>();
            this.initialMapSetup = new HashMap<>();
            initializeDefaults();
        }
        
        private void initializeDefaults() {
            // Set scenario-specific defaults
            parameters.put("temperature_drift", scenario.getTemperatureDrift());
            parameters.put("humidity_drift", scenario.getHumidityDrift());
            parameters.put("speed_multiplier", scenario.getSpeedMultiplier());
            parameters.put("spoilage_rate", scenario.getSpoilageRate());
            
            // Set map setup defaults based on scenario
            initialMapSetup.put("producer_count", 1);
            initialMapSetup.put("consumer_count", 1);
            initialMapSetup.put("warehouse_count", 1);
            
            // Adjust based on scenario type
            switch (scenario) {
                case EXTREME_DELAY:
                    initialMapSetup.put("route_complexity", "high");
                    initialMapSetup.put("waypoints_multiplier", 1.5);
                    break;
                case HOT_TRANSPORT:
                    initialMapSetup.put("temperature_monitoring", "critical");
                    initialMapSetup.put("alert_threshold_low", true);
                    break;
                case COLD_STORAGE:
                    initialMapSetup.put("temperature_monitoring", "strict");
                    initialMapSetup.put("cold_chain_required", true);
                    break;
                case HUMID_ROUTE:
                    initialMapSetup.put("humidity_monitoring", "enabled");
                    initialMapSetup.put("ventilation_points", 3);
                    break;
                case NORMAL:
                default:
                    initialMapSetup.put("route_complexity", "normal");
                    break;
            }
        }
        
        public Scenario getScenario() { return scenario; }
        public String getScenarioId() { return scenarioId; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        public Map<String, Object> getInitialMapSetup() { return new HashMap<>(initialMapSetup); }
        
        public void setParameter(String key, Object value) {
            parameters.put(key, value);
        }
        
        public void setMapSetup(String key, Object value) {
            initialMapSetup.put(key, value);
        }
    }
    
    private final ScenarioLoader scenarioLoader;
    private final Map<String, ScenarioConfig> configCache;
    
    /**
     * Create a new ScenarioManager.
     */
    public ScenarioManager() {
        this.scenarioLoader = new ScenarioLoader();
        this.configCache = new HashMap<>();
        logger.info("ScenarioManager initialized with {} scenario definitions", 
                   scenarioLoader.getAllScenarios().size());
    }
    
    /**
     * Get a scenario by ID or name. Returns NORMAL as default if not found.
     * 
     * @param scenarioId Scenario ID (e.g., "scenario-01", "NORMAL", "example-01")
     * @return Scenario enum value (never null, defaults to NORMAL)
     */
    public Scenario getScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            return Scenario.NORMAL; // Default
        }
        
        // Try direct mapping with original casing first (for JSON scenario IDs like "scenario-01")
        Scenario scenario = SCENARIO_MAPPING.get(scenarioId);
        if (scenario != null) {
            return scenario;
        }
        
        // Try uppercase mapping for enum names (NORMAL, HOT_TRANSPORT, etc.)
        scenario = SCENARIO_MAPPING.get(scenarioId.toUpperCase());
        if (scenario != null) {
            return scenario;
        }
        
        // Try using ScenarioLoader for custom scenarios (uses heuristics)
        scenario = scenarioLoader.mapToScenarioEnum(scenarioId);
        if (scenario != null && scenario != Scenario.NORMAL) {
            // Only accept ScenarioLoader result if it's not the default fallback
            return scenario;
        }
        
        logger.warn("Unknown scenario ID: {}, defaulting to NORMAL", scenarioId);
        return Scenario.NORMAL;
    }
    
    /**
     * Get a full scenario configuration by ID.
     * Returns a cached or newly created configuration with all parameters.
     * 
     * @param scenarioId Scenario ID
     * @return ScenarioConfig with all parameters and setup
     */
    public ScenarioConfig getScenarioConfig(String scenarioId) {
        // Check cache first
        if (configCache.containsKey(scenarioId)) {
            return configCache.get(scenarioId);
        }
        
        // Create new config
        Scenario scenario = getScenario(scenarioId);
        String description = getScenarioDescription(scenarioId);
        ScenarioConfig config = new ScenarioConfig(scenario, scenarioId, description);
        
        // Load additional parameters from JSON definition if available
        ScenarioLoader.ScenarioDefinition def = scenarioLoader.getScenarioDefinition(scenarioId);
        if (def != null) {
            config.setParameter("duration_minutes", def.getDurationMinutes());
            if (def.getTarget() != null) {
                config.setParameter("target_temp_min", def.getTarget().getMin());
                config.setParameter("target_temp_max", def.getTarget().getMax());
            }
        }
        
        // Cache and return
        configCache.put(scenarioId, config);
        return config;
    }
    
    /**
     * Get description for a scenario.
     */
    private String getScenarioDescription(String scenarioId) {
        ScenarioLoader.ScenarioDefinition def = scenarioLoader.getScenarioDefinition(scenarioId);
        if (def != null && def.getDescription() != null) {
            return def.getDescription();
        }
        
        Scenario scenario = getScenario(scenarioId);
        return scenario != null ? scenario.getDisplayName() : "Unknown scenario";
    }
    
    /**
     * Get all available scenario IDs.
     * 
     * @return List of all scenario IDs
     */
    public List<String> getAvailableScenarios() {
        Set<String> allScenarios = new HashSet<>();
        
        // Add mapped scenarios
        allScenarios.addAll(SCENARIO_MAPPING.keySet());
        
        // Add loaded scenarios
        allScenarios.addAll(scenarioLoader.getAllScenarios().keySet());
        
        List<String> result = new ArrayList<>(allScenarios);
        Collections.sort(result);
        return result;
    }
    
    /**
     * Get all available scenarios as enum values.
     * 
     * @return List of all Scenario enum values
     */
    public List<Scenario> getAvailableScenarioEnums() {
        return Arrays.asList(Scenario.values());
    }
    
    /**
     * Select scenario by ID and apply to MapSimulator.
     * 
     * @param scenarioId Scenario ID
     * @param mapSimulator MapSimulator instance to initialize
     * @param batchId Batch ID for the simulation
     * @param numWaypoints Number of waypoints in the route
     */
    public void applyScenarioToMap(String scenarioId, MapSimulator mapSimulator, 
                                   String batchId, int numWaypoints) {
        ScenarioConfig config = getScenarioConfig(scenarioId);
        
        logger.info("Applying scenario {} to map for batch {}", scenarioId, batchId);
        
        // Initialize map with scenario
        mapSimulator.initializeForScenario(config.getScenario(), batchId, numWaypoints);
        
        logger.info("Scenario {} applied successfully: {}", 
                   scenarioId, config.getDescription());
    }
    
    /**
     * Get default scenario ID.
     * 
     * @return Default scenario ID
     */
    public String getDefaultScenarioId() {
        return "scenario-01"; // Normal cold-chain
    }
    
    /**
     * Validate if a scenario ID is valid.
     * 
     * @param scenarioId Scenario ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            return false;
        }
        
        // Check both uppercase (enum names) and original casing (JSON IDs)
        return SCENARIO_MAPPING.containsKey(scenarioId.toUpperCase()) ||
               SCENARIO_MAPPING.containsKey(scenarioId) ||
               scenarioLoader.getScenarioDefinition(scenarioId) != null;
    }
    
    /**
     * Get scenario information as a map for API responses.
     * 
     * @param scenarioId Scenario ID
     * @return Map with scenario information
     */
    public Map<String, Object> getScenarioInfo(String scenarioId) {
        ScenarioConfig config = getScenarioConfig(scenarioId);
        
        Map<String, Object> info = new HashMap<>();
        info.put("scenario_id", config.getScenarioId());
        info.put("scenario_name", config.getScenario().name());
        info.put("display_name", config.getScenario().getDisplayName());
        info.put("description", config.getDescription());
        info.put("parameters", config.getParameters());
        info.put("initial_map_setup", config.getInitialMapSetup());
        
        return info;
    }
    
    /**
     * Get information for all available scenarios.
     * 
     * @return List of scenario information maps
     */
    public List<Map<String, Object>> getAllScenarioInfo() {
        List<Map<String, Object>> allInfo = new ArrayList<>();
        
        for (String scenarioId : getAvailableScenarios()) {
            // Only include primary scenario IDs (not aliases)
            if (scenarioId.startsWith("scenario-") || scenarioId.startsWith("example-")) {
                allInfo.add(getScenarioInfo(scenarioId));
            }
        }
        
        return allInfo;
    }
}
