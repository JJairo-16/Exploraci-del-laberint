package com.jairo.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.SimulatorLoader;
import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.i18n.LanguageManager;
import com.jairo.app.input.FocusKeeper;
import com.jairo.app.input.HeldItemTuningAdjuster;
import com.jairo.app.input.InputHandler;
import com.jairo.app.input.InputRepeatController;
import com.jairo.app.loop.GameLoop;
import com.jairo.app.state.GameStateCoordinator;
import com.jairo.app.time.FxTimeSource;
import com.jairo.app.ui.Dimensions;
import com.jairo.items.PowerType;
import com.jairo.models.Board;
import com.jairo.models.Inventory;
import com.jairo.services.Simulator;
import com.jairo.utils.KeyBind;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private boolean readKeys = true;
    private boolean started = false;

    private Simulator simulator;

    @FXML private HBox root;
    @FXML private StackPane leftPane;

    @FXML private Canvas mapCanvas;
    @FXML private Canvas entitiesCanvas;
    @FXML private Canvas postFxCanvas;
    @FXML private Canvas hudCanvas;

    @FXML private VBox rightPanel;
    @FXML private Label bottomText;

    @FXML private ChoiceBox<String> languageSelector;

    @FXML private Label blaiGlassesPowerText;

    private final Dimensions dims = new Dimensions();
    private final ImageStore images = ImageStore.getInstance();
    private final SoundManager sm = SoundManager.get();

    private InputHandler input;
    private Drawer drawer;

    private GameLoop gameLoop;
    private static final long FRAME_NS = 33_000_000L;

    private InputRepeatController inputRepeat;
    private static final long INITIAL_DELAY_NS = 240_000_000L;
    private static final long REPEAT_EVERY_NS = 240_000_000L;
    private static final double SPRINTING_SPEED = 0.55;

    private GameStateCoordinator state;

    private final FxTimeSource time = new FxTimeSource();

    // Punto crítico: drawer.update en un único sitio
    private boolean drawerDirty = true;

    private Drawer.CameraState pendingCameraState;

    public void initState(Simulator simulator, Drawer.CameraState cameraState) {
        this.simulator = simulator;
        this.pendingCameraState = cameraState;
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            if (started) {
                if (log.isWarnEnabled()) log.warn("Controller initialize() called twice; ignoring second init.");
                return;
            }
            started = true;

            stopRuntime();

            if (simulator == null) simulator = SimulatorLoader.load();

            sm.preload(Sound.values());
            sm.playBgmLoop(Sound.THEME.path());
            sm.setMasterVolume(0.9);
            sm.setBgmVolume(0.30);
            sm.setMuted(false);

            images.preloadAll();

            root.setFocusTraversable(true);
            root.requestFocus();

            dims.bindPanels(root, rightPanel, leftPane);
            dims.recalcAndResize(leftPane, Board.BOARD_WIDTH, Board.BOARD_HEIGHT,
                    mapCanvas, entitiesCanvas, hudCanvas, postFxCanvas);

            // ---- foco de teclado (punto crítico #1) ----
            // Click en área de juego => recuperar foco
            FocusKeeper.install(root, leftPane, mapCanvas, entitiesCanvas, hudCanvas, postFxCanvas);
            // Cuando se cierra el ChoiceBox => recuperar foco
            FocusKeeper.bindChoiceBoxRefocus(languageSelector, root);

            // ---- idioma ----
            if (languageSelector != null) {
                languageSelector.getItems().setAll(LanguageManager.getDisplayNames());
                languageSelector.setValue(LanguageManager.getCurrentDisplayName());

                languageSelector.setOnAction(e -> {
                    String selected = languageSelector.getValue();
                    String code = LanguageManager.getCodeFromDisplayName(selected);

                    if (code != null
                            && languageSelector.getScene() != null
                            && drawer != null
                            && simulator != null) {

                        Drawer.CameraState cameraState = drawer.getCameraState();

                        stopRuntime();

                        LanguageManager.changeLanguageAndReloadMain(
                                languageSelector.getScene(),
                                code,
                                simulator,
                                cameraState
                        );
                    }

                    // tras cambiar idioma (o intentar), recupera foco
                    Platform.runLater(root::requestFocus);
                });
            }

            input = new InputHandler(simulator, bottomText, () -> { /* fin centralizado */ });

            drawer = new Drawer(mapCanvas, entitiesCanvas, postFxCanvas, hudCanvas, simulator, dims.getTileSize());
            if (pendingCameraState != null) {
                drawer.setCameraState(pendingCameraState);
                pendingCameraState = null;
            }

            simulator.loadDrawer(drawer);
            drawerDirty = true;

            inputRepeat = new InputRepeatController(
                    simulator,
                    input,
                    INITIAL_DELAY_NS,
                    REPEAT_EVERY_NS,
                    SPRINTING_SPEED
            );

            state = new GameStateCoordinator(
                    simulator,
                    () -> readKeys,
                    this::handleGameEnded,
                    this::activeBlaiGlassesPower,
                    this::deactivateBlaiGlassesPower
            );

            gameLoop = new GameLoop(
                    FRAME_NS,
                    () -> readKeys,
                    this::onRenderTick,
                    this::onRepeatTick
            );
            gameLoop.start();

            // Si se desengancha de escena, parar runtime (evita duplicados)
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null) stopRuntime();
            });

            // ---- key handlers (sin drawer.update aquí) ----
            root.setOnKeyPressed(event -> {
                if (!readKeys) return;

                long now = time.now();
                boolean ran = inputRepeat.onKeyPressed(event.getCode(), now);
                if (ran) drawerDirty = true;
            });

            root.setOnKeyReleased(event -> {
                if (!readKeys) return;

                long now = time.now();
                KeyCode key = event.getCode();

                Inventory inv = simulator.getInventory();
                HeldItemTuningAdjuster.adjust(key, inv, (PowerType) inv.getSelectedPower());

                inputRepeat.onKeyReleased(key, now);

                KeyBind.Action action = KeyBind.getAction(key);
                if (action == null) return;

                if (action.canMaintain) {
                    // soltar mantenible puede cambiar timers internos, pero no cambia necesariamente el drawer;
                    // lo dejamos como antes (no marcamos dirty).
                    return;
                }

                // one-shot => cambia estado => marcar dirty
                input.handleKeyReleased(key);
                drawerDirty = true;
            });

            if (log.isInfoEnabled()) log.info("Controller ready.");
        });
    }

    // ---- ticks ----

    private void onRenderTick(long now) {
        if (!readKeys) return;

        // Fuente única de tiempo
        time.update(now);

        long last = (gameLoop == null) ? 0L : gameLoop.getLastRenderNs();
        long dt = (last == 0L) ? 0L : (now - last);

        if (state != null) {
            state.tick(now, dt);
        }

        // Punto crítico #2: orden fijo y único
        // lógica ya hecha -> update drawer una vez -> render
        if (drawerDirty) {
            drawer.update();
            drawerDirty = false;
        }

        drawer.renderFrame(now);
        drawer.renderHud();

        if (SkinManager.get().current().needArrow()) {
            drawer.renderArrow(now);
        }
    }

    private void onRepeatTick(long now) {
        if (!readKeys) return;
        if (inputRepeat == null) return;

        boolean executed = inputRepeat.handleRepeatTick(now);
        if (executed) {
            // NO drawer.update aquí: solo marcar dirty.
            drawerDirty = true;
        }
    }

    // ---- fin de juego ----

    private void handleGameEnded() {
        if (!readKeys) return;

        readKeys = false;

        stopRuntime();

        sm.setMuted(true);
        sm.stopBgm();

        if (root != null && root.getScene() != null) {
            LanguageManager.switchToEndView(root.getScene(), simulator);
        }
    }

    private void stopRuntime() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }

        if (root != null) {
            root.setOnKeyPressed(null);
            root.setOnKeyReleased(null);
        }
    }

    // ---- UI helpers (Blai) ----

    private void addClass(Label n, String cls) {
        if (n == null) return;
        if (!n.getStyleClass().contains(cls)) n.getStyleClass().add(cls);
    }

    private void removeClass(Label n, String cls) {
        if (n == null) return;
        n.getStyleClass().remove(cls);
    }

    private void activeBlaiGlassesPower(long remainingNs) {
        if (blaiGlassesPowerText == null) return;

        addClass(blaiGlassesPowerText, "blai-glasses-power");
        removeClass(blaiGlassesPowerText, "hidden");

        long remainingSec = (remainingNs + 999_999_999L) / 1_000_000_000L;

        double dis = simulator.getBlaiNumber();

        String distance;
        if (dis != -1) {
            if (simulator.getInventory().has(PowerType.KEY))
                distance = LanguageManager.tr("blai.glasses.exitDistance", dis);
            else
                distance = LanguageManager.tr("blai.glasses.keyDistance", dis);
        } else {
            distance = LanguageManager.tr("blai.glasses.nerf");
        }

        String title = LanguageManager.tr("blai.glasses.title");
        String timeTxt = LanguageManager.tr("blai.glasses.time", remainingSec);

        blaiGlassesPowerText.setText(title + "\n" + distance + "\n" + timeTxt);
    }

    private void deactivateBlaiGlassesPower() {
        if (blaiGlassesPowerText == null) return;

        addClass(blaiGlassesPowerText, "hidden");
        removeClass(blaiGlassesPowerText, "blai-glasses-power");
    }
}
