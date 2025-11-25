package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.IOException;
import org.vericrop.gui.util.QRDecoder;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import com.google.zxing.NotFoundException;
import com.vericrop.simulation.SimulationConfig;

public class ConsumerController implements SimulationListener {

    @FXML private TextField batchIdField;
    @FXML private ListView<String> verificationHistoryList;
    @FXML private Button backToProducerButton;
    @FXML private Button logisticsButton;
    @FXML private VBox productJourneyContainer;
    @FXML private HBox qualityMetricsContainer;
    @FXML private Label finalQualityLabel;
    @FXML private Label deliveryStatusLabel;
    @FXML private Label deliveryTimeLabel;

    private ObservableList<String> verificationHistory = FXCollections.observableArrayList();
    private Set<String> knownBatchIds = new HashSet<>();
    
    // Track active deliveries and their journey data
    private Map<String, DeliveryJourney> activeJourneys = new ConcurrentHashMap<>();
    
    /**
     * Tracks journey state for a delivery.
     */
    private static class DeliveryJourney {
        final String batchId;
        final String farmerId;
        final long startTime;
        volatile String currentStatus;
        volatile double currentProgress;
        volatile String currentLocation;
        volatile double finalQuality = -1;
        
        DeliveryJourney(String batchId, String farmerId) {
            this.batchId = batchId;
            this.farmerId = farmerId;
            this.startTime = System.currentTimeMillis();
            this.currentStatus = "Started";
            this.currentProgress = 0;
            this.currentLocation = "Origin";
        }
    }

    @FXML
    public void initialize() {
        setupVerificationHistory();
        setupNavigationButtons();
        registerWithSimulationManager();
        initializeProductJourney();
    }
    
    /**
     * Initialize the Product Journey section.
     */
    private void initializeProductJourney() {
        if (productJourneyContainer == null) {
            System.err.println("Warning: productJourneyContainer is null");
            return;
        }
        
        // If demo mode, show demo journey data
        if (shouldLoadDemoData()) {
            showDemoJourneyData();
        }
    }
    
    /**
     * Show demo journey data in the Product Journey section.
     */
    private void showDemoJourneyData() {
        if (productJourneyContainer == null) return;
        
        productJourneyContainer.getChildren().clear();
        
        addJourneyItem("✅", "MAR 07, 14:30 - Harvested at Sunny Valley Orchards",
                      "Quality: PRIME (82%) | Certified Organic", true);
        addJourneyItem("✅", "MAR 07, 15:45 - Shipped via GlobalLog Transport",
                      "Avg Temp: 4.2°C | Perfect Transport Conditions", true);
        addJourneyItem("✅", "MAR 07, 17:30 - Received at Metro Fresh Storage",
                      "Spoilage Risk: LOW (8%) | Stored in Cell 4B", true);
        addJourneyItem("✅", "MAR 08, 09:15 - Delivered to FreshMart Downtown",
                      "Shelf Life: 12 days remaining | Final Quality: 92%", true);
        
        // Update quality metrics for demo
        if (finalQualityLabel != null) {
            finalQualityLabel.setText("92%");
        }
        if (deliveryStatusLabel != null) {
            deliveryStatusLabel.setText("Delivered");
        }
        if (deliveryTimeLabel != null) {
            deliveryTimeLabel.setText("18h 45m");
        }
    }
    
    /**
     * Add a journey item to the Product Journey container.
     */
    private void addJourneyItem(String icon, String title, String details, boolean completed) {
        if (productJourneyContainer == null) return;
        
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16;");
        
        VBox textBox = new VBox();
        Label titleLabel = new Label(title);
        titleLabel.setStyle(completed ? "-fx-font-weight: bold;" : "-fx-font-weight: bold; -fx-text-fill: #64748b;");
        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-text-fill: #64748b;");
        textBox.getChildren().addAll(titleLabel, detailsLabel);
        
        item.getChildren().addAll(iconLabel, textBox);
        productJourneyContainer.getChildren().add(item);
    }
    
    /**
     * Update the Product Journey display for an active delivery.
     */
    private void updateProductJourneyDisplay(String batchId) {
        DeliveryJourney journey = activeJourneys.get(batchId);
        if (journey == null || productJourneyContainer == null) return;
        
        Platform.runLater(() -> {
            productJourneyContainer.getChildren().clear();
            
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"));
            
            // Show started state
            boolean hasStarted = journey.currentProgress >= 0;
            addJourneyItem(hasStarted ? "✅" : "⏳", 
                          timestamp + " - Batch created at " + journey.farmerId,
                          "Initial Quality: 100% | Ready for transport", hasStarted);
            
            // Show in transit state
            boolean inTransit = journey.currentProgress >= 10;
            if (inTransit) {
                addJourneyItem(journey.currentProgress >= 70 ? "✅" : "🚚",
                              timestamp + " - In transit via cold-chain transport",
                              String.format("Progress: %.0f%% | %s", journey.currentProgress, journey.currentLocation),
                              journey.currentProgress >= 70);
            } else {
                addJourneyItem("⏳", "In Transit",
                              "Waiting for transport...", false);
            }
            
            // Show approaching state
            boolean approaching = journey.currentProgress >= 70;
            if (approaching) {
                addJourneyItem(journey.currentProgress >= 95 ? "✅" : "📍",
                              timestamp + " - Approaching destination",
                              "Nearing delivery point", journey.currentProgress >= 95);
            } else {
                addJourneyItem("⏳", "Approaching Destination",
                              "Not yet approaching", false);
            }
            
            // Show completed state
            boolean completed = journey.currentProgress >= 95;
            if (completed && journey.finalQuality > 0) {
                addJourneyItem("✅", timestamp + " - Delivered successfully",
                              String.format("Final Quality: %.1f%% | Delivery complete", journey.finalQuality), true);
            } else {
                addJourneyItem("⏳", "Delivery Complete",
                              "Pending delivery...", false);
            }
            
            // Update quality metrics
            updateQualityMetrics(journey);
        });
    }
    
    /**
     * Update quality metrics display.
     */
    private void updateQualityMetrics(DeliveryJourney journey) {
        if (finalQualityLabel != null) {
            if (journey.finalQuality > 0) {
                finalQualityLabel.setText(String.format("%.1f%%", journey.finalQuality));
                // Color based on quality level
                String color = journey.finalQuality >= 90 ? "#10b981" : 
                              journey.finalQuality >= 70 ? "#f59e0b" : "#ef4444";
                finalQualityLabel.setStyle("-fx-text-fill: " + color + ";");
            } else {
                finalQualityLabel.setText("--");
            }
        }
        
        if (deliveryStatusLabel != null) {
            deliveryStatusLabel.setText(journey.currentStatus);
        }
        
        if (deliveryTimeLabel != null) {
            long elapsedMs = System.currentTimeMillis() - journey.startTime;
            long minutes = elapsedMs / 60000;
            long seconds = (elapsedMs % 60000) / 1000;
            deliveryTimeLabel.setText(String.format("%dm %ds", minutes, seconds));
        }
    }
    
    /**
     * Register this controller as a listener with SimulationManager.
     */
    private void registerWithSimulationManager() {
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager manager = SimulationManager.getInstance();
                manager.registerListener(this);
                
                // If simulation is already running, add note to verification history
                if (manager.isRunning()) {
                    String batchId = manager.getSimulationId();
                    Platform.runLater(() -> {
                        verificationHistory.add(0, "📦 Batch in transit: " + batchId + " - Track in Logistics tab");
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
        if (logisticsButton != null) {
            logisticsButton.setOnAction(e -> handleShowLogistics());
        }
    }

    private void setupVerificationHistory() {
        if (shouldLoadDemoData()) {
            verificationHistory.addAll(
                    "2024-03-08 09:15: BATCH_A2386 - ✅ VERIFIED (Summer Apples)",
                    "2024-03-07 14:30: BATCH_A2385 - ✅ VERIFIED (Organic Carrots)",
                    "2024-03-06 11:20: BATCH_A2384 - ✅ VERIFIED (Fresh Lettuce)"
            );
            // Populate known batch IDs from demo entries
            knownBatchIds.add("BATCH_A2386");
            knownBatchIds.add("BATCH_A2385");
            knownBatchIds.add("BATCH_A2384");
        } else {
            verificationHistory.add("No verification history. Scan a QR code or enter a Batch ID to verify products.");
            // In non-demo mode, leave knownBatchIds empty or load placeholder IDs if available
        }
        if (verificationHistoryList != null) {
            verificationHistoryList.setItems(verificationHistory);
        }
    }

    @FXML
    private void handleScanQR() {
        // Create file chooser for image upload
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select QR Code Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Get the current stage (window)
        Stage stage = (Stage) batchIdField.getScene().getWindow();
        
        // Show file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            try {
                // Decode the QR code from the image
                String qrContent = QRDecoder.decodeQRCode(selectedFile);
                
                // Extract batch ID from QR content
                String batchId = QRDecoder.extractBatchId(qrContent);
                
                if (batchId != null && !batchId.trim().isEmpty()) {
                    // Set the batch ID in the text field
                    batchIdField.setText(batchId);
                    
                    // Automatically verify the batch
                    verifyBatch(batchId);
                } else {
                    showAlert(Alert.AlertType.WARNING, "Invalid QR Code", 
                        "Could not extract batch ID from the QR code.");
                }
            } catch (NotFoundException e) {
                showAlert(Alert.AlertType.ERROR, "QR Code Not Found", 
                    "No QR code was found in the selected image. Please select an image containing a valid QR code.");
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "File Error", 
                    "Failed to read the image file: " + e.getMessage());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error Reading QR Code", 
                    "An unexpected error occurred while reading the QR code: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleVerifyAnother() {
        batchIdField.clear();
        showAlert(Alert.AlertType.INFORMATION, "Reset", "Ready to verify another product");
    }

    @FXML
    private void handleVerifyProduct() {
        String batchId = batchIdField.getText();
        if (batchId != null && !batchId.trim().isEmpty()) {
            verifyBatch(batchId.trim());
        } else {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Please enter a Batch ID or scan QR code");
        }
    }

    @FXML
    private void handleManualVerify() {
        handleVerifyProduct();
    }

    @FXML
    private void handleClearHistory() {
        verificationHistory.clear();
        verificationHistory.add("History cleared - " + java.time.LocalDateTime.now());
    }

    @FXML
    private void handleExportHistory() {
        showAlert(Alert.AlertType.INFORMATION, "Export", "Verification history exported successfully");
    }

    @FXML
    private void handleShareVerification() {
        showAlert(Alert.AlertType.INFORMATION, "Share", "Verification details shared successfully");
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
    private void handleShowMessages() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showInboxScreen();
        }
    }
    
    @FXML
    private void handleLogout() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("login.fxml");
        }
    }

    private void verifyBatch(String batchId) {
        // Validation: if batchId non-empty -> attempt verification
        if (batchId == null || batchId.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", "Batch ID cannot be empty");
            return;
        }

        // Check if batch ID exists in knownBatchIds (case-insensitive)
        if (shouldLoadDemoData() && !knownBatchIds.isEmpty()) {
            boolean found = false;
            for (String knownId : knownBatchIds) {
                if (knownId.equalsIgnoreCase(batchId.trim())) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                showAlert(Alert.AlertType.WARNING, "Batch ID Not Found", 
                    "The batch ID '" + batchId + "' was not found in our system.");
                
                // Add failure entry to verification history
                String failureEntry = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                        ": " + batchId + " - ❌ NOT FOUND";
                
                Platform.runLater(() -> {
                    verificationHistory.add(0, failureEntry);
                });
                return;
            }
        }

        // TODO: In production, query actual blockchain/ledger service for batch info
        // For now, provide basic verification response
        String productName;
        String quality;
        String origin;
        int qualityScore;
        
        // Only use demo data if flag is set
        if (shouldLoadDemoData()) {
            // Demo verification with mock data patterns
            if (batchId.toUpperCase().contains("A")) {
                productName = "Summer Apples (demo)";
                quality = "PRIME";
                qualityScore = 92;
                origin = "Sunny Valley Orchards (demo)";
            } else if (batchId.toUpperCase().contains("B")) {
                productName = "Organic Carrots (demo)";
                quality = "PRIME";
                qualityScore = 88;
                origin = "Green Fields Farm (demo)";
            } else {
                productName = "Mixed Vegetables (demo)";
                quality = "STANDARD";
                qualityScore = 85;
                origin = "Valley Fresh Farms (demo)";
            }
        } else {
            // Provide basic fallback when demo mode is not enabled
            productName = "Product (Batch " + batchId + ")";
            quality = "VERIFIED";
            qualityScore = 85;
            origin = "Farm Network";
        }

        String verificationResult = "✅ VERIFIED: Batch " + batchId +
                "\nProduct: " + productName +
                "\nQuality: " + quality + " (" + qualityScore + "%)" +
                "\nOrigin: " + origin +
                "\nVerification Time: " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        showAlert(Alert.AlertType.INFORMATION, "Verification Complete", verificationResult);

        // Add to history with proper formatting
        String historyEntry = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                ": " + batchId + " - ✅ VERIFIED (" + productName + ")";
        
        Platform.runLater(() -> {
            verificationHistory.add(0, historyEntry);
        });
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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void cleanup() {
        // Clear active journeys
        activeJourneys.clear();
        
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
        // Create new journey tracking
        DeliveryJourney journey = new DeliveryJourney(batchId, farmerId);
        journey.currentStatus = "Started";
        activeJourneys.put(batchId, journey);
        
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message = timestamp + ": 🚚 Batch " + batchId + " is now in transit from " + farmerId;
            verificationHistory.add(0, message);
            
            // Update Product Journey display
            updateProductJourneyDisplay(batchId);
            
            System.out.println("ConsumerController: Simulation started - " + batchId + " from " + farmerId);
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        // Update journey tracking
        DeliveryJourney journey = activeJourneys.get(batchId);
        if (journey != null) {
            journey.currentProgress = progress;
            journey.currentLocation = currentLocation != null ? currentLocation : "In Transit";
            
            // Update status based on progress
            if (progress >= 95) {
                journey.currentStatus = "Completing";
            } else if (progress >= 70) {
                journey.currentStatus = "Approaching";
            } else if (progress >= 10) {
                journey.currentStatus = "In Transit";
            }
            
            // Update display on significant progress points
            if (shouldUpdateDisplay(progress)) {
                Platform.runLater(() -> {
                    updateProductJourneyDisplay(batchId);
                });
            }
        }
        
        // Add journey milestone updates for significant progress points
        // Only update history at key milestones to avoid cluttering
        if (Math.abs(progress - 25.0) < 1.0 || Math.abs(progress - 50.0) < 1.0 || Math.abs(progress - 75.0) < 1.0) {
            Platform.runLater(() -> {
                String timestamp = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String message = String.format("%s: 📍 Batch %s - %.0f%% complete - %s", 
                                             timestamp, batchId, progress, currentLocation);
                verificationHistory.add(0, message);
                System.out.println("ConsumerController: Journey milestone - " + batchId + " at " + progress + "%");
            });
        }
    }
    
    /**
     * Determine if display should be updated at this progress point.
     */
    private boolean shouldUpdateDisplay(double progress) {
        // Update at key thresholds that correspond to state changes
        return progress < 1 ||
               (progress >= 10 && progress < 11) ||
               (progress >= 25 && progress < 26) ||
               (progress >= 50 && progress < 51) ||
               (progress >= 70 && progress < 71) ||
               (progress >= 95 && progress < 96);
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        DeliveryJourney journey = activeJourneys.get(batchId);
        
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            if (journey != null) {
                journey.currentStatus = completed ? "Delivered" : "Stopped";
                journey.currentProgress = completed ? 100 : journey.currentProgress;
                
                // For completed deliveries, estimate final quality based on transit time
                // In real implementation, this would come from the simulator
                if (completed && journey.finalQuality < 0) {
                    long elapsedMs = System.currentTimeMillis() - journey.startTime;
                    double elapsedSeconds = elapsedMs / 1000.0;
                    // Use demo decay rate constant from SimulationConfig
                    journey.finalQuality = 100.0 * Math.exp(-SimulationConfig.DEMO_MODE_DECAY_RATE * elapsedSeconds);
                }
                
                updateProductJourneyDisplay(batchId);
            }
            
            String message;
            if (completed && journey != null && journey.finalQuality > 0) {
                message = String.format("%s: ✅ Batch %s delivered - Final Quality: %.1f%% - Ready for verification",
                                       timestamp, batchId, journey.finalQuality);
            } else if (completed) {
                message = timestamp + ": ✅ Batch " + batchId + " delivered successfully - Ready for verification";
            } else {
                message = timestamp + ": ⏹ Delivery stopped for batch " + batchId;
            }
            
            verificationHistory.add(0, message);
            System.out.println("ConsumerController: " + (completed ? "Delivery completed" : "Delivery stopped") + " - " + batchId);
        });
    }
    
    @Override
    public void onSimulationError(String batchId, String error) {
        DeliveryJourney journey = activeJourneys.get(batchId);
        if (journey != null) {
            journey.currentStatus = "Error";
        }
        
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message = timestamp + ": ❌ Delivery issue for batch " + batchId + " - " + error;
            verificationHistory.add(0, message);
            System.err.println("ConsumerController: Simulation error - " + error + " for batch " + batchId);
        });
    }
    
    /**
     * Set the final quality for a delivery (called from enhanced simulator).
     * @param batchId Batch identifier
     * @param finalQuality Final quality score (0-100)
     */
    public void setFinalQuality(String batchId, double finalQuality) {
        DeliveryJourney journey = activeJourneys.get(batchId);
        if (journey != null) {
            journey.finalQuality = finalQuality;
            Platform.runLater(() -> {
                updateProductJourneyDisplay(batchId);
            });
        }
    }
}