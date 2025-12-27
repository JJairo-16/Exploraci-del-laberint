package com.jairo.app.settings;

import java.util.Objects;
import java.util.function.Consumer;

import com.jairo.app.audio.ConfigHelper;
import com.jairo.app.audio.SoundManager;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;

public final class SettingsOverlay {

    // Valores UI (0..1) por defecto
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

    private Runnable pauseGame = () -> {};
    private Runnable resumeGame = () -> {};
    private Runnable refocusRoot = () -> {};

    private boolean open = false;

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
            Runnable pauseGame,
            Runnable resumeGame,
            Runnable refocusRoot
    ) {
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

        if (pauseGame != null) this.pauseGame = pauseGame;
        if (resumeGame != null) this.resumeGame = resumeGame;
        if (refocusRoot != null) this.refocusRoot = refocusRoot;

        // Overlay hidden por defecto
        if (this.overlay != null) {
            this.overlay.setVisible(false);
            this.overlay.setManaged(false);
        }

        // UI defaults
        if (this.masterSlider != null) this.masterSlider.setValue(DEFAULT_MASTER_UI);
        if (this.bgmSlider != null) this.bgmSlider.setValue(DEFAULT_BGM_UI);
        if (this.sfxSlider != null) this.sfxSlider.setValue(DEFAULT_SFX_UI);

        // Percent labels (90%, 60%, ...)
        bindPercentLabel(this.masterSlider, this.masterPct);
        bindPercentLabel(this.bgmSlider, this.bgmPct);
        bindPercentLabel(this.sfxSlider, this.sfxPct);

        // Bind sliders -> SoundManager (con curva ConfigHelper)
        bindVolumeSlider(this.masterSlider, sm::setMasterVolume);
        bindVolumeSlider(this.bgmSlider, sm::setBgmVolume);
        bindVolumeSlider(this.sfxSlider, sm::setSfxVolume);

        if (this.muteCheck != null) {
            this.muteCheck.setSelected(DEFAULT_MUTED);
            this.muteCheck.selectedProperty().addListener((obs, o, v) -> sm.setMuted(v));
        }

        // Aplicar defaults al motor también
        applyDefaultsToEngine();
    }

    public void toggle() {
        if (open) close();
        else open();
    }

    public void open() {
        open = true;
        pauseGame.run();

        if (overlay != null) {
            overlay.setManaged(true);
            overlay.setVisible(true);
            overlay.toFront();
        }

        if (closeBtn != null) closeBtn.requestFocus();
    }

    public void close() {
        open = false;

        if (overlay != null) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }

        resumeGame.run();
        refocusRoot.run();
    }

    public void resetToDefaults() {
        // UI
        if (masterSlider != null) masterSlider.setValue(DEFAULT_MASTER_UI);
        if (bgmSlider != null) bgmSlider.setValue(DEFAULT_BGM_UI);
        if (sfxSlider != null) sfxSlider.setValue(DEFAULT_SFX_UI);
        if (muteCheck != null) muteCheck.setSelected(DEFAULT_MUTED);

        // Engine
        applyDefaultsToEngine();

        if (resetBtn != null) resetBtn.requestFocus();
    }

    private void applyDefaultsToEngine() {
        sm.setMasterVolume(ConfigHelper.getVolum(DEFAULT_MASTER_UI));
        sm.setBgmVolume(ConfigHelper.getVolum(DEFAULT_BGM_UI));
        sm.setSfxVolume(ConfigHelper.getVolum(DEFAULT_SFX_UI));
        sm.setMuted(DEFAULT_MUTED);
    }

    private void bindPercentLabel(Slider slider, Label label) {
        if (slider == null || label == null) return;
        label.textProperty().bind(Bindings.format("%.0f%%", slider.valueProperty().multiply(100)));
        label.setMouseTransparent(true);
    }

    private void bindVolumeSlider(Slider slider, Consumer<Double> setter) {
        if (slider == null || setter == null) return;

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double mapped = ConfigHelper.getVolum(newVal.doubleValue());

            // Si quieres que el thumb “salte” al valor mapeado (como pediste):
            if (Math.abs(mapped - newVal.doubleValue()) > EPS) {
                slider.setValue(mapped);
            }

            setter.accept(mapped);
        });
    }
}
