package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import org.vericrop.gui.util.QRDecoder;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import com.google.zxing.NotFoundException;

public class ConsumerController implements SimulationListener {

    @FXML private TextField batchIdField;
    @FXML private ListView<String> verificationHistoryList;
    @FXML private Button backToProducerButton;
    @FXML private Button analyticsButton;
    @FXML private Button logisticsButton;

    private ObservableList<String> verificationHistory = FXCollections.observableArrayList();
    private Set<String> knownBatchIds = new HashSet<>();

    @FXML
    public void initialize() {
        setupVerificationHistory();
        setupNavigationButtons();
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
        if (analyticsButton != null) {
            analyticsButton.setOnAction(e -> handleShowAnalytics());
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
    private void handleShowAnalytics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showAnalyticsScreen();
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
    
    // ========== SimulationListener Implementation ==========
    
    @Override
    public void onSimulationStarted(String batchId, String farmerId) {
        Platform.runLater(() -> {
            verificationHistory.add(0, "🚚 Batch " + batchId + " is now in transit - Track in Logistics tab");
            System.out.println("ConsumerController: Simulation started - " + batchId);
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        // Consumer doesn't need detailed progress updates
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        Platform.runLater(() -> {
            String message = completed ? 
                "✅ Batch " + batchId + " delivered successfully" : 
                "⏹ Delivery stopped for batch " + batchId;
            verificationHistory.add(0, message);
            System.out.println("ConsumerController: " + message);
        });
    }
    
    @Override
    public void onSimulationError(String batchId, String error) {
        Platform.runLater(() -> {
            verificationHistory.add(0, "❌ Delivery issue for batch " + batchId);
            System.err.println("ConsumerController: Simulation error - " + error);
        });
    }
}