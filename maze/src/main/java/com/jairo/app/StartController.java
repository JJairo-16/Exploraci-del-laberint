package com.jairo.app;

import com.jairo.app.i18n.LanguageManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;

public class StartController {

    @FXML
    private VBox root;

    @FXML
    private ChoiceBox<String> languageSelector;

    @FXML
    private void initialize() {
        languageSelector.getItems().setAll(LanguageManager.getDisplayNames());
        languageSelector.setValue(LanguageManager.getCurrentDisplayName());

        languageSelector.setOnAction(e -> {
            String display = languageSelector.getValue();
            String code = LanguageManager.getCodeFromDisplayName(display);
            if (code != null) {
                LanguageManager.changeLanguageAndReloadStart(root.getScene(), code);
            }
        });
    }

    @FXML
    private void onStart() {
        LanguageManager.switchToMainView(root.getScene());
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

}
