package com.phoenixcorp.overlay;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LightingDesignerFX extends Application {
    private LightingDesignerController controller;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/lighting_designer.fxml"));
        Scene scene = new Scene(fxml.load(), 800, 200);
        controller = fxml.getController();
        stage.setScene(scene);
        stage.setTitle("LightingDesigner");
        stage.show();
    }

    @Override
    public void stop() {
        try {
            if (controller != null) controller.shutdown(); // ferme OCR + Chroma proprement
        } catch (Exception e) {
            System.err.println("[App] stop() error: " + e.getMessage());
        }
    }

    public static void main(String[] args) { launch(args); }
}
