package org.vericrop.gui;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import org.vericrop.dto.ShipmentRecord;
import org.vericrop.service.BlockchainService;
import org.vericrop.service.impl.FileLedgerService;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.chart.PieChart;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Kafka imports
import org.vericrop.kafka.KafkaServiceManager;
import org.vericrop.kafka.producers.LogisticsEventProducer;
import org.vericrop.kafka.producers.BlockchainEventProducer;
import org.vericrop.kafka.producers.QualityAlertProducer;
import org.vericrop.kafka.events.LogisticsEvent;
import org.vericrop.kafka.events.BlockchainEvent;
import org.vericrop.kafka.events.QualityAlertEvent;
import org.vericrop.gui.util.BlockchainInitializer;

public class ProducerController {
    private Blockchain blockchain;
    private BlockchainService blockchainService;
    private ObjectMapper mapper;
    private OkHttpClient httpClient;
    private FileLedgerService ledgerService;
    private ExecutorService backgroundExecutor;
    private boolean blockchainReady = false;

    // Kafka components
    private KafkaServiceManager kafkaServiceManager;
    private LogisticsEventProducer logisticsProducer;
    private BlockchainEventProducer blockchainProducer;
    private QualityAlertProducer qualityAlertProducer;

    @FXML private ImageView imageView;
    @FXML private Label qualityLabel;
    @FXML private Label hashLabel;
    @FXML private Label confidenceLabel;
    @FXML private TextArea blockchainArea;
    @FXML private TextField batchNameField;
    @FXML private TextField farmerField;
    @FXML private TextField productTypeField;
    @FXML private TextField quantityField;
    @FXML private Button uploadButton;
    @FXML private Button createBatchButton;
    @FXML private ProgressIndicator progressIndicator;

    // Dashboard elements
    @FXML private Label totalBatchesLabel;
    @FXML private Label avgQualityLabel;
    @FXML private Label primePercentageLabel;
    @FXML private Label rejectionRateLabel;
    @FXML private PieChart qualityDistributionChart;
    @FXML private ListView<String> recentBatchesList;
    @FXML private Label statusLabel;
    @FXML private ScrollPane mainScrollPane;

    // New UI elements for enhanced design
    @FXML private VBox mainContainer;
    @FXML private HBox kpiContainer;
    @FXML private VBox leftPanel;
    @FXML private VBox rightPanel;

    // Navigation buttons
    @FXML private Button analyticsButton;
    @FXML private Button logisticsButton;
    @FXML private Button consumerButton;

    private String currentImagePath;
    private Map<String, Object> currentPrediction;

    public void initialize() {
        backgroundExecutor = Executors.newFixedThreadPool(4);
        mapper = new ObjectMapper();
        ledgerService = new FileLedgerService();

        // Initialize blockchain asynchronously based on mode
        initializeBlockchainAsync();

        // Initialize Kafka services first
        initializeKafkaServices();

        // Configure HTTP client with timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();

        // Apply modern styling
        applyModernStyling();

        // Setup navigation buttons
        setupNavigationButtons();

        updateBlockchainDisplay();
        loadDashboardData();

        if (mainScrollPane != null) {
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.getStyleClass().add("scroll-pane");
        }

        // Initialize dashboard labels if they exist
        Platform.runLater(() -> {
            if (totalBatchesLabel != null) {
                totalBatchesLabel.setText("0");
                avgQualityLabel.setText("0%");
                primePercentageLabel.setText("0%");
                rejectionRateLabel.setText("0%");
            }
        });
    }

    private void setupNavigationButtons() {
        if (analyticsButton != null) {
            analyticsButton.setOnAction(e -> handleShowAnalytics());
        }
        if (logisticsButton != null) {
            logisticsButton.setOnAction(e -> handleShowLogistics());
        }
        if (consumerButton != null) {
            consumerButton.setOnAction(e -> handleShowConsumer());
        }
    }

    private void initializeBlockchainAsync() {
        // Determine mode from environment
        BlockchainInitializer.Mode mode = BlockchainInitializer.getModeFromEnvironment();

        Platform.runLater(() -> {
            updateStatus("⏳ Initializing blockchain (" + mode + " mode)...");
            if (progressIndicator != null) {
                progressIndicator.setVisible(true);
            }
        });

        // Initialize blockchain asynchronously
        BlockchainInitializer.initializeAsync(mode, message -> {
            Platform.runLater(() -> updateStatus(message));
        }).thenAccept(initializedBlockchain -> {
            blockchain = initializedBlockchain;
            blockchainService = new BlockchainService(blockchain);
            blockchainReady = true;

            Platform.runLater(() -> {
                updateStatus("✅ Blockchain ready (" + mode + " mode)");
                updateBlockchainDisplay();
                if (progressIndicator != null) {
                    progressIndicator.setVisible(false);
                }
            });

            System.out.println("✅ Blockchain initialized in " + mode + " mode with " +
                    blockchain.getChain().size() + " blocks");
        }).exceptionally(ex -> {
            System.err.println("❌ Blockchain initialization failed: " + ex.getMessage());

            // Fallback to simple blockchain
            Platform.runLater(() -> {
                blockchain = new Blockchain();
                blockchainService = new BlockchainService(blockchain);
                blockchainReady = true;
                updateStatus("⚠️ Using fallback blockchain");
                updateBlockchainDisplay();
                if (progressIndicator != null) {
                    progressIndicator.setVisible(false);
                }
            });

            return null;
        });
    }

    private void initializeKafkaServices() {
        try {
            this.logisticsProducer = new LogisticsEventProducer();
            this.blockchainProducer = new BlockchainEventProducer();
            this.qualityAlertProducer = new QualityAlertProducer();

            this.kafkaServiceManager = new KafkaServiceManager();
            kafkaServiceManager.startAllConsumers();

            System.out.println("✅ Kafka services initialized successfully");

            Platform.runLater(() -> {
                updateStatus("✅ Kafka services ready");
            });

        } catch (Exception e) {
            System.err.println("⚠️ Kafka services unavailable: " + e.getMessage());
            System.out.println("🔄 Continuing without Kafka functionality");

            Platform.runLater(() -> {
                updateStatus("⚠️ Kafka services unavailable");
            });
        }
    }

    private void applyModernStyling() {
        if (mainContainer != null) {
            mainContainer.getStyleClass().add("main-container");
        }
        if (kpiContainer != null) {
            kpiContainer.getStyleClass().add("kpi-container");
        }
        if (leftPanel != null) {
            leftPanel.getStyleClass().add("left-panel");
        }
        if (rightPanel != null) {
            rightPanel.getStyleClass().add("right-panel");
        }

        if (uploadButton != null) uploadButton.getStyleClass().add("primary-button");
        if (createBatchButton != null) createBatchButton.getStyleClass().add("success-button");
    }

    @FXML
    private void handleCreateBatch() {
        if (currentPrediction == null || batchNameField.getText().isEmpty()) {
            showError("Please upload an image and enter batch details first");
            return;
        }

        if (!validateBatchInputs()) return;

        Platform.runLater(() -> {
            createBatchButton.setDisable(true);
            progressIndicator.setVisible(true);
            updateStatus("🔄 Creating batch...");
        });

        // Start async batch creation pipeline
        CompletableFuture.supplyAsync(this::prepareBatchData, backgroundExecutor)
                .thenCompose(this::sendBatchToBackend)
                .thenCompose(this::validateBlockchainReadiness)
                .thenCompose(this::processBlockchainOperations)
                .thenCompose(this::sendKafkaEventsAsync)
                .thenAcceptAsync(this::handleBatchSuccess, Platform::runLater)
                .exceptionallyAsync(this::handleBatchError, Platform::runLater);
    }

    private Map<String, Object> prepareBatchData() {
        String batchName = batchNameField.getText().trim();
        String farmer = farmerField.getText().isEmpty() ? "Unknown Farmer" : farmerField.getText().trim();
        String productType = productTypeField.getText().isEmpty() ? "Unknown Product" : productTypeField.getText().trim();
        int quantity = parseQuantity();
        String dataHash = safeGetString(currentPrediction, "data_hash");

        // Use actual prediction results for quality data
        Map<String, Object> qualityData = new HashMap<>();
        qualityData.put("quality_score", currentPrediction.get("quality_score"));
        qualityData.put("label", currentPrediction.get("label"));
        qualityData.put("confidence", currentPrediction.get("confidence"));
        qualityData.put("data_hash", dataHash);
        qualityData.put("all_predictions", currentPrediction.get("all_predictions"));

        Map<String, Object> batchData = new HashMap<>();
        batchData.put("name", batchName);
        batchData.put("farmer", farmer);
        batchData.put("product_type", productType);
        batchData.put("quantity", quantity);
        batchData.put("quality_data", qualityData);
        batchData.put("data_hash", dataHash);

        return batchData;
    }
    private Map<String, Object> ensureRequiredFields(Map<String, Object> result) {
        // Ensure all required fields exist with fallback values
        if (!result.containsKey("quality_score")) {
            result.put("quality_score", 0.8); // Default fallback
        }
        if (!result.containsKey("prime_rate")) {
            result.put("prime_rate", 0.7); // Default fallback
        }
        if (!result.containsKey("rejection_rate")) {
            result.put("rejection_rate", 0.1); // Default fallback
        }
        return result;
    }

    private CompletableFuture<Map<String, Object>> sendBatchToBackend(Map<String, Object> batchData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> updateStatus("📡 Sending to backend..."));

                String json = mapper.writeValueAsString(batchData);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url("http://localhost:8000/batches")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        Map<String, Object> result = mapper.readValue(responseBody, Map.class);
                        result.put("batch_data", batchData);

                        // Ensure all required fields exist
                        result = ensureRequiredFields(result);

                        // Store actual backend response data
                        result.put("backend_quality_score", result.get("quality_score"));
                        result.put("backend_prime_rate", result.get("prime_rate"));
                        result.put("backend_rejection_rate", result.get("rejection_rate"));

                        return result;
                    } else {
                        throw new IOException("Backend error: " + response.code() + " - " + response.message());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Backend communication failed: " + e.getMessage(), e);
            }
        }, backgroundExecutor);
    }

    private CompletableFuture<Map<String, Object>> validateBlockchainReadiness(Map<String, Object> result) {
        return CompletableFuture.supplyAsync(() -> {
            if (!blockchainReady) {
                throw new RuntimeException("Blockchain not ready - cannot create immutable record");
            }

            // Validate that we have required data for blockchain
            Map<String, Object> batchData = (Map<String, Object>) result.get("batch_data");
            String dataHash = (String) batchData.get("data_hash");
            if (dataHash == null || dataHash.trim().isEmpty()) {
                throw new RuntimeException("Invalid data hash - cannot create blockchain record");
            }

            return result;
        }, backgroundExecutor);
    }

    private CompletableFuture<Map<String, Object>> processBlockchainOperations(Map<String, Object> result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> updateStatus("⛓️ Adding to blockchain..."));

                Map<String, Object> batchData = (Map<String, Object>) result.get("batch_data");
                String batchId = safeGetString(result, "batch_id");
                if (batchId.isEmpty()) {
                    batchId = "BATCH_" + System.currentTimeMillis();
                }

                // Prepare transaction with actual results
                List<Transaction> transactions = createBlockchainTransactions(batchData, batchId, result);
                String dataHash = (String) batchData.get("data_hash");
                String farmer = (String) batchData.get("farmer");

                // Add to blockchain
                Block newBlock = blockchain.addBlock(transactions, dataHash, farmer);

                if (newBlock == null) {
                    throw new RuntimeException("Blockchain operation failed - no block created");
                }

                result.put("blockchain_block", newBlock);

                // Create shipment record with actual quality data
                createShipmentRecord(batchId, batchData, result);

                return result;
            } catch (Exception e) {
                throw new RuntimeException("Blockchain operation failed: " + e.getMessage(), e);
            }
        }, backgroundExecutor);
    }

    private List<Transaction> createBlockchainTransactions(Map<String, Object> batchData, String batchId, Map<String, Object> backendResult) {
        List<Transaction> transactions = new ArrayList<>();
        Map<String, Object> txData = new HashMap<>();

        // Use actual data from backend response
        txData.put("batch_name", batchData.get("name"));
        txData.put("farmer", batchData.get("farmer"));
        txData.put("product_type", batchData.get("product_type"));
        txData.put("quantity", batchData.get("quantity"));
        txData.put("quality_data", batchData.get("quality_data"));
        txData.put("backend_batch_id", backendResult.get("batch_id"));
        txData.put("backend_quality_score", backendResult.get("backend_quality_score"));
        txData.put("backend_prime_rate", backendResult.get("backend_prime_rate"));
        txData.put("backend_rejection_rate", backendResult.get("backend_rejection_rate"));
        txData.put("timestamp", new Date().toString());

        try {
            transactions.add(new Transaction(
                    "CREATE_BATCH",
                    (String) batchData.get("farmer"),
                    "system",
                    batchId,
                    mapper.writeValueAsString(txData)
            ));
        } catch (Exception e) {
            throw new RuntimeException("Transaction creation failed", e);
        }

        return transactions;
    }

    private void createShipmentRecord(String batchId, Map<String, Object> batchData, Map<String, Object> backendResult) {
        try {
            ShipmentRecord shipmentRecord = new ShipmentRecord();
            shipmentRecord.setShipmentId("SHIP_" + System.currentTimeMillis());
            shipmentRecord.setBatchId(batchId);
            shipmentRecord.setFromParty((String) batchData.get("farmer"));
            shipmentRecord.setToParty("Processing Center");
            shipmentRecord.setStatus("CREATED");

            // Use actual quality score from backend response
            Object qualityScoreObj = backendResult.get("backend_quality_score");
            if (qualityScoreObj instanceof Number) {
                double qualityScore = ((Number) qualityScoreObj).doubleValue();
                shipmentRecord.setQualityScore(qualityScore * 100); // Convert to percentage
            } else {
                // Fallback to prediction data
                Map<String, Object> qualityData = (Map<String, Object>) batchData.get("quality_data");
                qualityScoreObj = qualityData.get("quality_score");
                if (qualityScoreObj instanceof Number) {
                    shipmentRecord.setQualityScore(((Number) qualityScoreObj).doubleValue() * 100);
                }
            }

            // Store actual prime and rejection rates
            Object primeRateObj = backendResult.get("backend_prime_rate");
            Object rejectionRateObj = backendResult.get("backend_rejection_rate");
            if (primeRateObj instanceof Number) {
                shipmentRecord.setPrimeRate(((Number) primeRateObj).doubleValue());
            }
            if (rejectionRateObj instanceof Number) {
                shipmentRecord.setRejectionRate(((Number) rejectionRateObj).doubleValue());
            }

            ShipmentRecord recorded = ledgerService.recordShipment(shipmentRecord);
            System.out.println("📝 Recorded shipment in ledger: " + recorded.getLedgerId());

        } catch (Exception e) {
            System.err.println("❌ Failed to create shipment record: " + e.getMessage());
            // Don't fail the entire batch creation if shipment record fails
        }
    }

    private CompletableFuture<Map<String, Object>> sendKafkaEventsAsync(Map<String, Object> result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> updateStatus("📨 Sending Kafka events..."));

                Map<String, Object> batchData = (Map<String, Object>) result.get("batch_data");
                Block newBlock = (Block) result.get("blockchain_block");
                String batchId = safeGetString(result, "batch_id");
                String batchName = (String) batchData.get("name");
                String farmer = (String) batchData.get("farmer");
                String productType = (String) batchData.get("product_type");
                String dataHash = (String) batchData.get("data_hash");

                // Use actual results from backend
                Object qualityScoreObj = result.get("backend_quality_score");
                Object primeRateObj = result.get("backend_prime_rate");
                Object rejectionRateObj = result.get("backend_rejection_rate");

                sendKafkaEvents(batchId, batchName, farmer, productType, newBlock, dataHash,
                        qualityScoreObj, primeRateObj, rejectionRateObj);

                return result;
            } catch (Exception e) {
                System.err.println("⚠️ Kafka events failed, but batch creation continues: " + e.getMessage());
                return result; // Don't fail batch creation if Kafka fails
            }
        }, backgroundExecutor);
    }

    private void handleBatchSuccess(Map<String, Object> result) {
        String batchId = safeGetString(result, "batch_id");
        String batchName = (String) ((Map<String, Object>) result.get("batch_data")).get("name");

        // Display actual results from backend
        Object qualityScore = result.get("backend_quality_score");
        Object primeRate = result.get("backend_prime_rate");
        Object rejectionRate = result.get("backend_rejection_rate");

        updateBlockchainDisplay();
        loadDashboardData();

        String successMessage = "Batch '" + batchName + "' created successfully!\n" +
                "Batch ID: " + batchId + "\n" +
                "Quality Score: " + (qualityScore != null ? String.format("%.1f%%", ((Number)qualityScore).doubleValue() * 100) : "N/A") + "\n" +
                "Prime Rate: " + (primeRate != null ? String.format("%.1f%%", ((Number)primeRate).doubleValue() * 100) : "N/A") + "\n" +
                "Rejection Rate: " + (rejectionRate != null ? String.format("%.1f%%", ((Number)rejectionRate).doubleValue() * 100) : "N/A");

        showSuccess(successMessage);
        resetForm();
        updateStatus("✅ Batch created successfully with blockchain record!");

        Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            createBatchButton.setDisable(false);
        });
    }

    private Void handleBatchError(Throwable throwable) {
        System.err.println("❌ Batch creation failed: " + throwable.getMessage());

        String errorMessage = throwable.getMessage();
        boolean blockchainFailed = errorMessage != null &&
                (errorMessage.contains("Blockchain") || errorMessage.contains("blockchain") ||
                        errorMessage.contains("timeout") || errorMessage.contains("Timeout"));

        Platform.runLater(() -> {
            if (blockchainFailed) {
                // Offer option to create batch without blockchain
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Blockchain Record Failed");
                alert.setHeaderText("Blockchain record could not be created");
                alert.setContentText("The batch was created successfully but the blockchain record failed.\n\n" +
                        "Do you want to continue without blockchain immutability?\n" +
                        "Error: " + throwable.getMessage());

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    // User chose to continue without blockchain
                    updateStatus("⚠️ Batch created without blockchain record");
                    loadDashboardData();
                    resetForm();
                } else {
                    showError("Batch creation cancelled due to blockchain failure: " + throwable.getMessage());
                }
            } else {
                showError("Error creating batch: " + throwable.getMessage());
            }

            progressIndicator.setVisible(false);
            createBatchButton.setDisable(false);
        });

        return null;
    }

    private boolean validateBatchInputs() {
        try {
            String quantityText = quantityField.getText();
            if (!quantityText.isEmpty()) {
                int quantity = Integer.parseInt(quantityText);
                if (quantity <= 0) {
                    showError("Quantity must be a positive number");
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            showError("Please enter a valid number for quantity");
            return false;
        }
    }

    private int parseQuantity() {
        try {
            String quantityText = quantityField.getText();
            return quantityText.isEmpty() ? 1 : Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void loadDashboardData() {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url("http://localhost:8000/dashboard/farm")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        Map<String, Object> dashboardData = mapper.readValue(responseBody, Map.class);

                        Platform.runLater(() -> {
                            updateDashboardUI(dashboardData);
                        });
                    } else {
                        throw new IOException("HTTP error: " + response.code());
                    }
                }
            } catch (Exception e) {
                System.err.println("Dashboard load error: " + e.getMessage());
                Platform.runLater(() -> {
                    if (totalBatchesLabel != null) {
                        totalBatchesLabel.setText("N/A");
                        avgQualityLabel.setText("N/A");
                        primePercentageLabel.setText("N/A");
                        rejectionRateLabel.setText("N/A");
                    }
                    updateStatus("❌ Dashboard load failed: " + e.getMessage());
                });
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void updateDashboardUI(Map<String, Object> dashboardData) {
        if (dashboardData == null) return;

        Platform.runLater(() -> {
            try {
                Map<String, Object> kpis = (Map<String, Object>) dashboardData.get("kpis");
                Map<String, Object> counts = (Map<String, Object>) dashboardData.get("counts");
                Map<String, Object> distribution = (Map<String, Object>) dashboardData.get("quality_distribution");
                List<Map<String, Object>> recentBatches = (List<Map<String, Object>>) dashboardData.get("recent_batches");

<<<<<<< HEAD
                // Use actual backend data if available, otherwise compute from batches
                if (kpis == null || !kpis.containsKey("prime_percentage") || !kpis.containsKey("rejection_rate")) {
                    Map<String, Object> computed = computeKpisFromRecentBatches(recentBatches);
                    if (kpis == null) kpis = new HashMap<>();
                    kpis.putAll(computed);
                }

                if (kpis != null) {
                    // Use actual backend data
=======
                // Use counts to calculate rates consistently
                if (counts != null) {
                    int primeCount = safeGetInt(counts, "prime_count");
                    int rejectedCount = safeGetInt(counts, "rejected_count");
                    
                    // Calculate rates using the canonical formula
                    double[] rates = calculateRates(primeCount, rejectedCount);
                    double primeRate = rates[0];
                    double rejectionRate = rates[1];
                    
                    // Update UI with calculated rates
                    if (primePercentageLabel != null) {
                        primePercentageLabel.setText(String.format("%.1f%%", primeRate));
                    }
                    if (rejectionRateLabel != null) {
                        rejectionRateLabel.setText(String.format("%.1f%%", rejectionRate));
                    }
                }

                if (kpis != null) {
                    // Use consistent parsing with fallbacks for other KPIs
>>>>>>> 0c07b982be5469f140bd506799040c829361b1ea
                    if (totalBatchesLabel != null) {
                        Object totalBatches = kpis.get("total_batches_today");
                        totalBatchesLabel.setText(totalBatches != null ? String.valueOf(totalBatches) : "0");
                    }
                    if (avgQualityLabel != null) {
                        Object avgQuality = kpis.get("average_quality");
                        avgQualityLabel.setText(avgQuality != null ? avgQuality + "%" : "0%");
                    }
                }

<<<<<<< HEAD
                if (qualityDistributionChart != null) {
                    if (distribution == null) {
                        distribution = computeDistributionFromRecentBatches(recentBatches);
                    }
                    if (distribution != null) {
                        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

                        double primeValue = safeGetDouble(distribution, "prime");
                        double standardValue = safeGetDouble(distribution, "standard");
                        double subStandardValue = safeGetDouble(distribution, "sub_standard");

                        PieChart.Data primeData = new PieChart.Data("Prime", primeValue);
                        PieChart.Data standardData = new PieChart.Data("Standard", standardValue);
                        PieChart.Data subStandardData = new PieChart.Data("Sub-standard", subStandardValue);

                        pieChartData.addAll(primeData, standardData, subStandardData);
                        qualityDistributionChart.setData(pieChartData);
                        qualityDistributionChart.setLegendVisible(false);
                        qualityDistributionChart.setLabelsVisible(true);

                        // Set colors for each slice
                        setPieChartColors(qualityDistributionChart);

                        qualityDistributionChart.setStyle("-fx-font-size: 10px;");
                    }
=======
                // Update pie chart with labeled segments
                if (qualityDistributionChart != null && distribution != null) {
                    double primeCount = safeGetDouble(distribution, "prime");
                    double standardCount = safeGetDouble(distribution, "standard");
                    double subStandardCount = safeGetDouble(distribution, "sub_standard");
                    double total = primeCount + standardCount + subStandardCount;
                    
                    // Create pie chart data with labels showing category and percentage
                    ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                    
                    if (total > 0) {
                        double primePercent = (primeCount / total) * 100.0;
                        double standardPercent = (standardCount / total) * 100.0;
                        double subStandardPercent = (subStandardCount / total) * 100.0;
                        
                        pieChartData.add(new PieChart.Data(
                            String.format("Prime — %.1f%%", primePercent), primeCount));
                        pieChartData.add(new PieChart.Data(
                            String.format("Standard — %.1f%%", standardPercent), standardCount));
                        pieChartData.add(new PieChart.Data(
                            String.format("Sub-standard — %.1f%%", subStandardPercent), subStandardCount));
                    } else {
                        // Zero case: show labels with 0.0%
                        pieChartData.add(new PieChart.Data("Prime — 0.0%", 0));
                        pieChartData.add(new PieChart.Data("Standard — 0.0%", 0));
                        pieChartData.add(new PieChart.Data("Sub-standard — 0.0%", 0));
                    }
                    
                    qualityDistributionChart.setData(pieChartData);
                    qualityDistributionChart.setLegendVisible(true);
                    qualityDistributionChart.setStyle("-fx-font-size: 10px;");
>>>>>>> 0c07b982be5469f140bd506799040c829361b1ea
                }

                if (recentBatchesList != null && recentBatches != null) {
                    ObservableList<String> batches = FXCollections.observableArrayList();
                    for (Map<String, Object> batch : recentBatches) {
                        String batchName = safeGetString(batch, "name");
                        String qualityScore = safeGetString(batch, "quality_score");
                        String primeRate = safeGetString(batch, "prime_rate");
                        String rejectionRate = safeGetString(batch, "rejection_rate");

                        batches.add(batchName + " | Quality: " + qualityScore +
                                " | Prime: " + primeRate + " | Reject: " + rejectionRate);
                    }
                    recentBatchesList.setItems(batches);
                    recentBatchesList.getStyleClass().add("modern-list");

                    // Enable scrolling for recent batches
                    recentBatchesList.setPrefHeight(200);
                    recentBatchesList.setMaxHeight(Double.MAX_VALUE);
                }

                updateStatus("✅ Dashboard updated with actual data");

            } catch (Exception e) {
                System.err.println("Error updating dashboard UI: " + e.getMessage());
                e.printStackTrace();
                updateStatus("❌ Dashboard update failed");

                // Set fallback values on error
                if (totalBatchesLabel != null) totalBatchesLabel.setText("0");
                if (avgQualityLabel != null) avgQualityLabel.setText("0%");
                if (primePercentageLabel != null) primePercentageLabel.setText("0%");
                if (rejectionRateLabel != null) rejectionRateLabel.setText("0%");
            }
        });
    }

    private void setPieChartColors(PieChart chart) {
        // Colors will be set via CSS
    }

    private Map<String, Object> computeKpisFromRecentBatches(List<Map<String, Object>> recentBatches) {
        Map<String, Object> result = new HashMap<>();
        if (recentBatches == null || recentBatches.isEmpty()) {
            result.put("total_batches_today", 0);
            result.put("average_quality", 0);
            result.put("prime_percentage", 0);
            result.put("rejection_rate", 0);
            return result;
        }

        int total = 0;
        double sumQuality = 0.0;
        int primeCount = 0;
        int standardCount = 0;
        int subStandardCount = 0;

        for (Map<String, Object> batch : recentBatches) {
            Object qObj = batch.get("quality_score");
            double q = normalizeQualityScore(qObj);
            total++;
            sumQuality += q * 100.0;

            if (q > 0.8) primeCount++;
            else if (q > 0.6) standardCount++;
            else subStandardCount++;
        }

        double avgQuality = total > 0 ? (sumQuality / total) : 0.0;
        double primePct = total > 0 ? (100.0 * primeCount / total) : 0.0;
        double rejectionRate = total > 0 ? (100.0 * subStandardCount / total) : 0.0;

        result.put("total_batches_today", total);
        result.put("average_quality", Math.round(avgQuality * 10.0) / 10.0);
        result.put("prime_percentage", Math.round(primePct * 10.0) / 10.0);
        result.put("rejection_rate", Math.round(rejectionRate * 10.0) / 10.0);

        return result;
    }

    private Map<String, Object> computeDistributionFromRecentBatches(List<Map<String, Object>> recentBatches) {
        Map<String, Object> dist = new HashMap<>();
        if (recentBatches == null || recentBatches.isEmpty()) {
            dist.put("prime", 0.0);
            dist.put("standard", 0.0);
            dist.put("sub_standard", 0.0);
            return dist;
        }

        int total = 0;
        int primeCount = 0, standardCount = 0, subCount = 0;
        for (Map<String, Object> batch : recentBatches) {
            double q = normalizeQualityScore(batch.get("quality_score"));
            total++;
            if (q > 0.8) primeCount++;
            else if (q > 0.6) standardCount++;
            else subCount++;
        }

        dist.put("prime", 100.0 * primeCount / total);
        dist.put("standard", 100.0 * standardCount / total);
        dist.put("sub_standard", 100.0 * subCount / total);
        return dist;
    }

    private double normalizeQualityScore(Object scoreObj) {
        try {
            if (scoreObj == null) return 0.0;
            double v = 0.0;
            if (scoreObj instanceof Number) {
                v = ((Number) scoreObj).doubleValue();
            } else {
                String s = scoreObj.toString().trim();
                if (s.isEmpty()) return 0.0;
                v = Double.parseDouble(s);
            }
            if (v > 1.0) {
                v = v / 100.0;
            }
            if (v < 0.0) v = 0.0;
            if (v > 1.0) v = 1.0;
            return v;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @FXML
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Food Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                if (file.length() > 10 * 1024 * 1024) {
                    showError("File too large. Maximum size is 10MB.");
                    return;
                }

                currentImagePath = file.getAbsolutePath();
                Image image = new Image(file.toURI().toString());
                imageView.setImage(image);
                imageView.getStyleClass().add("uploaded-image");

                analyzeImageWithAI(file);

            } catch (Exception e) {
                showError("Error loading image: " + e.getMessage());
            }
        }
    }

    private void analyzeImageWithAI(File imageFile) {
        Platform.runLater(() -> {
            progressIndicator.setVisible(true);
            uploadButton.setDisable(true);
            updateStatus("🔄 Analyzing image...");
        });

        new Thread(() -> {
            try {
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", imageFile.getName(),
                                RequestBody.create(imageFile, MediaType.parse("image/jpeg")))
                        .build();

                Request request = new Request.Builder()
                        .url("http://localhost:8000/predict")
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        currentPrediction = mapper.readValue(responseBody, Map.class);

                        Platform.runLater(() -> {
                            updatePredictionUI();
                            progressIndicator.setVisible(false);
                            uploadButton.setDisable(false);
                            createBatchButton.setDisable(false);
                            updateStatus("✅ Analysis complete - Ready to create batch");
                        });
                    } else {
                        throw new IOException("Unexpected code " + response + ": " + response.body().string());
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("AI Service Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    uploadButton.setDisable(false);
                    updateStatus("❌ Analysis failed");
                });
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void updatePredictionUI() {
        if (currentPrediction == null) return;

        Platform.runLater(() -> {
            try {
                Object qualityScoreObj = currentPrediction.get("quality_score");
                Object labelObj = currentPrediction.get("label");
                Object dataHashObj = currentPrediction.get("data_hash");

                double qualityScore = 0.0;
                if (qualityScoreObj instanceof Number) {
                    qualityScore = ((Number) qualityScoreObj).doubleValue();
                }

                String label = labelObj != null ? labelObj.toString() : "Unknown";
                String dataHash = dataHashObj != null ? dataHashObj.toString() : "";

                qualityLabel.setText(String.format("Quality: %.1f%%", qualityScore * 100));
                confidenceLabel.setText("Category: " + label.toUpperCase());

                if (dataHash != null && dataHash.length() >= 16) {
                    hashLabel.setText("Hash: " + dataHash.substring(0, 16) + "...");
                } else {
                    hashLabel.setText("Hash: " + dataHash);
                }

                qualityLabel.getStyleClass().removeAll("quality-high", "quality-medium", "quality-low");
                if (qualityScore > 0.8) {
                    qualityLabel.getStyleClass().add("quality-high");
                } else if (qualityScore > 0.6) {
                    qualityLabel.getStyleClass().add("quality-medium");
                } else {
                    qualityLabel.getStyleClass().add("quality-low");
                }
            } catch (Exception e) {
                System.err.println("Error updating prediction UI: " + e.getMessage());
            }
        });
    }

    // Navigation methods
    @FXML
    private void handleShowAnalytics() {
        // Implementation depends on your MainApp class
        System.out.println("Navigating to Analytics screen");
    }

    @FXML
    private void handleShowLogistics() {
        // Implementation depends on your MainApp class
        System.out.println("Navigating to Logistics screen");
    }

    @FXML
    private void handleShowConsumer() {
        // Implementation depends on your MainApp class
        System.out.println("Navigating to Consumer screen");
    }

    private void sendKafkaEvents(String batchId, String batchName, String farmer,
                                 String productType, Block newBlock, String dataHash,
                                 Object qualityScoreObj, Object primeRateObj, Object rejectionRateObj) {
        try {
            if (logisticsProducer != null) {
                LogisticsEvent logisticsEvent = new LogisticsEvent(
                        batchId,
                        "CREATED",
                        4.2,
                        65.0,
                        "Farm Location"
                );
                logisticsEvent.setRoute("Farm → Processing Center");
                if (qualityScoreObj instanceof Number) {
                    logisticsEvent.setQualityScore(((Number) qualityScoreObj).doubleValue() * 100);
                }
                logisticsProducer.sendLogisticsEvent(logisticsEvent);
                System.out.println("📦 Logistics event sent for batch: " + batchId);
            }

            if (blockchainProducer != null) {
                BlockchainEvent blockchainEvent = new BlockchainEvent(
                        "CREATE_BATCH",
                        batchId,
                        farmer,
                        newBlock.getHash(),
                        newBlock.getIndex()
                );
                blockchainEvent.setDataHash(dataHash);
                blockchainEvent.setAdditionalData("Product: " + productType +
                        " | Quality: " + (qualityScoreObj != null ? qualityScoreObj : "N/A"));
                blockchainProducer.sendBlockchainEvent(blockchainEvent);
                System.out.println("⛓️ Blockchain event sent for batch: " + batchId);
            }

            if (qualityScoreObj instanceof Number) {
                double qualityScore = ((Number) qualityScoreObj).doubleValue();
                if (qualityScore < 0.6 && qualityAlertProducer != null) {
                    QualityAlertEvent alertEvent = new QualityAlertEvent(
                            batchId,
                            "QUALITY_DROP",
                            "MEDIUM",
                            "Initial quality score below acceptable threshold",
                            qualityScore * 100,
                            60.0
                    );
                    alertEvent.setLocation("Farm Quality Check");
                    qualityAlertProducer.sendQualityAlert(alertEvent);
                    System.out.println("🚨 Quality alert sent for batch: " + batchId);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to send Kafka events: " + e.getMessage());
        }
    }

<<<<<<< HEAD
=======
    @FXML
    private void handleSimulateShipment() {
        if (logisticsProducer == null) {
            showError("Kafka producer not initialized");
            return;
        }

        String batchId = "SIM_" + System.currentTimeMillis();
        updateStatus("🔄 Simulating shipment events...");

        try {
            List<LogisticsEvent> events = Arrays.asList(
                    new LogisticsEvent(batchId, "IN_TRANSIT", 4.5, 68.0, "Highway A - Mile 50"),
                    new LogisticsEvent(batchId, "IN_TRANSIT", 4.8, 67.0, "Highway A - Mile 120"),
                    new LogisticsEvent(batchId, "IN_TRANSIT", 5.2, 69.0, "Distribution Center Entrance"),
                    new LogisticsEvent(batchId, "AT_WAREHOUSE", 3.8, 62.0, "Metro Fresh Warehouse - Dock 3"),
                    new LogisticsEvent(batchId, "AT_WAREHOUSE", 3.9, 61.0, "Metro Fresh Warehouse - Storage A"),
                    new LogisticsEvent(batchId, "DELIVERED", 4.1, 63.0, "FreshMart Downtown - Received")
            );

            events.get(0).setVehicleId("TRUCK_001");
            events.get(0).setDriverId("DRIVER_123");

            new Thread(() -> {
                try {
                    for (int i = 0; i < events.size(); i++) {
                        LogisticsEvent event = events.get(i);
                        logisticsProducer.sendLogisticsEvent(event);
                        System.out.println("📦 Sent shipment update: " + event.getStatus() + " at " + event.getLocation());

                        final int progress = i + 1;
                        Platform.runLater(() -> {
                            updateStatus("📦 Shipment progress: " + progress + "/" + events.size() + " - " + event.getStatus());
                        });

                        Thread.sleep(2000);
                    }

                    Platform.runLater(() -> {
                        showSuccess("Shipment simulation completed!\nBatch: " + batchId +
                                "\n6 events sent to Kafka");
                        updateStatus("✅ Shipment simulation completed");
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Platform.runLater(() -> showError("Shipment simulation interrupted"));
                }
            }).start();

        } catch (Exception e) {
            showError("Error simulating shipment: " + e.getMessage());
        }
    }

    @FXML
    private void handleTestAlert() {
        if (qualityAlertProducer == null) {
            showError("Kafka alert producer not initialized");
            return;
        }

        String batchId = "TEST_ALERT_" + System.currentTimeMillis();

        try {
            QualityAlertEvent tempAlert = new QualityAlertEvent(
                    batchId,
                    "TEMPERATURE_BREACH",
                    "HIGH",
                    "Temperature exceeded safe threshold during transit",
                    8.5,
                    7.0
            );
            tempAlert.setSensorId("TEMP_SENSOR_001");
            tempAlert.setLocation("Vehicle TRUCK_001");

            QualityAlertEvent qualityAlert = new QualityAlertEvent(
                    batchId,
                    "QUALITY_DROP",
                    "MEDIUM",
                    "Quality degradation detected at warehouse inspection",
                    55.0,
                    70.0
            );
            qualityAlert.setLocation("Metro Fresh Warehouse");

            QualityAlertEvent humidityAlert = new QualityAlertEvent(
                    batchId,
                    "HUMIDITY_BREACH",
                    "LOW",
                    "Humidity slightly above optimal range",
                    75.0,
                    70.0
            );
            humidityAlert.setSensorId("HUMIDITY_SENSOR_002");
            humidityAlert.setLocation("Storage Room B");

            qualityAlertProducer.sendQualityAlert(tempAlert);
            qualityAlertProducer.sendQualityAlert(qualityAlert);
            qualityAlertProducer.sendQualityAlert(humidityAlert);

            showSuccess("Test alerts sent successfully!\n3 different alert types generated");
            updateStatus("✅ Test alerts sent to Kafka");

        } catch (Exception e) {
            showError("Error sending test alerts: " + e.getMessage());
        }
    }

    /**
     * Update the live blockchain view display.
     * 
     * Displays blocks in newest-first order to show recent activity at the top.
     * This method is called after new blocks are added to ensure real-time updates.
     * Runs on UI thread via Platform.runLater to ensure thread-safety.
     */
>>>>>>> 0c07b982be5469f140bd506799040c829361b1ea
    private void updateBlockchainDisplay() {
        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== VERICROP BLOCKCHAIN ===\n\n");

            List<Block> chain = blockchain.getChain();

            if (chain.isEmpty()) {
                sb.append("No blocks in the chain yet.\n");
                sb.append("Create your first batch to see blockchain data!\n");
            } else {
                // Display blocks in reverse order (newest first) for better UX
                for (int i = chain.size() - 1; i >= 0; i--) {
                    Block block = chain.get(i);
                    sb.append("Block #").append(block.getIndex()).append("\n");
                    sb.append("Hash: ").append(safeSubstring(block.getHash(), 20)).append("\n");
                    sb.append("Previous: ").append(safeSubstring(block.getPreviousHash(), 20)).append("\n");
                    sb.append("Participant: ").append(safeSubstring(block.getParticipant(), 20)).append("\n");
                    sb.append("Data Hash: ").append(safeSubstring(block.getDataHash(), 16)).append("\n");
                    sb.append("Transactions: ").append(block.getTransactions().size()).append("\n");
                    sb.append("Valid: ").append(blockchain.isChainValid() ? "✅" : "❌").append("\n");

                    if (!block.getTransactions().isEmpty()) {
                        sb.append("Transactions:\n");
                        for (Transaction tx : block.getTransactions()) {
                            sb.append("  - ").append(tx.getType()).append(": ").append(tx.getBatchId()).append("\n");
                        }
                    }
                    sb.append("---\n");
                }
            }

            blockchainArea.setText(sb.toString());
            blockchainArea.getStyleClass().add("blockchain-text");
        });
    }

    private String safeSubstring(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    private void resetForm() {
        Platform.runLater(() -> {
            batchNameField.clear();
            farmerField.clear();
            productTypeField.clear();
            quantityField.clear();
            imageView.setImage(null);
            imageView.getStyleClass().remove("uploaded-image");
            qualityLabel.setText("Quality: --");
            confidenceLabel.setText("Category: --");
            hashLabel.setText("Hash: --");
            qualityLabel.getStyleClass().removeAll("quality-high", "quality-medium", "quality-low");
            currentPrediction = null;
            createBatchButton.setDisable(true);
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Operation Failed");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Operation Completed");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    @FXML
    private void handleValidateChain() {
        CompletableFuture.supplyAsync(() -> blockchain.isChainValid(), backgroundExecutor)
                .thenAcceptAsync(isValid -> {
                    Alert alert = new Alert(isValid ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                    alert.setTitle("Chain Validation");
                    alert.setHeaderText(null);
                    alert.setContentText(isValid ?
                            "✅ Blockchain is valid and tamper-free!" :
                            "❌ Blockchain has been tampered with!");
                    alert.showAndWait();
                    updateBlockchainDisplay();
                }, Platform::runLater);
    }

    @FXML
    private void handleRefreshDashboard() {
        updateStatus("🔄 Refreshing dashboard...");
        loadDashboardData();
        Platform.runLater(() -> {
            showSuccess("Dashboard data refreshed!");
            updateStatus("✅ Dashboard refreshed!");
        });
    }

    private double safeGetDouble(Map<String, Object> map, String key) {
        try {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int safeGetInt(Map<String, Object> map, String key) {
        try {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String safeGetString(Map<String, Object> map, String key) {
        try {
            Object value = map.get(key);
            return value != null ? value.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

<<<<<<< HEAD
    @FXML
    private void handleValidateBlockchain() {
        if (!blockchainReady) {
            showError("Blockchain is not ready for validation");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            boolean isValid = blockchain.isChainValid();
            int blockCount = blockchain.getChain().size();
            return new ValidationResult(isValid, blockCount);
        }, backgroundExecutor).thenAcceptAsync(result -> {
            Alert alert = new Alert(result.valid ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.setTitle("Blockchain Validation");
            alert.setHeaderText(null);
            alert.setContentText(result.valid ?
                    "✅ Blockchain is valid and tamper-free!\n" +
                            "Blocks in chain: " + result.blockCount :
                    "❌ Blockchain has been tampered with!\n" +
                            "Blocks in chain: " + result.blockCount);
            alert.showAndWait();
            updateBlockchainDisplay();
        }, Platform::runLater);
    }

    // Helper class for validation result
    private static class ValidationResult {
        final boolean valid;
        final int blockCount;

        ValidationResult(boolean valid, int blockCount) {
            this.valid = valid;
            this.blockCount = blockCount;
        }
=======
    /**
     * Calculate prime rate and rejection rate using consistent formulas.
     * 
     * Canonical formulas:
     * - prime_rate = prime_count / total_count
     * - rejection_rate = rejected_count / total_count
     * - total_count = prime_count + rejected_count
     * 
     * Edge case: when total_count == 0, rates are defined as 0.0 (not NaN/inf).
     * 
     * @param primeCount number of prime quality samples
     * @param rejectedCount number of rejected samples
     * @return array with [primeRate, rejectionRate] as percentages (0.0 to 100.0)
     */
    private double[] calculateRates(int primeCount, int rejectedCount) {
        int totalCount = primeCount + rejectedCount;
        
        // Handle zero-count edge case: rates are 0.0
        if (totalCount == 0) {
            return new double[] {0.0, 0.0};
        }
        
        // Calculate rates as percentages (0.0 to 100.0)
        double primeRate = (primeCount * 100.0) / totalCount;
        double rejectionRate = (rejectedCount * 100.0) / totalCount;
        
        return new double[] {primeRate, rejectionRate};
>>>>>>> 0c07b982be5469f140bd506799040c829361b1ea
    }

    public void cleanup() {
        if (blockchainService != null) {
            blockchainService.shutdown();
        }
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        if (kafkaServiceManager != null) {
            kafkaServiceManager.stopAllConsumers();
        }
        if (logisticsProducer != null) {
            logisticsProducer.close();
        }
        if (blockchainProducer != null) {
            blockchainProducer.close();
        }
        if (qualityAlertProducer != null) {
            qualityAlertProducer.close();
        }
        System.out.println("🔴 All services cleaned up");
    }
}