package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // Map visualization constants
    private static final double MAP_WIDTH = 350;
    private static final double MAP_HEIGHT = 200;
    private static final double ORIGIN_X = 50;
    private static final double ORIGIN_Y = 100;
    private static final double DESTINATION_X = 300;
    private static final double DESTINATION_Y = 100;

    @FXML
    public void initialize() {
        setupShipmentsTable();
        setupAlertsList();
        setupReportCombo();
        setupTemperatureChart();
        setupNavigationButtons();
        setupMapContainer();
        startSyncService();

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
                if (alerts.size() > 50) {
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
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            alerts.add(0, "🚨 SIMULATED [" + timestamp + "]: Temperature breach detected - 8.5°C");
            showAlert(Alert.AlertType.WARNING, "Alert Simulated",
                    "A temperature breach alert has been simulated");
        });
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
        
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdownNow();
        }
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
            
            // Add timeline event for simulation start
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            addTimelineEvent("Simulation Started", 
                           "Batch " + batchId + " dispatch at " + timestamp, 
                           "#10b981");
            
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
     * Add timeline event for simulation progress.
     */
    private void addTimelineEvent(String title, String description, String iconColor) {
        if (timelineContainer == null) return;
        
        try {
            // Create timeline item
            HBox timelineItem = new HBox(10);
            timelineItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            timelineItem.getStyleClass().add("timeline-item");
            
            // Icon
            Label icon = new Label("●");
            icon.setStyle("-fx-text-fill: " + iconColor + "; -fx-font-weight: bold;");
            
            // Content
            VBox content = new VBox();
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold;");
            Label descLabel = new Label(description);
            content.getChildren().addAll(titleLabel, descLabel);
            
            timelineItem.getChildren().addAll(icon, content);
            
            // Add to timeline (prepend to show newest first)
            timelineContainer.getChildren().add(0, timelineItem);
            
            // Keep only last 10 events
            while (timelineContainer.getChildren().size() > 10) {
                timelineContainer.getChildren().remove(timelineContainer.getChildren().size() - 1);
            }
            
        } catch (Exception e) {
            System.err.println("Error adding timeline event: " + e.getMessage());
        }
    }
    
    /**
     * Initialize temperature chart series for this batch.
     */
    private void initializeTemperatureChartSeries(String batchId) {
        if (temperatureChart == null) return;
        
        try {
            // Create new series for this batch
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(batchId);
            
            // Add to chart
            temperatureChart.getData().add(series);
            
            System.out.println("Initialized temperature chart series for: " + batchId);
        } catch (Exception e) {
            System.err.println("Error initializing temperature chart: " + e.getMessage());
        }
    }
    
    /**
     * Update temperature chart with live data point.
     * Implements a sliding window to keep last 60 data points.
     */
    private void updateTemperatureChart(String batchId, double temperature) {
        if (temperatureChart == null || temperatureChart.getData().isEmpty()) {
            return;
        }
        
        try {
            // Find the series for this batchId
            XYChart.Series<String, Number> targetSeries = null;
            for (XYChart.Series<String, Number> series : temperatureChart.getData()) {
                if (series.getName().equals(batchId)) {
                    targetSeries = series;
                    break;
                }
            }
            
            // If series doesn't exist, create it
            if (targetSeries == null) {
                targetSeries = new XYChart.Series<>();
                targetSeries.setName(batchId);
                temperatureChart.getData().add(targetSeries);
            }
            
            // Format timestamp for X-axis
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            
            // Add new data point
            targetSeries.getData().add(new XYChart.Data<>(timestamp, temperature));
            
            // Implement sliding window: keep only last 60 points
            final int MAX_DATA_POINTS = 60;
            if (targetSeries.getData().size() > MAX_DATA_POINTS) {
                targetSeries.getData().remove(0);
            }
            
            System.out.println(String.format("Temperature chart updated: %s - %.1f°C (points: %d)", 
                batchId, temperature, targetSeries.getData().size()));
            
        } catch (Exception e) {
            System.err.println("Error updating temperature chart: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        Platform.runLater(() -> {
            // Get current environmental data from SimulationManager
            double temperature = 0.0;
            double humidity = 0.0;
            
            try {
                if (SimulationManager.isInitialized()) {
                    SimulationManager manager = SimulationManager.getInstance();
                    temperature = manager.getCurrentTemperature();
                    humidity = manager.getCurrentHumidity();
                }
            } catch (Exception e) {
                System.err.println("Error getting environmental data: " + e.getMessage());
            }
            
            // Update map marker position with smooth animation
            updateMapMarkerPosition(batchId, progress, currentLocation);
            
            // Update temperature chart with live data
            updateTemperatureChart(batchId, temperature);
            
            // Update shipments table with environmental data
            updateShipmentsTableRow(batchId, progress, currentLocation, temperature, humidity);
            
            // Add timeline events for major milestones
            addTimelineEventForProgress(batchId, progress, currentLocation, temperature);
            
            System.out.println("LogisticsController: Progress update - " + batchId + " at " + progress + "% - " + currentLocation + 
                             String.format(" (Temp: %.1f°C, Humidity: %.1f%%)", temperature, humidity));
        });
    }
    
    /**
     * Add timeline event for major progress milestones.
     */
    private void addTimelineEventForProgress(String batchId, double progress, String location, double temperature) {
        // Only add timeline events at major milestones to avoid clutter
        String event = null;
        String color = null;
        
        // Check for milestone progress points
        int progressInt = (int) progress;
        if (progressInt == 25) {
            event = "25% Complete - Departing Origin";
            color = "#3b82f6";
        } else if (progressInt == 50) {
            event = "50% Complete - Midpoint";
            color = "#3b82f6";
        } else if (progressInt == 75) {
            event = "75% Complete - Approaching Destination";
            color = "#3b82f6";
        } else if (progressInt == 90) {
            event = "90% Complete - Arrival Imminent";
            color = "#f59e0b";
        }
        
        // Add event if milestone reached
        if (event != null) {
            String description = String.format("%s | Temp: %.1f°C | Location: %s", 
                batchId.length() > 8 ? batchId.substring(0, 8) : batchId, 
                temperature, location);
            addTimelineEvent(event, description, color);
        }
    }
    
    /**
     * Update map marker position with smooth animation based on progress.
     * Animates marker from previous position to new position.
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
                // Create new visualization
                visualization = new MapVisualization();
                visualization.shipmentCircle = new Circle(newX, newY, 6, Color.ORANGE);
                visualization.shipmentCircle.setUserData("shipment");
                
                String displayId = batchId.length() > 8 ? batchId.substring(0, 8) : batchId;
                visualization.shipmentLabel = new Text(newX - 15, newY - 15, "🚚 " + displayId);
                visualization.shipmentLabel.setUserData("shipment");
                visualization.shipmentLabel.setFill(Color.DARKBLUE);
                
                mapContainer.getChildren().addAll(visualization.shipmentCircle, visualization.shipmentLabel);
                activeShipments.put(batchId, visualization);
                
                System.out.println("Created new map marker for: " + batchId + " at " + progress + "%");
            } else {
                // Animate existing visualization to new position
                // Use simple position updates for smooth movement
                visualization.shipmentCircle.setCenterX(newX);
                visualization.shipmentCircle.setCenterY(newY);
                visualization.shipmentLabel.setX(newX - 15);
                visualization.shipmentLabel.setY(newY - 15);
                
                // Update label text with current location if available
                if (currentLocation != null && !currentLocation.isEmpty()) {
                    String displayId = batchId.length() > 8 ? batchId.substring(0, 8) : batchId;
                    visualization.shipmentLabel.setText("🚚 " + displayId);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error updating map marker: " + e.getMessage());
        }
    }
    
    /**
     * Update shipments table row for a specific batch.
     * Since Shipment is immutable, we remove and re-add to update.
     */
    private void updateShipmentsTableRow(String batchId, double progress, String currentLocation, 
                                         double temperature, double humidity) {
        if (shipmentsTable == null) return;
        
        try {
            // Determine status based on progress
            String status = progress < 30 ? "In Transit - Departing" : 
                           progress < 70 ? "In Transit - En Route" :
                           progress < 90 ? "In Transit - Approaching" : 
                           progress >= 100 ? "Delivered" : "At Warehouse";
            
            // Find and remove existing shipment
            Shipment existingShipment = null;
            for (Shipment shipment : shipments) {
                if (shipment.getBatchId().equals(batchId)) {
                    existingShipment = shipment;
                    break;
                }
            }
            
            if (existingShipment != null) {
                shipments.remove(existingShipment);
            }
            
            // Create updated shipment with live environmental data
            if (progress < 100) {
                Shipment updatedShipment = new Shipment(
                    batchId,
                    status,
                    currentLocation != null ? currentLocation : "Unknown",
                    temperature, // Use live temperature data
                    humidity,    // Use live humidity data
                    String.format("%.0f%% Complete", progress),
                    existingShipment != null ? existingShipment.getVehicle() : "TRUCK-" + Math.abs(batchId.hashCode() % 1000)
                );
                shipments.add(updatedShipment);
            }
            
        } catch (Exception e) {
            System.err.println("Error updating shipments table: " + e.getMessage());
        }
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        Platform.runLater(() -> {
            String message = completed ? 
                "✅ Delivery completed for: " + batchId : 
                "⏹ Delivery simulation stopped for: " + batchId;
            alerts.add(0, message);
            
            // Add timeline event for simulation completion/stop
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            addTimelineEvent(
                completed ? "Delivery Completed" : "Simulation Stopped",
                "Batch " + batchId + " " + (completed ? "delivered at" : "stopped at") + " " + timestamp,
                completed ? "#10b981" : "#ef4444"
            );
            
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