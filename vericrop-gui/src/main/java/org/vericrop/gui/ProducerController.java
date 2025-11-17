package org.vericrop.gui;

import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ProducerController {
    private Blockchain blockchain;
    private ObjectMapper mapper;
    private OkHttpClient httpClient;

    @FXML private ImageView imageView;
    @FXML private Label qualityLabel;
    @FXML private Label hashLabel;
    @FXML private Label confidenceLabel;
    @FXML private TextArea blockchainArea;
    @FXML private TextField batchNameField;
    @FXML private Button uploadButton;
    @FXML private Button createBatchButton;
    @FXML private ProgressIndicator progressIndicator;

    private String currentImagePath;
    private Map<String, Object> currentPrediction;

    public void initialize() {
        blockchain = new Blockchain();
        mapper = new ObjectMapper();
        httpClient = new OkHttpClient();
        updateBlockchainDisplay();
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
                currentImagePath = file.getAbsolutePath();
                Image image = new Image(file.toURI().toString());
                imageView.setImage(image);

                // Send to ML service
                analyzeImageWithAI(file);

            } catch (Exception e) {
                showError("Error loading image: " + e.getMessage());
            }
        }
    }

    private void analyzeImageWithAI(File imageFile) {
        progressIndicator.setVisible(true);
        uploadButton.setDisable(true);

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

                        // Update UI on JavaFX thread
                        javafx.application.Platform.runLater(() -> {
                            updatePredictionUI();
                            progressIndicator.setVisible(false);
                            uploadButton.setDisable(false);
                            createBatchButton.setDisable(false);
                        });
                    } else {
                        throw new IOException("Unexpected code " + response);
                    }
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    showError("AI Service Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    uploadButton.setDisable(false);
                });
            }
        }).start();
    }

    private void updatePredictionUI() {
        if (currentPrediction != null) {
            double qualityScore = (Double) currentPrediction.get("quality_score");
            String label = (String) currentPrediction.get("label");
            String dataHash = (String) currentPrediction.get("data_hash");

            qualityLabel.setText(String.format("Quality: %.1f%%", qualityScore * 100));
            confidenceLabel.setText("Category: " + label.toUpperCase());
            hashLabel.setText("Hash: " + dataHash.substring(0, 16) + "...");

            // Color code based on quality
            if (qualityScore > 0.8) {
                qualityLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            } else if (qualityScore > 0.6) {
                qualityLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            } else {
                qualityLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        }
    }

    @FXML
    private void handleCreateBatch() {
        if (currentPrediction == null || batchNameField.getText().isEmpty()) {
            showError("Please upload an image and enter a batch name first");
            return;
        }

        try {
            String batchName = batchNameField.getText();
            String dataHash = (String) currentPrediction.get("data_hash");
            String report = (String) currentPrediction.get("report");

            // Create transaction
            List<Transaction> transactions = new ArrayList<>();
            Map<String, Object> txData = new HashMap<>();
            txData.put("batch_name", batchName);
            txData.put("image_path", currentImagePath);
            txData.put("quality_report", mapper.readValue(report, Map.class));

            transactions.add(new Transaction(
                    "CREATE_BATCH",
                    "farmer_john",
                    "system",
                    batchName,
                    mapper.writeValueAsString(txData)
            ));

            // Add to blockchain
            Block newBlock = blockchain.addBlock(transactions, dataHash, "farmer");

            // Save blockchain
            blockchain.saveToFile("vericrop_chain.json");

            updateBlockchainDisplay();
            showSuccess("Batch '" + batchName + "' created successfully!\nBlock #" + newBlock.getIndex() + " added to chain.");

            // Reset form
            resetForm();

        } catch (Exception e) {
            showError("Error creating batch: " + e.getMessage());
        }
    }

    private void updateBlockchainDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VERICROP BLOCKCHAIN ===\n\n");

        for (Block block : blockchain.getChain()) {
            sb.append("Block #").append(block.getIndex()).append("\n");

            // Safely handle hash substring
            String hash = block.getHash();
            String hashDisplay = hash.length() > 20 ? hash.substring(0, 20) + "..." : hash;
            sb.append("Hash: ").append(hashDisplay).append("\n");

            // Safely handle previous hash substring
            String prevHash = block.getPreviousHash();
            String prevHashDisplay = prevHash.length() > 20 ? prevHash.substring(0, 20) + "..." : prevHash;
            sb.append("Previous: ").append(prevHashDisplay).append("\n");

            sb.append("Participant: ").append(block.getParticipant()).append("\n");

            // Safely handle data hash substring
            String dataHash = block.getDataHash();
            String dataHashDisplay = dataHash.length() > 16 ? dataHash.substring(0, 16) + "..." : dataHash;
            sb.append("Data Hash: ").append(dataHashDisplay).append("\n");

            sb.append("Transactions: ").append(block.getTransactions().size()).append("\n");
            sb.append("Valid: ").append(blockchain.isChainValid() ? "✅" : "❌").append("\n");
            sb.append("---\n");
        }

        blockchainArea.setText(sb.toString());
    }

    private void resetForm() {
        batchNameField.clear();
        imageView.setImage(null);
        qualityLabel.setText("Quality: --");
        confidenceLabel.setText("Category: --");
        hashLabel.setText("Hash: --");
        currentPrediction = null;
        createBatchButton.setDisable(true);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleValidateChain() {
        boolean isValid = blockchain.isChainValid();
        Alert alert = new Alert(isValid ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setTitle("Chain Validation");
        alert.setHeaderText(null);
        alert.setContentText(isValid ?
                "✅ Blockchain is valid and tamper-free!" :
                "❌ Blockchain has been tampered with!");
        alert.showAndWait();
        updateBlockchainDisplay();
    }
}