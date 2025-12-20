package com.jairo.app;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.jairo.models.Board;
import com.jairo.services.Simulator;

import com.jairo.app.ui.Dimensions;
import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.app.gfx.BoardRenderer;
import com.jairo.app.ui.ConfirmPresenter;
import com.jairo.app.input.InputHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private GraphicsContext mapGC;
    private GraphicsContext entitiesGC;

    @FXML
    private VBox rightPanel;
    @FXML
    private Label bottomText;
    @FXML
    private Label confirmText;

    private final Dimensions dims = new Dimensions(0.40, 0.60);
    private final ImageStore images = new ImageStore();

    private BoardRenderer renderer;
    private ConfirmPresenter confirmPresenter;
    private InputHandler input;

    private boolean staticDrawn = false;

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
                    dims.getTileSize());

            mapGC = mapCanvas.getGraphicsContext2D();
            entitiesGC = entitiesCanvas.getGraphicsContext2D();

            renderer = new BoardRenderer(images);
            confirmPresenter = new ConfirmPresenter(confirmText);

            input = new InputHandler(simulator, bottomText, () -> confirmPresenter.setConfirm(simulator.getConfirm()));

            confirmPresenter.setConfirm(simulator.getConfirm());

            drawStaticOnce();

            root.setOnKeyReleased(event -> {
                if (!readKeys) {
                    log.trace("Key event ignored (readKeys=false)");
                    return;
                }
                KeyCode key = event.getCode();
                input.handleKeyReleased(key);
            });
        });
    }

    private void drawStaticOnce() {
        if (staticDrawn)
            return;

        double tileSize = dims.getTileSize();
        if (tileSize <= 0 || mapGC == null) {
            log.debug("Static draw skipped (tileSize={}, mapGC={})", tileSize, mapGC);
            return;
        }

        renderer.drawBorder(
                mapGC,
                mapCanvas.getWidth(),
                mapCanvas.getHeight(),
                Board.BOARD_WIDTH,
                Board.BOARD_HEIGHT,
                tileSize,
                Sprite.PATH // todo: actualitzar a mur quan sigui possible
        );

        staticDrawn = true;
        log.debug("Static border drawn");
    }
}
