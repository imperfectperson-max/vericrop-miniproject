package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.services.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public class LogisticsController {
    private static final Logger logger = LoggerFactory.getLogger(LogisticsController.class);

    @FXML private TableView<Shipment> shipmentsTable;
    @FXML private ListView<String> alertsList;
    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextArea reportArea;
    @FXML private LineChart<String, Number> temperatureChart;

    // Navigation buttons
    @FXML private Button backToProducerButton;
    @FXML private Button analyticsButton;
    @FXML private Button consumerButton;
    
    // Simulation controls
    @FXML private Button startSimulationButton;
    @FXML private Button stopSimulationButton;
    @FXML private Button seedTestDataButton;

    private ObservableList<Shipment> shipments = FXCollections.observableArrayList();
    private ObservableList<String> alerts = FXCollections.observableArrayList();
    
    // Services
    private NavigationService navigationService;
    private DeliverySimulationService deliverySimulationService;
    private AlertsService alertsService;
    private ReportGenerationService reportGenerationService;

    @FXML
    public void initialize() {
        logger.info("Initializing LogisticsController");
        
        // Get services from ApplicationContext
        ApplicationContext appContext = ApplicationContext.getInstance();
        this.navigationService = appContext.getNavigationService();
        this.deliverySimulationService = appContext.getDeliverySimulationService();
        this.alertsService = appContext.getAlertsService();
        this.reportGenerationService = appContext.getReportGenerationService();
        
        setupShipmentsTable();
        setupAlertsList();
        setupReportCombo();
        setupTemperatureChart();
        setupNavigationButtons();
        setupSimulationListeners();
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
    }

    private void setupShipmentsTable() {
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
        // Check system property (set via --load-demo flag)
        String loadDemo = System.getProperty("vericrop.loadDemo");
        if ("true".equalsIgnoreCase(loadDemo)) {
            return true;
        }
        
        // Check environment variable
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
        // Set chart title and legend
        if (temperatureChart != null) {
            temperatureChart.setTitle("Temperature Monitoring");
            temperatureChart.setLegendVisible(true);
            
            // Populate with demo data only if flag is set
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

    /**
     * Setup delivery simulation event listeners
     */
    private void setupSimulationListeners() {
        deliverySimulationService.addEventListener(event -> {
            logger.info("Delivery event received: {} - {}", event.getStatus(), event.getMessage());
            
            // Update alerts list
            Platform.runLater(() -> {
                String timestamp = event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String statusIcon = getStatusIcon(event.getStatus());
                alerts.add(0, String.format("%s [%s] %s: %s", 
                    statusIcon, timestamp, event.getShipmentId(), event.getMessage()));
                
                // Update shipments table if needed
                updateShipmentFromEvent(event);
            });
        });
        
        logger.info("Simulation listeners configured");
    }
    
    /**
     * Get icon for delivery status
     */
    private String getStatusIcon(DeliverySimulationService.DeliveryStatus status) {
        switch (status) {
            case CREATED: return "📦";
            case PICKED_UP: return "🚚";
            case IN_TRANSIT: return "🛣️";
            case DELAYED: return "⚠️";
            case DELIVERED: return "✅";
            default: return "•";
        }
    }
    
    /**
     * Update shipment in table from simulation event
     */
    private void updateShipmentFromEvent(DeliverySimulationService.DeliveryEvent event) {
        // Look for existing shipment
        Shipment existing = shipments.stream()
            .filter(s -> s.getBatchId().equals(event.getShipmentId()))
            .findFirst()
            .orElse(null);
        
        if (existing != null) {
            // Remove and re-add with updated status
            shipments.remove(existing);
            shipments.add(0, new Shipment(
                event.getShipmentId(),
                event.getStatus().toString(),
                event.getDestination(),
                4.2, // Default temperature
                65,  // Default humidity
                "ETA varies",
                "TRUCK_AUTO"
            ));
        } else if (event.getStatus() == DeliverySimulationService.DeliveryStatus.CREATED) {
            // New shipment
            shipments.add(0, new Shipment(
                event.getShipmentId(),
                event.getStatus().toString(),
                event.getOrigin(),
                4.2,
                65,
                "Calculating...",
                null
            ));
        }
    }

    // Navigation methods
    @FXML
    private void handleBackToProducer() {
        if (navigationService != null) {
            navigationService.navigateToProducer();
        } else {
            MainApp mainApp = MainApp.getInstance();
            if (mainApp != null) {
                mainApp.showProducerScreen();
            }
        }
    }

    @FXML
    private void handleShowAnalytics() {
        if (navigationService != null) {
            navigationService.navigateToAnalytics();
        } else {
            MainApp mainApp = MainApp.getInstance();
            if (mainApp != null) {
                mainApp.showAnalyticsScreen();
            }
        }
    }

    @FXML
    private void handleShowConsumer() {
        if (navigationService != null) {
            navigationService.navigateToConsumer();
        } else {
            MainApp mainApp = MainApp.getInstance();
            if (mainApp != null) {
                mainApp.showConsumerScreen();
            }
        }
    }
    
    // Simulation control methods
    @FXML
    private void handleStartSimulation() {
        logger.info("Starting delivery simulation");
        deliverySimulationService.startSimulation();
        showAlert(Alert.AlertType.INFORMATION, "Simulation Started", 
            "Delivery simulation is now running");
    }
    
    @FXML
    private void handleStopSimulation() {
        logger.info("Stopping delivery simulation");
        deliverySimulationService.stopSimulation();
        showAlert(Alert.AlertType.INFORMATION, "Simulation Stopped", 
            "Delivery simulation has been stopped");
    }
    
    @FXML
    private void handleSeedTestData() {
        logger.info("Seeding test shipments");
        var shipmentIds = deliverySimulationService.seedTestShipments(5);
        showAlert(Alert.AlertType.INFORMATION, "Test Data Seeded", 
            "Created " + shipmentIds.size() + " test shipments");
    }

    // Action methods
    @FXML
    private void handleRefresh() {
        System.out.println("Refreshing logistics data...");
        Platform.runLater(() -> {
            // Simulate data refresh by re-initializing
            setupShipmentsTable();
            setupAlertsList();
            setupTemperatureChart();
            showAlert(Alert.AlertType.INFORMATION, "Refresh Complete", 
                    "Logistics data has been refreshed successfully");
        });
    }

    @FXML
    private void handleExportReport() {
        logger.info("Exporting logistics report...");
        String reportType = reportTypeCombo.getValue();
        if (reportType == null || reportType.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Report Selected", 
                    "Please select a report type before exporting");
            return;
        }
        
        try {
            // Convert shipments to map format for report generation
            java.util.List<Map<String, Object>> deliveryData = new java.util.ArrayList<>();
            for (Shipment s : shipments) {
                Map<String, Object> record = new java.util.HashMap<>();
                record.put("shipmentId", s.getBatchId());
                record.put("batchId", s.getBatchId());
                record.put("origin", s.getLocation());
                record.put("destination", s.getLocation());
                record.put("status", s.getStatus());
                record.put("pickupTime", "N/A");
                record.put("deliveryTime", s.getEta());
                record.put("duration", "N/A");
                deliveryData.add(record);
            }
            
            String filepath = reportGenerationService.generateDeliveryReportCSV(deliveryData);
            if (filepath != null) {
                showAlert(Alert.AlertType.INFORMATION, "Export Complete", 
                    "Report exported to:\n" + filepath);
            } else {
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                    "Failed to export report. Check logs for details.");
            }
        } catch (Exception e) {
            logger.error("Error exporting report", e);
            showAlert(Alert.AlertType.ERROR, "Export Failed", 
                "Error: " + e.getMessage());
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