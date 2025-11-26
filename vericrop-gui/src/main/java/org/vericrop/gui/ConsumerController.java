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
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.vericrop.gui.util.QRDecoder;
import org.vericrop.service.TemperatureService;
import org.vericrop.service.simulation.SimulationConfig;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import com.google.zxing.NotFoundException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    
    /** HTTP client for backend API calls - reused for all requests */
    private OkHttpClient httpClient;
    
    /** JSON object mapper for parsing backend responses */
    private ObjectMapper mapper;

    @FXML
    public void initialize() {
        // Initialize HTTP client with 30s connect/read timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Initialize JSON mapper
        mapper = new ObjectMapper();
        
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

        String trimmedBatchId = batchId.trim();
        
        // Check if batch ID exists in knownBatchIds (case-insensitive) - for demo mode
        if (shouldLoadDemoData() && !knownBatchIds.isEmpty()) {
            boolean found = isKnownBatchId(trimmedBatchId);
            
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

        // Variables for batch info
        String productName;
        String quality;
        String origin;
        double qualityScore;
        double primeRate = 0.0;
        double rejectionRate = 0.0;
        boolean realDataFetched = false;
        
        // If NOT in demo mode, try to fetch real batch data from backend
        if (!shouldLoadDemoData()) {
            Map<String, Object> batchData = fetchBatchFromBackend(trimmedBatchId);
            
            if (batchData != null) {
                // Successfully fetched real batch data
                realDataFetched = true;
                
                // Extract fields from batch data
                productName = safeGetString(batchData, "product_type", 
                             safeGetString(batchData, "name", "Unknown Product"));
                origin = safeGetString(batchData, "farmer", "Unknown Origin");
                qualityScore = safeGetDouble(batchData, "quality_score", 0.85) * 100.0;
                primeRate = safeGetDouble(batchData, "prime_rate", 0.0) * 100.0;
                rejectionRate = safeGetDouble(batchData, "rejection_rate", 0.0) * 100.0;
                
                // Determine quality classification
                if (qualityScore >= 80) {
                    quality = "PRIME";
                } else if (qualityScore >= 60) {
                    quality = "STANDARD";
                } else {
                    quality = "SUB-STANDARD";
                }
                
                // Cache the batch ID for faster future lookups (case-insensitive)
                addToKnownBatchIds(trimmedBatchId);
                
                // Update journey display if simulation is running for this batch
                updateJourneyForVerifiedBatch(trimmedBatchId, batchData);
            } else {
                // Backend unreachable or batch not found - use fallback
                productName = "Product (Batch " + batchId + ")";
                quality = "VERIFIED";
                qualityScore = 85.0;
                origin = "Farm Network";
            }
        } else {
            // Demo mode - use mock data patterns
            if (trimmedBatchId.toUpperCase().contains("A")) {
                productName = "Summer Apples (demo)";
                quality = "PRIME";
                qualityScore = 92.0;
                origin = "Sunny Valley Orchards (demo)";
            } else if (trimmedBatchId.toUpperCase().contains("B")) {
                productName = "Organic Carrots (demo)";
                quality = "PRIME";
                qualityScore = 88.0;
                origin = "Green Fields Farm (demo)";
            } else {
                productName = "Mixed Vegetables (demo)";
                quality = "STANDARD";
                qualityScore = 85.0;
                origin = "Valley Fresh Farms (demo)";
            }
        }

        // Build verification result message
        StringBuilder verificationResult = new StringBuilder();
        verificationResult.append("✅ VERIFIED: Batch ").append(batchId)
                .append("\nProduct: ").append(productName)
                .append("\nQuality: ").append(quality).append(" (").append(String.format("%.1f", qualityScore)).append("%)")
                .append("\nOrigin: ").append(origin);
        
        // Include prime/rejection rates if real data was fetched
        if (realDataFetched) {
            verificationResult.append("\nPrime Rate: ").append(String.format("%.1f", primeRate)).append("%");
            verificationResult.append("\nRejection Rate: ").append(String.format("%.1f", rejectionRate)).append("%");
        }
        
        verificationResult.append("\nVerification Time: ").append(java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        showAlert(Alert.AlertType.INFORMATION, "Verification Complete", verificationResult.toString());

        // Add to history with proper formatting including quality score
        final String historyProductName = productName;
        final double historyQualityScore = qualityScore;
        final boolean updateQualityUI = realDataFetched;
        String historyEntry = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                ": " + batchId + " - ✅ VERIFIED (" + historyProductName + ", " + 
                String.format("%.1f", historyQualityScore) + "%)";
        
        Platform.runLater(() -> {
            verificationHistory.add(0, historyEntry);
            
            // Update quality metrics UI with real values
            if (updateQualityUI && finalQualityLabel != null) {
                finalQualityLabel.setText(String.format("%.1f%%", historyQualityScore));
                if (historyQualityScore >= 80) {
                    finalQualityLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 28px;"); // Green
                } else if (historyQualityScore >= 60) {
                    finalQualityLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 28px;"); // Yellow
                } else {
                    finalQualityLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 28px;"); // Red
                }
            }
        });
    }
    
    /**
     * Fetch batch data from the backend ledger/API.
     * 
     * @param batchId The batch ID to fetch
     * @return Map containing batch data, or null if not found or error
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchBatchFromBackend(String batchId) {
        try {
            Request request = new Request.Builder()
                    .url("http://localhost:8000/batches/" + batchId)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                
                if (response.isSuccessful() && body != null) {
                    String responseBody = body.string();
                    return mapper.readValue(responseBody, Map.class);
                } else if (response.code() == 404) {
                    System.out.println("Batch not found in backend: " + batchId);
                    return null;
                } else {
                    System.err.println("Backend returned error " + response.code() + " for batch: " + batchId);
                    return null;
                }
            }
        } catch (java.net.ConnectException e) {
            System.err.println("Cannot connect to backend service: " + e.getMessage());
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.WARNING, "Backend Unavailable", 
                    "Could not connect to backend service. Using fallback verification.");
            });
            return null;
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Backend request timed out: " + e.getMessage());
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.WARNING, "Request Timeout", 
                    "Backend request timed out. Using fallback verification.");
            });
            return null;
        } catch (IOException e) {
            System.err.println("Error fetching batch from backend: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error fetching batch: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Update journey display when a batch is verified, integrating with SimulationManager.
     * 
     * @param batchId The verified batch ID
     * @param batchData The batch data from backend
     */
    private void updateJourneyForVerifiedBatch(String batchId, Map<String, Object> batchData) {
        try {
            // Check if SimulationManager is running a simulation for this batch
            if (SimulationManager.isInitialized()) {
                SimulationManager manager = SimulationManager.getInstance();
                
                if (manager.isRunning()) {
                    String runningBatchId = manager.getSimulationId();
                    
                    // Case-insensitive comparison for batch ID
                    if (runningBatchId != null && runningBatchId.equalsIgnoreCase(batchId)) {
                        // Simulation is running for this batch - update journey display
                        double progress = manager.getProgress();
                        String location = manager.getCurrentLocation();
                        
                        Platform.runLater(() -> {
                            currentBatchId = batchId;
                            updateJourneyFromProgress(batchId, progress, location);
                            
                            if (statusLabel != null) {
                                statusLabel.setText("In Transit");
                            }
                        });
                        
                        System.out.println("ConsumerController: Updated journey display for running simulation - " + batchId);
                    }
                }
            }
            
            // Update UI with batch-specific data from backend
            Platform.runLater(() -> {
                double qualityScore = safeGetDouble(batchData, "quality_score", 0.0) * 100.0;
                String productType = safeGetString(batchData, "product_type", 
                                     safeGetString(batchData, "name", "Unknown"));
                String farmer = safeGetString(batchData, "farmer", "Unknown");
                
                // Update step 1 with real batch info
                updateJourneyStep(1, "✅", "Batch Verified - " + batchId,
                    "Product: " + productType + " | Origin: " + farmer + 
                    " | Quality: " + String.format("%.1f%%", qualityScore));
            });
            
        } catch (Exception e) {
            System.err.println("Error updating journey for verified batch: " + e.getMessage());
        }
    }
    
    /**
     * Check if batch ID exists in known batch IDs (case-insensitive).
     */
    private boolean isKnownBatchId(String batchId) {
        if (batchId == null) {
            return false;
        }
        for (String knownId : knownBatchIds) {
            if (knownId.equalsIgnoreCase(batchId.trim())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Add a batch ID to the known batch IDs cache (avoids duplicates, case-insensitive).
     */
    private void addToKnownBatchIds(String batchId) {
        if (batchId != null && !isKnownBatchId(batchId)) {
            knownBatchIds.add(batchId.trim());
        }
    }
    
    /**
     * Safely get a string value from a map, with a default fallback.
     */
    private String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Safely get a double value from a map, with a default fallback.
     */
    private double safeGetDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
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
    
    /**
     * Display average temperature for the delivered batch.
     * Retrieves temperature data from TemperatureService and shows it in the UI.
     * Shows "N/A" if no temperature samples exist for the batch.
     * 
     * @param batchId The batch identifier to get temperature data for
     */
    private void displayAverageTemperature(String batchId) {
        if (temperatureLabel == null) {
            return;
        }
        
        try {
            // Get TemperatureService from ApplicationContext
            TemperatureService tempService = MainApp.getInstance()
                .getApplicationContext()
                .getTemperatureService();
            
            if (tempService != null) {
                TemperatureService.TemperatureMonitoring monitoring = tempService.getMonitoring(batchId);
                
                if (monitoring != null && monitoring.getReadingCount() > 0) {
                    // Display average temperature with one decimal place
                    double avgTemp = monitoring.getAvgTemp();
                    temperatureLabel.setText(String.format("%.1f°C", avgTemp));
                    
                    // Color based on temperature compliance (2-8°C is optimal for cold chain)
                    if (avgTemp >= 2.0 && avgTemp <= 8.0) {
                        temperatureLabel.setStyle("-fx-text-fill: #10b981;"); // Green - compliant
                    } else if (avgTemp < 2.0 || avgTemp > 10.0) {
                        temperatureLabel.setStyle("-fx-text-fill: #dc2626;"); // Red - out of range
                    } else {
                        temperatureLabel.setStyle("-fx-text-fill: #f59e0b;"); // Yellow - marginal
                    }
                    
                    System.out.println("ConsumerController: Displayed average temperature " + 
                        String.format("%.1f°C", avgTemp) + " for batch " + batchId);
                } else {
                    // No temperature samples recorded
                    temperatureLabel.setText("N/A");
                    temperatureLabel.setStyle("-fx-text-fill: #64748b;"); // Gray
                    System.out.println("ConsumerController: No temperature data for batch " + batchId);
                }
            } else {
                temperatureLabel.setText("N/A");
                temperatureLabel.setStyle("-fx-text-fill: #64748b;");
            }
        } catch (Exception e) {
            System.err.println("Error getting average temperature: " + e.getMessage());
            temperatureLabel.setText("N/A");
            temperatureLabel.setStyle("-fx-text-fill: #64748b;");
        }
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
                
                // Get and display average temperature from TemperatureService
                displayAverageTemperature(batchId);
                
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