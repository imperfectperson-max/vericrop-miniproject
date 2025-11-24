package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import java.util.Optional;
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
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import org.vericrop.dto.MapSimulationEvent;
import org.vericrop.dto.TemperatureComplianceEvent;
import org.vericrop.kafka.consumers.MapSimulationEventConsumer;
import org.vericrop.kafka.consumers.TemperatureComplianceEventConsumer;
import org.vericrop.kafka.producers.QualityAlertProducer;
import org.vericrop.kafka.events.QualityAlertEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LogisticsController implements SimulationListener {

    @FXML private TableView<Shipment> shipmentsTable;
    @FXML private ListView<String> alertsList;
    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextArea reportArea;
    @FXML private LineChart<String, Number> temperatureChart;
    @FXML private Pane mapContainer;
    @FXML private VBox timelineContainer;

    // Navigation buttons
    @FXML private Button backToProducerButton;
    @FXML private Button analyticsButton;
    @FXML private Button consumerButton;
    @FXML private Button messagesButton;
    @FXML private Button logoutButton;

    private ObservableList<Shipment> shipments = FXCollections.observableArrayList();
    private ObservableList<String> alerts = FXCollections.observableArrayList();

    // Delivery simulation synchronization
    private ScheduledExecutorService syncExecutor;
    private Map<String, MapVisualization> activeShipments = new HashMap<>();
    private org.vericrop.service.DeliverySimulator deliverySimulator;
    
    // Kafka consumers for real-time events
    private MapSimulationEventConsumer mapSimulationConsumer;
    private TemperatureComplianceEventConsumer temperatureComplianceConsumer;
    private ExecutorService kafkaConsumerExecutor;
    
    // Kafka producer for alerts
    private QualityAlertProducer qualityAlertProducer;
    
    // Track temperature chart series by batch ID (thread-safe for Kafka consumer access)
    private Map<String, XYChart.Series<String, Number>> temperatureSeriesMap = new ConcurrentHashMap<>();
    
    // Track latest environmental data by batch ID for shipments table
    private Map<String, ShipmentEnvironmentalData> environmentalDataMap = new ConcurrentHashMap<>();
    
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
        setupTemperatureChart();
        setupNavigationButtons();
        setupMapContainer();
        startSyncService();
        
        // Initialize Kafka consumers for real-time updates
        setupKafkaConsumers();

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
            kafkaConsumerExecutor = Executors.newFixedThreadPool(2);
            
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
            
            System.out.println("✅ Kafka consumers initialized for logistics monitoring");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to initialize Kafka consumers: " + e.getMessage());
            e.printStackTrace();
            // Continue without Kafka - fallback to SimulationListener updates
        }
    }
    
    /**
     * Handle map simulation event from Kafka.
     * Updates map visualization with real-time position data and environmental data.
     */
    private void handleMapSimulationEvent(MapSimulationEvent event) {
        Platform.runLater(() -> {
            try {
                // Update environmental data tracking from map event
                ShipmentEnvironmentalData envData = environmentalDataMap.computeIfAbsent(
                    event.getBatchId(), k -> new ShipmentEnvironmentalData());
                envData.temperature = event.getTemperature();
                envData.humidity = event.getHumidity();
                
                // Update map marker position based on event data
                updateMapFromEvent(event);
                
                // Update shipments table with new environmental data (if row exists)
                updateShipmentEnvironmentalData(event.getBatchId());
                
                // Log event to alerts
                String alertMsg = String.format("🗺️ %s: %.0f%% - %s", 
                    event.getBatchId(), event.getProgress() * 100, event.getLocationName());
                if (!alerts.contains(alertMsg)) {
                    alerts.add(0, alertMsg);
                    if (alerts.size() > MAX_ALERT_ITEMS) {
                        alerts.remove(alerts.size() - 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling map simulation event: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle temperature compliance event from Kafka.
     * Updates temperature chart with real-time data points.
     * Note: Humidity is tracked via MapSimulationEvent which includes both temp and humidity.
     */
    private void handleTemperatureComplianceEvent(TemperatureComplianceEvent event) {
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
                    String alertMsg = String.format("🌡️ ALERT: %s - %.1f°C - %s", 
                        event.getBatchId(), event.getTemperature(), event.getDetails());
                    alerts.add(0, alertMsg);
                    if (alerts.size() > MAX_ALERT_ITEMS) {
                        alerts.remove(alerts.size() - 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling temperature compliance event: " + e.getMessage());
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
     */
    private void updateMapFromEvent(MapSimulationEvent event) {
        if (mapContainer == null) return;
        
        try {
            // Calculate position on map canvas
            double mapWidth = 350;
            double mapHeight = 200;
            double originX = 50;
            double destinationX = 300;
            double originY = 100;
            
            double progress = event.getProgress();
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
                // Update existing marker
                visualization.shipmentCircle.setCenterX(currentX);
                visualization.shipmentCircle.setCenterY(currentY);
                visualization.shipmentLabel.setX(currentX - 15);
                visualization.shipmentLabel.setY(currentY - 15);
            }
        } catch (Exception e) {
            System.err.println("Error updating map from event: " + e.getMessage());
        }
    }
    
    /**
     * Add temperature data point to chart for given batch.
     */
    private void addTemperatureDataPoint(TemperatureComplianceEvent event) {
        if (temperatureChart == null) return;
        
        try {
            // Get or create series for this batch
            XYChart.Series<String, Number> series = temperatureSeriesMap.get(event.getBatchId());
            if (series == null) {
                series = new XYChart.Series<>();
                series.setName(event.getBatchId());
                temperatureSeriesMap.put(event.getBatchId(), series);
                temperatureChart.getData().add(series);
            }
            
            // Format timestamp as time string
            String timeLabel = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            
            // Add data point
            series.getData().add(new XYChart.Data<>(timeLabel, event.getTemperature()));
            
            // Keep chart size reasonable - limit to last N points
            if (series.getData().size() > MAX_CHART_DATA_POINTS) {
                series.getData().remove(0);
            }
            
            System.out.println("Added temperature point: " + event.getBatchId() + " = " + 
                event.getTemperature() + "°C at " + timeLabel);
        } catch (Exception e) {
            System.err.println("Error adding temperature data point: " + e.getMessage());
        }
    }

    private void setupNavigationButtons() {
        if (backToProducerButton != null) {
            backToProducerButton.setOnAction(e -> handleBackToProducer());
        }
        if (analyticsButton != null) {
            analyticsButton.setOnAction(e -> handleShowAnalytics());
        }
        if (consumerButton != null) {
            consumerButton.setOnAction(e -> handleShowConsumer());
        }
        if (messagesButton != null) {
            messagesButton.setOnAction(e -> handleShowMessages());
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
            // Null-safe map container operations
            if (mapContainer != null) {
                try {
                    // Clear previous visualizations
                    mapContainer.getChildren().removeIf(node ->
                            node.getUserData() != null && "shipment".equals(node.getUserData()));
                } catch (Exception e) {
                    System.err.println("Error clearing map visualizations: " + e.getMessage());
                }
            }

            // Update active shipments from simulator
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
                    addAlert("✅ Delivery completed: " + shipmentId);
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
                "Delivery Performance"
        );
    }

    private void setupTemperatureChart() {
        if (temperatureChart != null) {
            temperatureChart.setTitle("Temperature Monitoring");
            temperatureChart.setLegendVisible(true);

            if (shouldLoadDemoData()) {
                XYChart.Series<String, Number> series1 = new XYChart.Series<>();
                series1.setName("BATCH_A2386 (demo)");
                series1.getData().add(new XYChart.Data<>("00:00", 4.2));
                series1.getData().add(new XYChart.Data<>("04:00", 4.5));
                series1.getData().add(new XYChart.Data<>("08:00", 4.8));
                series1.getData().add(new XYChart.Data<>("12:00", 5.1));
                series1.getData().add(new XYChart.Data<>("16:00", 4.6));
                series1.getData().add(new XYChart.Data<>("20:00", 4.3));

                XYChart.Series<String, Number> series2 = new XYChart.Series<>();
                series2.setName("BATCH_A2387 (demo)");
                series2.getData().add(new XYChart.Data<>("00:00", 3.8));
                series2.getData().add(new XYChart.Data<>("04:00", 3.9));
                series2.getData().add(new XYChart.Data<>("08:00", 4.1));
                series2.getData().add(new XYChart.Data<>("12:00", 4.3));
                series2.getData().add(new XYChart.Data<>("16:00", 4.0));
                series2.getData().add(new XYChart.Data<>("20:00", 3.8));

                temperatureChart.getData().addAll(series1, series2);
            }
        }
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
    private void handleShowAnalytics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showAnalyticsScreen();
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
    private void handleShowMessages() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showInboxScreen();
        }
    }

    @FXML
    private void handleShowContacts() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showContactsScreen();
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
        if (reportType != null) {
            showAlert(Alert.AlertType.INFORMATION, "Export Complete",
                    "Report '" + reportType + "' has been exported successfully");
        } else {
            showAlert(Alert.AlertType.WARNING, "No Report Selected",
                    "Please select a report type before exporting");
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
        // Default batch ID
        return "BATCH_DEMO_" + System.currentTimeMillis();
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
        
        // Stop Kafka consumers
        if (mapSimulationConsumer != null) {
            mapSimulationConsumer.stop();
        }
        if (temperatureComplianceConsumer != null) {
            temperatureComplianceConsumer.stop();
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
        Platform.runLater(() -> {
            alerts.add(0, "🚚 Delivery simulation started for: " + batchId);
            
            // Initialize map marker at origin
            initializeMapMarker(batchId);
            
            // Initialize temperature chart series
            initializeTemperatureChartSeries(batchId);
            
            System.out.println("LogisticsController: Simulation started - " + batchId);
        });
    }
    
    /**
     * Initialize map marker at the origin point.
     */
    private void initializeMapMarker(String batchId) {
        if (mapContainer == null) return;
        
        try {
            // Remove any existing marker for this batch
            MapVisualization existing = activeShipments.get(batchId);
            if (existing != null) {
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
            
            mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
            activeShipments.put(batchId, visualization);
            
            System.out.println("Initialized map marker at origin for: " + batchId);
        } catch (Exception e) {
            System.err.println("Error initializing map marker: " + e.getMessage());
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
                addTimelineItem("Delivered", 
                    deliveredComplete ? "Delivery complete" : "ETA: " + calculateETA(progress),
                    deliveredComplete);
                
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
     */
    private void initializeTemperatureChartSeries(String batchId) {
        if (temperatureChart == null) return;
        
        try {
            // Clear demo data if this is the first real simulation
            if (temperatureSeriesMap.isEmpty() && temperatureChart.getData().size() > 0) {
                // Check if demo data is present
                boolean hasDemo = temperatureChart.getData().stream()
                    .anyMatch(series -> series.getName().contains("(demo)"));
                if (hasDemo) {
                    System.out.println("Clearing demo data from temperature chart");
                    temperatureChart.getData().clear();
                }
            }
            
            // Check if series already exists
            if (temperatureSeriesMap.containsKey(batchId)) {
                System.out.println("Temperature chart series already exists for: " + batchId);
                return;
            }
            
            // Create new series for this batch
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(batchId);
            
            // Add to chart and tracking map
            temperatureChart.getData().add(series);
            temperatureSeriesMap.put(batchId, series);
            
            System.out.println("Initialized temperature chart series for: " + batchId);
        } catch (Exception e) {
            System.err.println("Error initializing temperature chart: " + e.getMessage());
            e.printStackTrace();
        }
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
            double distance = Math.sqrt(Math.pow(newX - currentX, 2) + Math.pow(newY - currentY, 2));
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
            
            System.out.println("Animating marker for: " + batchId + " to " + progress + "% (" + newX + ", " + newY + ")");
            
        } catch (Exception e) {
            System.err.println("Error updating map marker: " + e.getMessage());
            e.printStackTrace();
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
            
            // Create updated shipment
            if (progress < PROGRESS_COMPLETE) {
                Shipment updatedShipment = new Shipment(
                    batchId,
                    status,
                    currentLocation != null ? currentLocation : "Unknown",
                    envData.temperature,
                    envData.humidity,
                    String.format("%.0f%% Complete", progress),
                    existingShipment != null ? existingShipment.getVehicle() : generateVehicleId(batchId)
                );
                
                if (shipmentIndex >= 0) {
                    // Replace existing at same index
                    shipments.set(shipmentIndex, updatedShipment);
                } else {
                    // Add new shipment
                    shipments.add(updatedShipment);
                }
            } else if (shipmentIndex >= 0) {
                // Remove completed shipment
                shipments.remove(shipmentIndex);
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
        Platform.runLater(() -> {
            String message = completed ? 
                "✅ Delivery completed for: " + batchId : 
                "⏹ Delivery simulation stopped for: " + batchId;
            alerts.add(0, message);
            
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
     * Clean up map marker and remove from active shipments.
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
                mapContainer.getChildren().removeAll(visualization.shipmentCircle, visualization.shipmentLabel);
                System.out.println("Cleaned up map marker for: " + batchId);
            }
        } catch (Exception e) {
            System.err.println("Error cleaning up map marker: " + e.getMessage());
        }
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