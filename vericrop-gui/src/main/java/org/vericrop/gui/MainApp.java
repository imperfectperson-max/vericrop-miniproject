package org.vericrop.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.net.URL;
import java.util.Objects;

public class MainApp extends Application {
    private static MainApp instance;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        instance = this;
        this.primaryStage = primaryStage;

        try {
            // Load FXML with multiple fallback options
            URL fxmlUrl = getClass().getResource("/fxml/producer.fxml");
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("fxml/producer.fxml");
            }
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML file not found. Checked: /fxml/producer.fxml and fxml/producer.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            // Set application icon (optional)
            try {
                URL iconUrl = getClass().getResource("/images/icon.png");
                if (iconUrl == null) {
                    iconUrl = getClass().getResource("images/icon.png");
                }
                if (iconUrl != null) {
                    primaryStage.getIcons().add(new Image(iconUrl.toString()));
                }
            } catch (Exception e) {
                System.out.println("Icon not found, using default");
            }

            primaryStage.setTitle("VeriCrop - Farm Management Dashboard");
            Scene scene = new Scene(root, 1400, 900);

            // Add CSS styling if available
            try {
                URL cssUrl = getClass().getResource("/css/styles.css");
                if (cssUrl == null) {
                    cssUrl = getClass().getResource("css/styles.css");
                }
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                } else {
                    // Create basic CSS programmatically as fallback
                    scene.getStylesheets().add(createFallbackCSS());
                }
            } catch (Exception e) {
                System.out.println("CSS file not found, using default styling");
                scene.getStylesheets().add(createFallbackCSS());
            }

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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
                throw new RuntimeException("FXML file not found: " + fxmlFile);
            }
            Parent root = FXMLLoader.load(fxmlUrl);
            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            System.err.println("Error switching to screen: " + e.getMessage());
            e.printStackTrace();
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

    public static MainApp getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        System.out.println("=== DEBUG: Launching JavaFX ===");
        try {
            // Set better exception handling for JavaFX
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("Uncaught exception in thread " + thread.getName() + ": " + throwable.getMessage());
                throwable.printStackTrace();
            });

            launch(args);
        } catch (Exception e) {
            System.err.println("Application launch failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}