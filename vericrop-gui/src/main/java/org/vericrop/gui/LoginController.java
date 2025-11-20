package org.vericrop.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;

public class LoginController {

    @FXML private CheckBox demoModeCheckbox;

    @FXML
    public void initialize() {
        // Initialize demo mode based on system property
        String loadDemo = System.getProperty("vericrop.loadDemo");
        if ("true".equalsIgnoreCase(loadDemo)) {
            demoModeCheckbox.setSelected(true);
        }
    }

    @FXML
    private void handleFarmerLogin() {
        setDemoMode();
        System.out.println("👨‍🌾 Farmer login selected");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showProducerScreen();
        }
    }

    @FXML
    private void handleSupplierLogin() {
        setDemoMode();
        System.out.println("🏭 Supplier login selected");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showLogisticsScreen();
        }
    }

    @FXML
    private void handleConsumerLogin() {
        setDemoMode();
        System.out.println("👥 Consumer login selected");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.showConsumerScreen();
        }
    }

    @FXML
    private void handleDemoModeToggle() {
        boolean demoMode = demoModeCheckbox.isSelected();
        System.setProperty("vericrop.loadDemo", String.valueOf(demoMode));
        System.out.println("Demo mode: " + (demoMode ? "ENABLED" : "DISABLED"));

        if (demoMode) {
            showAlert(Alert.AlertType.INFORMATION, "Demo Mode Enabled",
                    "Sample data will be loaded for demonstration purposes.");
        }
    }

    private void setDemoMode() {
        boolean demoMode = demoModeCheckbox.isSelected();
        System.setProperty("vericrop.loadDemo", String.valueOf(demoMode));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}