package com.jairo.app;

import javafx.application.Platform;
import javafx.fxml.FXML;

public class EndController {
    @FXML
    private void onExit() {
        Platform.exit();
    }
}
