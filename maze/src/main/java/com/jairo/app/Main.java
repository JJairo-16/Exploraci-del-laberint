package com.jairo.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 590;

    private static final String ICON_ROOT = "/com/jairo/app/img/icon.png";
    private static final String FXML_TO_LOAD = "start-view.fxml";

    @Override
    public void start(Stage stage) throws Exception {
        log.info("Application starting");

        try {

            log.debug("Loading FXML: {}", FXML_TO_LOAD);

            Locale locale = Locale.getDefault();
            Locale.setDefault(locale);

            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);

            FXMLLoader fxmlLoader = new FXMLLoader(
                    Main.class.getResource(FXML_TO_LOAD),
                    bundle
            );

            Scene scene = new Scene(fxmlLoader.load(), WIDTH, HEIGHT);

            scene.getStylesheets()
                    .add(Main.class.getResource("style.css").toString());
            log.debug("Stylesheet loaded: style.css");

            stage.setScene(scene);
            stage.setTitle(bundle.getString("app.title"));
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
