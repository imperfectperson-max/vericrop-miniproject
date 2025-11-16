package org.untitled.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import okhttp3.*;
import org.untitled.core.Block;
import org.untitled.core.Blockchain;
import org.untitled.core.SupplyChainTx;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProducerController {
    @FXML private Button chooseBtn;
    @FXML private Button uploadBtn;
    @FXML private Label selectedFileLabel;
    @FXML private Label scoreLabel;
    @FXML private ListView<String> chainList;
    @FXML private ProgressIndicator progress;

    private File selectedFile;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Blockchain blockchain = org.untitled.gui.Main.getBlockchain();

    @FXML
    public void initialize() {
        selectedFileLabel.setText("No file selected");
        scoreLabel.setText("");
        progress.setVisible(false);
        refreshChainView();
    }

    @FXML
    private void onChoose() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pick produce image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File f = chooser.showOpenDialog(chooseBtn.getScene().getWindow());
        if (f != null) {
            selectedFile = f;
            selectedFileLabel.setText(f.getName());
        }
    }

    @FXML
    private void onUpload() {
        if (selectedFile == null) {
            showAlert("No file", "Choose an image first");
            return;
        }
        progress.setVisible(true);
        uploadBtn.setDisable(true);
        chooseBtn.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                MediaType mediaType = MediaType.parse(detectMime(selectedFile.getName()));
                RequestBody fileBody = RequestBody.create(selectedFile, mediaType);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", selectedFile.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url("http://localhost:8000/predict")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }
                    String json = response.body().string();
                    JsonNode node = mapper.readTree(json);
                    double score = node.get("quality_score").asDouble();
                    String dataHash = node.get("data_hash").asText();

                    System.out.println("Received ML score=" + score + " dataHash=" + dataHash);

                    // Deduplication check: do not add a block if the last block already contains this dataHash
                    Block latest = blockchain.getLatest();
                    if (latest != null && dataHash.equals(latest.getDataHash())) {
                        System.out.println("Duplicate dataHash detected, skipping block creation");
                        Platform.runLater(() -> {
                            scoreLabel.setText(String.format("Score: %.2f", score));
                            showAlert("Duplicate", "This data has already been recorded as the latest block.");
                        });
                    } else {
                        // create a SupplyChainTx (example)
                        SupplyChainTx tx = new SupplyChainTx("CreateShipment", "{\"file\":\"" + selectedFile.getName() + "\"}");
                        Block b = blockchain.addBlock("Farm A", dataHash, List.of(tx));

                        Platform.runLater(() -> {
                            scoreLabel.setText(String.format("Score: %.2f", score));
                            refreshChainView();
                            showAlert("Success", "Shipment created. Block index: " + b.getIndex());
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Upload failed", e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    progress.setVisible(false);
                    uploadBtn.setDisable(false);
                    chooseBtn.setDisable(false);
                });
            }
        });
    }

    private void refreshChainView() {
        chainList.getItems().clear();
        for (Block b : blockchain.getChain()) {
            chainList.getItems().add(String.format("#%d | %s | dataHash=%s", b.getIndex(), b.getParticipant(), b.getDataHash()));
        }
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String detectMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}