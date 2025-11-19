package org.vericrop.gui;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
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
import javafx.scene.chart.*;
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
import org.vericrop.kafka.KafkaConfig;
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

    // New Kafka buttons
    @FXML private Button simulateShipmentButton;
    @FXML private Button testAlertButton;

    // Dashboard elements
    @FXML private Label totalBatchesLabel;
    @FXML private Label avgQualityLabel;
    @FXML private Label primePercentageLabel;
    @FXML private Label rejectionRateLabel;
    @FXML private BarChart<String, Number> qualityChart;
    @FXML private ListView<String> recentBatchesList;
    @FXML private PieChart qualityDistributionChart;
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
                if (simulateShipmentButton != null) {
                    simulateShipmentButton.setDisable(false);
                    simulateShipmentButton.getStyleClass().add("kafka-button");
                }
                if (testAlertButton != null) {
                    testAlertButton.setDisable(false);
                    testAlertButton.getStyleClass().add("kafka-button");
                }
            });

        } catch (Exception e) {
            System.err.println("⚠️ Kafka services unavailable: " + e.getMessage());
            System.out.println("🔄 Continuing without Kafka functionality");

            Platform.runLater(() -> {
                if (simulateShipmentButton != null) {
                    simulateShipmentButton.setDisable(true);
                    simulateShipmentButton.setTooltip(new Tooltip("Kafka broker not available"));
                }
                if (testAlertButton != null) {
                    testAlertButton.setDisable(true);
                    testAlertButton.setTooltip(new Tooltip("Kafka broker not available"));
                }
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
        if (simulateShipmentButton != null) simulateShipmentButton.getStyleClass().add("kafka-button");
        if (testAlertButton != null) testAlertButton.getStyleClass().add("kafka-button");
    }

    // OPTIMIZED BATCH CREATION METHODS

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

        Map<String, Object> batchData = new HashMap<>();
        batchData.put("name", batchName);
        batchData.put("farmer", farmer);
        batchData.put("product_type", productType);
        batchData.put("quantity", quantity);
        batchData.put("quality_data", currentPrediction);
        batchData.put("data_hash", dataHash);

        return batchData;
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
                        return result;
                    } else {
                        throw new IOException("Backend error: " + response.code());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Backend communication failed", e);
            }
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

                // Prepare transaction
                List<Transaction> transactions = createBlockchainTransactions(batchData, batchId);
                String dataHash = (String) batchData.get("data_hash");
                String farmer = (String) batchData.get("farmer");

                // Add to blockchain asynchronously
                Block newBlock = blockchainService.addBlockAsync(transactions, dataHash, farmer).join();
                result.put("blockchain_block", newBlock);

                // Create shipment record
                createShipmentRecord(batchId, batchData);

                return result;
            } catch (Exception e) {
                throw new RuntimeException("Blockchain operation failed", e);
            }
        }, backgroundExecutor);
    }

    private List<Transaction> createBlockchainTransactions(Map<String, Object> batchData, String batchId) {
        List<Transaction> transactions = new ArrayList<>();
        Map<String, Object> txData = new HashMap<>();

        txData.put("batch_name", batchData.get("name"));
        txData.put("farmer", batchData.get("farmer"));
        txData.put("product_type", batchData.get("product_type"));
        txData.put("quantity", batchData.get("quantity"));
        txData.put("quality_data", batchData.get("quality_data"));

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

    private void createShipmentRecord(String batchId, Map<String, Object> batchData) {
        try {
            ShipmentRecord shipmentRecord = new ShipmentRecord();
            shipmentRecord.setShipmentId("SHIP_" + System.currentTimeMillis());
            shipmentRecord.setBatchId(batchId);
            shipmentRecord.setFromParty((String) batchData.get("farmer"));
            shipmentRecord.setToParty("Processing Center");
            shipmentRecord.setStatus("CREATED");

            Map<String, Object> qualityData = (Map<String, Object>) batchData.get("quality_data");
            Object qualityScoreObj = qualityData.get("quality_score");
            if (qualityScoreObj instanceof Number) {
                shipmentRecord.setQualityScore(((Number) qualityScoreObj).doubleValue() * 100);
            }

            ShipmentRecord recorded = ledgerService.recordShipment(shipmentRecord);
            System.out.println("📝 Recorded shipment in ledger: " + recorded.getLedgerId());

        } catch (Exception e) {
            System.err.println("❌ Failed to create shipment record: " + e.getMessage());
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

                sendKafkaEvents(batchId, batchName, farmer, productType, newBlock, dataHash);

                return result;
            } catch (Exception e) {
                throw new RuntimeException("Kafka events failed", e);
            }
        }, backgroundExecutor);
    }

    private void handleBatchSuccess(Map<String, Object> result) {
        String batchId = safeGetString(result, "batch_id");
        String batchName = (String) ((Map<String, Object>) result.get("batch_data")).get("name");

        updateBlockchainDisplay();
        loadDashboardData();

        showSuccess("Batch '" + batchName + "' created successfully!\nBatch ID: " + batchId);
        resetForm();
        updateStatus("✅ Batch created successfully!");

        Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            createBatchButton.setDisable(false);
        });
    }

    private Void handleBatchError(Throwable throwable) {
        System.err.println("❌ Batch creation failed: " + throwable.getMessage());

        Platform.runLater(() -> {
            showError("Error creating batch: " + throwable.getMessage());
            updateStatus("❌ Batch creation failed");
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

    // REST OF THE METHODS (unchanged but included for completeness)

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

        try {
            Map<String, Object> kpis = (Map<String, Object>) dashboardData.get("kpis");
            Map<String, Object> distribution = (Map<String, Object>) dashboardData.get("quality_distribution");
            List<Map<String, Object>> recentBatches = (List<Map<String, Object>>) dashboardData.get("recent_batches");

            if (kpis != null) {
                if (totalBatchesLabel != null)
                    totalBatchesLabel.setText(String.valueOf(kpis.getOrDefault("total_batches_today", "0")));
                if (avgQualityLabel != null)
                    avgQualityLabel.setText(kpis.getOrDefault("average_quality", "0") + "%");
                if (primePercentageLabel != null)
                    primePercentageLabel.setText(kpis.getOrDefault("prime_percentage", "0") + "%");
                if (rejectionRateLabel != null)
                    rejectionRateLabel.setText(kpis.getOrDefault("rejection_rate", "0") + "%");
            }

            if (qualityDistributionChart != null && distribution != null) {
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                        new PieChart.Data("Prime", safeGetDouble(distribution, "prime")),
                        new PieChart.Data("Standard", safeGetDouble(distribution, "standard")),
                        new PieChart.Data("Sub-standard", safeGetDouble(distribution, "sub_standard"))
                );
                qualityDistributionChart.setData(pieChartData);
                qualityDistributionChart.setLegendVisible(false);
                qualityDistributionChart.setStyle("-fx-font-size: 10px;");
            }

            if (recentBatchesList != null && recentBatches != null) {
                ObservableList<String> batches = FXCollections.observableArrayList();
                for (Map<String, Object> batch : recentBatches) {
                    String batchName = safeGetString(batch, "name");
                    String qualityScore = safeGetString(batch, "quality_score");
                    batches.add(batchName + " - Quality: " + qualityScore);
                }
                recentBatchesList.setItems(batches);
                recentBatchesList.getStyleClass().add("modern-list");
            }
        } catch (Exception e) {
            System.err.println("Error updating dashboard UI: " + e.getMessage());
            updateStatus("❌ Dashboard update failed");
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
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showAnalyticsScreen();
        } else {
            System.err.println("MainApp instance is null");
        }
    }

    @FXML
    private void handleShowLogistics() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showLogisticsScreen();
        } else {
            System.err.println("MainApp instance is null");
        }
    }

    @FXML
    private void handleShowConsumer() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showConsumerScreen();
        } else {
            System.err.println("MainApp instance is null");
        }
    }

    private void sendKafkaEvents(String batchId, String batchName, String farmer,
                                 String productType, Block newBlock, String dataHash) {
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
                blockchainEvent.setAdditionalData("Product: " + productType);
                blockchainProducer.sendBlockchainEvent(blockchainEvent);
                System.out.println("⛓️ Blockchain event sent for batch: " + batchId);
            }

            Object qualityScoreObj = currentPrediction.get("quality_score");
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

    private void updateBlockchainDisplay() {
        Platform.runLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== VERICROP BLOCKCHAIN ===\n\n");

            List<Block> chain = blockchain.getChain();

            if (chain.isEmpty()) {
                sb.append("No blocks in the chain yet.\n");
                sb.append("Create your first batch to see blockchain data!\n");
            } else {
                for (Block block : chain) {
                    sb.append("Block #").append(block.getIndex()).append("\n");
                    sb.append("Hash: ").append(safeSubstring(block.getHash(), 20)).append("\n");
                    sb.append("Previous: ").append(safeSubstring(block.getPreviousHash(), 20)).append("\n");
                    sb.append("Participant: ").append(safeSubstring(block.getParticipant(), 20)).append("\n");
                    sb.append("Data Hash: ").append(safeSubstring(block.getDataHash(), 16)).append("\n");
                    sb.append("Transactions: ").append(block.getTransactions().size()).append("\n");
                    sb.append("Valid: ").append(blockchain.isChainValid() ? "✅" : "❌").append("\n");
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

    private String safeGetString(Map<String, Object> map, String key) {
        try {
            Object value = map.get(key);
            return value != null ? value.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
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