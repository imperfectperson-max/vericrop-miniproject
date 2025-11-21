package org.vericrop.gui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.MainApp;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.models.User;

/**
 * Controller for the user registration screen.
 * Handles account creation with validation and role selection.
 */
public class RegisterController {
    private static final Logger logger = LoggerFactory.getLogger(RegisterController.class);
    
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    
    @FXML private ToggleButton farmerToggle;
    @FXML private ToggleButton consumerToggle;
    @FXML private ToggleButton supplierToggle;
    @FXML private ToggleButton adminToggle;
    
    @FXML private Label usernameError;
    @FXML private Label emailError;
    @FXML private Label fullNameError;
    @FXML private Label passwordError;
    @FXML private Label confirmPasswordError;
    @FXML private Label roleError;
    @FXML private Label statusLabel;
    
    private ToggleGroup roleGroup;
    private ApplicationContext appContext;
    private UserDao userDao;
    
    @FXML
    public void initialize() {
        logger.info("RegisterController initialized");
        
        // Get application context
        appContext = ApplicationContext.getInstance();
        userDao = appContext.getUserDao();
        
        // Create toggle group for role selection
        roleGroup = new ToggleGroup();
        farmerToggle.setToggleGroup(roleGroup);
        consumerToggle.setToggleGroup(roleGroup);
        supplierToggle.setToggleGroup(roleGroup);
        adminToggle.setToggleGroup(roleGroup);
        
        // Select farmer by default
        farmerToggle.setSelected(true);
        
        // Add input validation listeners
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> clearError(usernameError));
        emailField.textProperty().addListener((obs, oldVal, newVal) -> clearError(emailError));
        fullNameField.textProperty().addListener((obs, oldVal, newVal) -> clearError(fullNameError));
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearError(passwordError));
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> clearError(confirmPasswordError));
        roleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> clearError(roleError));
    }
    
    @FXML
    private void handleRegister() {
        // Clear all previous errors
        clearAllErrors();
        hideStatus();
        
        // Get input values
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String role = getSelectedRole();
        
        // Validate inputs
        if (!validateInputs(username, email, fullName, password, confirmPassword, role)) {
            return;
        }
        
        // Disable form during registration
        setFormDisabled(true);
        showStatus("Creating account...", "info");
        
        // Run registration in background thread
        Task<User> registrationTask = new Task<>() {
            @Override
            protected User call() {
                return userDao.createUser(username, password, email, fullName, role.toUpperCase());
            }
            
            @Override
            protected void succeeded() {
                User user = getValue();
                setFormDisabled(false);
                
                if (user != null) {
                    logger.info("✅ Registration successful for user: {}", username);
                    showStatus("✅ Account created successfully! Redirecting to login...", "success");
                    
                    // Navigate to login screen after 2 seconds
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            Platform.runLater(() -> {
                                MainApp mainApp = MainApp.getInstance();
                                if (mainApp != null) {
                                    mainApp.switchToScreen("login.fxml");
                                }
                            });
                        } catch (InterruptedException e) {
                            logger.error("Sleep interrupted", e);
                        }
                    }).start();
                } else {
                    logger.error("Registration failed - user creation returned null");
                    showStatus("❌ Registration failed. Username or email may already exist.", "error");
                }
            }
            
            @Override
            protected void failed() {
                setFormDisabled(false);
                Throwable exception = getException();
                logger.error("Registration failed with exception", exception);
                showStatus("❌ Registration failed: " + exception.getMessage(), "error");
            }
        };
        
        // Run task in background
        new Thread(registrationTask).start();
    }
    
    @FXML
    private void handleBackToLogin() {
        logger.info("Navigating back to login screen");
        MainApp mainApp = MainApp.getInstance();
        if (mainApp != null) {
            mainApp.switchToScreen("login.fxml");
        }
    }
    
    /**
     * Validate all registration inputs
     */
    private boolean validateInputs(String username, String email, String fullName, 
                                   String password, String confirmPassword, String role) {
        boolean valid = true;
        
        // Validate username
        if (username.isEmpty()) {
            showError(usernameError, "Username is required");
            valid = false;
        } else if (username.length() < 3) {
            showError(usernameError, "Username must be at least 3 characters");
            valid = false;
        } else if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError(usernameError, "Username can only contain letters, numbers, and underscores");
            valid = false;
        }
        
        // Validate email
        if (email.isEmpty()) {
            showError(emailError, "Email is required");
            valid = false;
        } else if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showError(emailError, "Invalid email format");
            valid = false;
        }
        
        // Validate full name
        if (fullName.isEmpty()) {
            showError(fullNameError, "Full name is required");
            valid = false;
        } else if (fullName.length() < 2) {
            showError(fullNameError, "Full name must be at least 2 characters");
            valid = false;
        }
        
        // Validate password
        if (password.isEmpty()) {
            showError(passwordError, "Password is required");
            valid = false;
        } else if (password.length() < 6) {
            showError(passwordError, "Password must be at least 6 characters");
            valid = false;
        }
        
        // Validate password confirmation
        if (confirmPassword.isEmpty()) {
            showError(confirmPasswordError, "Please confirm your password");
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            showError(confirmPasswordError, "Passwords do not match");
            valid = false;
        }
        
        // Validate role selection
        if (role == null || role.isEmpty()) {
            showError(roleError, "Please select a role");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Get the selected role from toggle buttons
     */
    private String getSelectedRole() {
        Toggle selected = roleGroup.getSelectedToggle();
        if (selected == farmerToggle) return "FARMER";
        if (selected == consumerToggle) return "CONSUMER";
        if (selected == supplierToggle) return "SUPPLIER";
        if (selected == adminToggle) return "ADMIN";
        return null;
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
        clearError(emailError);
        clearError(fullNameError);
        clearError(passwordError);
        clearError(confirmPasswordError);
        clearError(roleError);
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
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2E8B57; -fx-padding: 10 0 0 0;");
        } else if ("error".equals(type)) {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #d32f2f; -fx-padding: 10 0 0 0;");
        } else {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1976d2; -fx-padding: 10 0 0 0;");
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
        emailField.setDisable(disabled);
        fullNameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled);
        farmerToggle.setDisable(disabled);
        consumerToggle.setDisable(disabled);
        supplierToggle.setDisable(disabled);
        adminToggle.setDisable(disabled);
    }
}
