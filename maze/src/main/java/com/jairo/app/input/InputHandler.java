package com.jairo.app.input;

import com.jairo.services.Simulator;
import com.jairo.utils.KeyBind;
import static com.jairo.utils.KeyBind.Action;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InputHandler {
    private static final Logger log = LoggerFactory.getLogger(InputHandler.class);

    private static boolean debugInScreen = false;
    private static boolean debugAction = true;

    private static List<Action> blackListOfDebug = List.of(
        Action.NONE,
        Action.USE
    );

    private final Simulator simulator;
    private final Label bottomText;
    private final Runnable onAfterSimulate;

    public InputHandler(Simulator simulator, Label bottomText, Runnable onAfterSimulate) {
        this.simulator = simulator;
        this.bottomText = bottomText;
        this.onAfterSimulate = onAfterSimulate;
    }

    /**
     * Executa una acció directament (útil per al temporitzador de "mantenir tecla").
     */
    public void runAction(KeyBind.Action action) {
        if (action == null || action == KeyBind.Action.NONE) return;

        if (debugInScreen) bottomText.setText(action.name().toLowerCase());
        simulator.simulate(action);
        onAfterSimulate.run();
    }

    /**
     * Per a accions de "un sol toc" (use, confirm switch, zoom, etc.)
     * Normalment es crida a KeyReleased.
     */
    public void handleKeyReleased(KeyCode key) {
        Action action = KeyBind.getAction(key);
        debug(key, action);
        runAction(action);
    }

    private void debug(KeyCode key, Action action) {
        if (!debugAction) {
            return;
        }

        if (blackListOfDebug.contains(action)) {
            return;
        }

        log.debug("Key released: {} -> action {}", key, action);
    }
}
