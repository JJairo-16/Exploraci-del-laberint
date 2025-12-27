package com.jairo.app.settings;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import com.jairo.app.audio.ConfigHelper;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.settings.AudioConfigLoader.AudioConfig;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;

public final class SettingsOverlay {

    // Defaults UI (0..1)
    private static final double DEFAULT_MASTER_UI = 0.90;
    private static final double DEFAULT_BGM_UI = 0.60;
    private static final double DEFAULT_SFX_UI = 0.80;
    private static final boolean DEFAULT_MUTED = false;

    private static final double EPS = 0.0001;

    private final SoundManager sm;

    private StackPane overlay;

    private Slider masterSlider;
    private Slider bgmSlider;
    private Slider sfxSlider;

    private Label masterPct;
    private Label bgmPct;
    private Label sfxPct;

    private CheckBox muteCheck;

    private Button closeBtn;
    private Button resetBtn;
    private Button saveBtn;

    private Runnable pauseGame = () -> {
    };
    private Runnable resumeGame = () -> {
    };
    private Runnable refocusRoot = () -> {
    };

    private boolean open = false;

    // --- AppData ---
    private static final String APP_NAME = "JairoApp"; // <-- cambia esto si quieres
    private static final Path SETTINGS_FILE = getAppDataConfigPath(APP_NAME, "config.json");

    // ---- Aplicados ----
    private double appliedMasterUi = DEFAULT_MASTER_UI;
    private double appliedBgmUi = DEFAULT_BGM_UI;
    private double appliedSfxUi = DEFAULT_SFX_UI;
    private boolean appliedMuted = DEFAULT_MUTED;

    // ---- Pendientes ----
    private double pendingMasterUi = DEFAULT_MASTER_UI;
    private double pendingBgmUi = DEFAULT_BGM_UI;
    private double pendingSfxUi = DEFAULT_SFX_UI;
    private boolean pendingMuted = DEFAULT_MUTED;

    private boolean internalChange = false;

    private Drawer drawer;

    public SettingsOverlay(SoundManager sm) {
        this.sm = Objects.requireNonNull(sm, "SoundManager");
    }

    public void attach(
            StackPane overlay,
            Slider masterSlider, Label masterPct,
            Slider bgmSlider, Label bgmPct,
            Slider sfxSlider, Label sfxPct,
            CheckBox muteCheck,
            Button closeBtn,
            Button resetBtn,
            Button saveBtn,
            Runnable pauseGame,
            Runnable resumeGame,
            Runnable refocusRoot,
            Drawer drawer) {
        this.overlay = overlay;

        this.masterSlider = masterSlider;
        this.bgmSlider = bgmSlider;
        this.sfxSlider = sfxSlider;

        this.masterPct = masterPct;
        this.bgmPct = bgmPct;
        this.sfxPct = sfxPct;

        this.muteCheck = muteCheck;

        this.closeBtn = closeBtn;
        this.resetBtn = resetBtn;
        this.saveBtn = saveBtn;

        if (pauseGame != null)
            this.pauseGame = pauseGame;
        if (resumeGame != null)
            this.resumeGame = resumeGame;
        if (refocusRoot != null)
            this.refocusRoot = refocusRoot;

        if (this.overlay != null) {
            this.overlay.setVisible(false);
            this.overlay.setManaged(false);
        }

        bindPercentLabel(this.masterSlider, this.masterPct);
        bindPercentLabel(this.bgmSlider, this.bgmPct);
        bindPercentLabel(this.sfxSlider, this.sfxPct);

        this.drawer = drawer;

        installPendingListeners();

        // Cargar aplicados desde AppData (o defaults) + aplicar al motor
        loadSettings();
        loadAppliedIntoUi();
    }

    // =====================
    // Ciclo overlay
    // =====================

    public void toggle() {
        if (open)
            closeDiscard();
        else
            open();

        drawer.darkOverlay(open);
    }

    public void open() {
        open = true;
        pauseGame.run();

        loadAppliedIntoUi();

        if (overlay != null) {
            overlay.setManaged(true);
            overlay.setVisible(true);
            overlay.toFront();
        }

        if (saveBtn != null)
            saveBtn.requestFocus();
        else if (closeBtn != null)
            closeBtn.requestFocus();
    }

    public void closeDiscard() {
        drawer.darkOverlay(false);
        open = false;

        appliedMasterUi = pendingMasterUi;
        appliedBgmUi = pendingBgmUi;
        appliedSfxUi = pendingSfxUi;
        appliedMuted = pendingMuted;

        applyAppliedToEngine();

        AudioConfigLoader.saveToFile(
                SETTINGS_FILE,
                new AudioConfig(appliedMasterUi, appliedBgmUi, appliedSfxUi, appliedMuted));

        pendingMasterUi = appliedMasterUi;
        pendingBgmUi = appliedBgmUi;
        pendingSfxUi = appliedSfxUi;
        pendingMuted = appliedMuted;

        if (overlay != null) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }

        resumeGame.run();
        refocusRoot.run();
    }

    /** Aplica al motor Y guarda en AppData. */
    public void saveApplyAndClose() {
        closeDiscard();
        drawer.darkOverlay(false);
    }

    public void resetPendingToDefaults() {
        setUiValues(DEFAULT_MASTER_UI, DEFAULT_BGM_UI, DEFAULT_SFX_UI, DEFAULT_MUTED);

        pendingMasterUi = DEFAULT_MASTER_UI;
        pendingBgmUi = DEFAULT_BGM_UI;
        pendingSfxUi = DEFAULT_SFX_UI;
        pendingMuted = DEFAULT_MUTED;

        if (resetBtn != null)
            resetBtn.requestFocus();
    }

    // =====================
    // Internals
    // =====================

    private void applyAppliedToEngine() {
        sm.setMasterVolume(ConfigHelper.getVolum(appliedMasterUi));
        sm.setBgmVolume(ConfigHelper.getVolum(appliedBgmUi));
        sm.setSfxVolume(ConfigHelper.getVolum(appliedSfxUi));
        sm.setMuted(appliedMuted);
    }

    private void loadAppliedIntoUi() {
        setUiValues(appliedMasterUi, appliedBgmUi, appliedSfxUi, appliedMuted);

        pendingMasterUi = appliedMasterUi;
        pendingBgmUi = appliedBgmUi;
        pendingSfxUi = appliedSfxUi;
        pendingMuted = appliedMuted;
    }

    private void setUiValues(double masterUi, double bgmUi, double sfxUi, boolean muted) {
        internalChange = true;
        try {
            if (masterSlider != null)
                masterSlider.setValue(masterUi);
            if (bgmSlider != null)
                bgmSlider.setValue(bgmUi);
            if (sfxSlider != null)
                sfxSlider.setValue(sfxUi);
            if (muteCheck != null)
                muteCheck.setSelected(muted);
        } finally {
            internalChange = false;
        }
    }

    private void installPendingListeners() {
        if (masterSlider != null) {
            masterSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (internalChange)
                    return;
                pendingMasterUi = normalizeUiSlider(masterSlider, newVal.doubleValue());
            });
        }

        if (bgmSlider != null) {
            bgmSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (internalChange)
                    return;
                pendingBgmUi = normalizeUiSlider(bgmSlider, newVal.doubleValue());
            });
        }

        if (sfxSlider != null) {
            sfxSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (internalChange)
                    return;
                pendingSfxUi = normalizeUiSlider(sfxSlider, newVal.doubleValue());
            });
        }

        if (muteCheck != null) {
            muteCheck.selectedProperty().addListener((obs, o, v) -> {
                if (internalChange)
                    return;
                pendingMuted = v;
            });
        }
    }

    private double normalizeUiSlider(Slider slider, double ui) {
        double mapped = ConfigHelper.getVolum(ui);
        if (Math.abs(mapped - ui) > EPS) {
            internalChange = true;
            try {
                slider.setValue(mapped);
            } finally {
                internalChange = false;
            }
            return mapped;
        }
        return ui;
    }

    private void bindPercentLabel(Slider slider, Label label) {
        if (slider == null || label == null)
            return;
        label.textProperty().bind(Bindings.format("%.0f%%", slider.valueProperty().multiply(100)));
        label.setMouseTransparent(true);
    }

    /** Carga desde AppData si existe; si no existe (o falla), defaults. */
    private void loadSettings() {
        AudioConfig cfg = AudioConfigLoader.loadPreferFile(SETTINGS_FILE);

        appliedMasterUi = cfg.masterUi;
        appliedBgmUi = cfg.bgmUi;
        appliedSfxUi = cfg.sfxUi;
        appliedMuted = cfg.muted;

        pendingMasterUi = appliedMasterUi;
        pendingBgmUi = appliedBgmUi;
        pendingSfxUi = appliedSfxUi;
        pendingMuted = appliedMuted;

        applyAppliedToEngine();
    }

    // ------- helper AppData -------
    private static Path getAppDataConfigPath(String appName, String fileName) {
        String appData = System.getenv("APPDATA"); // Roaming
        if (appData == null || appData.isBlank()) {
            // fallback por si no existe APPDATA (raro en Windows, pero posible)
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, appName, fileName);
    }
}
