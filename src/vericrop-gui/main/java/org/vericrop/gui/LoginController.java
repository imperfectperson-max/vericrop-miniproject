package org.vericrop.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;

/**
 * Login Controller for the deprecated role-select login screen.
 * 
 * NOTE: This controller is deprecated. The login.fxml now uses EnhancedLoginController
 * which requires proper authentication. The role-select (skip authentication) functionality
 * has been removed for production correctness.
 * 
 * If you reach this controller, it means the application is using login_roleselect.fxml
 * instead of login.fxml. Users should be redirected to the proper login screen.
 */
public class LoginController {

    @FXML private CheckBox demoModeCheckbox;

    @FXML
    public void initialize() {
        // Demo/skip-auth code removed for production correctness.
        // This controller is deprecated - authentication is now required.
    }

    /**
     * These role-based login methods have been disabled for production correctness.
     * Users must authenticate via the proper login screen (EnhancedLoginController).
     */
    @FXML
    private void handleFarmerLogin() {
        // Skip-auth role selection removed for production correctness
        showAlert(Alert.AlertType.WARNING, "Authentication Required",
                "Direct role selection has been disabled. Please use the login screen with proper credentials.");
    }

    @FXML
    private void handleSupplierLogin() {
        // Skip-auth role selection removed for production correctness
        showAlert(Alert.AlertType.WARNING, "Authentication Required",
                "Direct role selection has been disabled. Please use the login screen with proper credentials.");
    }

    @FXML
    private void handleConsumerLogin() {
        // Skip-auth role selection removed for production correctness
        showAlert(Alert.AlertType.WARNING, "Authentication Required",
                "Direct role selection has been disabled. Please use the login screen with proper credentials.");
    }

    @FXML
    private void handleDemoModeToggle() {
        // Demo mode removed for production correctness
        showAlert(Alert.AlertType.WARNING, "Demo Mode Disabled",
                "Demo mode has been disabled for production correctness.");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}