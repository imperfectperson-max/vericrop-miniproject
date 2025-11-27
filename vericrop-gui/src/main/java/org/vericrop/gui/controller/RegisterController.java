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
import org.vericrop.gui.exception.DataAccessException;
import org.vericrop.gui.exception.UserCreationException;
import org.vericrop.gui.models.User;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        showStatus("Checking availability...", "info");
        
        // Store for use in inner class
        final String finalUsername = username;
        final String finalEmail = email;
        final String finalFullName = fullName;
        final String finalPassword = password;
        final String finalRole = role.toUpperCase();
        
        // Run registration in background thread
        Task<RegistrationResult> registrationTask = new Task<>() {
            @Override
            protected RegistrationResult call() {
                logger.debug("Starting registration process for user: {}", finalUsername);
                
                // Optimistic duplicate checks - advisory only, not authoritative
                // The database constraint is the final arbiter to avoid race conditions
                try {
                    if (userDao.usernameExists(finalUsername)) {
                        logger.debug("Advisory check: username '{}' already exists", finalUsername);
                        return RegistrationResult.usernameError("Username '" + finalUsername + "' is already taken");
                    }
                    if (userDao.emailExists(finalEmail)) {
                        logger.debug("Advisory check: email already exists");
                        return RegistrationResult.emailError("Email is already registered");
                    }
                } catch (DataAccessException e) {
                    logger.error("Database error during duplicate check: {}", e.getMessage());
                    return RegistrationResult.generalError("Unable to verify availability. Please try again.");
                }
                
                // Try to create the user - database constraints provide final protection
                try {
                    User user = userDao.createUser(finalUsername, finalPassword, finalEmail, finalFullName, finalRole);
                    logger.info("✅ Registration successful for user: {}", finalUsername);
                    return RegistrationResult.success(user);
                } catch (UserCreationException e) {
                    logger.warn("Registration failed for user '{}': {}", finalUsername, e.getMessage());
                    
                    // Map exception to specific field error
                    if (e.isDuplicateUsername()) {
                        return RegistrationResult.usernameError(e.getMessage());
                    } else if (e.isDuplicateEmail()) {
                        return RegistrationResult.emailError(e.getMessage());
                    } else {
                        // General database error - provide user-friendly message
                        return RegistrationResult.generalError(
                            "Could not create account. Please try again or contact support.");
                    }
                }
            }
            
            @Override
            protected void succeeded() {
                RegistrationResult result = getValue();
                handleRegistrationResult(result, finalUsername);
            }
            
            @Override
            protected void failed() {
                setFormDisabled(false);
                Throwable exception = getException();
                logger.error("Registration task failed with unexpected exception", exception);
                showStatus("❌ Registration failed. Please try again.", "error");
            }
        };
        
        // Run task in background
        new Thread(registrationTask, "RegistrationTask").start();
    }
    
    /**
     * Handle the registration result on the JavaFX Application Thread.
     * This method is called from Task.succeeded() which runs on the JavaFX thread.
     */
    private void handleRegistrationResult(RegistrationResult result, String username) {
        setFormDisabled(false);
        
        if (result.isSuccess()) {
            logger.info("✅ Registration successful for user: {}", username);
            showStatus("✅ Account created successfully! Redirecting to login...", "success");
            
            // Navigate to login screen after 2 seconds using ScheduledExecutorService
            // This keeps the delay off the JavaFX thread
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LoginRedirectTimer");
                t.setDaemon(true);
                return t;
            });
            
            scheduler.schedule(() -> {
                Platform.runLater(() -> {
                    MainApp mainApp = MainApp.getInstance();
                    if (mainApp != null) {
                        mainApp.switchToScreen("login.fxml");
                    }
                });
                scheduler.shutdown();
            }, 2, TimeUnit.SECONDS);
        } else {
            // Show field-specific error if applicable
            if (result.isUsernameError()) {
                showError(usernameError, result.getErrorMessage());
                logger.debug("Showing username error: {}", result.getErrorMessage());
            } else if (result.isEmailError()) {
                showError(emailError, result.getErrorMessage());
                logger.debug("Showing email error: {}", result.getErrorMessage());
            } else {
                // General error - show in status label
                showStatus("❌ " + result.getErrorMessage(), "error");
                logger.debug("Showing general error: {}", result.getErrorMessage());
            }
        }
    }
    
    /** 
     * Helper class to carry registration result with field-specific error information.
     */
    private static class RegistrationResult {
        private final User user;
        private final String errorMessage;
        private final ErrorType errorType;
        
        private enum ErrorType {
            NONE, USERNAME, EMAIL, GENERAL
        }
        
        private RegistrationResult(User user, String errorMessage, ErrorType errorType) {
            this.user = user;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
        }
        
        static RegistrationResult success(User user) {
            return new RegistrationResult(user, null, ErrorType.NONE);
        }
        
        static RegistrationResult usernameError(String message) {
            return new RegistrationResult(null, message, ErrorType.USERNAME);
        }
        
        static RegistrationResult emailError(String message) {
            return new RegistrationResult(null, message, ErrorType.EMAIL);
        }
        
        static RegistrationResult generalError(String message) {
            return new RegistrationResult(null, message, ErrorType.GENERAL);
        }
        
        boolean isSuccess() { return user != null; }
        boolean isUsernameError() { return errorType == ErrorType.USERNAME; }
        boolean isEmailError() { return errorType == ErrorType.EMAIL; }
        String getErrorMessage() { return errorMessage; }
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
        } else if (username.length() > 50) {
            showError(usernameError, "Username must be 50 characters or less");
            valid = false;
        } else if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError(usernameError, "Username can only contain letters, numbers, and underscores");
            valid = false;
        }
        
        // Validate email
        if (email.isEmpty()) {
            showError(emailError, "Email is required");
            valid = false;
        } else if (email.length() > 255) {
            showError(emailError, "Email must be 255 characters or less");
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
        
        // Validate password (at least 8 chars, uppercase, lowercase, digit)
        if (password.isEmpty()) {
            showError(passwordError, "Password is required");
            valid = false;
        } else if (password.length() < 8) {
            showError(passwordError, "Password must be at least 8 characters");
            valid = false;
        } else if (!password.matches(".*[A-Z].*")) {
            showError(passwordError, "Password must contain at least one uppercase letter");
            valid = false;
        } else if (!password.matches(".*[a-z].*")) {
            showError(passwordError, "Password must contain at least one lowercase letter");
            valid = false;
        } else if (!password.matches(".*\\d.*")) {
            showError(passwordError, "Password must contain at least one digit");
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
     * Get the selected role from toggle buttons.
     * Maps UI selection to database role names:
     * - Farmer → PRODUCER (producer/farmer operations)
     * - Consumer → CONSUMER (consumer operations)
     * - Supplier → LOGISTICS (logistics/supplier operations)
     * - Admin → ADMIN (access to all UIs)
     */
    private String getSelectedRole() {
        Toggle selected = roleGroup.getSelectedToggle();
        if (selected == farmerToggle) return "PRODUCER";
        if (selected == consumerToggle) return "CONSUMER";
        if (selected == supplierToggle) return "LOGISTICS";
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
