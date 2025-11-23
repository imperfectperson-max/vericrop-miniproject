package org.vericrop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.Message;
import org.vericrop.service.models.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Comprehensive service for simulating delivery routes with environmental conditions,
 * quality decay, spoilage tracking, alerts, and supplier performance metrics.
 */
public class DeliverySimulator {
    private static final Logger logger = LoggerFactory.getLogger(DeliverySimulator.class);
    private static final int SIMULATOR_THREAD_POOL_SIZE = 10;
    private static final int INITIAL_DELAY_DIVISOR = 10;
    
    // Temperature thresholds for fresh produce (°C)
    private static final double TEMP_MIN = 2.0;
    private static final double TEMP_MAX = 8.0;
    private static final double TEMP_IDEAL = 5.0;
    
    // Humidity thresholds (%)
    private static final double HUMIDITY_MIN = 65.0;
    private static final double HUMIDITY_MAX = 95.0;
    private static final double HUMIDITY_IDEAL = 80.0;
    
    // Quality decay parameters
    private static final double QUALITY_DECAY_PER_HOUR = 0.5;
    private static final double SPOILAGE_THRESHOLD = 50.0;
    
    private final MessageService messageService;
    private final AlertService alertService;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, SimulationState> activeSimulations;
    private final ConcurrentHashMap<String, SupplierData> supplierMetrics;
    private final Random random;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final String airflowHookUrl;
    
    /**
     * Internal state for an active simulation.
     */
    private static class SimulationState {
        final String batchId;
        final String farmerId;
        final List<org.vericrop.service.models.RouteWaypoint> route;
        final Scenario scenario;
        final long startTime;
        final long expectedEndTime;
        int currentWaypointIndex;
        boolean running;
        ScheduledFuture<?> task;
        double currentQualityScore;
        double spoilageProbability;
        List<Alert> alerts;
        List<DeliveryReport.TimelineEvent> timeline;
        
        SimulationState(String batchId, String farmerId, List<org.vericrop.service.models.RouteWaypoint> route,
                       Scenario scenario, long expectedEndTime) {
            this.batchId = batchId;
            this.farmerId = farmerId != null ? farmerId : "UNKNOWN";
            this.route = route;
            this.scenario = scenario;
            this.startTime = System.currentTimeMillis();
            this.expectedEndTime = expectedEndTime;
            this.currentWaypointIndex = 0;
            this.running = false;
            this.currentQualityScore = 100.0;
            this.spoilageProbability = 0.0;
            this.alerts = new CopyOnWriteArrayList<>();
            this.timeline = new CopyOnWriteArrayList<>();
        }
    }
    
    /**
     * Supplier performance data.
     */
    private static class SupplierData {
        final String farmerId;
        int totalDeliveries;
        int successfulDeliveries;
        int spoiledShipments;
        double totalQualityDecay;
        double totalDeliveryTime;
        
        SupplierData(String farmerId) {
            this.farmerId = farmerId;
        }
        
        void recordDelivery(double qualityDecay, long deliveryTime, boolean spoiled) {
            totalDeliveries++;
            if (!spoiled) {
                successfulDeliveries++;
            } else {
                spoiledShipments++;
            }
            totalQualityDecay += qualityDecay;
            totalDeliveryTime += deliveryTime;
        }
    }
    
    /**
     * Constructor with dependencies.
     */
    public DeliverySimulator(MessageService messageService, AlertService alertService) {
        this.messageService = messageService;
        this.alertService = alertService != null ? alertService : new AlertService();
        this.executor = Executors.newScheduledThreadPool(SIMULATOR_THREAD_POOL_SIZE);
        this.activeSimulations = new ConcurrentHashMap<>();
        this.supplierMetrics = new ConcurrentHashMap<>();
        
        String seedProp = System.getProperty("vericrop.sim.seed");
        this.random = seedProp != null ? new Random(Long.parseLong(seedProp)) : new Random();
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        
        this.airflowHookUrl = System.getProperty("vericrop.airflow.hook");
        
        logger.info("DeliverySimulator initialized");
    }
    
    /**
     * Constructor for backwards compatibility.
     */
    public DeliverySimulator(MessageService messageService) {
        this(messageService, null);
    }
    
    /**
     * Generate route (backwards compatibility).
     */
    public List<RouteWaypoint> generateRoute(GeoCoordinate origin, GeoCoordinate destination,
                                            int numWaypoints, long startTime, double avgSpeedKmh) {
        return generateRoute(origin, destination, numWaypoints, startTime, avgSpeedKmh, Scenario.NORMAL);
    }
    
    /**
     * Generate route with scenario.
     */
    public List<RouteWaypoint> generateRoute(GeoCoordinate origin, GeoCoordinate destination,
                                            int numWaypoints, long startTime, double avgSpeedKmh, Scenario scenario) {
        if (numWaypoints < 2) {
            throw new IllegalArgumentException("Must have at least 2 waypoints");
        }
        
        List<RouteWaypoint> route = new ArrayList<>();
        double distance = calculateDistance(origin.toModel(), destination.toModel());
        double effectiveSpeed = avgSpeedKmh * scenario.getSpeedMultiplier();
        long totalTravelTimeMs = (long) ((distance / effectiveSpeed) * 3600 * 1000);
        long intervalMs = totalTravelTimeMs / (numWaypoints - 1);
        
        for (int i = 0; i < numWaypoints; i++) {
            double fraction = (double) i / (numWaypoints - 1);
            double lat = origin.getLatitude() + 
                        (destination.getLatitude() - origin.getLatitude()) * fraction;
            double lon = origin.getLongitude() + 
                        (destination.getLongitude() - origin.getLongitude()) * fraction;
            
            String name = (i == 0) ? origin.getName() : 
                         (i == numWaypoints - 1) ? destination.getName() :
                         String.format("Waypoint %d", i);
            
            GeoCoordinate location = new GeoCoordinate(lat, lon, name);
            long timestamp = startTime + (i * intervalMs);
            
            double baseTemp = TEMP_IDEAL + scenario.getTemperatureDrift();
            double temp = baseTemp + random.nextGaussian() * 2.0;
            temp = Math.max(-50.0, Math.min(50.0, temp));
            
            double baseHumidity = HUMIDITY_IDEAL + scenario.getHumidityDrift();
            double humidity = baseHumidity + random.nextGaussian() * 10.0;
            humidity = Math.max(0.0, Math.min(100.0, humidity));
            
            route.add(new RouteWaypoint(location, timestamp, temp, humidity));
        }
        
        logger.info("Generated route: {} waypoints, {} km, scenario: {}",
                   numWaypoints, distance, scenario.getDisplayName());
        
        return route;
    }
    
    /**
     * Start simulation (backwards compatibility).
     */
    public void startSimulation(String batchId, List<RouteWaypoint> route, long updateIntervalMs) {
        startSimulation(batchId, null, route, updateIntervalMs, Scenario.NORMAL);
    }
    
    /**
     * Start simulation with full parameters.
     */
    public void startSimulation(String batchId, String farmerId, List<RouteWaypoint> route, 
                                long updateIntervalMs, Scenario scenario) {
        if (activeSimulations.containsKey(batchId)) {
            logger.warn("Simulation already running for batch: {}", batchId);
            return;
        }
        
        // Convert legacy RouteWaypoint to model RouteWaypoint
        List<org.vericrop.service.models.RouteWaypoint> modelRoute = new ArrayList<>();
        for (RouteWaypoint wp : route) {
            modelRoute.add(wp.toModel());
        }
        
        long expectedEndTime = modelRoute.isEmpty() ? System.currentTimeMillis() 
                             : modelRoute.get(modelRoute.size() - 1).getTimestamp();
        
        SimulationState state = new SimulationState(batchId, farmerId, 
                                                    modelRoute, scenario, expectedEndTime);
        state.running = true;
        state.timeline.add(new DeliveryReport.TimelineEvent(
            System.currentTimeMillis(),
            "SIMULATION_STARTED",
            "Delivery simulation started with scenario: " + scenario.getDisplayName()
        ));
        
        activeSimulations.put(batchId, state);
        logger.info("Started simulation for batch: {}, scenario: {}", batchId, scenario.getDisplayName());
        
        sendSimulationMessage(batchId, "Delivery simulation started", 
                            route.isEmpty() ? "Unknown" : route.get(0).getLocation().toString());
        
        state.task = executor.scheduleAtFixedRate(
            () -> runSimulationStep(state),
            updateIntervalMs / INITIAL_DELAY_DIVISOR,
            updateIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Stop simulation.
     */
    public void stopSimulation(String batchId) {
        SimulationState state = activeSimulations.get(batchId);
        if (state == null) {
            logger.warn("No active simulation for batch: {}", batchId);
            return;
        }
        
        state.running = false;
        if (state.task != null) {
            state.task.cancel(true);
        }
        
        activeSimulations.remove(batchId);
        logger.info("Stopped simulation for batch: {}", batchId);
        sendSimulationMessage(batchId, "Delivery simulation stopped", "");
    }
    
    /**
     * Get simulation status.
     */
    public SimulationStatus getSimulationStatus(String batchId) {
        SimulationState state = activeSimulations.get(batchId);
        if (state == null) {
            return new SimulationStatus(batchId, false, 0, 0, null);
        }
        
        RouteWaypoint current = state.currentWaypointIndex < state.route.size() 
            ? RouteWaypoint.fromModel(state.route.get(state.currentWaypointIndex)) 
            : null;
        return new SimulationStatus(batchId, state.running,
                                   state.currentWaypointIndex, state.route.size(), current);
    }
    
    /**
     * Run a single simulation step with quality decay, alerts, and Kafka publishing.
     */
    private void runSimulationStep(SimulationState state) {
        try {
            if (!state.running || state.currentWaypointIndex >= state.route.size()) {
                completeSimulation(state);
                return;
            }
            
            org.vericrop.service.models.RouteWaypoint waypoint = state.route.get(state.currentWaypointIndex);
            long elapsedMs = System.currentTimeMillis() - state.startTime;
            double elapsedHours = elapsedMs / (1000.0 * 3600.0);
            
            // Calculate quality decay
            double tempDeviation = Math.abs(waypoint.getTemperature() - TEMP_IDEAL);
            double humidityDeviation = Math.abs(waypoint.getHumidity() - HUMIDITY_IDEAL);
            double decay = QUALITY_DECAY_PER_HOUR * elapsedHours * 
                          (1 + tempDeviation / 10.0 + humidityDeviation / 20.0) *
                          (1 + state.scenario.getSpoilageRate() * 10);
            
            state.currentQualityScore = Math.max(0, 100.0 - decay);
            state.spoilageProbability = Math.min(100.0, decay);
            
            // Send location update
            String content = String.format(
                "Location: %s | Temp: %.1f°C | Humidity: %.1f%% | Quality: %.1f%% | Spoilage Risk: %.1f%%",
                waypoint.getLocation().toString(),
                waypoint.getTemperature(),
                waypoint.getHumidity(),
                state.currentQualityScore,
                state.spoilageProbability
            );
            sendSimulationMessage(state.batchId, "Location Update", content);
            
            // Check for alerts
            checkAndGenerateAlerts(state, waypoint);
            
            state.currentWaypointIndex++;
            
            if (state.currentWaypointIndex >= state.route.size()) {
                completeSimulation(state);
            }
            
        } catch (Exception e) {
            logger.error("Simulation error for batch: {}", state.batchId, e);
            state.running = false;
            if (state.task != null) {
                state.task.cancel(false);
            }
            activeSimulations.remove(state.batchId);
        }
    }
    
    /**
     * Check environmental conditions and generate alerts if thresholds are breached.
     */
    private void checkAndGenerateAlerts(SimulationState state, org.vericrop.service.models.RouteWaypoint waypoint) {
        // Temperature alerts
        if (waypoint.getTemperature() > TEMP_MAX) {
            createAlert(state, Alert.AlertType.TEMPERATURE_HIGH, Alert.Severity.HIGH,
                       String.format("Temperature too high: %.1f°C (max: %.1f°C)",
                                   waypoint.getTemperature(), TEMP_MAX),
                       waypoint.getTemperature(), TEMP_MAX, waypoint.getLocation().toString());
        } else if (waypoint.getTemperature() < TEMP_MIN) {
            createAlert(state, Alert.AlertType.TEMPERATURE_LOW, Alert.Severity.HIGH,
                       String.format("Temperature too low: %.1f°C (min: %.1f°C)",
                                   waypoint.getTemperature(), TEMP_MIN),
                       waypoint.getTemperature(), TEMP_MIN, waypoint.getLocation().toString());
        }
        
        // Humidity alerts
        if (waypoint.getHumidity() > HUMIDITY_MAX) {
            createAlert(state, Alert.AlertType.HUMIDITY_HIGH, Alert.Severity.MEDIUM,
                       String.format("Humidity too high: %.1f%% (max: %.1f%%)",
                                   waypoint.getHumidity(), HUMIDITY_MAX),
                       waypoint.getHumidity(), HUMIDITY_MAX, waypoint.getLocation().toString());
        } else if (waypoint.getHumidity() < HUMIDITY_MIN) {
            createAlert(state, Alert.AlertType.HUMIDITY_LOW, Alert.Severity.MEDIUM,
                       String.format("Humidity too low: %.1f%% (min: %.1f%%)",
                                   waypoint.getHumidity(), HUMIDITY_MIN),
                       waypoint.getHumidity(), HUMIDITY_MIN, waypoint.getLocation().toString());
        }
        
        // Spoilage risk alert
        if (state.spoilageProbability > SPOILAGE_THRESHOLD) {
            createAlert(state, Alert.AlertType.SPOILAGE_RISK, Alert.Severity.CRITICAL,
                       String.format("Critical spoilage risk: %.1f%%", state.spoilageProbability),
                       state.spoilageProbability, SPOILAGE_THRESHOLD, waypoint.getLocation().toString());
            
            // Trigger Airflow hook for critical spoilage
            triggerAirflowHook(state.batchId, "CRITICAL_SPOILAGE", state.spoilageProbability);
        }
        
        // Delivery delay alert
        long now = System.currentTimeMillis();
        if (now > state.expectedEndTime && state.running) {
            long delayMinutes = (now - state.expectedEndTime) / (1000 * 60);
            createAlert(state, Alert.AlertType.DELIVERY_DELAY, Alert.Severity.HIGH,
                       String.format("Delivery delayed by %d minutes", delayMinutes),
                       delayMinutes, 0, waypoint.getLocation().toString());
            
            // Trigger Airflow hook for significant delay
            if (delayMinutes > 30) {
                triggerAirflowHook(state.batchId, "DELAYED", delayMinutes);
            }
        }
        
        // Quality degradation alert
        if (state.currentQualityScore < 70.0) {
            createAlert(state, Alert.AlertType.QUALITY_DEGRADATION, Alert.Severity.HIGH,
                       String.format("Quality degraded to %.1f%%", state.currentQualityScore),
                       state.currentQualityScore, 70.0, waypoint.getLocation().toString());
        }
    }
    
    /**
     * Create and record an alert.
     */
    private void createAlert(SimulationState state, Alert.AlertType type, Alert.Severity severity,
                           String message, double currentValue, double thresholdValue, String location) {
        String alertId = UUID.randomUUID().toString();
        Alert alert = new Alert(alertId, state.batchId, type, severity, message,
                              currentValue, thresholdValue, System.currentTimeMillis(), location);
        
        state.alerts.add(alert);
        alertService.recordAlert(alert);
        
        state.timeline.add(new DeliveryReport.TimelineEvent(
            System.currentTimeMillis(), "ALERT_GENERATED", message
        ));
        
        logger.warn("Alert generated: {}", alert);
        sendSimulationMessage(state.batchId, "Alert: " + type.name(), message);
    }
    
    /**
     * Trigger Airflow webhook for critical events.
     */
    private void triggerAirflowHook(String batchId, String eventType, double value) {
        if (airflowHookUrl == null || airflowHookUrl.isEmpty()) {
            logger.debug("Airflow hook not configured, skipping webhook trigger");
            return;
        }
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("batch_id", batchId);
            payload.put("event_type", eventType);
            payload.put("value", value);
            payload.put("timestamp", System.currentTimeMillis());
            
            String json = objectMapper.writeValueAsString(payload);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(airflowHookUrl)
                    .post(body)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.warn("Failed to trigger Airflow hook: {}", e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) {
                    logger.info("Airflow hook triggered for batch: {}, event: {}", batchId, eventType);
                    response.close();
                }
            });
        } catch (Exception e) {
            logger.error("Error triggering Airflow hook", e);
        }
    }
    
    /**
     * Complete a simulation and record metrics.
     */
    private void completeSimulation(SimulationState state) {
        sendSimulationMessage(state.batchId, "Delivery Complete",
            "Shipment arrived. Final quality: " + String.format("%.1f%%", state.currentQualityScore));
        
        state.running = false;
        if (state.task != null) {
            state.task.cancel(false);
        }
        
        // Record supplier metrics
        long deliveryTime = System.currentTimeMillis() - state.startTime;
        double qualityDecay = 100.0 - state.currentQualityScore;
        boolean spoiled = state.spoilageProbability > SPOILAGE_THRESHOLD;
        
        SupplierData supplier = supplierMetrics.computeIfAbsent(
            state.farmerId, k -> new SupplierData(state.farmerId));
        supplier.recordDelivery(qualityDecay, deliveryTime, spoiled);
        
        state.timeline.add(new DeliveryReport.TimelineEvent(
            System.currentTimeMillis(), "DELIVERY_COMPLETE",
            String.format("Delivery completed with %.1f%% quality remaining", state.currentQualityScore)
        ));
        
        activeSimulations.remove(state.batchId);
        logger.info("Simulation completed for batch: {}, final quality: {}, spoilage: {}",
                   state.batchId, state.currentQualityScore, spoiled);
    }
    
    /**
     * Generate delivery report for a batch.
     */
    public DeliveryReport generateDeliveryReport(String batchId) {
        SimulationState state = activeSimulations.get(batchId);
        if (state == null) {
            logger.warn("No simulation data for batch: {}", batchId);
            return null;
        }
        
        org.vericrop.service.models.GeoCoordinate origin = state.route.isEmpty() ? null : state.route.get(0).getLocation();
        org.vericrop.service.models.GeoCoordinate destination = state.route.isEmpty() ? null : 
                                   state.route.get(state.route.size() - 1).getLocation();
        
        double avgTemp = state.route.stream()
                             .mapToDouble(org.vericrop.service.models.RouteWaypoint::getTemperature)
                             .average().orElse(0.0);
        double avgHumidity = state.route.stream()
                                 .mapToDouble(org.vericrop.service.models.RouteWaypoint::getHumidity)
                                 .average().orElse(0.0);
        
        double totalDistance = origin != null && destination != null 
                             ? calculateDistance(origin, destination) : 0.0;
        
        boolean onTime = System.currentTimeMillis() <= state.expectedEndTime;
        
        return new DeliveryReport(
            state.batchId,
            state.farmerId,
            origin,
            destination,
            state.scenario,
            state.startTime,
            System.currentTimeMillis(),
            totalDistance,
            avgTemp,
            avgHumidity,
            100.0,
            state.currentQualityScore,
            100.0 - state.currentQualityScore,
            state.spoilageProbability,
            onTime,
            new ArrayList<>(state.alerts),
            new ArrayList<>(state.route),
            new ArrayList<>(state.timeline)
        );
    }
    
    /**
     * Export delivery report to JSON file.
     */
    public void exportReportToJson(DeliveryReport report, String filePath) throws IOException {
        if (report == null) {
            throw new IllegalArgumentException("Report cannot be null");
        }
        
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        
        objectMapper.writeValue(file, report);
        logger.info("Report exported to: {}", filePath);
    }
    
    /**
     * Get supplier metrics for a farmer.
     */
    public SupplierMetrics getSupplierMetrics(String farmerId) {
        SupplierData data = supplierMetrics.get(farmerId);
        if (data == null) {
            return new SupplierMetrics(farmerId, 0, 0, 0, 0.0, 0.0);
        }
        
        double avgQualityDecay = data.totalDeliveries > 0 
            ? data.totalQualityDecay / data.totalDeliveries : 0.0;
        double avgDeliveryTime = data.totalDeliveries > 0 
            ? data.totalDeliveryTime / data.totalDeliveries : 0.0;
        
        return new SupplierMetrics(
            farmerId,
            data.totalDeliveries,
            data.successfulDeliveries,
            data.spoiledShipments,
            avgQualityDecay,
            avgDeliveryTime
        );
    }
    
    /**
     * Get all supplier metrics.
     */
    public List<SupplierMetrics> getAllSupplierMetrics() {
        return supplierMetrics.keySet().stream()
                             .map(this::getSupplierMetrics)
                             .collect(Collectors.toList());
    }
    
    /**
     * Send a message via the messaging system.
     */
    private void sendSimulationMessage(String batchId, String subject, String content) {
        if (messageService != null) {
            try {
                Message message = new Message(
                    "logistics",
                    "delivery_simulator",
                    "all",
                    null,
                    subject,
                    content
                );
                message.setShipmentId(batchId);
                messageService.sendMessage(message);
            } catch (Exception e) {
                logger.error("Failed to send simulation message", e);
            }
        }
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula.
     */
    private double calculateDistance(org.vericrop.service.models.GeoCoordinate c1, org.vericrop.service.models.GeoCoordinate c2) {
        double lat1 = Math.toRadians(c1.getLatitude());
        double lat2 = Math.toRadians(c2.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(c2.getLongitude() - c1.getLongitude());
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(lat1) * Math.cos(lat2) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return 6371 * c;  // Earth radius in km
    }
    
    /**
     * Cleanup and shutdown.
     */
    public void cleanup() {
        shutdown();
    }
    
    /**
     * Shutdown the simulator.
     */
    public void shutdown() {
        activeSimulations.values().forEach(state -> {
            state.running = false;
            if (state.task != null) {
                state.task.cancel(true);
            }
        });
        
        executor.shutdownNow();
        try {
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("Executor did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Shutdown interrupted");
        }
        
        if (httpClient != null) {
            httpClient.connectionPool().evictAll();
        }
        
        logger.info("DeliverySimulator shut down");
    }
    
    /**
     * Legacy compatibility - GeoCoordinate as inner class.
     * This is a direct copy of the model class for backwards compatibility.
     */
    @Deprecated
    public static class GeoCoordinate {
        private final double latitude;
        private final double longitude;
        private final String name;
        
        public GeoCoordinate(double latitude, double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }
        
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getName() { return name; }
        
        @Override
        public String toString() {
            return String.format("%s (%.4f, %.4f)", name, latitude, longitude);
        }
        
        public org.vericrop.service.models.GeoCoordinate toModel() {
            return new org.vericrop.service.models.GeoCoordinate(latitude, longitude, name);
        }
        
        public static GeoCoordinate fromModel(org.vericrop.service.models.GeoCoordinate model) {
            return new GeoCoordinate(model.getLatitude(), model.getLongitude(), model.getName());
        }
    }
    
    /**
     * Legacy compatibility - RouteWaypoint as inner class.
     */
    @Deprecated
    public static class RouteWaypoint {
        private final GeoCoordinate location;
        private final long timestamp;
        private final double temperature;
        private final double humidity;
        
        public RouteWaypoint(GeoCoordinate location, long timestamp,
                           double temperature, double humidity) {
            this.location = location;
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.humidity = humidity;
        }
        
        public GeoCoordinate getLocation() { return location; }
        public long getTimestamp() { return timestamp; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
        
        public org.vericrop.service.models.RouteWaypoint toModel() {
            return new org.vericrop.service.models.RouteWaypoint(
                location.toModel(), timestamp, temperature, humidity);
        }
        
        public static RouteWaypoint fromModel(org.vericrop.service.models.RouteWaypoint model) {
            return new RouteWaypoint(
                GeoCoordinate.fromModel(model.getLocation()),
                model.getTimestamp(),
                model.getTemperature(),
                model.getHumidity()
            );
        }
    }
    
    /**
     * Legacy compatibility - SimulationStatus as inner class.
     */
    @Deprecated
    public static class SimulationStatus {
        private final String shipmentId;
        private final boolean running;
        private final int currentWaypoint;
        private final int totalWaypoints;
        private final RouteWaypoint currentLocation;
        
        public SimulationStatus(String shipmentId, boolean running, int currentWaypoint,
                              int totalWaypoints, RouteWaypoint currentLocation) {
            this.shipmentId = shipmentId;
            this.running = running;
            this.currentWaypoint = currentWaypoint;
            this.totalWaypoints = totalWaypoints;
            this.currentLocation = currentLocation;
        }
        
        public String getShipmentId() { return shipmentId; }
        public boolean isRunning() { return running; }
        public int getCurrentWaypoint() { return currentWaypoint; }
        public int getTotalWaypoints() { return totalWaypoints; }
        public RouteWaypoint getCurrentLocation() { return currentLocation; }
    }
}
