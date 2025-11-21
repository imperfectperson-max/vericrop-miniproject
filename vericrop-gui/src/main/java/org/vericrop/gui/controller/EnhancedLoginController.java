package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.models.User;
import org.vericrop.gui.services.AuthenticationService;
import org.vericrop.gui.services.SessionManager;

/**
 * Enhanced Login Controller with actual authentication.
 * Supports username/password authentication against the database.
 */
public class EnhancedLoginController {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedLoginController.class);
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox demoModeCheckbox;
    @FXML private Label usernameError;
    @FXML private Label passwordError;
    @FXML private Label statusLabel;
    
    private ApplicationContext appContext;
    private AuthenticationService authService;
    private SessionManager sessionManager;
    
    @FXML
    public void initialize() {
        logger.info("EnhancedLoginController initialized");
        
        // Get application context
        appContext = ApplicationContext.getInstance();
        authService = appContext.getAuthenticationService();
        sessionManager = SessionManager.getInstance();
        
        // Check if already authenticated
        if (authService.isAuthenticated() || sessionManager.isLoggedIn()) {
            logger.info("User already authenticated: {}", 
                       sessionManager.isLoggedIn() ? sessionManager.getCurrentUsername() : authService.getCurrentUser());
            navigateToRoleBasedScreen();
        }
        
        // Add input validation listeners
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> clearError(usernameError));
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearError(passwordError));
        
        // Initialize demo mode checkbox
        String loadDemo = System.getProperty("vericrop.loadDemo");
        if ("true".equalsIgnoreCase(loadDemo)) {
            demoModeCheckbox.setSelected(true);
        }
        
        // Enable Enter key to submit
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> handleLogin());
    }
    
    @FXML
    private void handleLogin() {
        // Clear previous errors
        clearAllErrors();
        hideStatus();
        
        // Get input values
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        // Validate inputs
        if (!validateInputs(username, password)) {
            return;
        }
        
        // Disable form during authentication
        setFormDisabled(true);
        showStatus("Authenticating...", "info");
        
        // Run authentication in background thread
        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() {
                return authService.login(username, password);
            }
            
            @Override
            protected void succeeded() {
                boolean success = getValue();
                setFormDisabled(false);
                
                if (success) {
                    // Set session with authenticated user info
                    User user = new User();
                    user.setUsername(username);
                    user.setRole(authService.getCurrentRole());
                    sessionManager.setCurrentUser(user);
                    
                    logger.info("✅ Login successful for user: {} (role: {})", 
                               authService.getCurrentUser(), authService.getCurrentRole());
                    showStatus("✅ Login successful! Redirecting...", "success");
                    
                    // Navigate to appropriate screen after brief delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            Platform.runLater(() -> navigateToRoleBasedScreen());
                        } catch (InterruptedException e) {
                            logger.error("Sleep interrupted", e);
                        }
                    }).start();
                } else {
                    logger.warn("Login failed for user: {}", username);
                    showStatus("❌ Invalid username or password. Please try again.", "error");
                    passwordField.clear();
                    usernameField.requestFocus();
                }
            }
            
            @Override
            protected void failed() {
                setFormDisabled(false);
                Throwable exception = getException();
                logger.error("Login failed with exception", exception);
                showStatus("❌ Login error: " + exception.getMessage(), "error");
                passwordField.clear();
            }
        };
        
        // Run task in background
        new Thread(loginTask).start();
    }
    
    @FXML
    private void handleRegister() {
        logger.info("Navigating to registration screen");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("register.fxml");
        }
    }
    
    @FXML
    private void handleDemoModeToggle() {
        boolean demoMode = demoModeCheckbox.isSelected();
        System.setProperty("vericrop.loadDemo", String.valueOf(demoMode));
        logger.info("Demo mode: {}", demoMode ? "ENABLED" : "DISABLED");
        
        if (demoMode) {
            showAlert(Alert.AlertType.INFORMATION, "Demo Mode Enabled",
                    "Sample data will be loaded. Authentication is optional in demo mode.");
        }
    }
    
    /**
     * Navigate to the appropriate screen based on user role
     */
    private void navigateToRoleBasedScreen() {
        MainApp mainApp = MainApp.getInstance();
        if (mainApp == null) {
            logger.error("MainApp instance is null, cannot navigate");
            return;
        }
        
        String role = authService.getCurrentRole();
        if (role == null) {
            logger.warn("User role is null, defaulting to producer screen");
            mainApp.showProducerScreen();
            return;
        }
        
        logger.info("Navigating to screen for role: {}", role);
        
        switch (role.toUpperCase()) {
            case "FARMER":
                mainApp.showProducerScreen();
                break;
            case "SUPPLIER":
                mainApp.showLogisticsScreen();
                break;
            case "CONSUMER":
                mainApp.showConsumerScreen();
                break;
            case "ADMIN":
                mainApp.showAnalyticsScreen();
                break;
            default:
                logger.warn("Unknown role: {}, defaulting to producer screen", role);
                mainApp.showProducerScreen();
        }
    }
    
    /**
     * Validate login inputs
     */
    private boolean validateInputs(String username, String password) {
        boolean valid = true;
        
        if (username.isEmpty()) {
            showError(usernameError, "Username is required");
            valid = false;
        }
        
        if (password.isEmpty()) {
            showError(passwordError, "Password is required");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Show error message for a specific field
     */
    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }
    
    /**
     * Clear error for a specific field
     */
    private void clearError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
    
    /**
     * Clear all error messages
     */
    private void clearAllErrors() {
        clearError(usernameError);
        clearError(passwordError);
    }
    
    /**
     * Show status message
     */
    private void showStatus(String message, String type) {
        statusLabel.setText(message);
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        
        // Apply color based on type
        if ("success".equals(type)) {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2E8B57;");
        } else if ("error".equals(type)) {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #d32f2f;");
        } else {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1976d2;");
        }
    }
    
    /**
     * Hide status message
     */
    private void hideStatus() {
        statusLabel.setText("");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }
    
    /**
     * Enable/disable form inputs
     */
    private void setFormDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        demoModeCheckbox.setDisable(disabled);
    }
    
    /**
     * Show alert dialog
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
