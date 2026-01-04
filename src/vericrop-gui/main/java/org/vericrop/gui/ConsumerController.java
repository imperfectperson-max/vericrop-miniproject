package org.vericrop.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.FileWriter;
import java.io.PrintWriter;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.vericrop.gui.util.QRDecoder;
import org.vericrop.service.TemperatureService;
import org.vericrop.service.QualityAssessmentService;
import org.vericrop.service.simulation.SimulationConfig;
import org.vericrop.service.simulation.SimulationListener;
import org.vericrop.service.simulation.SimulationManager;
import org.vericrop.kafka.consumers.SimulationControlConsumer;
import org.vericrop.kafka.consumers.MapSimulationEventConsumer;
import org.vericrop.kafka.events.SimulationControlEvent;
import org.vericrop.kafka.events.InstanceHeartbeatEvent;
import org.vericrop.kafka.services.InstanceRegistry;
import org.vericrop.dto.MapSimulationEvent;
import org.vericrop.constants.ShipmentStatus;
import com.google.zxing.NotFoundException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerController implements SimulationListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerController.class);

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
    
    // Kafka consumer for simulation control events
    private SimulationControlConsumer simulationControlConsumer;
    private MapSimulationEventConsumer mapSimulationConsumer;
    private ExecutorService kafkaConsumerExecutor;
    
    // Instance registry for multi-instance tracking
    private InstanceRegistry instanceRegistry;
    
    // Quality assessment service for computing final quality
    private QualityAssessmentService qualityAssessmentService;
    
    // Track simulation start time for quality decay calculation
    private long simulationStartTimeMs = 0;
    
    /** Default final quality used when SimulationManager data is unavailable */
    private static final double DEFAULT_FINAL_QUALITY = 95.0;
    
    /** HTTP client timeout in seconds */
    private static final int HTTP_TIMEOUT_SECONDS = 30;
    
    /** Default backend URL for batch API - can be overridden via environment variable VERICROP_BACKEND_URL */
    private static final String DEFAULT_BACKEND_URL = "http://localhost:8000";
    
    // Pre-compiled regex patterns for QR fallback parsing (cached for performance)
    /** Pattern for extracting batch ID from URL path like /batches/{id} or /batch/{id} */
    private static final Pattern URL_PATH_PATTERN = Pattern.compile("/batch(?:es)?/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    
    /** Pattern for extracting batch ID from query parameters like batchId=, batch=, or id= */
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("[?&](?:batchId|batch|id)=([^&]+)", Pattern.CASE_INSENSITIVE);
    
    /** Pattern for extracting BATCH_* style identifiers */
    private static final Pattern BATCH_ID_PATTERN = Pattern.compile("BATCH_[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    
    /** Pattern for extracting alphanumeric tokens (4+ characters) as last resort */
    private static final Pattern ALPHANUMERIC_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{4,}");
    
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
        setupKafkaConsumers();
        initializeInstanceRegistry();
        initializeQualityAssessmentService();
    }
    
    /**
     * Initialize instance registry with CONSUMER role for multi-instance coordination.
     */
    private void initializeInstanceRegistry() {
        try {
            this.instanceRegistry = new InstanceRegistry(InstanceHeartbeatEvent.Role.CONSUMER);
            this.instanceRegistry.start();
            System.out.println("📡 ConsumerController instance registry started with ID: " + 
                              instanceRegistry.getInstanceId() + " (role: " + instanceRegistry.getRole() + ")");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to initialize instance registry: " + e.getMessage());
            // Continue without instance registry - simulation coordination may be affected
        }
    }
    
    /**
     * Initialize quality assessment service for computing final quality on delivery.
     */
    private void initializeQualityAssessmentService() {
        try {
            qualityAssessmentService = MainApp.getInstance()
                .getApplicationContext()
                .getQualityAssessmentService();
            
            if (qualityAssessmentService != null) {
                System.out.println("✅ QualityAssessmentService initialized for ConsumerController");
            } else {
                System.err.println("⚠️ QualityAssessmentService is null in ApplicationContext");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to initialize QualityAssessmentService: " + e.getMessage());
            // Continue without quality assessment - will use default quality values
        }
    }
    
    /**
     * Setup Kafka consumers for simulation control and map simulation events.
     * The MapSimulationEventConsumer is critical for receiving real-time progress
     * updates when running controllers as separate processes.
     */
    private void setupKafkaConsumers() {
        try {
            // Thread pool for Kafka consumers: SimulationControl and MapSimulation
            final int KAFKA_CONSUMER_COUNT = 2;
            kafkaConsumerExecutor = Executors.newFixedThreadPool(KAFKA_CONSUMER_COUNT);
            
            // Create simulation control consumer with unique group ID per instance
            // Using timestamp-based ID avoids UUID substring collisions
            String uniqueGroupId = "consumer-simulation-control-" + System.currentTimeMillis();
            simulationControlConsumer = new SimulationControlConsumer(
                uniqueGroupId,
                this::handleSimulationControlEvent
            );
            
            // Create map simulation consumer with unique group ID per instance
            // This consumer receives real-time progress updates from the simulation
            String mapGroupId = "consumer-map-simulation-" + System.currentTimeMillis();
            mapSimulationConsumer = new MapSimulationEventConsumer(
                mapGroupId,
                this::handleMapSimulationEvent
            );
            
            // Start simulation control consumer in background thread
            kafkaConsumerExecutor.submit(() -> {
                try {
                    simulationControlConsumer.startConsuming();
                } catch (Exception e) {
                    System.err.println("Simulation control consumer error: " + e.getMessage());
                }
            });
            
            // Start map simulation consumer in background thread
            kafkaConsumerExecutor.submit(() -> {
                try {
                    mapSimulationConsumer.startConsuming();
                } catch (Exception e) {
                    System.err.println("Map simulation consumer error: " + e.getMessage());
                }
            });
            
            System.out.println("✅ Kafka consumers initialized for consumer verification (SimulationControl + MapSimulation)");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to initialize Kafka consumers: " + e.getMessage());
            // Continue without Kafka - fallback to SimulationListener updates
        }
    }
    
    /**
     * Handle simulation control event from Kafka.
     * Coordinates simulation start/stop across instances.
     */
    /**
     * Handle simulation control event from Kafka.
     * Coordinates simulation start/stop across instances.
     * Uses Platform.runLater to ensure UI updates happen on JavaFX thread.
     */
    private void handleSimulationControlEvent(SimulationControlEvent event) {
        if (event == null) {
            return;
        }
        
        System.out.println("📡 ConsumerController received simulation control event: " + event);
        
        Platform.runLater(() -> {
            try {
                if (event.isStart()) {
                    // Handle simulation START
                    String batchId = event.getBatchId();
                    String farmerId = event.getFarmerId();
                    
                    currentBatchId = batchId;
                    lastProgress = 0.0;
                    simulationStartTimeMs = System.currentTimeMillis();
                    
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String message = timestamp + ": 🚚 Batch " + batchId + " is now in transit from " + farmerId + " (via Kafka)";
                    verificationHistory.add(0, message);
                    
                    // Reset and start journey display
                    initializeJourneyDisplay();
                    updateJourneyStep(1, "✅", "Batch Created - " + batchId, 
                        "Started at " + timestamp + " | From: " + farmerId);
                    updateJourneyStep(2, "🚚", "In Transit", "Starting delivery...");
                    
                    if (statusLabel != null) statusLabel.setText("Started");
                    if (temperatureLabel != null) temperatureLabel.setText("Monitoring...");
                    
                    System.out.println("✅ ConsumerController: Started tracking simulation via Kafka: " + batchId);
                    
                } else if (event.isStop()) {
                    // Handle simulation STOP
                    String batchId = event.getBatchId() != null ? event.getBatchId() : event.getSimulationId();
                    
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String message = timestamp + ": ✅ Batch " + batchId + " delivered (via Kafka) - Ready for verification";
                    verificationHistory.add(0, message);
                    
                    // Compute final quality using QualityAssessmentService (same as onSimulationStopped)
                    double finalQuality = DEFAULT_FINAL_QUALITY;
                    try {
                        if (qualityAssessmentService != null && simulationStartTimeMs > 0) {
                            QualityAssessmentService.FinalQualityAssessment assessment =
                                qualityAssessmentService.assessFinalQualityFromMonitoring(
                                    batchId, 100.0, simulationStartTimeMs);
                            
                            if (assessment != null) {
                                finalQuality = assessment.getFinalQuality();
                                System.out.println("ConsumerController: Computed final quality via Kafka stop: " + 
                                                 finalQuality + "% (grade: " + assessment.getQualityGrade() + ")");
                                
                                // Update journey step with detailed quality info
                                updateJourneyStep(4, "✅", "Delivered - " + assessment.getQualityGrade(),
                                    String.format("Final Quality: %.1f%% | Temp Violations: %d | Alerts: %d",
                                        finalQuality, assessment.getTemperatureViolations(), assessment.getAlertCount()));
                            }
                        } else if (SimulationManager.isInitialized()) {
                            finalQuality = SimulationManager.getInstance().getFinalQuality();
                        }
                    } catch (Exception e) {
                        System.err.println("Could not compute final quality via Kafka: " + e.getMessage());
                    }
                    
                    // Display final quality
                    displayFinalQuality(batchId, finalQuality);
                    
                    // Get and display average temperature from TemperatureService
                    displayAverageTemperature(batchId);
                    
                    // Update all journey steps to complete
                    updateJourneyFromProgress(batchId, 100.0, "Delivered");
                    
                    // Reset tracking
                    currentBatchId = null;
                    lastProgress = 0.0;
                    simulationStartTimeMs = 0;
                    
                    System.out.println("✅ ConsumerController: Stopped tracking simulation via Kafka: " + batchId);
                }
            } catch (Exception e) {
                System.err.println("❌ Error handling simulation control event: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Handle map simulation event from Kafka.
     * This provides real-time progress updates when running controllers as separate processes.
     * Updates Product Journey UI with current status and progress.
     */
    private void handleMapSimulationEvent(MapSimulationEvent event) {
        if (event == null || event.getBatchId() == null) {
            logger.warn("Received null or invalid map simulation event");
            return;
        }
        
        logger.debug("🗺️  ConsumerController received map event: {} at progress {}% - {}", 
                    event.getBatchId(), event.getProgress() * 100, event.getLocationName());
        
        Platform.runLater(() -> {
            try {
                String batchId = event.getBatchId();
                double progressPercent = event.getProgress() * 100; // Convert from 0-1 to 0-100
                String location = event.getLocationName();
                String status = event.getStatus();
                
                // Skip backward progress updates (prevent restart issues)
                // Use strict less-than to allow events at the same progress level to update
                // temperature, location, and other fields
                if (currentBatchId != null && currentBatchId.equals(batchId) && progressPercent < lastProgress) {
                    return;
                }
                
                // Track this batch if not already tracking
                if (currentBatchId == null || !currentBatchId.equals(batchId)) {
                    currentBatchId = batchId;
                    lastProgress = 0.0;
                    // Initialize start time if not set
                    if (simulationStartTimeMs == 0) {
                        simulationStartTimeMs = System.currentTimeMillis();
                    }
                    
                    // Initialize journey display for new batch
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    updateJourneyStep(1, "✅", "Batch Created - " + batchId, 
                        "Started tracking at " + timestamp);
                    
                    // Add to verification history
                    String historyTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String message = historyTimestamp + ": 🚚 Tracking batch " + batchId + " (via Kafka map events)";
                    verificationHistory.add(0, message);
                }
                
                // Update journey display based on progress
                updateJourneyFromProgress(batchId, progressPercent, location);
                
                // Update status label
                if (statusLabel != null) {
                    String displayStatus = ShipmentStatus.normalize(status);
                    if (ShipmentStatus.isDelivered(displayStatus)) {
                        statusLabel.setText("Delivered");
                    } else if (ShipmentStatus.isApproaching(displayStatus)) {
                        statusLabel.setText("Approaching");
                    } else if (ShipmentStatus.isInTransit(displayStatus)) {
                        statusLabel.setText("In Transit");
                    } else {
                        statusLabel.setText(status != null ? status : "In Progress");
                    }
                }
                
                // Update temperature display from event
                // Check if temperature data appears to be initialized (cold chain typically operates between 2-12°C)
                // A value of exactly 0.0 with humidity also at 0.0 likely indicates uninitialized data
                double temp = event.getTemperature();
                double humidity = event.getHumidity();
                boolean hasValidTempData = !(temp == 0.0 && humidity == 0.0);
                
                if (temperatureLabel != null && hasValidTempData) {
                    temperatureLabel.setText(String.format("%.1f°C", temp));
                    // Color based on temperature compliance (2-8°C is optimal for cold chain)
                    if (temp >= 2.0 && temp <= 8.0) {
                        temperatureLabel.setStyle("-fx-text-fill: #10b981;"); // Green - compliant
                    } else if (temp < 2.0 || temp > 10.0) {
                        temperatureLabel.setStyle("-fx-text-fill: #dc2626;"); // Red - out of range
                    } else {
                        temperatureLabel.setStyle("-fx-text-fill: #f59e0b;"); // Yellow - marginal
                    }
                }
                
                // Add milestone entries to history at key points
                boolean isMilestone = (progressPercent >= 25 && lastProgress < 25) ||
                                     (progressPercent >= 50 && lastProgress < 50) ||
                                     (progressPercent >= 75 && lastProgress < 75) ||
                                     (progressPercent >= 100 && lastProgress < 100);
                
                if (isMilestone) {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String message = String.format("%s: 📍 Batch %s - %.0f%% complete - %s (via Kafka)", 
                                                 timestamp, batchId, progressPercent, location);
                    verificationHistory.add(0, message);
                    logger.info("ConsumerController: Journey milestone via Kafka - {} at {}%", batchId, progressPercent);
                }
                
                // Update last progress
                lastProgress = progressPercent;
                
                // Check if delivered and compute final quality
                if (progressPercent >= 100.0 || ShipmentStatus.isDelivered(status)) {
                    handleDeliveryComplete(batchId);
                }
                
            } catch (Exception e) {
                logger.error("Error handling map simulation event: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Handle delivery completion - compute and display final quality.
     * Called when progress reaches 100% or status is DELIVERED.
     */
    private void handleDeliveryComplete(String batchId) {
        logger.info("🎯 Delivery complete for batch: {} - computing final quality", batchId);
        
        // Compute final quality using QualityAssessmentService
        double finalQuality = DEFAULT_FINAL_QUALITY;
        try {
            if (qualityAssessmentService != null && simulationStartTimeMs > 0) {
                QualityAssessmentService.FinalQualityAssessment assessment =
                    qualityAssessmentService.assessFinalQualityFromMonitoring(
                        batchId, 100.0, simulationStartTimeMs);
                
                if (assessment != null) {
                    finalQuality = assessment.getFinalQuality();
                    logger.info("✅ Final quality computed for batch {}: {}% (grade: {})", 
                               batchId, String.format("%.1f", finalQuality), assessment.getQualityGrade());
                    
                    // Update journey step with detailed quality info
                    updateJourneyStep(4, "✅", "Delivered - " + assessment.getQualityGrade(),
                        String.format("Final Quality: %.1f%% | Temp Violations: %d | Alerts: %d",
                            finalQuality, assessment.getTemperatureViolations(), assessment.getAlertCount()));
                }
            } else if (SimulationManager.isInitialized()) {
                finalQuality = SimulationManager.getInstance().getFinalQuality();
            }
        } catch (Exception e) {
            logger.error("Could not compute final quality: {}", e.getMessage());
        }
        
        // Display final quality
        displayFinalQuality(batchId, finalQuality);
        
        // Get and display average temperature from TemperatureService
        displayAverageTemperature(batchId);
        
        // Add delivery completion to history
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String message = timestamp + ": ✅ Batch " + batchId + " delivered - Final Quality: " + 
                        String.format("%.1f%%", finalQuality) + " - Ready for verification";
        verificationHistory.add(0, message);
        
        // Update status label
        if (statusLabel != null) {
            statusLabel.setText("Delivered");
        }
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
                
                // If simulation is already running, catch up with current state
                if (manager.isRunning()) {
                    currentBatchId = manager.getSimulationId();
                    double progress = manager.getProgress();
                    String location = manager.getCurrentLocation();
                    String farmerId = manager.getCurrentProducer();
                    
                    // Track simulation start time for quality calculation
                    // Use current time minus estimated elapsed time based on progress
                    // Use SimulationConfig.forDemo() to get the correct simulation duration
                    long simulationDurationMs = SimulationConfig.forDemo().getSimulationDurationMs();
                    long estimatedElapsedMs = (long)(progress / 100.0 * simulationDurationMs);
                    simulationStartTimeMs = System.currentTimeMillis() - estimatedElapsedMs;
                    lastProgress = progress;
                    
                    Platform.runLater(() -> {
                        verificationHistory.add(0, "📦 Batch in transit: " + currentBatchId + 
                            " (" + String.format("%.0f%%", progress) + ") - Track in Logistics tab");
                        updateJourneyFromProgress(currentBatchId, progress, location);
                        
                        // Update status label
                        if (statusLabel != null) {
                            statusLabel.setText(progress >= 100 ? "Delivered" : "In Transit");
                        }
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
                logger.info("Decoded QR content from {}: {} characters", selectedFile.getName(), qrContent.length());
                
                // Parse the QR payload to extract batch ID and quality
                QRDecoder.QRPayload payload = QRDecoder.parsePayload(qrContent);
                
                String batchId = null;
                String quality = null;
                
                if (payload != null) {
                    batchId = payload.getBatchId();
                    quality = payload.getQuality();
                    logger.info("Parsed QR payload: type={}, batchId={}, quality={}", 
                        payload.getType(), batchId, quality);
                }
                
                // If parsing failed, try fallback extraction
                if (batchId == null || batchId.trim().isEmpty()) {
                    batchId = QRDecoder.extractBatchId(qrContent);
                }
                if (batchId == null || batchId.trim().isEmpty()) {
                    batchId = extractBatchIdFallback(qrContent);
                }
                
                if (batchId != null && !batchId.trim().isEmpty()) {
                    // Set the batch ID in the text field
                    batchIdField.setText(batchId);
                    
                    // Verify the batch with quality information from QR
                    verifyBatchWithQuality(batchId, quality);
                } else {
                    logger.warn("Could not extract batch ID from QR content");
                    showAlert(Alert.AlertType.WARNING, "Invalid QR Code", 
                        "Could not extract batch ID from the QR code.");
                }
            } catch (NotFoundException e) {
                logger.warn("No QR code found in image: {}", selectedFile.getAbsolutePath());
                showAlert(Alert.AlertType.ERROR, "QR Code Not Found", 
                    "No QR code was found in the selected image. Please select an image containing a valid QR code.");
            } catch (IOException e) {
                logger.error("Failed to read QR image file: {}", e.getMessage());
                showAlert(Alert.AlertType.ERROR, "File Error", 
                    "Failed to read the image file: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error reading QR code: {}", e.getMessage(), e);
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
        Matcher pathMatcher = URL_PATH_PATTERN.matcher(content);
        if (pathMatcher.find()) {
            String batchId = pathMatcher.group(1);
            System.out.println("QR fallback parsing: extracted from URL path: " + batchId);
            return batchId;
        }
        
        // 2. Try to extract from query parameters: batchId=, batch=, or id=
        Matcher queryMatcher = QUERY_PARAM_PATTERN.matcher(content);
        if (queryMatcher.find()) {
            String batchId = queryMatcher.group(1);
            System.out.println("QR fallback parsing: extracted from query parameter: " + batchId);
            return batchId;
        }
        
        // 3. Try regex for BATCH_* pattern
        Matcher batchMatcher = BATCH_ID_PATTERN.matcher(content);
        if (batchMatcher.find()) {
            String batchId = batchMatcher.group();
            System.out.println("QR fallback parsing: extracted BATCH_* pattern: " + batchId);
            return batchId;
        }
        
        // 4. Last resort: look for alphanumeric token at least 4 characters
        Matcher alphanumMatcher = ALPHANUMERIC_TOKEN_PATTERN.matcher(content);
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
        if (verificationHistory == null || verificationHistory.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No History", "There is no verification history to export.");
            return;
        }
        
        // Create file chooser for export
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Verification History");
        fileChooser.setInitialFileName("verification_history_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        
        // Add file extension filters
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Get the current stage (window)
        Stage stage = (Stage) verificationHistoryList.getScene().getWindow();
        
        // Show save dialog
        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile != null) {
            try {
                // Determine format based on file extension
                String fileName = selectedFile.getName().toLowerCase();
                boolean isCsv = fileName.endsWith(".csv");
                
                // Write history to file
                try (PrintWriter writer = new PrintWriter(new FileWriter(selectedFile))) {
                    
                    // Write header
                    if (isCsv) {
                        // CSV format with proper column headers
                        writer.println("Entry");
                    } else {
                        writer.println("==============================================");
                        writer.println("VERICROP - Verification History Export");
                        writer.println("Export Date: " + 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        writer.println("==============================================");
                        writer.println();
                    }
                    
                    // Write each history entry
                    for (String entry : verificationHistory) {
                        if (isCsv) {
                            // Properly escape CSV data per RFC 4180
                            // Wrap in quotes if contains comma, quote, newline, or carriage return
                            boolean needsQuoting = entry.contains(",") || entry.contains("\"") || 
                                                  entry.contains("\n") || entry.contains("\r");
                            if (needsQuoting) {
                                // Escape quotes by doubling them, then wrap entire field in quotes
                                writer.println("\"" + entry.replace("\"", "\"\"") + "\"");
                            } else {
                                writer.println(entry);
                            }
                        } else {
                            writer.println(entry);
                        }
                    }
                    
                    // Write footer
                    if (!isCsv) {
                        writer.println();
                        writer.println("==============================================");
                        writer.println("Total Entries: " + verificationHistory.size());
                        writer.println("==============================================");
                    }
                }
                
                logger.info("Verification history exported successfully to: {}", selectedFile.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                    "Verification history exported successfully to:\n" + selectedFile.getAbsolutePath() +
                    "\n\nTotal entries: " + verificationHistory.size());
                
            } catch (IOException e) {
                logger.error("Failed to export verification history: {}", e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                    "Failed to export verification history:\n" + e.getMessage());
            }
        }
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
    private void handleLogout() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("login.fxml");
        }
    }

    private void verifyBatch(String batchId) {
        // Delegate to verifyBatchWithQuality with null quality (will be fetched from backend or use fallback)
        verifyBatchWithQuality(batchId, null);
    }
    
    /**
     * Verify a batch with optional quality information from QR code.
     * The QR-provided quality is used if available, otherwise quality is fetched from backend
     * or determined using fallback logic.
     * 
     * @param batchId The batch ID to verify
     * @param qrQuality Quality from QR code (may be null if not available)
     */
    private void verifyBatchWithQuality(String batchId, String qrQuality) {
        // Validation: if batchId non-empty -> attempt verification
        if (batchId == null || batchId.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", "Batch ID cannot be empty");
            return;
        }

        String trimmedBatchId = batchId.trim();
        
        // Log QR quality if available
        if (qrQuality != null && !qrQuality.trim().isEmpty()) {
            logger.info("Verifying batch {} with QR quality: {}", trimmedBatchId, qrQuality);
        } else {
            logger.info("Verifying batch {} (no quality from QR)", trimmedBatchId);
        }
        
        // Check if batch ID exists in knownBatchIds (case-insensitive) - for demo mode
        if (shouldLoadDemoData() && !knownBatchIds.isEmpty()) {
            boolean found = isKnownBatchId(trimmedBatchId);
            
            if (!found) {
                // Build actionable error message for demo mode
                String errorMessage = String.format(
                    "Scanned batch ID: %s\n\n" +
                    "This batch was not found in demo mode.\n" +
                    "Demo mode only recognizes pre-defined batch IDs.\n\n" +
                    "To test with real batches, disable demo mode by removing\n" +
                    "the VERICROP_LOAD_DEMO environment variable.",
                    trimmedBatchId);
                    
                showAlert(Alert.AlertType.WARNING, "Batch ID Not Found", errorMessage);
                
                // Add failure entry to verification history with parsed batch ID
                String failureEntry = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                        ": " + trimmedBatchId + " - ❌ NOT FOUND (demo mode)";
                
                Platform.runLater(() -> {
                    verificationHistory.add(0, failureEntry);
                });
                return;
            }
        }

        // Variables for batch info - initialize with defaults
        String productName = "Unknown Product";
        String quality = null;
        String origin = "Unknown Origin";
        double qualityScore = 0.0;
        double primeRate = 0.0;
        double rejectionRate = 0.0;
        boolean realDataFetched = false;
        boolean qualityFromQR = false;
        
        // Use QR quality if available and valid
        String effectiveQrQuality = (qrQuality != null && !qrQuality.trim().isEmpty()) ? qrQuality.trim() : null;
        
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
                
                // Determine quality classification - prefer QR quality, then compute from score
                if (effectiveQrQuality != null) {
                    quality = effectiveQrQuality;
                    qualityFromQR = true;
                    logger.info("Using quality from QR: {}", quality);
                } else if (qualityScore >= 80) {
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
                logger.warn("Batch NOT FOUND in backend (404): {}", trimmedBatchId);
                
                // Build actionable error message with parsed batch ID
                String errorMessage = String.format(
                    "Scanned batch ID: %s\n\n" +
                    "This batch was not found in the backend system.\n\n" +
                    "Possible causes:\n" +
                    "• The batch has not been created yet in ProducerController\n" +
                    "• The backend service was restarted and batch data was lost\n" +
                    "• The QR code is from a different environment\n\n" +
                    "Please verify the batch exists in the Producer dashboard or create a new batch.",
                    trimmedBatchId);
                
                showAlert(Alert.AlertType.WARNING, "Batch ID Not Found", errorMessage);
                
                // Add failure entry to verification history with parsed batch ID
                String failureEntry = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                        ": " + trimmedBatchId + " - ❌ NOT FOUND (batch not in backend)";
                
                Platform.runLater(() -> {
                    verificationHistory.add(0, failureEntry);
                });
                
                // Do NOT mark as verified, do NOT update UI
                return;
            } else {
                // Backend unavailable - use fallback verification with QR quality if available
                logger.warn("Backend UNAVAILABLE for batch: {} - using fallback verification", trimmedBatchId);
                showAlert(Alert.AlertType.WARNING, "Backend Unavailable", 
                    "Backend unavailable - using fallback verification.");
                productName = "Product (Batch " + batchId + ")";
                qualityScore = 85.0;
                origin = "Farm Network";
                
                // Use QR quality or default
                if (effectiveQrQuality != null) {
                    quality = effectiveQrQuality;
                    qualityFromQR = true;
                    logger.info("Using quality from QR (fallback mode): {}", quality);
                } else {
                    quality = "VERIFIED";
                }
            }
        } else {
            // Demo mode - use mock data patterns, but prefer QR quality if available
            if (effectiveQrQuality != null) {
                quality = effectiveQrQuality;
                qualityFromQR = true;
            }
            
            if (trimmedBatchId.toUpperCase().contains("A")) {
                productName = "Summer Apples (demo)";
                if (!qualityFromQR) quality = "PRIME";
                qualityScore = 92.0;
                origin = "Sunny Valley Orchards (demo)";
            } else if (trimmedBatchId.toUpperCase().contains("B")) {
                productName = "Organic Carrots (demo)";
                if (!qualityFromQR) quality = "PRIME";
                qualityScore = 88.0;
                origin = "Green Fields Farm (demo)";
            } else {
                productName = "Mixed Vegetables (demo)";
                if (!qualityFromQR) quality = "STANDARD";
                qualityScore = 85.0;
                origin = "Valley Fresh Farms (demo)";
            }
        }

        // Build verification result message - include quality with "Unknown" fallback
        String displayQuality = (quality != null && !quality.trim().isEmpty()) ? quality : "Unknown";
        
        StringBuilder verificationResult = new StringBuilder();
        verificationResult.append("✅ VERIFIED: Batch ").append(batchId)
                .append("\nProduct: ").append(productName)
                .append("\nQuality: ").append(displayQuality);
        
        // Add quality score if available
        if (qualityScore > 0) {
            verificationResult.append(" (").append(String.format("%.1f", qualityScore)).append("%)");
        }
        
        // Indicate if quality came from QR code
        if (qualityFromQR) {
            verificationResult.append(" [from QR]");
        }
        
        verificationResult.append("\nOrigin: ").append(origin);
        
        // Include prime/rejection rates if real data was fetched
        if (realDataFetched) {
            verificationResult.append("\nPrime Rate: ").append(String.format("%.1f", primeRate)).append("%");
            verificationResult.append("\nRejection Rate: ").append(String.format("%.1f", rejectionRate)).append("%");
        }
        
        verificationResult.append("\nVerification Time: ").append(java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        showAlert(Alert.AlertType.INFORMATION, "Verification Complete", verificationResult.toString());

        // Add to history with proper formatting including quality
        final String historyProductName = productName;
        final double historyQualityScore = qualityScore;
        final String historyQuality = displayQuality;
        final boolean updateQualityUI = realDataFetched;
        String historyEntry = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                ": " + batchId + " - ✅ VERIFIED (" + historyProductName + ", Quality: " + historyQuality + 
                (historyQualityScore > 0 ? " " + String.format("%.1f", historyQualityScore) + "%" : "") + ")";
        
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
        
        // Close instance registry
        if (instanceRegistry != null) {
            try {
                instanceRegistry.close();
            } catch (Exception e) {
                System.err.println("Error closing instance registry: " + e.getMessage());
            }
        }
        
        // Stop Kafka consumers
        if (simulationControlConsumer != null) {
            simulationControlConsumer.stop();
        }
        if (mapSimulationConsumer != null) {
            mapSimulationConsumer.stop();
        }
        if (kafkaConsumerExecutor != null && !kafkaConsumerExecutor.isShutdown()) {
            kafkaConsumerExecutor.shutdownNow();
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
    public void onSimulationStarted(String batchId, String farmerId, String scenarioId) {
        Platform.runLater(() -> {
            currentBatchId = batchId;
            lastProgress = 0.0;
            simulationStartTimeMs = System.currentTimeMillis();
            
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
            
            System.out.println("ConsumerController: Simulation started - " + batchId + " from " + farmerId + " (scenario: " + scenarioId + ")");
        });
    }
    
    @Override
    public void onProgressUpdate(String batchId, double progress, String currentLocation) {
        logger.debug("📊 Progress update received - batchId: {}, progress: {}%, location: {}", 
                    batchId, String.format("%.1f", progress), currentLocation);
        
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
                logger.info("ConsumerController: Journey milestone - {} at {}%", batchId, progress);
            }
            
            lastProgress = progress;
        });
    }
    
    @Override
    public void onSimulationStopped(String batchId, boolean completed) {
        logger.info("🛑 Simulation stopped - batchId: {}, completed: {}", batchId, completed);
        
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String message;
            
            if (completed) {
                message = timestamp + ": ✅ Batch " + batchId + " delivered successfully - Ready for verification";
                
                // Compute final quality using QualityAssessmentService
                double finalQuality = DEFAULT_FINAL_QUALITY;
                try {
                    if (qualityAssessmentService != null && simulationStartTimeMs > 0) {
                        // Use initial quality of 100% and compute final quality from temperature monitoring
                        QualityAssessmentService.FinalQualityAssessment assessment =
                            qualityAssessmentService.assessFinalQualityFromMonitoring(
                                batchId, 100.0, simulationStartTimeMs);
                        
                        if (assessment != null) {
                            finalQuality = assessment.getFinalQuality();
                            logger.info("✅ Final quality computed - batch: {}, quality: {}%, grade: {}, tempViolations: {}", 
                                       batchId, String.format("%.1f", finalQuality), 
                                       assessment.getQualityGrade(), assessment.getTemperatureViolations());
                            
                            // Update journey step with detailed quality info
                            updateJourneyStep(4, "✅", "Delivered - " + assessment.getQualityGrade(),
                                String.format("Final Quality: %.1f%% | Temp Violations: %d | Alerts: %d",
                                    finalQuality, assessment.getTemperatureViolations(), assessment.getAlertCount()));
                        }
                    } else if (SimulationManager.isInitialized()) {
                        // Fallback to SimulationManager if QualityAssessmentService unavailable
                        finalQuality = SimulationManager.getInstance().getFinalQuality();
                        logger.info("Final quality from SimulationManager fallback: {}%", String.format("%.1f", finalQuality));
                    }
                } catch (Exception e) {
                    logger.error("Could not compute final quality: {}", e.getMessage());
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
            simulationStartTimeMs = 0;
            
            logger.info("ConsumerController: {} - {}", completed ? "Delivery completed" : "Delivery stopped", batchId);
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