package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class LogisticsController {

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

    private ObservableList<Shipment> shipments = FXCollections.observableArrayList();
    private ObservableList<String> alerts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupShipmentsTable();
        setupAlertsList();
        setupReportCombo();
        setupTemperatureChart();
        setupNavigationButtons();
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
        // Sample data
        shipments.addAll(
                new Shipment("BATCH_A2386", "IN_TRANSIT", "Highway A - Mile 120", 4.2, 65, "17:30", "TRUCK_001"),
                new Shipment("BATCH_A2387", "AT_WAREHOUSE", "Metro Fresh Warehouse", 3.8, 62, "ARRIVED", null),
                new Shipment("BATCH_A2388", "DELIVERED", "FreshMart Downtown", 4.1, 63, "DELIVERED", null)
        );
        shipmentsTable.setItems(shipments);
    }

    private void setupAlertsList() {
        alerts.addAll(
                "✓ No active alerts - System normal",
                "✓ Last temperature check: 4.2°C ✅",
                "✓ Humidity within optimal range ✅"
        );
        alertsList.setItems(alerts);
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
        // Populate temperature chart with sample data showing temperature monitoring
        if (temperatureChart != null) {
            XYChart.Series<String, Number> series1 = new XYChart.Series<>();
            series1.setName("BATCH_A2386");
            series1.getData().add(new XYChart.Data<>("00:00", 4.2));
            series1.getData().add(new XYChart.Data<>("04:00", 4.5));
            series1.getData().add(new XYChart.Data<>("08:00", 4.8));
            series1.getData().add(new XYChart.Data<>("12:00", 5.1));
            series1.getData().add(new XYChart.Data<>("16:00", 4.6));
            series1.getData().add(new XYChart.Data<>("20:00", 4.3));
            
            XYChart.Series<String, Number> series2 = new XYChart.Series<>();
            series2.setName("BATCH_A2387");
            series2.getData().add(new XYChart.Data<>("00:00", 3.8));
            series2.getData().add(new XYChart.Data<>("04:00", 3.9));
            series2.getData().add(new XYChart.Data<>("08:00", 4.1));
            series2.getData().add(new XYChart.Data<>("12:00", 4.3));
            series2.getData().add(new XYChart.Data<>("16:00", 4.0));
            series2.getData().add(new XYChart.Data<>("20:00", 3.8));
            
            temperatureChart.getData().addAll(series1, series2);
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
        System.out.println("Exporting logistics report...");
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