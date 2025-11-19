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
            primaryStage.setTitle("VeriCrop - Supply Chain Management");

            try {
                URL iconUrl = getClass().getResource("/images/vericrop-icon.png");
                if (iconUrl != null) {
                    Image icon = new Image(iconUrl.toString());
                    primaryStage.getIcons().add(icon);
                } else {
                    System.out.println("⚠️  Icon not found, continuing without icon");
                }
            } catch (Exception e) {
                System.out.println("⚠️  Could not load icon: " + e.getMessage());
            }

            showProducerScreen();

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

    public static MainApp getInstance() {
        return instance;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        System.out.println("=== DEBUG: Launching JavaFX ===");
        try {
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