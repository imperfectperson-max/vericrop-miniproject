package org.vericrop.service.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.Scenario;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading simulation configurations from JSON files.
 * Validates required fields and provides access to simulation parameters.
 */
public class SimulationLoader {
    private static final Logger logger = LoggerFactory.getLogger(SimulationLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Loaded simulation definition with all parameters.
     */
    public static class SimulationDefinition {
        private String id;
        private String name;
        private String description;
        private int durationMinutes;
        private double timeScale;
        private GeoCoordinate origin;
        private GeoCoordinate warehouse;
        private GeoCoordinate destination;
        private String productType;
        private String batchPrefix;
        private double initialQuality;
        private TemperatureConfig temperature;
        private HumidityConfig humidity;
        private int waypointsPerSegment;
        private double speedKmh;
        private Scenario scenario;
        private List<TemperatureEvent> temperatureEvents;
        private String notes;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
        
        public double getTimeScale() { return timeScale; }
        public void setTimeScale(double timeScale) { this.timeScale = timeScale; }
        
        public GeoCoordinate getOrigin() { return origin; }
        public void setOrigin(GeoCoordinate origin) { this.origin = origin; }
        
        public GeoCoordinate getWarehouse() { return warehouse; }
        public void setWarehouse(GeoCoordinate warehouse) { this.warehouse = warehouse; }
        
        public GeoCoordinate getDestination() { return destination; }
        public void setDestination(GeoCoordinate destination) { this.destination = destination; }
        
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        
        public String getBatchPrefix() { return batchPrefix; }
        public void setBatchPrefix(String batchPrefix) { this.batchPrefix = batchPrefix; }
        
        public double getInitialQuality() { return initialQuality; }
        public void setInitialQuality(double initialQuality) { this.initialQuality = initialQuality; }
        
        public TemperatureConfig getTemperature() { return temperature; }
        public void setTemperature(TemperatureConfig temperature) { this.temperature = temperature; }
        
        public HumidityConfig getHumidity() { return humidity; }
        public void setHumidity(HumidityConfig humidity) { this.humidity = humidity; }
        
        public int getWaypointsPerSegment() { return waypointsPerSegment; }
        public void setWaypointsPerSegment(int waypointsPerSegment) { this.waypointsPerSegment = waypointsPerSegment; }
        
        public double getSpeedKmh() { return speedKmh; }
        public void setSpeedKmh(double speedKmh) { this.speedKmh = speedKmh; }
        
        public Scenario getScenario() { return scenario; }
        public void setScenario(Scenario scenario) { this.scenario = scenario; }
        
        public List<TemperatureEvent> getTemperatureEvents() { return temperatureEvents; }
        public void setTemperatureEvents(List<TemperatureEvent> temperatureEvents) { this.temperatureEvents = temperatureEvents; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        
        /**
         * Check if this simulation has a warehouse stop.
         */
        public boolean hasWarehouse() {
            return warehouse != null;
        }
        
        /**
         * Generate a unique batch ID for this simulation.
         * Uses UUID to prevent collisions from rapid batch creation.
         */
        public String generateBatchId() {
            String prefix = batchPrefix != null ? batchPrefix : "BATCH";
            String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return prefix + "_" + uniqueId;
        }
        
        /**
         * Create a SimulationConfig from this definition.
         */
        public SimulationConfig toSimulationConfig() {
            return SimulationConfig.builder()
                .simulationDurationMs(durationMinutes * 60_000L)
                .timeScale(timeScale > 0 ? timeScale : SimulationConfig.DEFAULT_TIME_SCALE)
                .waypointsPerSegment(waypointsPerSegment > 0 ? waypointsPerSegment : SimulationConfig.DEFAULT_WAYPOINTS_PER_SEGMENT)
                .build();
        }
    }
    
    /**
     * Temperature configuration from JSON.
     */
    public static class TemperatureConfig {
        private double target;
        private double minAllowed;
        private double maxAllowed;
        private double variance;
        
        public double getTarget() { return target; }
        public void setTarget(double target) { this.target = target; }
        
        public double getMinAllowed() { return minAllowed; }
        public void setMinAllowed(double minAllowed) { this.minAllowed = minAllowed; }
        
        public double getMaxAllowed() { return maxAllowed; }
        public void setMaxAllowed(double maxAllowed) { this.maxAllowed = maxAllowed; }
        
        public double getVariance() { return variance; }
        public void setVariance(double variance) { this.variance = variance; }
    }
    
    /**
     * Humidity configuration from JSON.
     */
    public static class HumidityConfig {
        private double target;
        private double minAllowed;
        private double maxAllowed;
        private double variance;
        
        public double getTarget() { return target; }
        public void setTarget(double target) { this.target = target; }
        
        public double getMinAllowed() { return minAllowed; }
        public void setMinAllowed(double minAllowed) { this.minAllowed = minAllowed; }
        
        public double getMaxAllowed() { return maxAllowed; }
        public void setMaxAllowed(double maxAllowed) { this.maxAllowed = maxAllowed; }
        
        public double getVariance() { return variance; }
        public void setVariance(double variance) { this.variance = variance; }
    }
    
    /**
     * Temperature event (spike) from JSON.
     */
    public static class TemperatureEvent {
        private int atMinute;
        private int durationMinutes;
        private double temperature;
        private String description;
        
        public int getAtMinute() { return atMinute; }
        public void setAtMinute(int atMinute) { this.atMinute = atMinute; }
        
        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * Load a simulation definition from a resource path.
     * @param resourcePath path relative to classpath (e.g., "/simulations/example_1_farmer_to_consumer.json")
     * @return loaded simulation definition
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if required fields are missing
     */
    public static SimulationDefinition loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = SimulationLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return loadFromInputStream(is, resourcePath);
        }
    }
    
    /**
     * Load a simulation definition from an input stream.
     */
    public static SimulationDefinition loadFromInputStream(InputStream is, String sourceName) throws IOException {
        JsonNode root = objectMapper.readTree(is);
        return parseDefinition(root, sourceName);
    }
    
    /**
     * Load a simulation definition from a JSON string.
     */
    public static SimulationDefinition loadFromString(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        return parseDefinition(root, "inline");
    }
    
    /**
     * Parse a simulation definition from a JSON node.
     */
    private static SimulationDefinition parseDefinition(JsonNode root, String sourceName) {
        SimulationDefinition def = new SimulationDefinition();
        
        // Required fields
        def.setId(getRequiredString(root, "id", sourceName));
        def.setName(getRequiredString(root, "name", sourceName));
        
        // Optional fields with defaults
        def.setDescription(getOptionalString(root, "description", ""));
        def.setDurationMinutes(getOptionalInt(root, "durationMinutes", 30));
        def.setTimeScale(getOptionalDouble(root, "timeScale", SimulationConfig.DEFAULT_TIME_SCALE));
        def.setProductType(getOptionalString(root, "productType", "Mixed Produce"));
        def.setBatchPrefix(getOptionalString(root, "batchPrefix", "BATCH"));
        def.setInitialQuality(getOptionalDouble(root, "initialQuality", 95.0));
        def.setWaypointsPerSegment(getOptionalInt(root, "waypointsPerSegment", 15));
        def.setSpeedKmh(getOptionalDouble(root, "speedKmh", 50.0));
        def.setNotes(getOptionalString(root, "notes", ""));
        
        // Parse coordinates
        def.setOrigin(parseCoordinate(root.get("origin"), "origin", sourceName));
        def.setDestination(parseCoordinate(root.get("destination"), "destination", sourceName));
        
        // Warehouse is optional
        JsonNode warehouseNode = root.get("warehouse");
        if (warehouseNode != null && !warehouseNode.isNull()) {
            def.setWarehouse(parseCoordinate(warehouseNode, "warehouse", sourceName));
        }
        
        // Parse temperature config
        JsonNode tempNode = root.get("temperature");
        if (tempNode != null) {
            def.setTemperature(parseTemperatureConfig(tempNode));
        }
        
        // Parse humidity config
        JsonNode humidityNode = root.get("humidity");
        if (humidityNode != null) {
            def.setHumidity(parseHumidityConfig(humidityNode));
        }
        
        // Parse scenario
        String scenarioStr = getOptionalString(root, "scenario", "NORMAL");
        try {
            def.setScenario(Scenario.valueOf(scenarioStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown scenario '{}', using NORMAL", scenarioStr);
            def.setScenario(Scenario.NORMAL);
        }
        
        // Parse temperature events
        JsonNode eventsNode = root.get("temperatureEvents");
        if (eventsNode != null && eventsNode.isArray()) {
            List<TemperatureEvent> events = new ArrayList<>();
            for (JsonNode eventNode : eventsNode) {
                events.add(parseTemperatureEvent(eventNode));
            }
            def.setTemperatureEvents(events);
        }
        
        logger.info("Loaded simulation definition: {} ({})", def.getName(), def.getId());
        return def;
    }
    
    private static GeoCoordinate parseCoordinate(JsonNode node, String fieldName, String sourceName) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required field '" + fieldName + "' in " + sourceName);
        }
        
        double lat = getRequiredDouble(node, "latitude", sourceName + "." + fieldName);
        double lon = getRequiredDouble(node, "longitude", sourceName + "." + fieldName);
        String name = getOptionalString(node, "name", fieldName);
        
        return new GeoCoordinate(lat, lon, name);
    }
    
    private static TemperatureConfig parseTemperatureConfig(JsonNode node) {
        TemperatureConfig config = new TemperatureConfig();
        config.setTarget(getOptionalDouble(node, "target", 4.0));
        config.setMinAllowed(getOptionalDouble(node, "minAllowed", 2.0));
        config.setMaxAllowed(getOptionalDouble(node, "maxAllowed", 8.0));
        config.setVariance(getOptionalDouble(node, "variance", 1.5));
        return config;
    }
    
    private static HumidityConfig parseHumidityConfig(JsonNode node) {
        HumidityConfig config = new HumidityConfig();
        config.setTarget(getOptionalDouble(node, "target", 75.0));
        config.setMinAllowed(getOptionalDouble(node, "minAllowed", 65.0));
        config.setMaxAllowed(getOptionalDouble(node, "maxAllowed", 85.0));
        config.setVariance(getOptionalDouble(node, "variance", 5.0));
        return config;
    }
    
    private static TemperatureEvent parseTemperatureEvent(JsonNode node) {
        TemperatureEvent event = new TemperatureEvent();
        event.setAtMinute(getOptionalInt(node, "atMinute", 0));
        event.setDurationMinutes(getOptionalInt(node, "durationMinutes", 1));
        event.setTemperature(getOptionalDouble(node, "temperature", 10.0));
        event.setDescription(getOptionalString(node, "description", "Temperature event"));
        return event;
    }
    
    private static String getRequiredString(JsonNode node, String field, String sourceName) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + sourceName);
        }
        return valueNode.asText();
    }
    
    private static double getRequiredDouble(JsonNode node, String field, String sourceName) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + sourceName);
        }
        return valueNode.asDouble();
    }
    
    private static String getOptionalString(JsonNode node, String field, String defaultValue) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asText(defaultValue);
    }
    
    private static int getOptionalInt(JsonNode node, String field, int defaultValue) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asInt(defaultValue);
    }
    
    private static double getOptionalDouble(JsonNode node, String field, double defaultValue) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asDouble(defaultValue);
    }
    
    /**
     * List available simulation definitions from resources.
     */
    public static List<String> listAvailableSimulations() {
        List<String> simulations = new ArrayList<>();
        // 2-minute presentation scenarios (for multi-instance Kafka demos)
        simulations.add("presentation_scenario_1_smooth_delivery");
        simulations.add("presentation_scenario_2_temperature_alert");
        simulations.add("presentation_scenario_3_quality_journey");
        // Demo simulations optimized for 3-4 minute presentations
        simulations.add("demo_short_3min");
        simulations.add("demo_medium_4min");
        simulations.add("demo_full_journey");
        // Original example simulations
        simulations.add("example_1_farmer_to_consumer");
        simulations.add("example_2_producer_local");
        simulations.add("example_3_long_route");
        return simulations;
    }
    
    /**
     * List demo simulation definitions optimized for presentations.
     */
    public static List<String> listDemoSimulations() {
        List<String> demos = new ArrayList<>();
        demos.add("demo_short_3min");
        demos.add("demo_medium_4min");
        demos.add("demo_full_journey");
        return demos;
    }
    
    /**
     * List 2-minute presentation scenarios for multi-instance Kafka demos.
     * These scenarios are designed for running 3 instances (Producer, Logistics, Consumer)
     * with real-time Kafka event streaming across all controllers.
     */
    public static List<String> listPresentationScenarios() {
        List<String> scenarios = new ArrayList<>();
        scenarios.add("presentation_scenario_1_smooth_delivery");
        scenarios.add("presentation_scenario_2_temperature_alert");
        scenarios.add("presentation_scenario_3_quality_journey");
        return scenarios;
    }
    
    /**
     * Load the quick 3-minute demo simulation.
     */
    public static SimulationDefinition loadQuickDemo() throws IOException {
        return loadFromResource("/simulations/demo_short_3min.json");
    }
    
    /**
     * Load the standard 4-minute demo simulation.
     */
    public static SimulationDefinition loadStandardDemo() throws IOException {
        return loadFromResource("/simulations/demo_medium_4min.json");
    }
    
    /**
     * Load the full journey demo simulation.
     */
    public static SimulationDefinition loadFullJourneyDemo() throws IOException {
        return loadFromResource("/simulations/demo_full_journey.json");
    }
    
    /**
     * Load Presentation Scenario 1: Smooth Cold Chain Delivery (2 minutes).
     * Demonstrates baseline functionality with stable temperatures across all controllers.
     */
    public static SimulationDefinition loadPresentationScenario1() throws IOException {
        return loadFromResource("/simulations/presentation_scenario_1_smooth_delivery.json");
    }
    
    /**
     * Load Presentation Scenario 2: Temperature Alert Demo (2 minutes).
     * Demonstrates temperature breach alerts and cold chain monitoring.
     */
    public static SimulationDefinition loadPresentationScenario2() throws IOException {
        return loadFromResource("/simulations/presentation_scenario_2_temperature_alert.json");
    }
    
    /**
     * Load Presentation Scenario 3: Quality Journey Demo (2 minutes).
     * Demonstrates complete product journey with quality tracking and consumer verification.
     */
    public static SimulationDefinition loadPresentationScenario3() throws IOException {
        return loadFromResource("/simulations/presentation_scenario_3_quality_journey.json");
    }
    
    /**
     * Load a presentation scenario by number (1, 2, or 3).
     * 
     * @param scenarioNumber Scenario number (1-3)
     * @return Loaded simulation definition
     * @throws IOException if the scenario file cannot be read
     * @throws IllegalArgumentException if scenario number is invalid
     */
    public static SimulationDefinition loadPresentationScenario(int scenarioNumber) throws IOException {
        switch (scenarioNumber) {
            case 1:
                return loadPresentationScenario1();
            case 2:
                return loadPresentationScenario2();
            case 3:
                return loadPresentationScenario3();
            default:
                throw new IllegalArgumentException("Invalid scenario number: " + scenarioNumber + ". Valid values are 1, 2, or 3.");
        }
    }
    
    /**
     * Load all available simulations.
     */
    public static List<SimulationDefinition> loadAllSimulations() {
        List<SimulationDefinition> definitions = new ArrayList<>();
        for (String simName : listAvailableSimulations()) {
            try {
                definitions.add(loadFromResource("/simulations/" + simName + ".json"));
            } catch (Exception e) {
                logger.warn("Failed to load simulation {}: {}", simName, e.getMessage());
            }
        }
        return definitions;
    }
}
