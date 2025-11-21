package org.vericrop.gui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.services.AuthService;
import org.vericrop.gui.util.ValidationUtil;

import java.util.concurrent.CompletableFuture;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    // Login tab fields
    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Button loginButton;
    @FXML private ProgressIndicator loginProgress;
    @FXML private Label loginErrorLabel;
    
    // Register tab fields
    @FXML private TextField registerUsernameField;
    @FXML private TextField registerEmailField;
    @FXML private TextField registerFullNameField;
    @FXML private PasswordField registerPasswordField;
    @FXML private PasswordField registerConfirmPasswordField;
    @FXML private ComboBox<String> registerRoleCombo;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator registerProgress;
    @FXML private Label registerErrorLabel;
    @FXML private Label registerSuccessLabel;
    @FXML private TabPane authTabPane;

    private AuthService authService;

    @FXML
    public void initialize() {
        logger.info("Initializing LoginController");
        
        // Get auth service from application context
        ApplicationContext appContext = ApplicationContext.getInstance();
        this.authService = appContext.getAuthService();
        
        // Initialize role combo box
        if (registerRoleCombo != null) {
            registerRoleCombo.setItems(FXCollections.observableArrayList(
                "Farmer", "Consumer", "Supplier", "Admin"
            ));
            registerRoleCombo.getSelectionModel().selectFirst();
        }
        
        // Add enter key handler for login
        if (loginPasswordField != null) {
            loginPasswordField.setOnAction(event -> handleLogin());
        }
        
        logger.info("LoginController initialized");
    }

    @FXML
    private void handleLogin() {
        String username = loginUsernameField.getText();
        String password = loginPasswordField.getText();
        
        // Clear previous error
        loginErrorLabel.setVisible(false);
        
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            showLoginError("Please enter your username");
            return;
        }
        
        if (password == null || password.trim().isEmpty()) {
            showLoginError("Please enter your password");
            return;
        }
        
        // Disable button and show progress
        loginButton.setDisable(true);
        loginProgress.setVisible(true);
        
        // Perform authentication asynchronously
        CompletableFuture.supplyAsync(() -> {
            return authService.login(username, password);
        }).thenAcceptAsync(success -> {
            loginButton.setDisable(false);
            loginProgress.setVisible(false);
            
            if (success) {
                logger.info("Login successful for user: {}", username);
                // Navigate to role-based screen
                MainApp mainApp = MainApp.getInstance();
                if (mainApp != null) {
                    mainApp.showRoleBasedScreen();
                }
            } else {
                showLoginError("Invalid username or password. Please try again.");
            }
        }, Platform::runLater);
    }

    @FXML
    private void handleRegister() {
        String username = registerUsernameField.getText();
        String email = registerEmailField.getText();
        String fullName = registerFullNameField.getText();
        String password = registerPasswordField.getText();
        String confirmPassword = registerConfirmPasswordField.getText();
        String role = registerRoleCombo.getValue();
        
        // Clear previous messages
        registerErrorLabel.setVisible(false);
        registerSuccessLabel.setVisible(false);
        
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            showRegisterError("Please enter a username");
            return;
        }
        
        if (username.length() < 3) {
            showRegisterError("Username must be at least 3 characters");
            return;
        }
        
        if (email == null || email.trim().isEmpty()) {
            showRegisterError("Please enter an email address");
            return;
        }
        
        if (!ValidationUtil.isValidEmail(email)) {
            showRegisterError("Please enter a valid email address");
            return;
        }
        
        if (password == null || password.trim().isEmpty()) {
            showRegisterError("Please enter a password");
            return;
        }
        
        if (password.length() < 6) {
            showRegisterError("Password must be at least 6 characters");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showRegisterError("Passwords do not match");
            return;
        }
        
        if (role == null || role.trim().isEmpty()) {
            showRegisterError("Please select a role");
            return;
        }
        
        // Disable button and show progress
        registerButton.setDisable(true);
        registerProgress.setVisible(true);
        
        // Perform registration asynchronously
        CompletableFuture.supplyAsync(() -> {
            return authService.register(username, password, email, 
                                       fullName != null && !fullName.trim().isEmpty() ? fullName : username, 
                                       role.toLowerCase());
        }).thenAcceptAsync(success -> {
            registerButton.setDisable(false);
            registerProgress.setVisible(false);
            
            if (success) {
                logger.info("Registration successful for user: {}", username);
                showRegisterSuccess("Account created successfully! You can now login.");
                
                // Clear form
                registerUsernameField.clear();
                registerEmailField.clear();
                registerFullNameField.clear();
                registerPasswordField.clear();
                registerConfirmPasswordField.clear();
                
                // Switch to login tab after 2 seconds using JavaFX PauseTransition
                PauseTransition pause = new PauseTransition(Duration.seconds(2));
                pause.setOnFinished(event -> authTabPane.getSelectionModel().selectFirst());
                pause.play();
            } else {
                showRegisterError("Registration failed. Username may already exist.");
            }
        }, Platform::runLater);
    }
    
    private void showLoginError(String message) {
        Platform.runLater(() -> {
            loginErrorLabel.setText(message);
            loginErrorLabel.setVisible(true);
        });
    }
    
    private void showRegisterError(String message) {
        Platform.runLater(() -> {
            registerErrorLabel.setText(message);
            registerErrorLabel.setVisible(true);
        });
    }
    
    private void showRegisterSuccess(String message) {
        Platform.runLater(() -> {
            registerSuccessLabel.setText(message);
            registerSuccessLabel.setVisible(true);
        });
    }
}