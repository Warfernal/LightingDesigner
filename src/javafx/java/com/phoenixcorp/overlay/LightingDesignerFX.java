package com.phoenixcorp.overlay;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class LightingDesignerFX extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/lighting_designer.fxml"));
        Parent root = loader.load();
        LightingDesignerController controller = loader.getController();

        // Taille par défaut plus généreuse
        double w = 1200;
        double h = 800;

        // Option : s’adapter à l’écran si très petit
        var bounds = Screen.getPrimary().getVisualBounds();
        if (w > bounds.getWidth() * 0.95)  w = Math.floor(bounds.getWidth() * 0.95);
        if (h > bounds.getHeight() * 0.95) h = Math.floor(bounds.getHeight() * 0.95);

        Scene scene = new Scene(root, w, h);
        stage.setTitle("Phoenix Lighting Designer");
        stage.setScene(scene);

        // Laisser redimensionner, mais poser un minimum confortable
        stage.setMinWidth(1000);
        stage.setMinHeight(680);

        stage.setOnCloseRequest(e -> {
            try { if (controller != null) controller.shutdown(); } catch (Exception ignore) {}
        });

        stage.show();
        // Option : ouvrir maximisé
        // stage.setMaximized(true);
    }

    public static void main(String[] args) { launch(args); }
}
