package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class AnalyticsController {

    @FXML private Label totalBatchesLabel;
    @FXML private Label avgQualityLabel;
    @FXML private Label spoilageRateLabel;
    @FXML private Label onTimeDeliveryLabel;

    @FXML private LineChart<String, Number> qualityTrendChart;
    @FXML private TableView<Supplier> supplierTable;
    @FXML private PieChart temperatureComplianceChart;
    @FXML private TableView<Alert> alertsTable;

    @FXML private ComboBox<String> exportTypeCombo;
    @FXML private DatePicker exportStartDate;
    @FXML private DatePicker exportEndDate;
    @FXML private ComboBox<String> formatCombo;
    @FXML private TextArea exportPreview;

    // Navigation button
    @FXML private Button backToProducerButton;

    private ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private ObservableList<Alert> alerts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupKPIs();
        setupSupplierTable();
        setupAlertsTable();
        setupExportCombo();
        setupCharts();
        setupNavigation();
    }

    private void setupNavigation() {
        if (backToProducerButton != null) {
            backToProducerButton.setOnAction(e -> handleBackToProducer());
        }
    }

    private void setupKPIs() {
        totalBatchesLabel.setText("1,247");
        avgQualityLabel.setText("87% ↑2%");
        spoilageRateLabel.setText("2% ↓1%");
        onTimeDeliveryLabel.setText("96%");
    }

    private void setupSupplierTable() {
        suppliers.addAll(
                new Supplier("Farm A", 92, 1),
                new Supplier("Farm B", 85, 3),
                new Supplier("Farm C", 78, 7)
        );
        supplierTable.setItems(suppliers);
    }

    private void setupAlertsTable() {
        alerts.addAll(
                new Alert("Mar 07", "Temp Breach", "Temperature exceeded 7°C for 30min"),
                new Alert("Mar 05", "Delivery Delay", "Shipment delayed by 45 minutes"),
                new Alert("Mar 01", "Quality Drop", "Quality score dropped to 65%")
        );
        alertsTable.setItems(alerts);
    }

    private void setupExportCombo() {
        exportTypeCombo.getItems().addAll(
                "Supply Chain Summary",
                "Quality Analytics",
                "Logistics Performance",
                "Supplier Report"
        );
        formatCombo.getItems().addAll("PDF", "CSV", "Excel", "JSON");
    }

    private void setupCharts() {
        // Populate quality trend chart with sample data
        if (qualityTrendChart != null) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Quality Score");
            series.getData().add(new XYChart.Data<>("Jan", 82));
            series.getData().add(new XYChart.Data<>("Feb", 85));
            series.getData().add(new XYChart.Data<>("Mar", 87));
            series.getData().add(new XYChart.Data<>("Apr", 86));
            series.getData().add(new XYChart.Data<>("May", 89));
            series.getData().add(new XYChart.Data<>("Jun", 91));
            qualityTrendChart.getData().add(series);
            qualityTrendChart.setLegendVisible(false);
        }
        
        // Populate temperature compliance pie chart with sample data
        if (temperatureComplianceChart != null) {
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                    new PieChart.Data("Compliant", 94),
                    new PieChart.Data("Minor Issues", 4),
                    new PieChart.Data("Major Issues", 2)
            );
            temperatureComplianceChart.setData(pieChartData);
            temperatureComplianceChart.setLegendVisible(true);
        }
    }

    @FXML
    private void handleGenerateReport() {
        System.out.println("Generating analytics report...");
        showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Report Generated",
                "Analytics report has been generated successfully.");
    }

    @FXML
    private void handleSetAlerts() {
        System.out.println("Opening alert configuration...");
        showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Alert Settings",
                "Alert configuration panel would open here.");
    }

    @FXML
    private void handleExportData() {
        String selectedType = exportTypeCombo.getValue();
        if (selectedType != null) {
            exportPreview.setText("Export Preview: " + selectedType + "\n\n" +
                    "• Data range: " + exportStartDate.getValue() + " to " + exportEndDate.getValue() + "\n" +
                    "• Format: " + formatCombo.getValue() + "\n" +
                    "• Records: 1,247 batches\n" +
                    "• Generated: " + java.time.LocalDateTime.now());
        }
    }

    @FXML
    private void handleBackToProducer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showProducerScreen();
        }
    }

    private void showAlert(javafx.scene.control.Alert.AlertType type, String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Data model classes
    public static class Supplier {
        private final String name;
        private final int quality;
        private final int spoilage;

        public Supplier(String name, int quality, int spoilage) {
            this.name = name;
            this.quality = quality;
            this.spoilage = spoilage;
        }

        public String getName() { return name; }
        public int getQuality() { return quality; }
        public int getSpoilage() { return spoilage; }
    }

    public static class Alert {
        private final String date;
        private final String type;
        private final String details;

        public Alert(String date, String type, String details) {
            this.date = date;
            this.type = type;
            this.details = details;
        }

        public String getDate() { return date; }
        public String getType() { return type; }
        public String getDetails() { return details; }
    }
}