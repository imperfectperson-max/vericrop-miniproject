package org.vericrop.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.services.NavigationService;

import java.net.URL;

/**
 * Main JavaFX Application entry point for VeriCrop GUI.
 * Initializes ApplicationContext and manages screen navigation.
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static MainApp instance;
    private Stage primaryStage;
    private ApplicationContext appContext;

    @Override
    public void start(Stage primaryStage) throws Exception {
        instance = this;
        this.primaryStage = primaryStage;

        try {
            logger.info("=== Starting VeriCrop GUI Application ===");
            
            // Initialize application context (services, config, etc.)
            this.appContext = ApplicationContext.getInstance();
            logger.info("Application context initialized");
            
            // Initialize NavigationService
            NavigationService navigationService = NavigationService.getInstance();
            navigationService.initialize(primaryStage);
            logger.info("NavigationService initialized");
            
            primaryStage.setTitle("VeriCrop - Supply Chain Management");

            try {
                URL iconUrl = getClass().getResource("./images/vericrop-icon.png");
                if (iconUrl != null) {
                    Image icon = new Image(iconUrl.toString());
                    primaryStage.getIcons().add(icon);
                    logger.debug("Application icon loaded");
                } else {
                    logger.warn("Icon not found, continuing without icon");
                }
            } catch (Exception e) {
                logger.warn("Could not load icon: {}", e.getMessage());
            }
            
            // Add shutdown hook
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application closing...");
                shutdown();
            });

            // Start with login screen using NavigationService
            navigationService.navigateToLogin();
            logger.info("Application started successfully");

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }
    
    /**
     * Get ApplicationContext for dependency injection
     */
    public ApplicationContext getApplicationContext() {
        return appContext;
    }
    
    /**
     * Shutdown application and release resources
     */
    public void shutdown() {
        logger.info("Shutting down application...");
        if (appContext != null) {
            appContext.shutdown();
        }
        logger.info("Application shutdown complete");
    }

    private String createFallbackCSS() {
        return "data:text/css," +
                ".root { -fx-font-family: 'Segoe UI', Arial, sans-serif; }" +
                ".button { -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-border-radius: 4px; }" +
                ".text-field { -fx-border-color: #ccc; -fx-border-radius: 4px; }";
    }

    public void switchToScreen(String fxmlFile) {
        try {
            URL fxmlUrl = getClass().getResource("/fxml/" + fxmlFile);
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("fxml/" + fxmlFile);
            }
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource(fxmlFile);
            }
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML file not found: " + fxmlFile);
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Scene scene = new Scene(root, 1400, 900);

            try {
                URL cssUrl = getClass().getResource("/css/styles.css");
                if (cssUrl == null) {
                    cssUrl = getClass().getResource("css/styles.css");
                }
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                } else {
                    scene.getStylesheets().add(createFallbackCSS());
                }
            } catch (Exception e) {
                scene.getStylesheets().add(createFallbackCSS());
            }

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("Error switching to screen " + fxmlFile + ": " + e.getMessage());
            e.printStackTrace();
            showProducerScreen();
        }
    }

    public void showProducerScreen() {
        switchToScreen("producer.fxml");
    }

    public void showLogisticsScreen() {
        switchToScreen("logistics.fxml");
    }

    public void showConsumerScreen() {
        switchToScreen("consumer.fxml");
    }

    public void showAnalyticsScreen() {
        switchToScreen("analytics.fxml");
    }

    public void showInboxScreen() {
        switchToScreen("inbox.fxml");
    }

    public static MainApp getInstance() {
        return instance;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        logger.info("=== Launching VeriCrop JavaFX Application ===");
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                logger.error("Uncaught exception in thread {}: {}", thread.getName(), throwable.getMessage(), throwable);
            });

            launch(args);
        } catch (Exception e) {
            logger.error("Application launch failed", e);
            System.exit(1);
        }
    }
}