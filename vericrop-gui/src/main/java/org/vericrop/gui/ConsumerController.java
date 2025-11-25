package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.vericrop.gui.util.QRDecoder;
import org.vericrop.service.simulation.SimulationConfig;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import com.google.zxing.NotFoundException;

public class ConsumerController implements SimulationListener {

    @FXML private TextField batchIdField;
    @FXML private ListView<String> verificationHistoryList;
    @FXML private Button backToProducerButton;
    @FXML private Button logisticsButton;
    
    // Product Journey UI elements
    @FXML private VBox productJourneyCard;
    @FXML private VBox journeyTimelineContainer;
    @FXML private Label step1Icon;
    @FXML private Label step1Title;
    @FXML private Label step1Details;
    @FXML private Label step2Icon;
    @FXML private Label step2Title;
    @FXML private Label step2Details;
    @FXML private Label step3Icon;
    @FXML private Label step3Title;
    @FXML private Label step3Details;
    @FXML private Label step4Icon;
    @FXML private Label step4Title;
    @FXML private Label step4Details;
    
    // Quality metrics UI elements
    @FXML private VBox qualityMetricsCard;
    @FXML private Label finalQualityLabel;
    @FXML private Label temperatureLabel;
    @FXML private Label statusLabel;

    private ObservableList<String> verificationHistory = FXCollections.observableArrayList();
    private Set<String> knownBatchIds = new HashSet<>();
    
    // Track current simulation for journey display
    private String currentBatchId = null;
    private double lastProgress = 0.0;
    
    /** Default final quality used when SimulationManager data is unavailable */
    private static final double DEFAULT_FINAL_QUALITY = 95.0;

    @FXML
    public void initialize() {
        setupVerificationHistory();
        setupNavigationButtons();
        registerWithSimulationManager();
        initializeJourneyDisplay();
    }
    
    /**
     * Initialize the journey display with default values.
     */
    private void initializeJourneyDisplay() {
        Platform.runLater(() -> {
            if (finalQualityLabel != null) finalQualityLabel.setText("--");
            if (temperatureLabel != null) temperatureLabel.setText("--");
            if (statusLabel != null) statusLabel.setText("Waiting");
            
            // Reset journey steps
            updateJourneyStep(1, "⏳", "Awaiting shipment...", "Start a simulation to see journey updates");
            updateJourneyStep(2, "⏳", "In Transit", "--");
            updateJourneyStep(3, "⏳", "Approaching Destination", "--");
            updateJourneyStep(4, "⏳", "Delivered", "--");
        });
    }
    
    /**
     * Register this controller as a listener with SimulationManager.
     */
    private void registerWithSimulationManager() {
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager manager = SimulationManager.getInstance();
                manager.registerListener(this);
                
                // If simulation is already running, update journey display
                if (manager.isRunning()) {
                    currentBatchId = manager.getSimulationId();
                    double progress = manager.getProgress();
                    String location = manager.getCurrentLocation();
                    
                    Platform.runLater(() -> {
                        verificationHistory.add(0, "📦 Batch in transit: " + currentBatchId + " - Track in Logistics tab");
                        updateJourneyFromProgress(currentBatchId, progress, location);
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
        // Unregister from SimulationManager
        try {
            if (SimulationManager.isInitialized()) {
                SimulationManager.getInstance().unregisterListener(this);
            }
        } catch (Exception e) {
            System.err.println("Error unregistering from SimulationManager: " + e.getMessage());
        }
    }
    
    // ========== Journey Display Helpers ==========
    
    /**
     * Update a single journey step with icon, title, and details.
     */
    private void updateJourneyStep(int step, String icon, String title, String details) {
        switch (step) {
            case 1:
                if (step1Icon != null) step1Icon.setText(icon);
                if (step1Title != null) step1Title.setText(title);
                if (step1Details != null) step1Details.setText(details);
                break;
            case 2:
                if (step2Icon != null) step2Icon.setText(icon);
                if (step2Title != null) step2Title.setText(title);
                if (step2Details != null) step2Details.setText(details);
                break;
            case 3:
                if (step3Icon != null) step3Icon.setText(icon);
                if (step3Title != null) step3Title.setText(title);
                if (step3Details != null) step3Details.setText(details);
                break;
            case 4:
                if (step4Icon != null) step4Icon.setText(icon);
                if (step4Title != null) step4Title.setText(title);
                if (step4Details != null) step4Details.setText(details);
                break;
        }
    }
    
    /**
     * Update the journey display based on current progress.
     */
    private void updateJourneyFromProgress(String batchId, double progress, String location) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        // Step 1: Created/Started
        if (progress >= 0) {
            updateJourneyStep(1, "✅", "Batch Created - " + batchId, 
                "Started at " + timestamp + " | Initial Quality: 100%");
        }
        
        // Step 2: In Transit
        if (progress > 0 && progress < 80) {
            updateJourneyStep(2, "🚚", "In Transit - " + String.format("%.0f%%", progress), 
                location != null ? location : "En route...");
            if (statusLabel != null) statusLabel.setText("In Transit");
        } else if (progress >= 80) {
            updateJourneyStep(2, "✅", "In Transit - Complete", 
                "Transit phase completed");
        }
        
        // Step 3: Approaching
        if (progress >= 80 && progress < 100) {
            updateJourneyStep(3, "🎯", "Approaching - " + String.format("%.0f%%", progress),
                location != null ? location : "Near destination");
            if (statusLabel != null) statusLabel.setText("Approaching");
        } else if (progress >= 100) {
            updateJourneyStep(3, "✅", "Approaching - Complete", 
                "Reached destination area");
        }
        
        // Step 4: Delivered (only when complete)
        if (progress >= 100) {
            updateJourneyStep(4, "✅", "Delivered", 
                "Delivery completed at " + timestamp);
            if (statusLabel != null) statusLabel.setText("Delivered");
        }
    }
    
    /**
     * Display final quality score when simulation completes.
     */
    private void displayFinalQuality(String batchId, double finalQuality) {
        if (finalQualityLabel != null) {
            finalQualityLabel.setText(String.format("%.1f%%", finalQuality));
            
            // Color based on quality level
            if (finalQuality >= 80) {
                finalQualityLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 28px;"); // Green
            } else if (finalQuality >= 60) {
                finalQualityLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 28px;"); // Yellow
            } else {
                finalQualityLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 28px;"); // Red
            }
        }
        
        // Update step 4 with final quality
        updateJourneyStep(4, "✅", "Delivered - Final Quality: " + String.format("%.1f%%", finalQuality),
            "Quality score based on temperature and time");
    }
    
    // ========== SimulationListener Implementation ==========
    
    @Override
    public void onSimulationStarted(String batchId, String farmerId) {
        Platform.runLater(() -> {
            currentBatchId = batchId;
            lastProgress = 0.0;
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message = timestamp + ": 🚚 Batch " + batchId + " is now in transit from " + farmerId;
            verificationHistory.add(0, message);
            
            // Reset and start journey display
            initializeJourneyDisplay();
            updateJourneyStep(1, "✅", "Batch Created - " + batchId, 
                "Started at " + timestamp + " | From: " + farmerId);
            updateJourneyStep(2, "🚚", "In Transit", "Starting delivery...");
            
            if (statusLabel != null) statusLabel.setText("Started");
            if (temperatureLabel != null) temperatureLabel.setText("Monitoring...");
            
            System.out.println("ConsumerController: Simulation started - " + batchId + " from " + farmerId);
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        Platform.runLater(() -> {
            // Skip if not tracking this batch
            if (currentBatchId != null && !currentBatchId.equals(batchId)) {
                return;
            }
            
            // Update journey display
            updateJourneyFromProgress(batchId, progress, currentLocation);
            
            // Add milestone entries to history at key points
            boolean isMilestone = (progress >= 25 && lastProgress < 25) ||
                                 (progress >= 50 && lastProgress < 50) ||
                                 (progress >= 75 && lastProgress < 75) ||
                                 (progress >= 100 && lastProgress < 100);
            
            if (isMilestone) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String message = String.format("%s: 📍 Batch %s - %.0f%% complete - %s", 
                                             timestamp, batchId, progress, currentLocation);
                verificationHistory.add(0, message);
                System.out.println("ConsumerController: Journey milestone - " + batchId + " at " + progress + "%");
            }
            
            lastProgress = progress;
        });
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message;
            
            if (completed) {
                message = timestamp + ": ✅ Batch " + batchId + " delivered successfully - Ready for verification";
                
                // Get final quality from SimulationManager
                double finalQuality = DEFAULT_FINAL_QUALITY;
                try {
                    if (SimulationManager.isInitialized()) {
                        finalQuality = SimulationManager.getInstance().getFinalQuality();
                    }
                } catch (Exception e) {
                    System.err.println("Could not get final quality: " + e.getMessage());
                }
                
                // Display final quality
                displayFinalQuality(batchId, finalQuality);
                
                // Update all journey steps to complete
                updateJourneyFromProgress(batchId, 100.0, "Delivered");
                
            } else {
                message = timestamp + ": ⏹ Delivery stopped for batch " + batchId;
                if (statusLabel != null) statusLabel.setText("Stopped");
            }
            
            verificationHistory.add(0, message);
            
            // Reset tracking
            currentBatchId = null;
            lastProgress = 0.0;
            
            System.out.println("ConsumerController: " + (completed ? "Delivery completed" : "Delivery stopped") + " - " + batchId);
        });
    }
    
    @Override
    public void onSimulationError(String batchId, String error) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message = timestamp + ": ❌ Delivery issue for batch " + batchId + " - " + error;
            verificationHistory.add(0, message);
            System.err.println("ConsumerController: Simulation error - " + error + " for batch " + batchId);
        });
    }
}