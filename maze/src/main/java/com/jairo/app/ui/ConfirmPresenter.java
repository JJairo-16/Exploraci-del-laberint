package com.jairo.app.ui;

import javafx.scene.control.Label;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfirmPresenter {
    private static final Logger log = LoggerFactory.getLogger(ConfirmPresenter.class);

    private final Label confirmLabel;

    private static final PseudoClass CONFIRM_TRUE = PseudoClass.getPseudoClass("confirm-true");
    private static final PseudoClass CONFIRM_FALSE = PseudoClass.getPseudoClass("confirm-false");

    private static final PseudoClass FLASH = PseudoClass.getPseudoClass("flash");

    private PauseTransition flashTimer;

    public ConfirmPresenter(Label confirmLabel) {
        this.confirmLabel = confirmLabel;
    }

    public void setConfirm(boolean confirmation) {
        log.debug("Confirmation state updated: {}", confirmation);
        confirmLabel.setText(confirmation ? "Sempre" : "Mai");

        // Marca estado (solo uno activo)
        confirmLabel.pseudoClassStateChanged(CONFIRM_TRUE, confirmation);
        confirmLabel.pseudoClassStateChanged(CONFIRM_FALSE, !confirmation);

        // Flash corto al actualizar (robusto aunque pulsen rápido)
        flash();
    }

    private void flash() {
        confirmLabel.pseudoClassStateChanged(FLASH, true);

        if (flashTimer != null)
            flashTimer.stop();

        flashTimer = new PauseTransition(Duration.millis(120));
        flashTimer.setOnFinished(e -> confirmLabel.pseudoClassStateChanged(FLASH, false));
        flashTimer.play();
    }

}
