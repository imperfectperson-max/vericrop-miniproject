package org.vericrop.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal JavaFX Hello World application for VeriCrop GUI.
 * This is a placeholder to establish the GUI module structure.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VeriCrop GUI - Hello World");

        Label label = new Label("Welcome to VeriCrop!");
        label.setStyle("-fx-font-size: 16px; -fx-padding: 10px;");

        Button button = new Button("Click Me");
        button.setOnAction(e -> {
            label.setText("Hello from VeriCrop MVP!");
        });

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(label, button);

        Scene scene = new Scene(vbox, 400, 200);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
