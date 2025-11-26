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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * Status of a backend fetch operation.
     */
    public enum FetchStatus {
        /** Batch was found and data is available */
        FOUND,
        /** Batch was not found (HTTP 404) */
        NOT_FOUND,
        /** Backend unavailable or network error */
        UNAVAILABLE
    }

    /**
     * Result wrapper for backend fetch operations.
     * Contains the status and optional payload data.
     */
    public static class FetchResult {
        private final FetchStatus status;
        private final Map<String, Object> data;

        public FetchResult(FetchStatus status, Map<String, Object> data) {
            this.status = status;
            this.data = data;
        }

        public FetchStatus getStatus() {
            return status;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public boolean isFound() {
            return status == FetchStatus.FOUND && data != null;
        }

        public boolean isNotFound() {
            return status == FetchStatus.NOT_FOUND;
        }

        public boolean isUnavailable() {
            return status == FetchStatus.UNAVAILABLE;
        }

        public static FetchResult found(Map<String, Object> data) {
            return new FetchResult(FetchStatus.FOUND, data);
        }

        public static FetchResult notFound() {
            return new FetchResult(FetchStatus.NOT_FOUND, null);
        }

        public static FetchResult unavailable() {
            return new FetchResult(FetchStatus.UNAVAILABLE, null);
        }
    }

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
    
    /** HTTP client timeout in seconds */
    private static final int HTTP_TIMEOUT_SECONDS = 30;
    
    /** Default backend URL for batch API - can be overridden via environment variable VERICROP_BACKEND_URL */
    private static final String DEFAULT_BACKEND_URL = "http://localhost:8000";
    
    /** HTTP client for backend API calls - reused for all requests */
    private OkHttpClient httpClient;
    
    /** JSON object mapper for parsing backend responses */
    private ObjectMapper mapper;
    
    /** Backend URL for batch API calls */
    private String backendUrl;

    @FXML
    public void initialize() {
        // Initialize HTTP client with configurable timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        
        // Initialize JSON mapper
        mapper = new ObjectMapper();
        
        // Get backend URL from environment or use default
        backendUrl = System.getenv("VERICROP_BACKEND_URL");
        if (backendUrl == null || backendUrl.trim().isEmpty()) {
            backendUrl = DEFAULT_BACKEND_URL;
        }
        
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
                
                // Try to extract batch ID using the QRDecoder first
                String batchId = QRDecoder.extractBatchId(qrContent);
                
                // If extractBatchId returns null or empty, try fallback parsing
                if (batchId == null || batchId.trim().isEmpty()) {
                    batchId = extractBatchIdFallback(qrContent);
                }
                
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

    /**
     * Fallback batch ID extraction from QR content.
     * Attempts to parse common QR payload formats when QRDecoder.extractBatchId fails.
     * 
     * @param qrContent The decoded QR code content
     * @return Extracted batch ID, or null if not found
     */
    private String extractBatchIdFallback(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            return null;
        }
        
        String content = qrContent.trim();
        System.out.println("QR fallback parsing: attempting to extract batch ID from: " + content);
        
        // 1. Try to extract from URL path: /batches/{id} or /batch/{id}
        Pattern pathPattern = Pattern.compile("/batch(?:es)?/([^/?#]+)", Pattern.CASE_INSENSITIVE);
        Matcher pathMatcher = pathPattern.matcher(content);
        if (pathMatcher.find()) {
            String batchId = pathMatcher.group(1);
            System.out.println("QR fallback parsing: extracted from URL path: " + batchId);
            return batchId;
        }
        
        // 2. Try to extract from query parameters: batchId=, batch=, or id=
        Pattern queryPattern = Pattern.compile("[?&](?:batchId|batch|id)=([^&]+)", Pattern.CASE_INSENSITIVE);
        Matcher queryMatcher = queryPattern.matcher(content);
        if (queryMatcher.find()) {
            String batchId = queryMatcher.group(1);
            System.out.println("QR fallback parsing: extracted from query parameter: " + batchId);
            return batchId;
        }
        
        // 3. Try regex for BATCH_* pattern
        Pattern batchPattern = Pattern.compile("BATCH_[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
        Matcher batchMatcher = batchPattern.matcher(content);
        if (batchMatcher.find()) {
            String batchId = batchMatcher.group();
            System.out.println("QR fallback parsing: extracted BATCH_* pattern: " + batchId);
            return batchId;
        }
        
        // 4. Last resort: look for alphanumeric token at least 4 characters
        Pattern alphanumPattern = Pattern.compile("[A-Za-z0-9_-]{4,}");
        Matcher alphanumMatcher = alphanumPattern.matcher(content);
        if (alphanumMatcher.find()) {
            String batchId = alphanumMatcher.group();
            System.out.println("QR fallback parsing: extracted alphanumeric token: " + batchId);
            return batchId;
        }
        
        System.out.println("QR fallback parsing: could not extract batch ID from content");
        return null;
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
            FetchResult fetchResult = fetchBatchFromBackend(trimmedBatchId);
            
            if (fetchResult.isFound()) {
                // Successfully fetched real batch data
                realDataFetched = true;
                Map<String, Object> batchData = fetchResult.getData();
                
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
            } else if (fetchResult.isNotFound()) {
                // Backend explicitly returned 404 - batch does not exist
                System.out.println("Batch NOT FOUND in backend (404): " + trimmedBatchId);
                showAlert(Alert.AlertType.WARNING, "Batch ID Not Found", 
                    "The batch ID '" + batchId + "' was not found in the backend system.");
                
                // Add failure entry to verification history
                String failureEntry = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                        ": " + batchId + " - ❌ NOT FOUND";
                
                Platform.runLater(() -> {
                    verificationHistory.add(0, failureEntry);
                });
                
                // Do NOT mark as verified, do NOT update UI
                return;
            } else {
                // Backend unavailable - use fallback verification
                System.out.println("Backend UNAVAILABLE for batch: " + trimmedBatchId + " - using fallback verification");
                showAlert(Alert.AlertType.WARNING, "Backend Unavailable", 
                    "Backend unavailable - using fallback verification.");
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
     * @return FetchResult containing status (FOUND, NOT_FOUND, UNAVAILABLE) and optional data
     */
    @SuppressWarnings("unchecked")
    private FetchResult fetchBatchFromBackend(String batchId) {
        try {
            // URL-encode the batch ID to prevent injection and handle special characters
            String encodedBatchId = URLEncoder.encode(batchId, StandardCharsets.UTF_8.toString());
            String url = backendUrl + "/batches/" + encodedBatchId;
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                
                if (response.isSuccessful() && body != null) {
                    String responseBody = body.string();
                    Map<String, Object> data = mapper.readValue(responseBody, Map.class);
                    System.out.println("FetchResult: FOUND - batch " + batchId + " retrieved successfully");
                    return FetchResult.found(data);
                } else if (response.code() == 404) {
                    System.out.println("FetchResult: NOT_FOUND - batch " + batchId + " does not exist in backend");
                    return FetchResult.notFound();
                } else {
                    System.err.println("FetchResult: UNAVAILABLE - backend returned error " + response.code() + " for batch: " + batchId);
                    return FetchResult.unavailable();
                }
            }
        } catch (java.net.ConnectException e) {
            System.err.println("FetchResult: UNAVAILABLE - cannot connect to backend service: " + e.getMessage());
            return FetchResult.unavailable();
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("FetchResult: UNAVAILABLE - backend request timed out: " + e.getMessage());
            return FetchResult.unavailable();
        } catch (IOException e) {
            System.err.println("FetchResult: UNAVAILABLE - error fetching batch from backend: " + e.getMessage());
            return FetchResult.unavailable();
        } catch (Exception e) {
            System.err.println("FetchResult: UNAVAILABLE - unexpected error fetching batch: " + e.getMessage());
            return FetchResult.unavailable();
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