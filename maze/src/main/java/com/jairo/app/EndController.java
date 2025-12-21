package com.jairo.app;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;

import javafx.application.Platform;
import javafx.fxml.FXML;

public class EndController {
    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            SoundManager sm = SoundManager.get();
            sm.setMuted(false);
            sm.playSfx(Sound.VICTORY.path());
        });
    }
}
