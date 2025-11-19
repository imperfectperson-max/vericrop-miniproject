package org.vericrop.gui;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
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

    private ObservableList<Shipment> shipments = FXCollections.observableArrayList();
    private ObservableList<String> alerts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupShipmentsTable();
        setupAlertsList();
        setupReportCombo();
        setupTemperatureChart();
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
        // Chart would be populated with real data in implementation
    }

    // Action methods
    @FXML
    private void handleRefresh() {
        System.out.println("Refreshing logistics data...");
    }

    @FXML
    private void handleExportReport() {
        System.out.println("Exporting logistics report...");
    }

    @FXML
    private void handleAcknowledgeAlerts() {
        alerts.clear();
        alerts.add("All alerts acknowledged ✅");
    }

    @FXML
    private void handleSimulateAlert() {
        alerts.add(0, "🚨 SIMULATED: Temperature breach detected - 8.5°C");
    }

    @FXML
    private void handleGenerateReport() {
        reportArea.setText("Logistics Report Generated:\n\n" +
                "• Total Shipments: 3\n" +
                "• In Transit: 1\n" +
                "• Delivered: 1\n" +
                "• Avg Temperature: 4.0°C\n" +
                "• Compliance: 100%");
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