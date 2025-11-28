package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import java.util.Optional;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import org.vericrop.dto.MapSimulationEvent;
import org.vericrop.dto.TemperatureComplianceEvent;
import org.vericrop.kafka.consumers.MapSimulationEventConsumer;
import org.vericrop.kafka.consumers.TemperatureComplianceEventConsumer;
import org.vericrop.kafka.consumers.SimulationControlConsumer;
import org.vericrop.kafka.events.SimulationControlEvent;
import org.vericrop.kafka.events.InstanceHeartbeatEvent;
import org.vericrop.kafka.producers.QualityAlertProducer;
import org.vericrop.kafka.events.QualityAlertEvent;
import org.vericrop.kafka.services.InstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.File;
import org.vericrop.gui.persistence.ShipmentPersistenceService;
import org.vericrop.gui.persistence.PersistedShipment;
import org.vericrop.gui.persistence.PersistedSimulation;
import org.vericrop.gui.services.ReportExportService;
import org.vericrop.dto.SimulationResult;

public class LogisticsController implements SimulationListener {
    private static final Logger logger = LoggerFactory.getLogger(LogisticsController.class);

    @FXML private TableView<Shipment> shipmentsTable;
    @FXML private ListView<String> alertsList;
    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextArea reportArea;
    @FXML private LineChart<Number, Number> temperatureChart;
    @FXML private NumberAxis temperatureXAxis;
    @FXML private NumberAxis temperatureYAxis;
    @FXML private Pane mapContainer;
    @FXML private VBox timelineContainer;

    // Navigation buttons
    @FXML private Button backToProducerButton;
    @FXML private Button consumerButton;
    @FXML private Button logoutButton;

    private ObservableList<Shipment> shipments = FXCollections.observableArrayList();
    private ObservableList<String> alerts = FXCollections.observableArrayList();

    // Delivery simulation synchronization
    private ScheduledExecutorService syncExecutor;
    private Map<String, MapVisualization> activeShipments = new HashMap<>();
    private org.vericrop.service.DeliverySimulator deliverySimulator;
    
    // Persistence and export services
    private ShipmentPersistenceService persistenceService;
    private ReportExportService reportExportService;
    
    // Kafka consumers for real-time events
    private MapSimulationEventConsumer mapSimulationConsumer;
    private TemperatureComplianceEventConsumer temperatureComplianceConsumer;
    private SimulationControlConsumer simulationControlConsumer;
    private ExecutorService kafkaConsumerExecutor;
    
    // Kafka producer for alerts
    private QualityAlertProducer qualityAlertProducer;
    
    // Instance registry for multi-instance tracking
    private InstanceRegistry instanceRegistry;
    
    // Track temperature chart series by batch ID (thread-safe for Kafka consumer access)
    private Map<String, XYChart.Series<Number, Number>> temperatureSeriesMap = new ConcurrentHashMap<>();
    
    // Track stopped simulations to prevent further chart updates (prevents "running" graph after sim ends)
    private final java.util.Set<String> stoppedSimulations = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    // Chart epoch start time for calculating x-values in seconds
    private long chartEpochStart;
    
    // Track latest environmental data by batch ID for shipments table
    private Map<String, ShipmentEnvironmentalData> environmentalDataMap = new ConcurrentHashMap<>();
    
    // Report type display name to enum mapping
    private static final Map<String, ReportExportService.ReportType> REPORT_TYPE_MAP = new HashMap<>();
    static {
        REPORT_TYPE_MAP.put("Shipment Summary", ReportExportService.ReportType.SHIPMENT_SUMMARY);
        REPORT_TYPE_MAP.put("Temperature Log", ReportExportService.ReportType.TEMPERATURE_LOG);
        REPORT_TYPE_MAP.put("Quality Compliance", ReportExportService.ReportType.QUALITY_COMPLIANCE);
        REPORT_TYPE_MAP.put("Delivery Performance", ReportExportService.ReportType.DELIVERY_PERFORMANCE);
        REPORT_TYPE_MAP.put("Simulation Log", ReportExportService.ReportType.SIMULATION_LOG);
    }
    
    /**
     * Helper class to track environmental data for a shipment
     */
    private static class ShipmentEnvironmentalData {
        double temperature = 4.0; // Default cold-chain temp
        double humidity = 65.0; // Default humidity
        
        ShipmentEnvironmentalData() {}
        
        ShipmentEnvironmentalData(double temp, double humidity) {
            this.temperature = temp;
            this.humidity = humidity;
        }
    }

    // Map visualization constants
    private static final double MAP_WIDTH = 350;
    private static final double MAP_HEIGHT = 200;
    private static final double ORIGIN_X = 50;
    private static final double ORIGIN_Y = 100;
    private static final double DESTINATION_X = 300;
    private static final double DESTINATION_Y = 100;
    
    // Temperature chart configuration
    private static final int MAX_CHART_DATA_POINTS = 20;
    private static final int MAX_ALERT_ITEMS = 50;
    
    // Demo data identifier
    private static final String DEMO_DATA_SUFFIX = "(demo)";
    
    // Simulation progress thresholds (percentage)
    private static final double PROGRESS_DEPARTING_THRESHOLD = 10.0;
    private static final double PROGRESS_EN_ROUTE_THRESHOLD = 30.0;
    private static final double PROGRESS_APPROACHING_THRESHOLD = 70.0;
    private static final double PROGRESS_AT_WAREHOUSE_THRESHOLD = 90.0;
    private static final double PROGRESS_COMPLETE = 100.0;
    
    // Simulation timing
    private static final int ESTIMATED_TOTAL_TRIP_MINUTES = 120;

    @FXML
    public void initialize() {
        setupShipmentsTable();
        setupAlertsList();
        setupReportCombo();
        setupExportFormatCombo();
        setupTemperatureChart();
        setupNavigationButtons();
        setupMapContainer();
        startSyncService();
        
        // Initialize persistence and export services
        initializePersistenceServices();
        
        // Initialize Kafka consumers for real-time updates
        setupKafkaConsumers();
        
        // Initialize instance registry with LOGISTICS role
        initializeInstanceRegistry();

        // Get delivery simulator from application context
        try {
            this.deliverySimulator = MainApp.getInstance().getApplicationContext().getDeliverySimulator();
        } catch (Exception e) {
            System.err.println("Delivery simulator not available: " + e.getMessage());
        }
        
        // Register with SimulationManager to track running simulations
        registerWithSimulationManager();
    }
    
    /**
     * Initialize instance registry with LOGISTICS role for multi-instance coordination.
     */
    private void initializeInstanceRegistry() {
        try {
            this.instanceRegistry = new InstanceRegistry(InstanceHeartbeatEvent.Role.LOGISTICS);
            this.instanceRegistry.start();
            logger.info("📡 LogisticsController instance registry started with ID: {} (role: {})",
                       instanceRegistry.getInstanceId(), instanceRegistry.getRole());
        } catch (Exception e) {
            logger.warn("⚠️ Failed to initialize instance registry: {} - simulation coordination may be affected", 
                       e.getMessage());
        }
    }
    
    /**
     * Register this controller as a listener with SimulationManager.
     * If a simulation is already running, update the map to show it.
     */
    private void registerWithSimulationManager() {
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager manager = SimulationManager.getInstance();
                manager.registerListener(this);
                
                // If simulation is already running when we initialize, show it on map
                if (manager.isRunning()) {
                    String batchId = manager.getSimulationId();
                    Platform.runLater(() -> {
                        alerts.add(0, "📍 Active simulation detected: " + batchId);
                        System.out.println("LogisticsController: Detected running simulation: " + batchId);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not register with SimulationManager: " + e.getMessage());
        }
    }
    
    /**
     * Setup Kafka consumers for real-time map and temperature events.
     * Runs consumers in background threads and handles events on JavaFX UI thread.
     */
    private void setupKafkaConsumers() {
        try {
            // Thread pool for Kafka consumers: MapSimulation, TemperatureCompliance, SimulationControl
            final int KAFKA_CONSUMER_COUNT = 3;
            kafkaConsumerExecutor = Executors.newFixedThreadPool(KAFKA_CONSUMER_COUNT);
            
            // Initialize alert producer
            try {
                qualityAlertProducer = new QualityAlertProducer();
                System.out.println("✅ Quality alert producer initialized");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to initialize alert producer: " + e.getMessage());
                // Continue without alert producer
            }
            
            // Create map simulation consumer with event handler
            mapSimulationConsumer = new MapSimulationEventConsumer(
                "logistics-ui-group",
                this::handleMapSimulationEvent
            );
            
            // Create temperature compliance consumer with event handler
            temperatureComplianceConsumer = new TemperatureComplianceEventConsumer(
                "logistics-ui-group",
                this::handleTemperatureComplianceEvent
            );
            
            // Create simulation control consumer with unique group ID per instance
            // Using unique group ID ensures each instance receives all simulation control events
            String uniqueGroupId = "logistics-simulation-control-" + System.currentTimeMillis();
            simulationControlConsumer = new SimulationControlConsumer(
                uniqueGroupId,
                this::handleSimulationControlEvent
            );
            
            // Start consumers in background threads
            kafkaConsumerExecutor.submit(() -> {
                try {
                    mapSimulationConsumer.startConsuming();
                } catch (Exception e) {
                    System.err.println("Map simulation consumer error: " + e.getMessage());
                }
            });
            
            kafkaConsumerExecutor.submit(() -> {
                try {
                    temperatureComplianceConsumer.startConsuming();
                } catch (Exception e) {
                    System.err.println("Temperature compliance consumer error: " + e.getMessage());
                }
            });
            
            kafkaConsumerExecutor.submit(() -> {
                try {
                    simulationControlConsumer.startConsuming();
                } catch (Exception e) {
                    System.err.println("Simulation control consumer error: " + e.getMessage());
                }
            });
            
            System.out.println("✅ Kafka consumers initialized for logistics monitoring");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to initialize Kafka consumers: " + e.getMessage());
            e.printStackTrace();
            // Continue without Kafka - fallback to SimulationListener updates
        }
    }
    
    /**
     * Handle simulation control event from Kafka.
     * Coordinates simulation start/stop across instances.
     */
    private void handleSimulationControlEvent(SimulationControlEvent event) {
        if (event == null) {
            return;
        }
        
        logger.info("📡 LogisticsController received simulation control event: {}", event);
        
        Platform.runLater(() -> {
            try {
                if (event.isStart()) {
                    // Handle simulation START - initialize tracking for this batch
                    String batchId = event.getBatchId();
                    String farmerId = event.getFarmerId();
                    
                    // Remove from stopped simulations if restarting
                    stoppedSimulations.remove(batchId);
                    
                    alerts.add(0, "🚚 Simulation started via Kafka: " + batchId);
                    if (alerts.size() > MAX_ALERT_ITEMS) {
                        alerts.remove(alerts.size() - 1);
                    }
                    
                    // Initialize map marker at origin for this batch
                    initializeMapMarker(batchId);
                    
                    // Initialize temperature chart series
                    initializeTemperatureChartSeries(batchId);
                    
                    // Initialize timeline to starting state
                    updateTimeline(batchId, 0.0, "Started");
                    
                    // Persist simulation start
                    persistSimulationStart(batchId, farmerId);
                    
                    logger.info("✅ LogisticsController: Started tracking simulation via Kafka: {}", batchId);
                    
                } else if (event.isStop()) {
                    // Handle simulation STOP - cleanup tracking for this batch
                    String batchId = event.getBatchId() != null ? event.getBatchId() : event.getSimulationId();
                    
                    // Check if already stopped to avoid duplicate alerts
                    if (stoppedSimulations.contains(batchId)) {
                        logger.debug("Ignoring duplicate stop event for already stopped simulation: {}", batchId);
                        return;
                    }
                    
                    // Mark simulation as stopped to prevent further chart updates
                    stoppedSimulations.add(batchId);
                    
                    alerts.add(0, "⏹ Simulation stopped via Kafka: " + batchId);
                    if (alerts.size() > MAX_ALERT_ITEMS) {
                        alerts.remove(alerts.size() - 1);
                    }
                    
                    // Update timeline to final state
                    updateTimeline(batchId, 100.0, "Delivered");
                    
                    // Persist simulation completion
                    persistSimulationComplete(batchId, true);
                    
                    // Cleanup map marker
                    cleanupMapMarker(batchId);
                    
                    logger.info("✅ LogisticsController: Stopped tracking simulation via Kafka: {}", batchId);
                }
            } catch (Exception e) {
                logger.error("Error handling simulation control event: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Handle map simulation event from Kafka.
     * Updates map visualization with real-time position data and environmental data.
     * Implements status transition guards to prevent duplicate alerts.
     * Generates humidity and temperature alerts when thresholds are breached.
     * Respects stopped simulations to prevent updates after simulation ends.
     */
    private void handleMapSimulationEvent(MapSimulationEvent event) {
        if (event == null || event.getBatchId() == null) {
            logger.warn("Received null or invalid map simulation event");
            return;
        }
        
        // Don't process events for stopped simulations
        if (stoppedSimulations.contains(event.getBatchId())) {
            logger.debug("Ignoring map event for stopped simulation: {}", event.getBatchId());
            return;
        }
        
        logger.debug("🗺️  Received map event: {} at progress {}% - {}", 
                    event.getBatchId(), event.getProgress() * 100, event.getLocationName());
        
        Platform.runLater(() -> {
            try {
                // Update environmental data tracking from map event
                ShipmentEnvironmentalData envData = environmentalDataMap.computeIfAbsent(
                    event.getBatchId(), k -> new ShipmentEnvironmentalData());
                double previousHumidity = envData.humidity;
                envData.temperature = event.getTemperature();
                envData.humidity = event.getHumidity();
                
                // Update map marker position based on event data
                updateMapFromEvent(event);
                
                // Update shipments table with new environmental data (if row exists)
                updateShipmentEnvironmentalData(event.getBatchId());
                
                // Determine status based on progress to detect transitions
                double progressPercent = event.getProgress() * 100;
                String newStatus = determineStatusFromProgress(progressPercent);
                
                // Update timeline with current progress
                updateTimeline(event.getBatchId(), progressPercent, newStatus);
                
                // Get visualization to track last status
                MapVisualization visualization = activeShipments.get(event.getBatchId());
                if (visualization != null) {
                    String lastStatus = visualization.lastStatus;
                    
                    // Only add status alert if status has changed (idempotent status transitions)
                    if (lastStatus == null || !lastStatus.equals(newStatus)) {
                        String locationName = event.getLocationName() != null ? event.getLocationName() : "Unknown Location";
                        String alertMsg = String.format("🗺️ %s: %s - %s", 
                            event.getBatchId(), newStatus, locationName);
                        alerts.add(0, alertMsg);
                        if (alerts.size() > MAX_ALERT_ITEMS) {
                            alerts.remove(alerts.size() - 1);
                        }
                        
                        // Update last status to prevent duplicates
                        visualization.lastStatus = newStatus;
                        logger.info("Status transition: {} -> {}", event.getBatchId(), newStatus);
                    }
                }
                
                // Generate humidity alerts when thresholds are breached
                // Optimal humidity for cold chain is 65-85%
                checkAndGenerateHumidityAlert(event.getBatchId(), event.getHumidity(), 
                                             event.getLocationName(), previousHumidity);
                
                // Generate temperature alerts for extreme values from map event
                // (complements TemperatureComplianceEvent which handles detailed temp monitoring)
                checkAndGenerateTemperatureAlert(event.getBatchId(), event.getTemperature(),
                                                event.getLocationName());
                
            } catch (Exception e) {
                logger.error("Error handling map simulation event: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Check humidity value and generate alert if outside optimal range.
     * Optimal humidity for cold chain produce is 65-85%.
     * 
     * @param batchId Batch identifier
     * @param humidity Current humidity percentage
     * @param location Current location
     * @param previousHumidity Previous humidity reading (for change detection)
     */
    private void checkAndGenerateHumidityAlert(String batchId, double humidity, 
                                               String location, double previousHumidity) {
        // Humidity thresholds for cold chain (optimal: 65-85%)
        final double HUMIDITY_LOW_THRESHOLD = 50.0;
        final double HUMIDITY_HIGH_THRESHOLD = 90.0;
        final double HUMIDITY_CHANGE_THRESHOLD = 10.0; // Alert on significant changes
        
        String alertMsg = null;
        
        if (humidity < HUMIDITY_LOW_THRESHOLD) {
            alertMsg = String.format("💧 LOW HUMIDITY ALERT: %s - %.1f%% at %s (threshold: %.1f%%)",
                batchId, humidity, location != null ? location : "Unknown", HUMIDITY_LOW_THRESHOLD);
        } else if (humidity > HUMIDITY_HIGH_THRESHOLD) {
            alertMsg = String.format("💧 HIGH HUMIDITY ALERT: %s - %.1f%% at %s (threshold: %.1f%%)",
                batchId, humidity, location != null ? location : "Unknown", HUMIDITY_HIGH_THRESHOLD);
        } else if (Math.abs(humidity - previousHumidity) > HUMIDITY_CHANGE_THRESHOLD) {
            // Alert on significant humidity change (could indicate seal issue)
            alertMsg = String.format("💧 HUMIDITY CHANGE: %s - %.1f%% (was %.1f%%) at %s",
                batchId, humidity, previousHumidity, location != null ? location : "Unknown");
        }
        
        if (alertMsg != null) {
            alerts.add(0, alertMsg);
            if (alerts.size() > MAX_ALERT_ITEMS) {
                alerts.remove(alerts.size() - 1);
            }
            logger.info("Humidity alert generated: {}", alertMsg);
        }
    }
    
    /**
     * Check temperature value and generate alert if outside safe range.
     * Safe temperature for cold chain is 2-8°C.
     * 
     * @param batchId Batch identifier
     * @param temperature Current temperature in Celsius
     * @param location Current location
     */
    private void checkAndGenerateTemperatureAlert(String batchId, double temperature, String location) {
        // Temperature thresholds for cold chain (optimal: 2-8°C)
        final double TEMP_LOW_THRESHOLD = 2.0;
        final double TEMP_HIGH_THRESHOLD = 8.0;
        final double TEMP_CRITICAL_LOW = 0.0;
        final double TEMP_CRITICAL_HIGH = 12.0;
        
        String alertMsg = null;
        
        if (temperature < TEMP_CRITICAL_LOW) {
            alertMsg = String.format("❄️ CRITICAL FREEZE ALERT: %s - %.1f°C at %s",
                batchId, temperature, location != null ? location : "Unknown");
        } else if (temperature < TEMP_LOW_THRESHOLD) {
            alertMsg = String.format("❄️ LOW TEMP WARNING: %s - %.1f°C at %s (threshold: %.1f°C)",
                batchId, temperature, location != null ? location : "Unknown", TEMP_LOW_THRESHOLD);
        } else if (temperature > TEMP_CRITICAL_HIGH) {
            alertMsg = String.format("🔥 CRITICAL TEMP ALERT: %s - %.1f°C at %s",
                batchId, temperature, location != null ? location : "Unknown");
        } else if (temperature > TEMP_HIGH_THRESHOLD) {
            alertMsg = String.format("🔥 HIGH TEMP WARNING: %s - %.1f°C at %s (threshold: %.1f°C)",
                batchId, temperature, location != null ? location : "Unknown", TEMP_HIGH_THRESHOLD);
        }
        
        if (alertMsg != null) {
            alerts.add(0, alertMsg);
            if (alerts.size() > MAX_ALERT_ITEMS) {
                alerts.remove(alerts.size() - 1);
            }
            logger.info("Temperature alert generated: {}", alertMsg);
        }
    }
    
    /**
     * Determine status string based on progress percentage.
     * Used to detect status transitions and prevent duplicate alerts.
     * Only transitions to "Delivered" at exactly 100% progress to prevent
     * duplicate alerts at high progress levels (95-99%).
     * @param progressPercent Progress as percentage (0-100)
     * @return Status string
     */
    private String determineStatusFromProgress(double progressPercent) {
        // Use exact comparison for terminal state to prevent duplicates
        // This ensures "Delivered" only triggers once when progress hits exactly 100%
        if (progressPercent == PROGRESS_COMPLETE) {
            return "Delivered";
        } else if (progressPercent >= PROGRESS_AT_WAREHOUSE_THRESHOLD) {
            return "At Warehouse";
        } else if (progressPercent >= PROGRESS_APPROACHING_THRESHOLD) {
            return "Approaching";
        } else if (progressPercent >= PROGRESS_EN_ROUTE_THRESHOLD) {
            return "En Route";
        } else if (progressPercent >= PROGRESS_DEPARTING_THRESHOLD) {
            return "Departing";
        } else {
            return "Created";
        }
    }
    
    /**
     * Handle temperature compliance event from Kafka.
     * Updates temperature chart with real-time data points.
     * Note: Humidity is tracked via MapSimulationEvent which includes both temp and humidity.
     * Respects stopped simulations to prevent updates after simulation ends.
     */
    private void handleTemperatureComplianceEvent(TemperatureComplianceEvent event) {
        if (event == null || event.getBatchId() == null) {
            logger.warn("Received null or invalid temperature compliance event");
            return;
        }
        
        // Don't process events for stopped simulations
        if (stoppedSimulations.contains(event.getBatchId())) {
            logger.debug("Ignoring temperature event for stopped simulation: {}", event.getBatchId());
            return;
        }
        
        logger.debug("🌡️  Received temperature event: {} = {}°C (compliant: {})",
                    event.getBatchId(), event.getTemperature(), event.isCompliant());
        
        Platform.runLater(() -> {
            try {
                // Update environmental data tracking (temperature only)
                // Humidity comes from MapSimulationEvent
                ShipmentEnvironmentalData envData = environmentalDataMap.computeIfAbsent(
                    event.getBatchId(), k -> new ShipmentEnvironmentalData());
                envData.temperature = event.getTemperature();
                
                // Add data point to temperature chart
                addTemperatureDataPoint(event);
                
                // Update shipments table with new temperature (if row exists)
                updateShipmentEnvironmentalData(event.getBatchId());
                
                // Add alert if not compliant
                if (!event.isCompliant()) {
                    String details = event.getDetails() != null ? event.getDetails() : "Temperature out of range";
                    String alertMsg = String.format("🌡️ ALERT: %s - %.1f°C - %s", 
                        event.getBatchId(), event.getTemperature(), details);
                    alerts.add(0, alertMsg);
                    if (alerts.size() > MAX_ALERT_ITEMS) {
                        alerts.remove(alerts.size() - 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error handling temperature compliance event: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Update shipment environmental data in the table (temperature/humidity).
     * Finds existing shipment row and updates it with latest environmental data.
     * Uses index-based update for efficiency.
     */
    private void updateShipmentEnvironmentalData(String batchId) {
        if (shipmentsTable == null) return;
        
        try {
            ShipmentEnvironmentalData envData = environmentalDataMap.get(batchId);
            if (envData == null) return;
            
            // Find existing shipment by index for efficient update
            int shipmentIndex = -1;
            Shipment existingShipment = null;
            for (int i = 0; i < shipments.size(); i++) {
                if (shipments.get(i).getBatchId().equals(batchId)) {
                    shipmentIndex = i;
                    existingShipment = shipments.get(i);
                    break;
                }
            }
            
            if (existingShipment != null && shipmentIndex >= 0) {
                // Create updated shipment with new environmental data
                Shipment updatedShipment = new Shipment(
                    existingShipment.getBatchId(),
                    existingShipment.getStatus(),
                    existingShipment.getLocation(),
                    envData.temperature,
                    envData.humidity,
                    existingShipment.getEta(),
                    existingShipment.getVehicle()
                );
                
                // Replace at same index to maintain order and minimize UI updates
                shipments.set(shipmentIndex, updatedShipment);
            }
        } catch (Exception e) {
            System.err.println("Error updating shipment environmental data: " + e.getMessage());
        }
    }
    
    /**
     * Update map visualization based on map simulation event.
     * Adds trail points as the shipment moves to show the progressive route.
     */
    private void updateMapFromEvent(MapSimulationEvent event) {
        if (mapContainer == null) {
            System.err.println("⚠️ Map container is null, cannot update map visualization");
            return;
        }
        
        if (event == null || event.getBatchId() == null) {
            System.err.println("⚠️ Invalid event or batch ID, cannot update map");
            return;
        }
        
        try {
            // Calculate position on map canvas
            double mapWidth = 350;
            double mapHeight = 200;
            double originX = 50;
            double destinationX = 300;
            double originY = 100;
            
            double progress = event.getProgress();
            // Clamp progress to valid range [0, 1]
            progress = Math.max(0.0, Math.min(1.0, progress));
            
            double currentX = originX + (destinationX - originX) * progress;
            double currentY = originY;
            
            MapVisualization visualization = activeShipments.get(event.getBatchId());
            if (visualization == null) {
                // Create new marker
                visualization = new MapVisualization();
                visualization.shipmentCircle = new Circle(currentX, currentY, 6, Color.ORANGE);
                visualization.shipmentCircle.setUserData("shipment");
                
                String displayId = event.getBatchId().length() > 8 ? 
                    event.getBatchId().substring(0, 8) : event.getBatchId();
                visualization.shipmentLabel = new Text(currentX - 15, currentY - 15, "🚚 " + displayId);
                visualization.shipmentLabel.setUserData("shipment");
                visualization.shipmentLabel.setFill(Color.DARKBLUE);
                
                mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
                activeShipments.put(event.getBatchId(), visualization);
            } else {
                // Update existing marker position
                double oldX = visualization.shipmentCircle.getCenterX();
                double oldY = visualization.shipmentCircle.getCenterY();
                
                // Only add trail point if position has changed significantly (avoid stacking)
                // Use a threshold of 2 pixels to filter out tiny movements
                double deltaX = Math.abs(currentX - oldX);
                double deltaY = Math.abs(currentY - oldY);
                if (deltaX > 2.0 || deltaY > 2.0) {
                    // Add trail point at previous position
                    Circle trailPoint = new Circle(oldX, oldY, 3, Color.ORANGE);
                    trailPoint.setOpacity(0.6);
                    trailPoint.setUserData("trail");
                    visualization.trailPoints.add(trailPoint);
                    
                    // Add trail point to map (behind the moving marker)
                    mapContainer.getChildren().add(mapContainer.getChildren().indexOf(visualization.shipmentCircle), trailPoint);
                    
                    // Limit trail length to prevent clutter (keep last 20 points)
                    if (visualization.trailPoints.size() > 20) {
                        Circle oldestPoint = visualization.trailPoints.remove(0);
                        mapContainer.getChildren().remove(oldestPoint);
                    }
                }
                
                // Update marker position
                visualization.shipmentCircle.setCenterX(currentX);
                visualization.shipmentCircle.setCenterY(currentY);
                visualization.shipmentLabel.setX(currentX - 15);
                visualization.shipmentLabel.setY(currentY - 15);
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating map from event: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Add temperature data point to chart for given batch.
     * Uses numeric x-axis (seconds since chart epoch) for reliable live updates.
     */
    /**
     * Add a temperature data point to the chart for a batch.
     * Respects stopped simulations to prevent the graph from "running" after sim ends.
     */
    private void addTemperatureDataPoint(TemperatureComplianceEvent event) {
        if (temperatureChart == null) return;
        
        // Don't add data points for stopped simulations (prevents "running" chart)
        if (stoppedSimulations.contains(event.getBatchId())) {
            logger.debug("Ignoring temperature data for stopped simulation: {}", event.getBatchId());
            return;
        }
        
        try {
            // Get or create series for this batch
            XYChart.Series<Number, Number> series = temperatureSeriesMap.get(event.getBatchId());
            if (series == null) {
                series = new XYChart.Series<>();
                series.setName(event.getBatchId());
                temperatureSeriesMap.put(event.getBatchId(), series);
                temperatureChart.getData().add(series);
            }
            
            // Calculate x-value as seconds since chart epoch
            double secondsSinceEpoch = (System.currentTimeMillis() - chartEpochStart) / 1000.0;
            
            // Add data point with numeric x-value
            series.getData().add(new XYChart.Data<>(secondsSinceEpoch, event.getTemperature()));
            
            // Keep chart size reasonable - limit to last N points
            if (series.getData().size() > MAX_CHART_DATA_POINTS) {
                series.getData().remove(0);
            }
            
            // Format time for logging
            String timeLabel = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logger.debug("Added temperature point: {} = {}°C at {} (x={}s)", 
                event.getBatchId(), event.getTemperature(), timeLabel, 
                String.format("%.1f", secondsSinceEpoch));
        } catch (Exception e) {
            logger.error("Error adding temperature data point: {}", e.getMessage());
        }
    }

    private void setupNavigationButtons() {
        if (backToProducerButton != null) {
            backToProducerButton.setOnAction(e -> handleBackToProducer());
        }
        if (consumerButton != null) {
            consumerButton.setOnAction(e -> handleShowConsumer());
        }
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> handleLogout());
        }
    }

    private void setupMapContainer() {
        // Null-safe map visualization initialization
        if (mapContainer == null) {
            System.err.println("Warning: mapContainer is null, skipping map visualization setup");
            return;
        }
        
        try {
            // Clear any existing content
            mapContainer.getChildren().clear();

            // Draw route background
            Line routeLine = new Line(ORIGIN_X, ORIGIN_Y, DESTINATION_X, DESTINATION_Y);
            routeLine.setStroke(Color.LIGHTGRAY);
            routeLine.setStrokeWidth(3);
            routeLine.getStrokeDashArray().addAll(5d, 5d);
            mapContainer.getChildren().add(routeLine);

            // Draw origin point
            Circle origin = new Circle(ORIGIN_X, ORIGIN_Y, 8, Color.GREEN);
            mapContainer.getChildren().add(origin);
            Text originLabel = new Text(ORIGIN_X - 40, ORIGIN_Y - 10, "🏠 Farm");
            mapContainer.getChildren().add(originLabel);

            // Draw destination point
            Circle destination = new Circle(DESTINATION_X, DESTINATION_Y, 8, Color.BLUE);
            mapContainer.getChildren().add(destination);
            Text destinationLabel = new Text(DESTINATION_X - 20, DESTINATION_Y - 10, "🏢 Warehouse");
            mapContainer.getChildren().add(destinationLabel);

            // Initial instruction text
            Text instruction = new Text(MAP_WIDTH/2 - 100, MAP_HEIGHT - 10,
                    "Start simulation from Producer screen to see real-time tracking");
            instruction.setFill(Color.GRAY);
            mapContainer.getChildren().add(instruction);
        } catch (Exception e) {
            System.err.println("Error setting up map container: " + e.getMessage());
        }
    }

    private void startSyncService() {
        // Safe initialization of sync service
        try {
            syncExecutor = Executors.newSingleThreadScheduledExecutor();
            syncExecutor.scheduleAtFixedRate(this::syncWithDeliverySimulator, 0, 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Warning: Could not start sync service: " + e.getMessage());
            // Continue without sync service - manual refresh will still work
        }
    }

    private void syncWithDeliverySimulator() {
        if (deliverySimulator == null) {
            try {
                this.deliverySimulator = MainApp.getInstance().getApplicationContext().getDeliverySimulator();
            } catch (Exception e) {
                return; // Simulator not available yet
            }
        }

        Platform.runLater(() -> {
            // Update active shipments from simulator
            // NOTE: Do NOT clear map visualizations here - markers should persist
            // They are updated in place by updateMapMarkerPosition
            updateActiveShipmentsFromSimulator();

            // Update alerts based on simulation status
            updateAlertsFromSimulator();
        });
    }

    private void updateActiveShipmentsFromSimulator() {
        if (deliverySimulator == null) return;

        // Get all active simulations and update map
        for (String shipmentId : activeShipments.keySet()) {
            try {
                org.vericrop.service.DeliverySimulator.SimulationStatus status =
                        deliverySimulator.getSimulationStatus(shipmentId);

                if (status.isRunning() && status.getCurrentLocation() != null) {
                    updateShipmentOnMap(shipmentId, status);
                } else {
                    // Simulation ended, remove from map
                    activeShipments.remove(shipmentId);
                    
                    // Only add "delivery completed" alert if not already in stoppedSimulations
                    // This prevents duplicate alerts when onSimulationStopped has already fired
                    if (!stoppedSimulations.contains(shipmentId)) {
                        stoppedSimulations.add(shipmentId);
                        addAlert("✅ Delivery completed: " + shipmentId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting status for " + shipmentId + ": " + e.getMessage());
            }
        }

        // Check for new simulations (this would typically come from an event system)
        // For demo purposes, we'll simulate discovering new shipments
        checkForNewSimulations();
    }

    private void checkForNewSimulations() {
        // In a real system, you'd have an event bus or message queue
        // For now, we'll rely on the shared DeliverySimulator instance
        // New simulations are automatically detected when they appear in the simulator
    }

    private void updateShipmentOnMap(String shipmentId,
                                     org.vericrop.service.DeliverySimulator.SimulationStatus status) {
        // Calculate position on map based on progress
        double progress = (double) status.getCurrentWaypoint() / status.getTotalWaypoints();
        double currentX = ORIGIN_X + (DESTINATION_X - ORIGIN_X) * progress;
        double currentY = ORIGIN_Y; // Simple straight line for now

        MapVisualization visualization = activeShipments.get(shipmentId);
        if (visualization == null) {
            // Create new visualization
            visualization = new MapVisualization();
            visualization.shipmentCircle = new Circle(currentX, currentY, 6, Color.ORANGE);
            visualization.shipmentCircle.setUserData("shipment");

            visualization.shipmentLabel = new Text(currentX - 15, currentY - 15,
                    "🚚 " + shipmentId.substring(0, Math.min(8, shipmentId.length())));
            visualization.shipmentLabel.setUserData("shipment");
            visualization.shipmentLabel.setFill(Color.DARKBLUE);
            visualization.shipmentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");

            mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
            activeShipments.put(shipmentId, visualization);

            // Add to alerts
            addAlert("🔄 Tracking started: " + shipmentId);
        } else {
            // Update existing visualization
            visualization.shipmentCircle.setCenterX(currentX);
            visualization.shipmentCircle.setCenterY(currentY);
            visualization.shipmentLabel.setX(currentX - 15);
            visualization.shipmentLabel.setY(currentY - 15);
        }

        // Update status label with environmental data
        if (status.getCurrentLocation() != null) {
            String statusText = String.format("📍 %s | 🌡%.1f°C | 💧%.1f%%",
                    status.getCurrentLocation().getLocation().getName(),
                    status.getCurrentLocation().getTemperature(),
                    status.getCurrentLocation().getHumidity());

            visualization.shipmentLabel.setText("🚚 " + shipmentId.substring(0, Math.min(8, shipmentId.length())) + "\n" + statusText);

            // Check for environmental alerts
            checkEnvironmentalAlerts(shipmentId, status.getCurrentLocation());
        }

        // Update shipments table
        updateShipmentsTable(shipmentId, status);
    }

    private void checkEnvironmentalAlerts(String shipmentId, org.vericrop.service.DeliverySimulator.RouteWaypoint waypoint) {
        // Temperature alerts
        if (waypoint.getTemperature() < 2.0) {
            addAlert("❄️ LOW TEMP: " + shipmentId + " at " + waypoint.getTemperature() + "°C");
        } else if (waypoint.getTemperature() > 8.0) {
            addAlert("🔥 HIGH TEMP: " + shipmentId + " at " + waypoint.getTemperature() + "°C");
        }

        // Humidity alerts
        if (waypoint.getHumidity() < 50.0) {
            addAlert("🏜️ LOW HUMIDITY: " + shipmentId + " at " + waypoint.getHumidity() + "%");
        } else if (waypoint.getHumidity() > 90.0) {
            addAlert("💦 HIGH HUMIDITY: " + shipmentId + " at " + waypoint.getHumidity() + "%");
        }
    }

    private void updateShipmentsTable(String shipmentId,
                                      org.vericrop.service.DeliverySimulator.SimulationStatus status) {
        // Find or create shipment entry
        Shipment existing = shipments.stream()
                .filter(s -> s.getBatchId().contains(shipmentId))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            // Create new shipment entry
            String location = status.getCurrentLocation() != null ?
                    status.getCurrentLocation().getLocation().getName() : "Unknown";
            double temp = status.getCurrentLocation() != null ?
                    status.getCurrentLocation().getTemperature() : 0.0;
            double humidity = status.getCurrentLocation() != null ?
                    status.getCurrentLocation().getHumidity() : 0.0;

            shipments.add(new Shipment(
                    shipmentId + " (Live)",
                    "IN_TRANSIT",
                    location,
                    temp,
                    humidity,
                    calculateETA(status),
                    "TRUCK_" + Math.abs(shipmentId.hashCode() % 1000)
            ));
        } else {
            // Update existing entry
            if (status.getCurrentLocation() != null) {
                // Update with real data from simulator
                int index = shipments.indexOf(existing);
                shipments.set(index, new Shipment(
                        existing.getBatchId(),
                        status.isRunning() ? "IN_TRANSIT" : "DELIVERED",
                        status.getCurrentLocation().getLocation().getName(),
                        status.getCurrentLocation().getTemperature(),
                        status.getCurrentLocation().getHumidity(),
                        status.isRunning() ? calculateETA(status) : "DELIVERED",
                        existing.getVehicle()
                ));
            }
        }
    }

    private String calculateETA(org.vericrop.service.DeliverySimulator.SimulationStatus status) {
        if (!status.isRunning() || status.getCurrentWaypoint() >= status.getTotalWaypoints()) {
            return "ARRIVED";
        }

        double progress = (double) status.getCurrentWaypoint() / status.getTotalWaypoints();
        double remaining = 1.0 - progress;

        // Simple ETA calculation (in minutes)
        int etaMinutes = (int) (remaining * 120); // Assuming 2 hour total trip
        return etaMinutes + " min";
    }

    private void updateAlertsFromSimulator() {
        // Additional alert logic can be added here
        // For example, checking for stalled shipments or other conditions
    }

    private void addAlert(String alertMessage) {
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            // Check if this alert already exists to avoid duplicates
            String fullAlert = "[" + timestamp + "] " + alertMessage;
            if (!alerts.contains(fullAlert)) {
                alerts.add(0, fullAlert);
                // Keep only recent alerts
                if (alerts.size() > MAX_ALERT_ITEMS) {
                    alerts.remove(alerts.size() - 1);
                }
            }
        });
    }

    private void setupShipmentsTable() {
        // Configure table columns programmatically with cell value factories
        if (shipmentsTable != null && shipmentsTable.getColumns().size() >= 7) {
            try {
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, String> batchIdCol = 
                    (javafx.scene.control.TableColumn<Shipment, String>) shipmentsTable.getColumns().get(0);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, String> statusCol = 
                    (javafx.scene.control.TableColumn<Shipment, String>) shipmentsTable.getColumns().get(1);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, String> locationCol = 
                    (javafx.scene.control.TableColumn<Shipment, String>) shipmentsTable.getColumns().get(2);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, Number> tempCol = 
                    (javafx.scene.control.TableColumn<Shipment, Number>) shipmentsTable.getColumns().get(3);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, Number> humidityCol = 
                    (javafx.scene.control.TableColumn<Shipment, Number>) shipmentsTable.getColumns().get(4);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, String> etaCol = 
                    (javafx.scene.control.TableColumn<Shipment, String>) shipmentsTable.getColumns().get(5);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Shipment, String> vehicleCol = 
                    (javafx.scene.control.TableColumn<Shipment, String>) shipmentsTable.getColumns().get(6);
                
                batchIdCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getBatchId()));
                statusCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));
                locationCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLocation()));
                tempCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getTemperature()));
                humidityCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getHumidity()));
                etaCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getEta()));
                vehicleCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getVehicle() != null ? cellData.getValue().getVehicle() : "N/A"));
            } catch (ClassCastException | IndexOutOfBoundsException e) {
                System.err.println("Warning: Could not configure shipments table columns: " + e.getMessage());
            }
        }
        
        // Load demo data only if flag is set
        if (shouldLoadDemoData()) {
            shipments.addAll(
                    new Shipment("BATCH_A2386 (demo)", "IN_TRANSIT", "Highway A - Mile 120", 4.2, 65, "17:30", "TRUCK_001"),
                    new Shipment("BATCH_A2387 (demo)", "AT_WAREHOUSE", "Metro Fresh Warehouse", 3.8, 62, "ARRIVED", null),
                    new Shipment("BATCH_A2388 (demo)", "DELIVERED", "FreshMart Downtown", 4.1, 63, "DELIVERED", null)
            );
        }
        shipmentsTable.setItems(shipments);
    }

    private void setupAlertsList() {
        if (shouldLoadDemoData()) {
            alerts.addAll(
                    "✓ No active alerts - System normal (demo)",
                    "✓ Last temperature check: 4.2°C ✅ (demo)",
                    "✓ Humidity within optimal range ✅ (demo)"
            );
        } else {
            alerts.add("No alerts. System ready for real shipments.");
        }
        alertsList.setItems(alerts);
    }

    private boolean shouldLoadDemoData() {
        String loadDemo = System.getProperty("vericrop.loadDemo");
        if ("true".equalsIgnoreCase(loadDemo)) {
            return true;
        }
        String loadDemoEnv = System.getenv("VERICROP_LOAD_DEMO");
        return "true".equalsIgnoreCase(loadDemoEnv);
    }

    private void setupReportCombo() {
        reportTypeCombo.getItems().addAll(
                "Shipment Summary",
                "Temperature Log",
                "Quality Compliance",
                "Delivery Performance",
                "Simulation Log"
        );
    }
    
    /**
     * Setup export format dropdown with TXT and CSV options.
     * Defaults to TXT format.
     */
    private void setupExportFormatCombo() {
        if (exportFormatCombo != null) {
            exportFormatCombo.getItems().addAll("TXT", "CSV");
            exportFormatCombo.setValue("TXT"); // Default to TXT
        }
    }
    
    /**
     * Initialize persistence and report export services.
     */
    private void initializePersistenceServices() {
        try {
            persistenceService = new ShipmentPersistenceService();
            reportExportService = new ReportExportService(persistenceService);
            logger.info("Persistence services initialized. Data directory: {}", persistenceService.getDataDirectoryPath());
        } catch (Exception e) {
            logger.warn("Failed to initialize persistence services: {}", e.getMessage(), e);
        }
    }

    private void setupTemperatureChart() {
        // Initialize chart epoch for calculating x-values in seconds
        chartEpochStart = System.currentTimeMillis();
        
        if (temperatureChart != null) {
            temperatureChart.setTitle("Temperature Monitoring");
            temperatureChart.setLegendVisible(true);
            
            // Set up tick label formatter on x-axis to show HH:mm:ss timestamps
            if (temperatureXAxis != null) {
                temperatureXAxis.setAutoRanging(true);
                temperatureXAxis.setTickLabelFormatter(new StringConverter<Number>() {
                    @Override
                    public String toString(Number secondsSinceEpoch) {
                        if (secondsSinceEpoch == null) return "";
                        return formatSecondsAsWallClockTime(secondsSinceEpoch.longValue());
                    }
                    
                    @Override
                    public Number fromString(String string) {
                        // Not needed for display-only formatter
                        return 0;
                    }
                });
            }

            if (shouldLoadDemoData()) {
                // Demo data uses small numeric x-values (0, 4, 8, etc. seconds elapsed since chart start)
                // to show sample temperature readings for demonstration purposes
                XYChart.Series<Number, Number> series1 = new XYChart.Series<>();
                series1.setName("BATCH_A2386 (demo)");
                series1.getData().add(new XYChart.Data<>(0, 4.2));
                series1.getData().add(new XYChart.Data<>(4, 4.5));
                series1.getData().add(new XYChart.Data<>(8, 4.8));
                series1.getData().add(new XYChart.Data<>(12, 5.1));
                series1.getData().add(new XYChart.Data<>(16, 4.6));
                series1.getData().add(new XYChart.Data<>(20, 4.3));

                XYChart.Series<Number, Number> series2 = new XYChart.Series<>();
                series2.setName("BATCH_A2387 (demo)");
                series2.getData().add(new XYChart.Data<>(0, 3.8));
                series2.getData().add(new XYChart.Data<>(4, 3.9));
                series2.getData().add(new XYChart.Data<>(8, 4.1));
                series2.getData().add(new XYChart.Data<>(12, 4.3));
                series2.getData().add(new XYChart.Data<>(16, 4.0));
                series2.getData().add(new XYChart.Data<>(20, 3.8));

                temperatureChart.getData().addAll(series1, series2);
            }
        }
    }
    
    /**
     * Convert seconds since chart epoch to wall clock time formatted as HH:mm:ss.
     * 
     * @param secondsSinceEpoch seconds elapsed since chartEpochStart
     * @return formatted time string in HH:mm:ss format
     */
    private String formatSecondsAsWallClockTime(long secondsSinceEpoch) {
        long actualTimestamp = chartEpochStart + (secondsSinceEpoch * 1000);
        java.time.LocalTime wallClockTime = java.time.Instant.ofEpochMilli(actualTimestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime();
        return wallClockTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // Navigation methods
    @FXML
    private void handleBackToProducer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showProducerScreen();
        }
    }

    @FXML
    private void handleShowConsumer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showConsumerScreen();
        }
    }

    @FXML
    private void handleLogout() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("login.fxml");
        }
    }

    // Action methods
    @FXML
    private void handleRefresh() {
        Platform.runLater(() -> {
            syncWithDeliverySimulator();
            showAlert(Alert.AlertType.INFORMATION, "Refresh Complete",
                    "Logistics data has been refreshed with latest simulation data");
        });
    }

    @FXML
    private void handleExportReport() {
        String reportType = reportTypeCombo.getValue();
        if (reportType == null) {
            showAlert(Alert.AlertType.WARNING, "No Report Selected",
                    "Please select a report type before exporting");
            return;
        }
        
        // Get date range
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        
        if (startDate == null || endDate == null) {
            // Use default date range (last 30 days)
            endDate = LocalDate.now();
            startDate = endDate.minusDays(30);
        }
        
        // Get export format using enum parsing
        String formatStr = exportFormatCombo != null && exportFormatCombo.getValue() != null 
                ? exportFormatCombo.getValue() : "TXT";
        ReportExportService.ExportFormat format = parseExportFormat(formatStr);
        
        // Map report type string to enum using the static map
        ReportExportService.ReportType reportTypeEnum = REPORT_TYPE_MAP.get(reportType);
        if (reportTypeEnum == null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Report Type",
                    "Unknown report type: " + reportType);
            return;
        }
        
        try {
            if (reportExportService == null) {
                showAlert(Alert.AlertType.ERROR, "Export Error",
                        "Report export service not available. Please restart the application.");
                return;
            }
            
            File exportedFile = reportExportService.exportReport(reportTypeEnum, startDate, endDate, format);
            
            String message = String.format(
                    "Report '%s' exported successfully!\n\nFile: %s\nFormat: %s\nDate Range: %s to %s",
                    reportType, exportedFile.getName(), format, startDate, endDate);
            showAlert(Alert.AlertType.INFORMATION, "Export Complete", message);
            
            // Add alert to the alerts list
            alerts.add(0, "📄 Exported report: " + exportedFile.getName());
            
        } catch (Exception e) {
            logger.error("Failed to export report: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Export Failed",
                    "Failed to export report: " + e.getMessage());
        }
    }
    
    /**
     * Parse export format string to enum with proper fallback.
     */
    private ReportExportService.ExportFormat parseExportFormat(String formatStr) {
        if (formatStr == null || formatStr.trim().isEmpty()) {
            return ReportExportService.ExportFormat.TXT;
        }
        try {
            return ReportExportService.ExportFormat.valueOf(formatStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown export format '{}', defaulting to TXT", formatStr);
            return ReportExportService.ExportFormat.TXT;
        }
    }

    @FXML
    private void handleAcknowledgeAlerts() {
        Platform.runLater(() -> {
            int alertCount = alerts.size();
            alerts.clear();
            alerts.add("All alerts acknowledged ✅ (" + alertCount + " alerts cleared)");
            showAlert(Alert.AlertType.INFORMATION, "Alerts Acknowledged",
                    alertCount + " alerts have been acknowledged");
        });
    }

    @FXML
    private void handleSimulateAlert() {
        // Show dialog to select alert scenario
        Platform.runLater(() -> {
            try {
                // Create alert scenario selection dialog
                ChoiceDialog<String> dialog = new ChoiceDialog<>("Temperature Breach",
                    "Temperature Breach", "Humidity Breach", "Quality Drop", "Delayed Delivery");
                dialog.setTitle("Select Alert Scenario");
                dialog.setHeaderText("Choose an alert scenario to simulate");
                dialog.setContentText("Scenario:");
                
                Optional<String> result = dialog.showAndWait();
                if (!result.isPresent()) {
                    return;
                }
                
                String scenario = result.get();
                String batchId = getActiveBatchIdOrDefault();
                
                // Generate appropriate alert based on scenario
                QualityAlertEvent alertEvent;
                String alertMessage;
                
                switch (scenario) {
                    case "Temperature Breach":
                        double tempValue = 8.5;
                        alertEvent = new QualityAlertEvent(
                            batchId, "TEMPERATURE_BREACH", "HIGH",
                            "Temperature exceeded safe threshold",
                            tempValue, 8.0
                        );
                        alertEvent.setSensorId("TEMP-SENSOR-001");
                        alertEvent.setLocation("Highway Mile 30");
                        alertMessage = String.format("🌡️ TEMPERATURE BREACH: %s - %.1f°C (threshold: 8.0°C)", 
                            batchId, tempValue);
                        break;
                        
                    case "Humidity Breach":
                        double humidityValue = 92.0;
                        alertEvent = new QualityAlertEvent(
                            batchId, "HUMIDITY_BREACH", "MEDIUM",
                            "Humidity exceeded optimal range",
                            humidityValue, 90.0
                        );
                        alertEvent.setSensorId("HUMID-SENSOR-001");
                        alertEvent.setLocation("Highway Mile 30");
                        alertMessage = String.format("💧 HUMIDITY BREACH: %s - %.1f%% (threshold: 90.0%%)", 
                            batchId, humidityValue);
                        break;
                        
                    case "Quality Drop":
                        double qualityValue = 65.0;
                        alertEvent = new QualityAlertEvent(
                            batchId, "QUALITY_DROP", "CRITICAL",
                            "Product quality has degraded below acceptable level",
                            qualityValue, 70.0
                        );
                        alertEvent.setSensorId("QUALITY-SENSOR-001");
                        alertEvent.setLocation("Distribution Center");
                        alertMessage = String.format("⚠️ QUALITY DROP: %s - %.1f%% (threshold: 70.0%%)", 
                            batchId, qualityValue);
                        break;
                        
                    case "Delayed Delivery":
                        double delayMinutes = 45.0;
                        alertEvent = new QualityAlertEvent(
                            batchId, "DELIVERY_DELAY", "LOW",
                            "Delivery is experiencing unexpected delays",
                            delayMinutes, 30.0
                        );
                        alertEvent.setLocation("Highway Mile 50");
                        alertMessage = String.format("⏰ DELAYED DELIVERY: %s - %.0f min delay", 
                            batchId, delayMinutes);
                        break;
                        
                    default:
                        return;
                }
                
                // Publish alert to Kafka if producer is available
                if (qualityAlertProducer != null) {
                    try {
                        qualityAlertProducer.sendQualityAlert(alertEvent);
                        System.out.println("✅ Published alert to Kafka: " + alertEvent);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to publish alert to Kafka: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("⚠️ Alert producer not available, alert not published to Kafka");
                }
                
                // Update Live Alerts Panel immediately
                String timestamp = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                String fullAlert = "[" + timestamp + "] " + alertMessage;
                alerts.add(0, fullAlert);
                
                // Keep only recent alerts
                if (alerts.size() > MAX_ALERT_ITEMS) {
                    alerts.remove(alerts.size() - 1);
                }
                
                // Show confirmation dialog
                showAlert(Alert.AlertType.WARNING, "Alert Triggered",
                        "Alert scenario '" + scenario + "' has been triggered:\n\n" + alertMessage + 
                        "\n\nAlert published to Kafka topic and Live Alerts Panel updated.");
                
            } catch (Exception e) {
                System.err.println("❌ Error simulating alert: " + e.getMessage());
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Alert Error",
                        "Failed to simulate alert: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get an active batch ID or return a default value for alert simulation.
     * Tries to get a batch from active shipments first, otherwise uses a default.
     */
    private String getActiveBatchIdOrDefault() {
        // Try to get an active shipment batch ID
        if (!activeShipments.isEmpty()) {
            return activeShipments.keySet().iterator().next();
        }
        // Try to get from shipments table
        if (!shipments.isEmpty()) {
            return shipments.get(0).getBatchId();
        }
        // Default batch ID with short UUID suffix
        return "BATCH_DEMO_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @FXML
    private void handleGenerateReport() {
        final String reportType = reportTypeCombo.getValue() != null ?
                reportTypeCombo.getValue() : "General Report";
        final String dateRange;
        if (startDatePicker.getValue() != null && endDatePicker.getValue() != null) {
            dateRange = "Date Range: " + startDatePicker.getValue() + " to " + endDatePicker.getValue() + "\n";
        } else {
            dateRange = "";
        }

        Platform.runLater(() -> {
            reportArea.setText("=== " + reportType + " ===\n\n" +
                    dateRange +
                    "• Total Shipments: " + shipments.size() + "\n" +
                    "• Active Simulations: " + activeShipments.size() + "\n" +
                    "• In Transit: " + countByStatus("IN_TRANSIT") + "\n" +
                    "• At Warehouse: " + countByStatus("AT_WAREHOUSE") + "\n" +
                    "• Delivered: " + countByStatus("DELIVERED") + "\n" +
                    "• Avg Temperature: " + calculateAvgTemperature() + "°C\n" +
                    "• Compliance: 100%\n" +
                    "• Generated: " + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        });
    }

    private long countByStatus(String status) {
        return shipments.stream().filter(s -> s.getStatus().equals(status)).count();
    }

    private String calculateAvgTemperature() {
        double avg = shipments.stream()
                .mapToDouble(Shipment::getTemperature)
                .average()
                .orElse(0.0);
        return String.format("%.1f", avg);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        // Unregister from SimulationManager
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager.getInstance().unregisterListener(this);
            }
        } catch (Exception e) {
            System.err.println("Error unregistering from SimulationManager: " + e.getMessage());
        }
        
        // Close instance registry
        if (instanceRegistry != null) {
            try {
                instanceRegistry.close();
            } catch (Exception e) {
                System.err.println("Error closing instance registry: " + e.getMessage());
            }
        }
        
        // Stop Kafka consumers
        if (mapSimulationConsumer != null) {
            mapSimulationConsumer.stop();
        }
        if (temperatureComplianceConsumer != null) {
            temperatureComplianceConsumer.stop();
        }
        if (simulationControlConsumer != null) {
            simulationControlConsumer.stop();
        }
        if (kafkaConsumerExecutor != null && !kafkaConsumerExecutor.isShutdown()) {
            kafkaConsumerExecutor.shutdownNow();
        }
        
        // Close Kafka producer
        if (qualityAlertProducer != null) {
            try {
                qualityAlertProducer.close();
            } catch (Exception e) {
                System.err.println("Error closing alert producer: " + e.getMessage());
            }
        }
        
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdownNow();
        }
        
        // Stop all animations
        for (MapVisualization viz : activeShipments.values()) {
            if (viz.animation != null) {
                viz.animation.stop();
            }
        }
        
        System.out.println("🔴 LogisticsController cleanup complete");
    }
    
    // ========== SimulationListener Implementation ==========
    
    @Override
    public void onSimulationStarted(String batchId, String farmerId) {
        // Remove from stopped simulations if restarting (allows chart updates again)
        stoppedSimulations.remove(batchId);
        logger.info("Simulation started for batch: {} from farmer: {}", batchId, farmerId);
        
        Platform.runLater(() -> {
            alerts.add(0, "🚚 Delivery simulation started for: " + batchId);
            
            // Initialize map marker at origin
            initializeMapMarker(batchId);
            
            // Initialize temperature chart series
            initializeTemperatureChartSeries(batchId);
            
            // Initialize timeline to starting state
            updateTimeline(batchId, 0.0, "Started");
            
            // Persist simulation start
            persistSimulationStart(batchId, farmerId);
            
            // Persist shipment
            persistShipmentUpdate(batchId, farmerId, "CREATED", "Origin", 4.0, 65.0, "Starting", null);
            
            logger.info("LogisticsController: Simulation started - {}", batchId);
        });
    }
    
    /**
     * Persist simulation start event.
     */
    private void persistSimulationStart(String batchId, String farmerId) {
        if (persistenceService == null) return;
        
        try {
            PersistedSimulation simulation = new PersistedSimulation(batchId, farmerId, "NORMAL");
            simulation.setStatus("STARTED");
            simulation.setInitialQuality(100.0);
            persistenceService.saveSimulation(simulation);
            System.out.println("📝 Persisted simulation start: " + batchId);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to persist simulation start: " + e.getMessage());
        }
    }
    
    /**
     * Persist shipment update.
     */
    private void persistShipmentUpdate(String batchId, String farmerId, String status, 
                                       String location, double temperature, double humidity,
                                       String eta, String vehicle) {
        if (persistenceService == null) return;
        
        try {
            PersistedShipment shipment = persistenceService.getShipment(batchId)
                    .orElse(new PersistedShipment(batchId, status, location, temperature, humidity, eta, vehicle));
            
            shipment.setFarmerId(farmerId);
            shipment.setStatus(status);
            shipment.setLocation(location);
            shipment.setTemperature(temperature);
            shipment.setHumidity(humidity);
            shipment.setEta(eta);
            if (vehicle != null) {
                shipment.setVehicle(vehicle);
            }
            
            persistenceService.saveShipment(shipment);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to persist shipment update: " + e.getMessage());
        }
    }
    
    /**
     * Initialize map marker at the origin point.
     * Clears any existing trail and resets status tracking.
     * This ensures a clean slate when restarting or starting a new simulation.
     */
    private void initializeMapMarker(String batchId) {
        if (mapContainer == null) {
            System.err.println("⚠️  Map container is null, cannot initialize marker");
            return;
        }
        
        try {
            // Remove any existing marker and trail for this batch
            MapVisualization existing = activeShipments.get(batchId);
            if (existing != null) {
                System.out.println("🧹 Cleaning up existing marker for batch: " + batchId);
                // Stop any running animation
                if (existing.animation != null) {
                    existing.animation.stop();
                }
                // Clean up old trail points
                for (Circle trailPoint : existing.trailPoints) {
                    mapContainer.getChildren().remove(trailPoint);
                }
                existing.trailPoints.clear();
                mapContainer.getChildren().removeAll(existing.shipmentCircle, existing.shipmentLabel);
            }
            
            // Create new marker at origin
            MapVisualization visualization = new MapVisualization();
            visualization.shipmentCircle = new Circle(ORIGIN_X, ORIGIN_Y, 6, Color.ORANGE);
            visualization.shipmentCircle.setUserData("shipment");
            
            String displayId = batchId.length() > 8 ? batchId.substring(0, 8) : batchId;
            visualization.shipmentLabel = new Text(ORIGIN_X - 15, ORIGIN_Y - 15, "🚚 " + displayId);
            visualization.shipmentLabel.setUserData("shipment");
            visualization.shipmentLabel.setFill(Color.DARKBLUE);
            
            // Initialize status tracking (starts at Created)
            // This is critical for preventing duplicate alerts
            visualization.lastStatus = "Created";
            
            mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
            activeShipments.put(batchId, visualization);
            
            System.out.println("✅ Initialized map marker at origin for: " + batchId);
        } catch (Exception e) {
            System.err.println("❌ Error initializing map marker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update timeline based on current simulation progress.
     * Shows visual state progression: Created → In Transit → At Warehouse → Delivered
     * @param progress Progress as percentage (0-100)
     * NOTE: Caller must ensure this is called on JavaFX Application Thread
     */
    private void updateTimeline(String batchId, double progress, String status) {
        if (timelineContainer == null) return;
        
        try {
            // Clear existing timeline
            timelineContainer.getChildren().clear();
                
                // Determine which states are completed based on progress
                boolean createdComplete = true;
                boolean inTransitComplete = progress >= PROGRESS_DEPARTING_THRESHOLD;
                boolean approachingComplete = progress >= PROGRESS_APPROACHING_THRESHOLD;
                boolean deliveredComplete = progress >= PROGRESS_COMPLETE;
                
                // Add "Created" state
                addTimelineItem("Created", 
                    "Batch " + batchId + " created at origin",
                    createdComplete);
                
                // Add "In Transit" state
                addTimelineItem("In Transit", 
                    String.format("Delivery progress: %.0f%%", progress),
                    inTransitComplete);
                
                // Add "Approaching Warehouse" state
                addTimelineItem("Approaching", 
                    progress >= PROGRESS_APPROACHING_THRESHOLD ? "Nearing destination" : "Not yet approaching",
                    approachingComplete);
                
                // Add "At Warehouse / Delivered" state
                // Fix Bug #3: Only show "Delivered" title when actually delivered (progress >= 100%)
                String finalStateTitle = deliveredComplete ? "Delivered" : "Pending Delivery";
                String finalStateDesc = deliveredComplete ? "Delivery complete" : "ETA: " + calculateETA(progress);
                addTimelineItem(finalStateTitle, finalStateDesc, deliveredComplete);
                
        } catch (Exception e) {
            System.err.println("Error updating timeline: " + e.getMessage());
        }
    }
    
    /**
     * Add a timeline item to the timeline container.
     */
    private void addTimelineItem(String title, String description, boolean completed) {
        HBox timelineItem = new HBox(10);
        timelineItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        timelineItem.getStyleClass().add("timeline-item");
        
        // Bullet point (filled if completed, empty if not)
        Label bullet = new Label(completed ? "●" : "○");
        bullet.setStyle(completed ? 
            "-fx-text-fill: #10b981; -fx-font-weight: bold;" : 
            "-fx-text-fill: #64748b;");
        
        // Title and description
        VBox textBox = new VBox();
        Label titleLabel = new Label(title);
        titleLabel.setStyle(completed ? 
            "-fx-font-weight: bold;" : 
            "-fx-text-fill: #64748b;");
        Label descLabel = new Label(description);
        descLabel.setStyle(completed ? "" : "-fx-text-fill: #64748b;");
        
        textBox.getChildren().addAll(titleLabel, descLabel);
        timelineItem.getChildren().addAll(bullet, textBox);
        timelineContainer.getChildren().add(timelineItem);
    }
    
    /**
     * Calculate ETA string based on progress (percentage 0-100).
     */
    private String calculateETA(double progress) {
        if (progress >= PROGRESS_COMPLETE) return "ARRIVED";
        // Progress is percentage, convert to decimal for calculation
        double remaining = 1.0 - (progress / 100.0);
        int etaMinutes = (int) (remaining * ESTIMATED_TOTAL_TRIP_MINUTES);
        return etaMinutes + " min";
    }
    
    /**
     * Initialize temperature chart series for this batch.
     * Clears demo data on first real simulation.
     * Thread-safe: can be called from Kafka consumer threads.
     */
    private void initializeTemperatureChartSeries(String batchId) {
        if (temperatureChart == null) {
            System.err.println("⚠️  Temperature chart is null, cannot initialize series");
            return;
        }
        
        // Ensure chart operations run on JavaFX thread
        Platform.runLater(() -> {
            try {
                // Clear demo data if this is the first real simulation
                if (temperatureSeriesMap.isEmpty() && temperatureChart.getData().size() > 0) {
                    // Check if demo data is present
                    boolean hasDemo = temperatureChart.getData().stream()
                        .anyMatch(series -> series.getName().contains(DEMO_DATA_SUFFIX));
                    if (hasDemo) {
                        System.out.println("🧹 Clearing demo data from temperature chart");
                        temperatureChart.getData().clear();
                    }
                }
                
                // Check if series already exists
                if (temperatureSeriesMap.containsKey(batchId)) {
                    System.out.println("📊 Temperature chart series already exists for: " + batchId);
                    return;
                }
                
                // Create new series for this batch with numeric x-values
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(batchId);
                
                // Add to chart and tracking map
                temperatureChart.getData().add(series);
                temperatureSeriesMap.put(batchId, series);
                
                System.out.println("✅ Initialized temperature chart series for: " + batchId);
            } catch (Exception e) {
                System.err.println("❌ Error initializing temperature chart: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        Platform.runLater(() -> {
            // Update map marker position with smooth animation
            updateMapMarkerPosition(batchId, progress, currentLocation);
            
            // Update shipments table if it exists
            updateShipmentsTableRow(batchId, progress, currentLocation);
            
            // Update timeline to show current state
            String status = progress < PROGRESS_EN_ROUTE_THRESHOLD ? "In Transit - Departing" : 
                           progress < PROGRESS_APPROACHING_THRESHOLD ? "In Transit - En Route" :
                           progress < PROGRESS_AT_WAREHOUSE_THRESHOLD ? "In Transit - Approaching" : 
                           progress >= PROGRESS_COMPLETE ? "Delivered" : "At Warehouse";
            updateTimeline(batchId, progress, status);
            
            System.out.println("LogisticsController: Progress update - " + batchId + " at " + progress + "% - " + currentLocation);
        });
    }
    
    /**
     * Update map marker position with smooth animation based on progress.
     * Animates marker from previous position to new position using JavaFX Timeline.
     */
    private void updateMapMarkerPosition(String batchId, double progress, String currentLocation) {
        if (mapContainer == null) return;
        
        try {
            // Calculate new position based on progress (linear interpolation along route)
            double progressFraction = progress / 100.0;
            double newX = ORIGIN_X + (DESTINATION_X - ORIGIN_X) * progressFraction;
            double newY = ORIGIN_Y; // Simple horizontal route for now
            
            MapVisualization visualization = activeShipments.get(batchId);
            if (visualization == null) {
                // Create new visualization at initial position
                visualization = new MapVisualization();
                visualization.shipmentCircle = new Circle(ORIGIN_X, ORIGIN_Y, 6, Color.ORANGE);
                visualization.shipmentCircle.setUserData("shipment");
                
                String displayId = batchId.length() > 8 ? batchId.substring(0, 8) : batchId;
                visualization.shipmentLabel = new Text(ORIGIN_X - 15, ORIGIN_Y - 15, "🚚 " + displayId);
                visualization.shipmentLabel.setUserData("shipment");
                visualization.shipmentLabel.setFill(Color.DARKBLUE);
                
                mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
                activeShipments.put(batchId, visualization);
                
                System.out.println("Created new map marker for: " + batchId + " at " + progress + "%");
            }
            
            // Stop any existing animation for this marker
            if (visualization.animation != null) {
                visualization.animation.stop();
            }
            
            // Get current position
            double currentX = visualization.shipmentCircle.getCenterX();
            double currentY = visualization.shipmentCircle.getCenterY();
            
            // Create smooth animation from current position to new position
            // Duration based on distance traveled (smoother for small movements)
            double dx = newX - currentX;
            double dy = newY - currentY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double animationDuration = Math.max(500, Math.min(2000, distance * 10)); // 0.5-2 seconds
            
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(animationDuration),
                    new KeyValue(visualization.shipmentCircle.centerXProperty(), newX),
                    new KeyValue(visualization.shipmentCircle.centerYProperty(), newY)
                )
            );
            
            // Also animate label position
            timeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(animationDuration),
                    new KeyValue(visualization.shipmentLabel.xProperty(), newX - 15),
                    new KeyValue(visualization.shipmentLabel.yProperty(), newY - 15)
                )
            );
            
            // Update label text with current location if available
            if (currentLocation != null && !currentLocation.isEmpty()) {
                String displayId = batchId.length() > 8 ? batchId.substring(0, 8) : batchId;
                visualization.shipmentLabel.setText("🚚 " + displayId);
            }
            
            visualization.animation = timeline;
            timeline.play();
            
            // Center camera on checkpoint when reached
            centerCameraOnCheckpoint(progress, currentLocation);
            
            System.out.println("Animating marker for: " + batchId + " to " + progress + "% (" + newX + ", " + newY + ")");
            
        } catch (Exception e) {
            System.err.println("Error updating map marker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Center camera/view on checkpoint when truck reaches it.
     * Highlights checkpoint markers and provides visual focus.
     * @param progress Current progress as percentage (0-100)
     * @param locationName Name of current location
     */
    private void centerCameraOnCheckpoint(double progress, String locationName) {
        if (mapContainer == null) return;
        
        try {
            // Determine if at a major checkpoint
            boolean atWarehouse = progress >= PROGRESS_AT_WAREHOUSE_THRESHOLD;
            boolean atFarm = progress < PROGRESS_DEPARTING_THRESHOLD;
            
            if (atWarehouse) {
                // Highlight warehouse checkpoint
                highlightCheckpoint("warehouse", DESTINATION_X, DESTINATION_Y);
                System.out.println("📍 Truck reached checkpoint: Warehouse (progress: " + progress + "%)");
            } else if (atFarm) {
                // Highlight farm checkpoint
                highlightCheckpoint("farm", ORIGIN_X, ORIGIN_Y);
            }
        } catch (Exception e) {
            System.err.println("Error centering camera on checkpoint: " + e.getMessage());
        }
    }
    
    /**
     * Highlight a checkpoint on the map by pulsing its marker.
     * @param checkpointName Name of checkpoint (for tracking)
     * @param x X coordinate of checkpoint
     * @param y Y coordinate of checkpoint
     */
    private void highlightCheckpoint(String checkpointName, double x, double y) {
        if (mapContainer == null) return;
        
        try {
            // Find the checkpoint circle in map container
            Circle checkpointCircle = null;
            final double COORDINATE_TOLERANCE = 1.0;
            
            for (javafx.scene.Node node : mapContainer.getChildren()) {
                if (node instanceof Circle) {
                    Circle circle = (Circle) node;
                    // Check if this is a checkpoint (not a shipment marker)
                    if (circle.getUserData() == null && 
                        Math.abs(circle.getCenterX() - x) < COORDINATE_TOLERANCE && 
                        Math.abs(circle.getCenterY() - y) < COORDINATE_TOLERANCE) {
                        checkpointCircle = circle;
                        break;
                    }
                }
            }
            
            if (checkpointCircle == null) return;
            
            // Store original color before animation to ensure proper restoration
            Circle finalCheckpoint = checkpointCircle;
            Color originalColor = (Color) finalCheckpoint.getFill();
            double originalRadius = finalCheckpoint.getRadius();
            
            // Create pulsing animation to highlight checkpoint
            Timeline pulseAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(finalCheckpoint.radiusProperty(), originalRadius),
                    new KeyValue(finalCheckpoint.fillProperty(), originalColor)
                ),
                new KeyFrame(Duration.millis(500),
                    new KeyValue(finalCheckpoint.radiusProperty(), 12),
                    new KeyValue(finalCheckpoint.fillProperty(), Color.GOLD)
                ),
                new KeyFrame(Duration.millis(1000),
                    new KeyValue(finalCheckpoint.radiusProperty(), originalRadius),
                    new KeyValue(finalCheckpoint.fillProperty(), originalColor)
                )
            );
            pulseAnimation.setCycleCount(2); // Pulse twice
            pulseAnimation.play();
            
            System.out.println("Highlighted checkpoint: " + checkpointName);
        } catch (Exception e) {
            System.err.println("Error highlighting checkpoint: " + e.getMessage());
        }
    }
    
    /**
     * Update shipments table row for a specific batch.
     * Uses index-based update for efficiency.
     * @param progress Progress as percentage (0-100)
     */
    private void updateShipmentsTableRow(String batchId, double progress, String currentLocation) {
        if (shipmentsTable == null) return;
        
        try {
            // Determine status based on progress
            String status = progress < PROGRESS_EN_ROUTE_THRESHOLD ? "In Transit - Departing" : 
                           progress < PROGRESS_APPROACHING_THRESHOLD ? "In Transit - En Route" :
                           progress < PROGRESS_AT_WAREHOUSE_THRESHOLD ? "In Transit - Approaching" : 
                           progress >= PROGRESS_COMPLETE ? "Delivered" : "At Warehouse";
            
            // Find existing shipment by index
            int shipmentIndex = -1;
            Shipment existingShipment = null;
            for (int i = 0; i < shipments.size(); i++) {
                if (shipments.get(i).getBatchId().equals(batchId)) {
                    shipmentIndex = i;
                    existingShipment = shipments.get(i);
                    break;
                }
            }
            
            // Get latest environmental data for this batch
            ShipmentEnvironmentalData envData = environmentalDataMap.computeIfAbsent(
                batchId, k -> new ShipmentEnvironmentalData());
            
            // Create updated shipment (keep all shipments in table, including completed ones)
            String etaDisplay = progress >= PROGRESS_COMPLETE ? "DELIVERED" : String.format("%.0f%% Complete", progress);
            
            Shipment updatedShipment = new Shipment(
                batchId,
                status,
                currentLocation != null ? currentLocation : "Unknown",
                envData.temperature,
                envData.humidity,
                etaDisplay,
                existingShipment != null ? existingShipment.getVehicle() : generateVehicleId(batchId)
            );
            
            if (shipmentIndex >= 0) {
                // Replace existing at same index to maintain persistent list
                shipments.set(shipmentIndex, updatedShipment);
            } else {
                // Add new shipment to persistent list
                shipments.add(updatedShipment);
            }
            
        } catch (Exception e) {
            System.err.println("Error updating shipments table: " + e.getMessage());
        }
    }
    
    /**
     * Generate a consistent vehicle ID for a batch based on its hash.
     * @param batchId The batch identifier
     * @return A vehicle ID like "TRUCK-123"
     */
    private String generateVehicleId(String batchId) {
        return "TRUCK-" + Math.abs(batchId.hashCode() % 1000);
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        // Check if already stopped to avoid duplicate alerts
        // Use putIfAbsent semantics - only proceed if not already stopped
        if (!stoppedSimulations.add(batchId)) {
            logger.debug("Ignoring duplicate stop event for already stopped simulation: {}", batchId);
            return;
        }
        
        logger.info("Simulation stopped for batch: {} (completed: {})", batchId, completed);
        
        Platform.runLater(() -> {
            String message = completed ? 
                "✅ Delivery completed for: " + batchId : 
                "⏹ Delivery simulation stopped for: " + batchId;
            alerts.add(0, message);
            
            // Persist simulation completion
            persistSimulationComplete(batchId, completed);
            
            // Update timeline to final state
            updateTimeline(batchId, 100.0, completed ? "Delivered" : "Stopped");
            
            // Update shipment status
            String status = completed ? "DELIVERED" : "STOPPED";
            ShipmentEnvironmentalData envData = environmentalDataMap.get(batchId);
            if (envData != null) {
                persistShipmentUpdate(batchId, null, status, "Destination", 
                        envData.temperature, envData.humidity, 
                        completed ? "DELIVERED" : "STOPPED", null);
            }
            
            // Clean up map marker after a delay if completed
            if (completed) {
                // Move marker to destination
                updateMapMarkerPosition(batchId, 100.0, "Delivered");
                
                // Schedule cleanup using syncExecutor instead of creating new thread
                if (syncExecutor != null && !syncExecutor.isShutdown()) {
                    syncExecutor.schedule(() -> {
                        Platform.runLater(() -> cleanupMapMarker(batchId));
                    }, 5, TimeUnit.SECONDS);
                } else {
                    // Fallback: clean up immediately if executor not available
                    cleanupMapMarker(batchId);
                }
            } else {
                // Remove marker immediately if stopped manually
                cleanupMapMarker(batchId);
            }
            
            System.out.println("LogisticsController: " + message);
        });
    }
    
    /**
     * Clean up map marker, trail points, and remove from active shipments.
     */
    private void cleanupMapMarker(String batchId) {
        if (mapContainer == null) return;
        
        try {
            MapVisualization visualization = activeShipments.remove(batchId);
            if (visualization != null) {
                // Stop any running animation
                if (visualization.animation != null) {
                    visualization.animation.stop();
                }
                
                // Remove trail points
                for (Circle trailPoint : visualization.trailPoints) {
                    mapContainer.getChildren().remove(trailPoint);
                }
                visualization.trailPoints.clear();
                
                // Remove marker and label
                mapContainer.getChildren().removeAll(visualization.shipmentCircle, visualization.shipmentLabel);
                System.out.println("Cleaned up map marker and trail for: " + batchId);
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up map marker: " + e.getMessage());
        }
    }
    
    /**
     * Persist simulation completion.
     */
    private void persistSimulationComplete(String batchId, boolean completed) {
        if (persistenceService == null) return;
        
        try {
            // Get temperature series data to build SimulationResult
            XYChart.Series<Number, Number> tempSeries = temperatureSeriesMap.get(batchId);
            ShipmentEnvironmentalData envData = environmentalDataMap.get(batchId);
            
            // Create simulation result with temperature series
            SimulationResult result = new SimulationResult(batchId, null);
            result.setStartTime(System.currentTimeMillis() - 120000); // Approximate
            result.setEndTime(System.currentTimeMillis());
            result.setStatus(completed ? "COMPLETED" : "STOPPED");
            result.setFinalQuality(completed ? 95.0 : 80.0); // Default values
            
            // Add temperature data points from chart series
            if (tempSeries != null && tempSeries.getData() != null) {
                for (XYChart.Data<Number, Number> point : tempSeries.getData()) {
                    long totalSeconds = point.getXValue().longValue();
                    long actualTimestamp = chartEpochStart + (totalSeconds * 1000);
                    String timeLabel = formatSecondsAsWallClockTime(totalSeconds);
                    result.addTemperaturePoint(
                            actualTimestamp, 
                            point.getYValue().doubleValue(),
                            timeLabel);
                }
            }
            
            // Calculate statistics
            result.calculateStatistics();
            
            // Create persisted simulation from result
            PersistedSimulation simulation = PersistedSimulation.fromSimulationResult(result, "NORMAL");
            persistenceService.saveSimulation(simulation);
            
            System.out.println("📝 Persisted simulation complete: " + batchId + " (completed=" + completed + ")");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to persist simulation completion: " + e.getMessage());
        }
    }
    
    /**
     * Get the current temperature series data for a batch (for graph rendering in simulations).
     * This method provides the same data structure used by the live temperature monitoring graph.
     * 
     * @param batchId The batch identifier
     * @return SimulationResult containing temperature series, or null if not found
     */
    public SimulationResult getSimulationResultWithTemperatureSeries(String batchId) {
        XYChart.Series<Number, Number> tempSeries = temperatureSeriesMap.get(batchId);
        ShipmentEnvironmentalData envData = environmentalDataMap.get(batchId);
        
        if (tempSeries == null) {
            return null;
        }
        
        SimulationResult result = new SimulationResult(batchId, null);
        
        // Populate temperature series from chart data
        for (XYChart.Data<Number, Number> point : tempSeries.getData()) {
            long totalSeconds = point.getXValue().longValue();
            long actualTimestamp = chartEpochStart + (totalSeconds * 1000);
            String timeLabel = formatSecondsAsWallClockTime(totalSeconds);
            result.addTemperaturePoint(
                    actualTimestamp, 
                    point.getYValue().doubleValue(),
                    timeLabel);
        }
        
        // Add humidity data if available
        if (envData != null) {
            result.addHumidityPoint(System.currentTimeMillis(), envData.humidity, "Current");
        }
        
        result.calculateStatistics();
        return result;
    }
    
    @Override
    public void onSimulationError(String batchId, String error) {
        Platform.runLater(() -> {
            alerts.add(0, "❌ Simulation error for " + batchId + ": " + error);
            System.err.println("LogisticsController: Simulation error - " + error);
        });
    }

    // Helper class for map visualization
    private static class MapVisualization {
        Circle shipmentCircle;
        Text shipmentLabel;
        Timeline animation; // Track animation for cleanup
        List<Circle> trailPoints = new java.util.ArrayList<>(); // Orange trail points
        String lastStatus = null; // Track last status to avoid duplicate alerts
    }

    // Data model class
    public static class Shipment {
        private final String batchId;
        private final String status;
        private final String location;
        private final double temperature;
        private final double humidity;
        private final String eta;
        private final String vehicle;

        public Shipment(String batchId, String status, String location,
                        double temperature, double humidity, String eta, String vehicle) {
            this.batchId = batchId;
            this.status = status;
            this.location = location;
            this.temperature = temperature;
            this.humidity = humidity;
            this.eta = eta;
            this.vehicle = vehicle;
        }

        // Getters
        public String getBatchId() { return batchId; }
        public String getStatus() { return status; }
        public String getLocation() { return location; }
        public double getTemperature() { return temperature; }
        public double getHumidity() { return humidity; }
        public String getEta() { return eta; }
        public String getVehicle() { return vehicle; }
    }
}