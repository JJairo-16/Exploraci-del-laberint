package com.jairo.app;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.jairo.models.Board;
import com.jairo.services.Simulator;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.ui.Dimensions;
import com.jairo.app.gfx.ImageStore;
import com.jairo.app.input.InputHandler;
import com.jairo.utils.KeyBind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private boolean readKeys = true;
    private static Simulator simulator;

    public static void load(Simulator simulatorInput) {
        if (simulatorInput == null) {
            log.error("Controller.load() received null Simulator");
        }
        simulator = simulatorInput;
    }

    @FXML
    private HBox root;
    @FXML
    private StackPane leftPane;
    @FXML
    private Canvas mapCanvas;
    @FXML
    private Canvas entitiesCanvas;

    @FXML
    private VBox rightPanel;
    @FXML
    private Label bottomText;

    private final Dimensions dims = new Dimensions(0.40, 0.60);
    private final ImageStore images = ImageStore.getInstance();

    private InputHandler input;
    private Drawer drawer;

    // ! --- Tecles mantingudes (estable) ---
    private final Set<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);
    private KeyBind.Action activeMoveAction = null;

    private AnimationTimer moveTimer;
    private long nextRepeatNs = 0L;

    private static final long INITIAL_DELAY_NS = 200_000_000L; // ? 0.20s
    private static final long REPEAT_EVERY_NS = 240_000_000L; // ? 0.24s

    private boolean isActionHeld(KeyBind.Action action) {
        if (action == null) return false;
        for (KeyCode k : pressed) {
            if (KeyBind.getAction(k) == action) return true;
        }
        return false;
    }

    private KeyBind.Action findAnyHeldMaintainableAction() {
        for (KeyCode k : pressed) {
            KeyBind.Action a = KeyBind.getAction(k);
            if (KeyBind.actionCanMaintains(a)) return a;
        }
        return null;
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            log.debug("Controller initialized");

            images.preloadAll();
            log.info("Images preloaded");

            root.setFocusTraversable(true);
            root.requestFocus();

            dims.bindPanels(root, rightPanel, leftPane);

            dims.recalcAndResize(leftPane, Board.BOARD_WIDTH, Board.BOARD_HEIGHT, mapCanvas, entitiesCanvas);
            log.debug(
                    "Layout ready: leftPane={}x{}, mapCanvas={}x{}, entitiesCanvas={}x{}, tileSize={}",
                    leftPane.getWidth(), leftPane.getHeight(),
                    mapCanvas.getWidth(), mapCanvas.getHeight(),
                    entitiesCanvas.getWidth(), entitiesCanvas.getHeight(),
                    dims.getTileSize()
            );

            input = new InputHandler(simulator, bottomText, () ->
                readKeys = simulator.getContinue()
            );

            drawer = new Drawer(mapCanvas, entitiesCanvas, simulator, dims.getTileSize());
            drawer.update();
            simulator.loadDrawer(drawer);

            // * Temporitzador que fa el repeat CONTROLAT (no el del SO)
            moveTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!readKeys) return;

                    if (activeMoveAction == null) return;
                    if (!isActionHeld(activeMoveAction)) return;

                    if (now < nextRepeatNs) return;

                    if (!KeyBind.actionCanMaintains(activeMoveAction)) return;

                    input.runAction(activeMoveAction);
                    drawer.update();

                    nextRepeatNs = now + REPEAT_EVERY_NS;
                }
            };
            moveTimer.start();

            root.setOnKeyPressed(event -> {
                if (!readKeys) {
                    log.trace("Key event ignored (readKeys=false)");
                    return;
                }

                KeyCode key = event.getCode();
                KeyBind.Action action = KeyBind.getAction(key);

                boolean wasDown = pressed.contains(key);
                pressed.add(key);

                // * Mantenibles: es gestionen amb el temporitzador
                if (KeyBind.actionCanMaintains(action)) {
                    // ! Només al primer press real
                    if (!wasDown) {
                        if (activeMoveAction != action) {
                            activeMoveAction = action;

                            input.runAction(action);
                            drawer.update();

                            long now = System.nanoTime();
                            nextRepeatNs = now + INITIAL_DELAY_NS;
                        }
                    }
                    return;
                }
            });

            root.setOnKeyReleased(event -> {
                if (!readKeys) {
                    log.trace("Key event ignored (readKeys=false)");
                    return;
                }

                KeyCode key = event.getCode();
                KeyBind.Action action = KeyBind.getAction(key);

                pressed.remove(key);

                // * Si has deixat anar la tecla activa, busca una altra mantenible que continuï premuda
                if (action == activeMoveAction && !isActionHeld(activeMoveAction)) {
                    activeMoveAction = findAnyHeldMaintainableAction();
                    nextRepeatNs = System.nanoTime() + INITIAL_DELAY_NS;
                }

                // *  Mantenibles
                if (KeyBind.actionCanMaintains(action)) {
                    return;
                }

                // * Accions “d’un sol toc”
                input.handleKeyReleased(key);
                drawer.update();
            });
        });
    }
}
