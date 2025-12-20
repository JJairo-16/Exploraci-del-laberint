package com.jairo.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 590;

    private static final String TITLE = "Exploració del laberint";
    private static final String ICON_ROOT = "/com/jairo/app/img/icon.png";

    @Override
    public void start(Stage stage) throws Exception {
        log.info("Application starting");

        try {
            log.debug("Loading FXML: main-view.fxml");
            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("main-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), WIDTH, HEIGHT);

            scene.getStylesheets()
                    .add(Main.class.getResource("style.css").toString());
            log.debug("Stylesheet loaded: style.css");

            stage.setScene(scene);
            stage.setTitle(TITLE);
            stage.setResizable(false);

            var iconUrl = Main.class.getResource(ICON_ROOT);
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } else {
                log.warn("Application icon not found at {}", ICON_ROOT);
            }

            stage.centerOnScreen();
            stage.show();

            log.info("Application started ({}x{})", WIDTH, HEIGHT);

        } catch (Exception e) {
            log.error("Application failed to start", e);
            throw e;
        }
    }

    public static void launchGui() {
        launch();
    }
}
