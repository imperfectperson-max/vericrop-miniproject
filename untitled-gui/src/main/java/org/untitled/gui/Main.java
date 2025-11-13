
package org.untitled.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.untitled.core.Blockchain;

public class Main extends Application {
    // Single shared blockchain instance for the GUI demo
    private static final Blockchain blockchain = new Blockchain();

    public static Blockchain getBlockchain() {
        return blockchain;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/producer.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("VeriCrop - Producer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}