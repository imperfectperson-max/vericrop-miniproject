package org.vericrop.gui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ConsumerController {

    @FXML private TextField batchIdField;
    @FXML private ListView<String> verificationHistoryList;
    @FXML private Button backToProducerButton;
    @FXML private Button analyticsButton;
    @FXML private Button logisticsButton;

    private ObservableList<String> verificationHistory = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupVerificationHistory();
        setupNavigationButtons();
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
        verificationHistory.addAll(
                "2024-03-08 09:15: BATCH_A2386 - ✅ VERIFIED (Summer Apples)",
                "2024-03-07 14:30: BATCH_A2385 - ✅ VERIFIED (Organic Carrots)",
                "2024-03-06 11:20: BATCH_A2384 - ✅ VERIFIED (Fresh Lettuce)"
        );
        verificationHistoryList.setItems(verificationHistory);
    }

    @FXML
    private void handleScanQR() {
        showAlert(Alert.AlertType.INFORMATION, "QR Scanner", "QR scanner would activate here");
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
            verifyBatch(batchId);
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

    private void verifyBatch(String batchId) {
        // Simulate verification process
        String verificationResult = "✅ VERIFIED: Genuine Product - Batch " + batchId +
                "\nProduct: Summer Apples" +
                "\nQuality: PRIME (82%)" +
                "\nOrigin: Sunny Valley Orchards";

        showAlert(Alert.AlertType.INFORMATION, "Verification Complete", verificationResult);

        // Add to history
        String historyEntry = java.time.LocalDateTime.now() + ": " + batchId + " - ✅ VERIFIED";
        verificationHistory.add(0, historyEntry);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}