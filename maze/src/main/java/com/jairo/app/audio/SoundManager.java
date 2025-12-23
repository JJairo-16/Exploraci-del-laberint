package com.jairo.app.audio;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    // Grups d'SFX (ex: "steps", "doors") -> set de resourcePaths
    private final Map<String, Set<String>> sfxGroups = new ConcurrentHashMap<>();

    private final Map<String, Long> sfxArtificialDelayUntil = new ConcurrentHashMap<>();

    private MediaPlayer bgmPlayer;
    private String currentBgmPath;

    private volatile boolean muted = false;
    private volatile double masterVolume = 1.0; // 0..1
    private volatile double bgmVolume = 0.45; // 0..1 (relatiu)
    private volatile double sfxVolume = 0.8; // 0..1 (relatiu)

    private SoundManager() {
    }

    /*
     * =======================
     * Configuració global
     * =======================
     */

    public void setMuted(boolean muted) {
        this.muted = muted;
        Platform.runLater(() -> {
            if (bgmPlayer != null)
                bgmPlayer.setMute(muted);
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

    /*
     * =======================
     * Música de fons (BGM)
     * =======================
     */

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
                    if (log.isDebugEnabled())
                        log.debug("Resumed BGM: {}", resourcePath);
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
            if (log.isInfoEnabled())
                log.info("Started BGM loop: {}", resourcePath);
        });
    }

    public void pauseBgm() {
        Platform.runLater(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.pause();
                if (log.isDebugEnabled())
                    log.debug("Paused BGM");
            }
        });
    }

    public void resumeBgm() {
        Platform.runLater(() -> {
            if (bgmPlayer != null) {
                bgmPlayer.play();
                if (log.isDebugEnabled())
                    log.debug("Resumed BGM");
            }
        });
    }

    public void stopBgm() {
        Platform.runLater(() -> {
            stopBgmInternal();
            if (log.isInfoEnabled())
                log.info("Stopped BGM");
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

    /*
     * =======================
     * Efectes (SFX)
     * =======================
     */

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
        if (muted)
            return;

        AudioClip clip = getOrLoadSfx(resourcePath);
        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        clip.play(v);
    }

    /**
     * Reprodueix un SFX amb control de solapament:
     * - allowSelfOverlap: si false, no torna a reproduir el mateix so si ja està
     * sonant
     * - mustNotOverlapGroups: si algun so de qualsevol d'aquests grups està sonant,
     * NO el reprodueix
     *
     * Ex:
     * playSfx("/sfx/door_try.wav", 1.0, false, "doors");
     * playSfx("/sfx/step.wav", 0.7, true, "doors", "ui");
     */
    public void playSfx(String resourcePath, double volumeMultiplier, boolean allowSelfOverlap,
            String... mustNotOverlapGroups) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        if (muted)
            return;

        AudioClip clip = getOrLoadSfx(resourcePath);

        if (!allowSelfOverlap && clip.isPlaying()) {
            return;
        }

        if (mustNotOverlapGroups != null && mustNotOverlapGroups.length > 0) {
            for (String group : mustNotOverlapGroups) {
                if (group == null)
                    continue;
                if (isAnyGroupPlaying(group)) {
                    return;
                }
            }
        }

        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        clip.play(v);
    }

    /*
     * =======================
     * Grups d'SFX
     * =======================
     */

    public void defineGroup(String groupName, String... resourcePaths) {
        Objects.requireNonNull(groupName, "groupName");
        Set<String> set = sfxGroups.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet());
        if (resourcePaths != null) {
            for (String p : resourcePaths) {
                if (p != null)
                    set.add(p);
            }
        }
    }

    public void addToGroup(String groupName, String resourcePath) {
        Objects.requireNonNull(groupName, "groupName");
        Objects.requireNonNull(resourcePath, "resourcePath");
        sfxGroups.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet()).add(resourcePath);
    }

    public void removeFromGroup(String groupName, String resourcePath) {
        if (groupName == null || resourcePath == null)
            return;
        Set<String> set = sfxGroups.get(groupName);
        if (set != null)
            set.remove(resourcePath);
    }

    public void clearGroups() {
        sfxGroups.clear();
        if (log.isDebugEnabled())
            log.debug("Cleared SFX groups");
    }

    public boolean isAnyGroupPlaying(String groupName) {
        if (groupName == null)
            return false;
        Set<String> set = sfxGroups.get(groupName);
        if (set == null || set.isEmpty())
            return false;

        for (String path : set) {
            if (path == null)
                continue;
            AudioClip other = sfxCache.get(path);
            if (other != null && other.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    /** Neteja la memòria cau d'SFX (si vols alliberar memòria) */
    public void clearSfxCache() {
        sfxCache.clear();
        if (log.isDebugEnabled())
            log.debug("Cleared SFX cache");
    }

    /**
     * Alliberar-ho tot (per exemple en tancar el joc/app).
     */
    public void dispose() {
        stopBgm();
        clearSfxCache();
        clearGroups();
        if (log.isInfoEnabled())
            log.info("SoundManager disposed");
    }

    /*
     * =======================
     * Helpers
     * =======================
     */

    private AudioClip getOrLoadSfx(String resourcePath) {
        return sfxCache.computeIfAbsent(resourcePath, path -> {
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
    }

    private static double clamp01(double v) {
        if (v < 0.0)
            return 0.0;
        if (v > 1.0)
            return 1.0;
        return v;
    }

    public boolean isSfxPlaying(String resourcePath) {
        if (resourcePath == null)
            return false;
        AudioClip clip = sfxCache.get(resourcePath);
        return clip != null && clip.isPlaying();
    }

    public boolean isGroupPlaying(String groupName) {
        return isAnyGroupPlaying(groupName);
    }

    public boolean isSfxOrGroupPlaying(String resourcePath, String groupName) {
        if (isSfxPlaying(resourcePath))
            return true;
        return (groupName != null && isAnyGroupPlaying(groupName));
    }

    public void playSfxWithTailDelay(
            String resourcePath,
            double volumeMultiplier,
            boolean allowSelfOverlap,
            long tailDelayMs,
            String... mustNotOverlapGroups) {

        Objects.requireNonNull(resourcePath, "resourcePath");
        if (muted)
            return;

        AudioClip clip = getOrLoadSfx(resourcePath);

        if (!allowSelfOverlap && isSfxPlaying(resourcePath)) {
            return;
        }

        if (mustNotOverlapGroups != null) {
            for (String group : mustNotOverlapGroups) {
                if (isAnyGroupPlaying(group)) {
                    return;
                }
            }
        }

        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        clip.play(v);

        if (tailDelayMs > 0) {
            long until = System.nanoTime() + tailDelayMs * 1_000_000L;
            sfxArtificialDelayUntil.put(resourcePath, until);
        }
    }

    public void preloadSfx(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        getOrLoadSfx(resourcePath);
    }

    public void preloadSfx(String... resourcePaths) {
        if (resourcePaths == null)
            return;

        for (String path : resourcePaths) {
            if (path != null) {
                preloadSfx(path);
            }
        }
    }

    public void preload(Sound... sounds) {
        if (sounds == null)
            return;
        for (Sound s : sounds) {
            if (s != null) {
                getOrLoadSfx(s.path());
            }
        }
    }

    public void stopSfx(String resourcePath) {
        AudioClip clip = sfxCache.get(resourcePath);
        if (clip != null) {
            clip.stop();
        }
    }

    public void stopGroup(String groupName) {
        Set<String> set = sfxGroups.get(groupName);
        if (set == null)
            return;

        for (String path : set) {
            AudioClip clip = sfxCache.get(path);
            if (clip != null) {
                clip.stop();
            }
        }
    }

}
