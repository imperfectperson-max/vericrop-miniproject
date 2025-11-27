package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;

public class AnalyticsController implements SimulationListener {

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
        registerWithSimulationManager();
    }
    
    /**
     * Register this controller as a listener with SimulationManager.
     */
    private void registerWithSimulationManager() {
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager manager = SimulationManager.getInstance();
                manager.registerListener(this);
                
                // If simulation is already running, add to alerts
                if (manager.isRunning()) {
                    String batchId = manager.getSimulationId();
                    Platform.runLater(() -> {
                        alerts.add(new Alert(
                            java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                            "INFO",
                            "Active delivery simulation: " + batchId
                        ));
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not register with SimulationManager: " + e.getMessage());
        }
    }

    private void setupNavigation() {
        if (backToProducerButton != null) {
            backToProducerButton.setOnAction(e -> handleBackToProducer());
        }
    }

    private void setupKPIs() {
        // Load real data from services or display empty state
        if (shouldLoadDemoData()) {
            totalBatchesLabel.setText("1,247 (demo)");
            avgQualityLabel.setText("87% ↑2% (demo)");
            spoilageRateLabel.setText("2% ↓1% (demo)");
            onTimeDeliveryLabel.setText("96% (demo)");
        } else {
            totalBatchesLabel.setText("0");
            avgQualityLabel.setText("--");
            spoilageRateLabel.setText("--");
            onTimeDeliveryLabel.setText("--");
        }
    }

    private void setupSupplierTable() {
        // Configure table columns with cell value factories
        if (supplierTable != null && supplierTable.getColumns().size() >= 3) {
            try {
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Supplier, String> nameCol = 
                    (javafx.scene.control.TableColumn<Supplier, String>) supplierTable.getColumns().get(0);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Supplier, Number> qualityCol = 
                    (javafx.scene.control.TableColumn<Supplier, Number>) supplierTable.getColumns().get(1);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Supplier, Number> spoilageCol = 
                    (javafx.scene.control.TableColumn<Supplier, Number>) supplierTable.getColumns().get(2);
                
                nameCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
                qualityCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getQuality()));
                spoilageCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getSpoilage()));
            } catch (ClassCastException | IndexOutOfBoundsException e) {
                System.err.println("Warning: Could not configure supplier table columns: " + e.getMessage());
            }
        }
        
        if (shouldLoadDemoData()) {
            suppliers.addAll(
                    new Supplier("Farm A (demo)", 92, 1),
                    new Supplier("Farm B (demo)", 85, 3),
                    new Supplier("Farm C (demo)", 78, 7)
            );
        }
        supplierTable.setItems(suppliers);
    }

    private void setupAlertsTable() {
        // Configure table columns with cell value factories
        if (alertsTable != null && alertsTable.getColumns().size() >= 3) {
            try {
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Alert, String> dateCol = 
                    (javafx.scene.control.TableColumn<Alert, String>) alertsTable.getColumns().get(0);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Alert, String> typeCol = 
                    (javafx.scene.control.TableColumn<Alert, String>) alertsTable.getColumns().get(1);
                @SuppressWarnings("unchecked")
                javafx.scene.control.TableColumn<Alert, String> detailsCol = 
                    (javafx.scene.control.TableColumn<Alert, String>) alertsTable.getColumns().get(2);
                
                dateCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDate()));
                typeCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType()));
                detailsCol.setCellValueFactory(cellData -> 
                    new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDetails()));
            } catch (ClassCastException | IndexOutOfBoundsException e) {
                System.err.println("Warning: Could not configure alerts table columns: " + e.getMessage());
            }
        }
        
        if (shouldLoadDemoData()) {
            alerts.addAll(
                    new Alert("Mar 07", "Temp Breach (demo)", "Temperature exceeded 7°C for 30min"),
                    new Alert("Mar 05", "Delivery Delay (demo)", "Shipment delayed by 45 minutes"),
                    new Alert("Mar 01", "Quality Drop (demo)", "Quality score dropped to 65%")
            );
        }
        alertsTable.setItems(alerts);
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
        // Populate quality trend chart with demo data only if flag is set
        if (qualityTrendChart != null && shouldLoadDemoData()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Quality Score (demo)");
            series.getData().add(new XYChart.Data<>("Jan", 82));
            series.getData().add(new XYChart.Data<>("Feb", 85));
            series.getData().add(new XYChart.Data<>("Mar", 87));
            series.getData().add(new XYChart.Data<>("Apr", 86));
            series.getData().add(new XYChart.Data<>("May", 89));
            series.getData().add(new XYChart.Data<>("Jun", 91));
            qualityTrendChart.getData().add(series);
            qualityTrendChart.setLegendVisible(true);
            qualityTrendChart.setTitle("Quality Trend Over Time");
        }
        
        // Populate temperature compliance pie chart with labels
        if (temperatureComplianceChart != null) {
            temperatureComplianceChart.setTitle("Temperature Compliance");
            temperatureComplianceChart.setLegendVisible(true);
            
            if (shouldLoadDemoData()) {
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                        new PieChart.Data("Compliant (94%)", 94),
                        new PieChart.Data("Minor Issues (4%)", 4),
                        new PieChart.Data("Major Issues (2%)", 2)
                );
                temperatureComplianceChart.setData(pieChartData);
                
                // Add percentage labels to pie slices
                pieChartData.forEach(data -> {
                    data.nameProperty().setValue(data.getName());
                });
            }
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
        String format = formatCombo.getValue();
        
        if (selectedType == null) {
            if (exportPreview != null) {
                exportPreview.setText("Please select an export type first.");
            }
            return;
        }
        
        // Build export preview with null-safe date handling
        StringBuilder preview = new StringBuilder();
        preview.append("Export Preview: ").append(selectedType).append("\n\n");
        
        if (exportStartDate != null && exportStartDate.getValue() != null && 
            exportEndDate != null && exportEndDate.getValue() != null) {
            preview.append("• Data range: ")
                   .append(exportStartDate.getValue())
                   .append(" to ")
                   .append(exportEndDate.getValue())
                   .append("\n");
        } else {
            preview.append("• Data range: All time\n");
        }
        
        preview.append("• Format: ").append(format != null ? format : "Not selected").append("\n");
        preview.append("• Records: 1,247 batches\n");
        preview.append("• Generated: ").append(java.time.LocalDateTime.now());
        
        if (exportPreview != null) {
            exportPreview.setText(preview.toString());
        }
    }

    @FXML
    private void handleBackToProducer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showProducerScreen();
        }
    }
    
    @FXML
    private void handleShowLogistics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showLogisticsScreen();
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
            //mainApp.showInboxScreen();
        }
    }
    
    @FXML
    private void handleShowContacts() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            //mainApp.showContactsScreen();
        }
    }
    
    @FXML
    private void handleLogout() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("login.fxml");
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
    
    public void cleanup() {
        // Unregister from SimulationManager
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager.getInstance().unregisterListener(this);
            }
        } catch (Exception e) {
            System.err.println("Error unregistering from SimulationManager: " + e.getMessage());
        }
    }
    
    // ========== SimulationListener Implementation ==========
    
    @Override
    public void onSimulationStarted(String batchId, String farmerId) {
        Platform.runLater(() -> {
            alerts.add(0, new Alert(
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                "INFO",
                "Delivery simulation started for batch: " + batchId
            ));
            System.out.println("AnalyticsController: Simulation started - " + batchId);
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        // Analytics doesn't need detailed progress updates
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        Platform.runLater(() -> {
            String details = completed ? 
                "Delivery completed for batch: " + batchId : 
                "Delivery simulation stopped for batch: " + batchId;
            alerts.add(0, new Alert(
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                completed ? "SUCCESS" : "INFO",
                details
            ));
            System.out.println("AnalyticsController: " + details);
        });
    }
    
    @Override
    public void onSimulationError(String batchId, String error) {
        Platform.runLater(() -> {
            alerts.add(0, new Alert(
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                "ERROR",
                "Simulation error for batch " + batchId + ": " + error
            ));
            System.err.println("AnalyticsController: Simulation error - " + error);
        });
    }
}