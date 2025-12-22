package com.jairo.app;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.BasicItemType;
import com.jairo.services.Simulator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

public class EndController {
    private Simulator simulator;

    @FXML private Label coinsLabel;
    @FXML private ImageView coinIcon;

    public void initState(Simulator simulator) {
        this.simulator = simulator;
        renderCoins();
    }

    private void renderCoins() {
        if (coinIcon != null) {
            coinIcon.setImage(ImageStore.getInstance().get(Sprite.COIN));
        }
        if (coinsLabel != null) {
            coinsLabel.setText(String.valueOf(getCoins()));
        }
    }

    private int getCoins() {
        if (simulator == null)
            return 20;

        return simulator.getInventory().getCount(BasicItemType.COIN);
    }

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
            renderCoins();
        });
    }
}
