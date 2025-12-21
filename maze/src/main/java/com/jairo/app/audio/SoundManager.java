package com.jairo.app.audio;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SoundManager (JavaFX)
 * - Música de fons: MediaPlayer (loop)
 * - SFX simultanis: AudioClip (pot reproduir múltiples alhora)
 *
 * Requisits:
 * - Dependència: javafx-media
 * - Recursos a src/main/resources (ex: /music/theme.mp3, /sfx/click.wav)
 */
public final class SoundManager {

    private static final Logger log = LoggerFactory.getLogger(SoundManager.class);

    private static final SoundManager INSTANCE = new SoundManager();

    public static SoundManager get() {
        return INSTANCE;
    }

    // Memòria cau d'SFX per no recarregar cada vegada
    private final Map<String, AudioClip> sfxCache = new ConcurrentHashMap<>();

    private MediaPlayer bgmPlayer;
    private String currentBgmPath;

    private volatile boolean muted = false;
    private volatile double masterVolume = 1.0;  // 0..1
    private volatile double bgmVolume = 0.45;    // 0..1 (relatiu)
    private volatile double sfxVolume = 0.8;     // 0..1 (relatiu)

    private SoundManager() {}

    /* =======================
       Configuració global
       ======================= */

    public void setMuted(boolean muted) {
        this.muted = muted;
        Platform.runLater(() -> {
            if (bgmPlayer != null) bgmPlayer.setMute(muted);
        });

        if (log.isDebugEnabled()) {
            log.debug("Mute set to {}", muted);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    /** Volum mestre (0..1). Afecta la BGM i els SFX. */
    public void setMasterVolume(double volume) {
        this.masterVolume = clamp01(volume);
        Platform.runLater(this::applyBgmVolume);

        if (log.isDebugEnabled()) {
            log.debug("Master volume set to {}", this.masterVolume);
        }
    }

    public double getMasterVolume() {
        return masterVolume;
    }

    public void setBgmVolume(double volume) {
        this.bgmVolume = clamp01(volume);
        Platform.runLater(this::applyBgmVolume);

        if (log.isDebugEnabled()) {
            log.debug("BGM volume set to {}", this.bgmVolume);
        }
    }

    public double getBgmVolume() {
        return bgmVolume;
    }

    public void setSfxVolume(double volume) {
        this.sfxVolume = clamp01(volume);

        if (log.isDebugEnabled()) {
            log.debug("SFX volume set to {}", this.sfxVolume);
        }
    }

    public double getSfxVolume() {
        return sfxVolume;
    }

    /* =======================
       Música de fons (BGM)
       ======================= */

    /**
     * Selecciona i inicia música de fons en loop.
     * Si ja està sonant aquesta mateixa ruta, no la reinicia.
     *
     * @param resourcePath Ex: "/music/theme.mp3"
     */
    public void playBgmLoop(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");

        Platform.runLater(() -> {
            if (resourcePath.equals(currentBgmPath) && bgmPlayer != null) {
                // Ja és la mateixa
                if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                    bgmPlayer.play();
                    if (log.isDebugEnabled()) log.debug("Resumed BGM: {}", resourcePath);
                }
                return;
            }

            stopBgmInternal();

            URL url = SoundManager.class.getResource(resourcePath);
            if (url == null) {
                log.warn("BGM resource not found: {}", resourcePath);
                throw new IllegalArgumentException("BGM resource not found: " + resourcePath);
            }

            Media media = new Media(url.toExternalForm());
            bgmPlayer = new MediaPlayer(media);
            currentBgmPath = resourcePath;

            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            bgmPlayer.setMute(muted);
            applyBgmVolume();

            bgmPlayer.play();
            if (log.isInfoEnabled()) log.info("Started BGM loop: {}", resourcePath);
        });
    }

    public void pauseBgm() {
        Platform.runLater(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.pause();
                if (log.isDebugEnabled()) log.debug("Paused BGM");
            }
        });
    }

    public void resumeBgm() {
        Platform.runLater(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.play();
                if (log.isDebugEnabled()) log.debug("Resumed BGM");
            }
        });
    }

    public void stopBgm() {
        Platform.runLater(() -> {
            stopBgmInternal();
            if (log.isInfoEnabled()) log.info("Stopped BGM");
        });
    }

    public String getCurrentBgmPath() {
        return currentBgmPath;
    }

    private void stopBgmInternal() {
        if (bgmPlayer != null) {
            try {
                bgmPlayer.stop();
            } catch (Exception e) {
                log.debug("Error while stopping BGM player.", e);
            } finally {
                try {
                    bgmPlayer.dispose();
                } catch (Exception e) {
                    log.debug("Error while disposing BGM player.", e);
                }
                bgmPlayer = null;
                currentBgmPath = null;
            }
        }
    }

    private void applyBgmVolume() {
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(clamp01(masterVolume * bgmVolume));
        }
    }

    /* =======================
       Efectes (SFX)
       ======================= */

    /**
     * Reprodueix un SFX (simultani) fent servir AudioClip (ideal per a efectes).
     *
     * @param resourcePath Ex: "/sfx/click.wav"
     */
    public void playSfx(String resourcePath) {
        playSfx(resourcePath, 1.0);
    }

    /**
     * Reprodueix un SFX amb un multiplicador de volum (0..1+).
     * Ex: playSfx("/sfx/hit.wav", 0.6)
     */
    public void playSfx(String resourcePath, double volumeMultiplier) {
        Objects.requireNonNull(resourcePath, "resourcePath");

        if (muted) return;

        AudioClip clip = sfxCache.computeIfAbsent(resourcePath, path -> {
            URL url = SoundManager.class.getResource(path);
            if (url == null) {
                log.warn("SFX resource not found: {}", path);
                throw new IllegalArgumentException("SFX resource not found: " + path);
            }
            AudioClip c = new AudioClip(url.toExternalForm());
            // Per defecte: sense loop
            c.setCycleCount(1);
            return c;
        });

        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        clip.play(v); // AudioClip permet diverses reproduccions simultànies
    }

    /** Neteja la memòria cau d'SFX (si vols alliberar memòria) */
    public void clearSfxCache() {
        sfxCache.clear();
        if (log.isDebugEnabled()) log.debug("Cleared SFX cache");
    }

    /**
     * Alliberar-ho tot (per exemple en tancar el joc/app).
     */
    public void dispose() {
        stopBgm();
        clearSfxCache();
        if (log.isInfoEnabled()) log.info("SoundManager disposed");
    }

    /* =======================
       Helpers
       ======================= */

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
