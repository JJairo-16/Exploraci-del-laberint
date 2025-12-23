package com.jairo.app;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.SimulatorLoader;
import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.i18n.LanguageManager;
import com.jairo.app.input.HeldItemTuningAdjuster;
import com.jairo.app.input.InputHandler;
import com.jairo.app.ui.Dimensions;
import com.jairo.items.PowerType;
import com.jairo.models.Board;
import com.jairo.models.Inventory;
import com.jairo.services.Simulator;
import com.jairo.utils.KeyBind;

import javafx.animation.AnimationTimer;
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
    private Simulator simulator;

    @FXML
    private HBox root;
    @FXML
    private StackPane leftPane;

    @FXML
    private Canvas mapCanvas;
    @FXML
    private Canvas entitiesCanvas;
    @FXML
    private Canvas postFxCanvas;
    @FXML
    private Canvas hudCanvas;

    @FXML
    private VBox rightPanel;
    @FXML
    private Label bottomText;

    @FXML
    private ChoiceBox<String> languageSelector;

    @FXML
    private Label blaiGlassesPowerText;
    private static final int BLAI_GLASSES_NERF = 20;

    private final Dimensions dims = new Dimensions();
    private final ImageStore images = ImageStore.getInstance();
    private final SoundManager sm = SoundManager.get();

    private InputHandler input;
    private Drawer drawer;

    private AnimationTimer renderTimer;
    private static final long FRAME_NS = 33_000_000L;
    private long last = 0;

    private Drawer.CameraState pendingCameraState;

    public void initState(Simulator simulator, Drawer.CameraState cameraState) {
        this.simulator = simulator;
        this.pendingCameraState = cameraState;

        if (log.isDebugEnabled()) {
            log.debug("initState called. simulatorPresent={}, cameraStatePresent={}",
                    simulator != null, cameraState != null);
        }
    }

    // --- Tecles mantingudes (estable) ---
    private final Set<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);
    private KeyBind.Action activeMoveAction = null;

    private AnimationTimer moveTimer;
    private long nextRepeatNs = 0L;

    private static final long INITIAL_DELAY_NS = 240_000_000L; // 0.24s
    private static final long REPEAT_EVERY_NS = 240_000_000L; // 0.24s

    private static final Double SPRINTING_SPEED = 0.55;
    private boolean sprinting = false;

    private boolean isActionHeld(KeyBind.Action action) {
        if (action == null)
            return false;
        for (KeyCode k : pressed) {
            if (KeyBind.getAction(k) == action)
                return true;
        }
        return false;
    }

    private KeyBind.Action findAnyHeldMaintainableAction() {
        for (KeyCode k : pressed) {
            KeyBind.Action a = KeyBind.getAction(k);
            if (a != null && a.canMaintain)
                return a;
        }
        return null;
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            if (log.isDebugEnabled())
                log.debug("Controller initialize() scheduled on FX thread");

            if (simulator == null) {
                simulator = SimulatorLoader.load();
                if (log.isInfoEnabled())
                    log.info("Simulator loaded via SimulatorLoader.");
            } else {
                if (log.isInfoEnabled())
                    log.info("Simulator injected via initState().");
            }

            sm.preload(Sound.values());

            sm.playBgmLoop(Sound.THEME.path());
            sm.setMasterVolume(0.9);
            sm.setBgmVolume(0.30);
            sm.setMuted(false);

            if (log.isDebugEnabled()) {
                log.debug("Audio configured. theme={}, masterVol={}, bgmVol={}, muted={}",
                        Sound.THEME.path(), 0.9, 0.30, false);
            }

            images.preloadAll();
            if (log.isInfoEnabled())
                log.info("Images preloaded.");

            root.setFocusTraversable(true);
            root.requestFocus();

            dims.bindPanels(root, rightPanel, leftPane);

            dims.recalcAndResize(leftPane, Board.BOARD_WIDTH, Board.BOARD_HEIGHT, mapCanvas, entitiesCanvas, hudCanvas,
                    postFxCanvas);

            if (log.isDebugEnabled()) {
                log.debug(
                        "Layout ready: leftPane={}x{}, mapCanvas={}x{}, entitiesCanvas={}x{}, hudCanvas={}x{}, tileSize={}",
                        leftPane.getWidth(), leftPane.getHeight(),
                        mapCanvas.getWidth(), mapCanvas.getHeight(),
                        entitiesCanvas.getWidth(), entitiesCanvas.getHeight(),
                        hudCanvas.getWidth(), hudCanvas.getHeight(),
                        dims.getTileSize());
            }

            if (languageSelector != null) {
                languageSelector.getItems().setAll(LanguageManager.getDisplayNames());
                languageSelector.setValue(LanguageManager.getCurrentDisplayName());

                if (log.isDebugEnabled()) {
                    log.debug("Language selector initialized. current='{}'", languageSelector.getValue());
                }

                languageSelector.setOnAction(e -> {
                    String selected = languageSelector.getValue();
                    String code = LanguageManager.getCodeFromDisplayName(selected);

                    if (log.isInfoEnabled()) {
                        log.info("Language change requested. display='{}' code='{}'", selected, code);
                    }

                    if (code != null
                            && languageSelector.getScene() != null
                            && drawer != null
                            && simulator != null) {

                        Drawer.CameraState cameraState = drawer.getCameraState();

                        LanguageManager.changeLanguageAndReloadMain(
                                languageSelector.getScene(),
                                code,
                                simulator,
                                cameraState);
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Language change ignored. codePresent={} scenePresent={} drawerPresent={} simulatorPresent={}",
                                    code != null,
                                    languageSelector.getScene() != null,
                                    drawer != null,
                                    simulator != null);
                        }
                    }
                });
            } else {
                if (log.isWarnEnabled())
                    log.warn("languageSelector is null (FXML injection failed?).");
            }

            input = new InputHandler(simulator, bottomText, () -> {
                readKeys = simulator.getContinue();
                if (!readKeys && root.getScene() != null) {
                    if (log.isInfoEnabled())
                        log.info("Game ended. Switching to EndView.");
                    shutdownControllerLogic();
                    sm.setMuted(true);
                    sm.stopBgm();
                    LanguageManager.switchToEndView(root.getScene(), simulator);
                }
            });

            drawer = new Drawer(mapCanvas, entitiesCanvas, postFxCanvas, hudCanvas, simulator, dims.getTileSize());
            if (pendingCameraState != null) {
                if (log.isDebugEnabled())
                    log.debug("Applying pending camera state: {}", pendingCameraState);
                drawer.setCameraState(pendingCameraState);
                pendingCameraState = null;
            }

            drawer.update();
            simulator.loadDrawer(drawer);

            renderTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!readKeys)
                        return;

                    if (now - last < FRAME_NS)
                        return;

                    // Update por frame del poder
                    if (simulator.isBlaiGlassesPowerActive()) {
                        long remainingBlaiGlassesPower = simulator.getRemainingBlaiGlassesPower();

                        long dt = (last == 0) ? 0 : (now - last);
                        simulator.updateRemainingBlaiGlassesPower(Math.max(0L, remainingBlaiGlassesPower - dt));

                        activeBlaiGlassesPower(remainingBlaiGlassesPower);

                        if (remainingBlaiGlassesPower == 0L) {
                            simulator.offBlaiGlasses();
                            deactivateBlaiGlassesPower();
                        }
                    } else {
                        deactivateBlaiGlassesPower();
                    }

                    last = now;

                    // Esto hará que los items "floten" siempre
                    drawer.renderFrame(now);
                    drawer.renderHud();

                    // Flecha opcional
                    if (SkinManager.get().current().needArrow()) {
                        drawer.renderArrow(now);
                    }
                }
            };
            renderTimer.start();

            // Temporitzador que fa el repeat CONTROLAT (no el del SO)
            moveTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!readKeys)
                        return;

                    if (activeMoveAction == null)
                        return;

                    if (!isActionHeld(activeMoveAction))
                        return;

                    if (now < nextRepeatNs)
                        return;

                    if (!activeMoveAction.canMaintain)
                        return;

                    input.runAction(activeMoveAction);
                    drawer.update();

                    long interval = (long) (REPEAT_EVERY_NS
                            * (sprinting && simulator.getCurrentAction().isAMovement ? SPRINTING_SPEED : 1.0));
                    nextRepeatNs = now + interval;
                }
            };
            moveTimer.start();

            root.setOnKeyPressed(event -> {
                if (!readKeys) {
                    if (log.isTraceEnabled())
                        log.trace("KeyPressed ignored (readKeys=false)");
                    return;
                }

                KeyCode key = event.getCode();

                boolean wasDown = pressed.contains(key);
                pressed.add(key);

                if (key == KeyCode.SHIFT) {
                    sprinting = true;
                    return;
                }

                KeyBind.Action action = KeyBind.getAction(key);
                if (action == null)
                    return;

                // Mantenibles: es gestionen amb el temporitzador
                if (action.canMaintain) {
                    // Només al primer press real
                    if (!wasDown && activeMoveAction != action) {
                        activeMoveAction = action;

                        if (log.isTraceEnabled())
                            log.trace("Move start. key={} action={}", key, action);

                        input.runAction(action);
                        drawer.update();

                        long now = System.nanoTime();
                        long delay = (long) (INITIAL_DELAY_NS
                                * (sprinting && simulator.getCurrentAction().isAMovement ? SPRINTING_SPEED : 1.0));
                        nextRepeatNs = now + delay;
                    }
                    return;
                }
            });

            root.setOnKeyReleased(event -> {
                if (!readKeys) {
                    if (log.isTraceEnabled())
                        log.trace("KeyReleased ignored (readKeys=false)");
                    return;
                }

                KeyCode key = event.getCode();
                Inventory inv = simulator.getInventory();
                HeldItemTuningAdjuster.adjust(key, inv, (PowerType) inv.getSelectedPower());

                pressed.remove(key);

                if (key == KeyCode.SHIFT) {
                    sprinting = false;
                    return;
                }

                KeyBind.Action action = KeyBind.getAction(key);
                if (action == null)
                    return;

                // Si has deixat anar la tecla activa, busca una altra mantenible que continuï
                // premuda
                if (action == activeMoveAction && !isActionHeld(activeMoveAction)) {
                    KeyBind.Action prev = activeMoveAction;
                    activeMoveAction = findAnyHeldMaintainableAction();
                    long base = (long) (INITIAL_DELAY_NS * action.cooldownMultiplier);
                    long delay = (long) (base
                            * (sprinting && simulator.getCurrentAction().isAMovement ? SPRINTING_SPEED : 1.0));

                    boolean use = action == KeyBind.Action.USE;
                    boolean power = simulator.getInventory().containsPower(simulator.getLastPower());
                    delay *= use && power ? 1.5 : 1;

                    nextRepeatNs = System.nanoTime() + delay;

                    if (log.isTraceEnabled()) {
                        log.trace("Move stop/switch. releasedKey={} prevAction={} newAction={}", key, prev,
                                activeMoveAction);
                    }
                }

                // Mantenibles
                if (action.canMaintain) {
                    return;
                }

                // Accions “d’un sol toc”
                input.handleKeyReleased(key);
                drawer.update();
            });

            if (log.isInfoEnabled())
                log.info("Controller ready.");
        });
    }

    private void shutdownControllerLogic() {
        readKeys = false;

        if (moveTimer != null) {
            moveTimer.stop();
            if (log.isDebugEnabled())
                log.debug("moveTimer stopped.");
        }

        if (root != null) {
            root.setOnKeyPressed(null);
            root.setOnKeyReleased(null);
            if (log.isDebugEnabled())
                log.debug("Key handlers detached.");
        }

        if (renderTimer != null) {
            renderTimer.stop();
        }
    }

    private void addClass(Label n, String cls) {
        if (n == null)
            return;
        if (!n.getStyleClass().contains(cls))
            n.getStyleClass().add(cls);
    }

    private void removeClass(Label n, String cls) {
        if (n == null)
            return;
        n.getStyleClass().remove(cls);
    }

    private void activeBlaiGlassesPower(long remainingNs) {
        if (blaiGlassesPowerText == null)
            return;

        addClass(blaiGlassesPowerText, "blai-glasses-power");
        removeClass(blaiGlassesPowerText, "hidden");

        long remainingSec = (remainingNs + 999_999_999L) / 1_000_000_000L; // ceil

        Simulator.Position playerPos = simulator.getPlayerPosition();
        int playerX = playerPos.x();
        int playerY = playerPos.y();

        Board board = simulator.getBoardRef();
        int exitX = board.getExitX();
        int exitY = board.getExitY();

        double dx = (double) exitX - playerX;
        double dy = (double) exitY - playerY;

        double dis = Math.hypot(dx, dy); // distancia en tiles
        double dis2 = Math.round(dis * 100.0) / 100.0;

        String title = LanguageManager.tr("blai.glasses.title");

        String distance;
        if (dis2 >= BLAI_GLASSES_NERF) {
            distance = LanguageManager.tr("blai.glasses.distance", dis2);
        } else {
            distance = LanguageManager.tr("blai.glasses.nerf");
        }

        String time = LanguageManager.tr("blai.glasses.time", remainingSec);

        String text = title + "\n" + distance + "\n" + time;

        blaiGlassesPowerText.setText(text);
    }

    private void deactivateBlaiGlassesPower() {
        if (blaiGlassesPowerText == null)
            return;

        addClass(blaiGlassesPowerText, "hidden");
        removeClass(blaiGlassesPowerText, "blai-glasses-power");
    }

}
