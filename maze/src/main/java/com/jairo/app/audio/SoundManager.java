package com.jairo.app.audio;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SoundManager (JavaFX)
 * - BGM: MediaPlayer (playlist)
 * - SFX simultanis: AudioClip
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

    // ===== BGM playlist state =====
    public enum RepeatMode {
        NONE, ONE, ALL
    }

    private MediaPlayer bgmPlayer;

    /**
     * Ruta REAL actualmente cargada en el MediaPlayer (no depende del índice de
     * playlist).
     */
    private String currentBgmPath;

    private final List<String> bgmPlaylist = new ArrayList<>();
    private int bgmIndex = -1;

    private boolean bgmShuffle = false;
    private RepeatMode bgmRepeatMode = RepeatMode.ALL;
    private final Random rng = new Random();

    private volatile boolean muted = false;
    private volatile double masterVolume = 1.0; // 0..1
    private volatile double bgmVolume = 0.45; // 0..1
    private volatile double sfxVolume = 0.8; // 0..1

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

        if (muted) {
            // Si muteas, para loops persistentes (como el slide)
            stopIceSlideLoop();
        }

        if (log.isDebugEnabled())
            log.debug("Mute set to {}", muted);
    }

    public boolean isMuted() {
        return muted;
    }

    /** Volum mestre (0..1). Afecta la BGM i els SFX. */
    public void setMasterVolume(double volume) {
        this.masterVolume = quantize01(volume);
        Platform.runLater(this::applyBgmVolume);

        // Nota: AudioClip no actualiza volumen del loop ya sonando automáticamente.
        // Si quieres, podrías reiniciar loops aquí.

        if (log.isDebugEnabled())
            log.debug("Master volume set to {}", this.masterVolume);
    }

    public double getMasterVolume() {
        return masterVolume;
    }

    public void setBgmVolume(double volume) {
        this.bgmVolume = quantize01(volume);
        Platform.runLater(this::applyBgmVolume);
        if (log.isDebugEnabled())
            log.debug("BGM volume set to {}", this.bgmVolume);
    }

    public double getBgmVolume() {
        return bgmVolume;
    }

    public void setSfxVolume(double volume) {
        this.sfxVolume = quantize01(volume);
        if (log.isDebugEnabled())
            log.debug("SFX volume set to {}", this.sfxVolume);
    }

    public double getSfxVolume() {
        return sfxVolume;
    }

    /*
     * =======================
     * Música de fons (BGM) - PLAYLIST
     * =======================
     */

    public void setBgmPlaylist(List<String> resourcePaths, boolean startAtFirst) {
        Objects.requireNonNull(resourcePaths, "resourcePaths");
        Platform.runLater(() -> {
            bgmPlaylist.clear();
            for (String p : resourcePaths) {
                if (p != null && !p.isBlank())
                    bgmPlaylist.add(p);
            }
            bgmIndex = bgmPlaylist.isEmpty() ? -1 : 0;

            if (log.isInfoEnabled())
                log.info("BGM playlist set ({} tracks)", bgmPlaylist.size());

            if (startAtFirst && bgmIndex >= 0) {
                playBgm();
            } else {
                stopBgmInternal();
            }
        });
    }

    public void addToBgmPlaylist(String... resourcePaths) {
        if (resourcePaths == null)
            return;
        Platform.runLater(() -> {
            for (String p : resourcePaths) {
                if (p != null && !p.isBlank())
                    bgmPlaylist.add(p);
            }
            if (bgmIndex < 0 && !bgmPlaylist.isEmpty())
                bgmIndex = 0;
            if (log.isDebugEnabled())
                log.debug("Added tracks to BGM playlist. Total={}", bgmPlaylist.size());
        });
    }

    public List<String> getBgmPlaylistSnapshot() {
        return new ArrayList<>(bgmPlaylist);
    }

    public void setBgmShuffle(boolean shuffle) {
        this.bgmShuffle = shuffle;
        if (log.isDebugEnabled())
            log.debug("BGM shuffle set to {}", shuffle);
    }

    public boolean isBgmShuffle() {
        return bgmShuffle;
    }

    public void setBgmRepeatMode(RepeatMode mode) {
        this.bgmRepeatMode = (mode == null) ? RepeatMode.NONE : mode;
        if (log.isDebugEnabled())
            log.debug("BGM repeat mode set to {}", this.bgmRepeatMode);
    }

    public RepeatMode getBgmRepeatMode() {
        return bgmRepeatMode;
    }

    public void playBgm() {
        Platform.runLater(() -> {
            if (bgmPlaylist.isEmpty()) {
                if (log.isWarnEnabled())
                    log.warn("BGM playlist is empty. Nothing to play.");
                stopBgmInternal();
                return;
            }
            if (bgmIndex < 0)
                bgmIndex = 0;
            startBgmTrack(bgmPlaylist.get(bgmIndex), false);
        });
    }

    public void playBgmFromStart() {
        Platform.runLater(() -> {
            if (bgmPlaylist.isEmpty())
                return;
            if (bgmIndex < 0)
                bgmIndex = 0;
            startBgmTrack(bgmPlaylist.get(bgmIndex), true);
        });
    }

    public void nextBgm() {
        Platform.runLater(() -> advanceBgm(true));
    }

    public void prevBgm() {
        Platform.runLater(() -> {
            if (bgmPlaylist.isEmpty())
                return;

            if (bgmShuffle) {
                bgmIndex = rng.nextInt(bgmPlaylist.size());
                startBgmTrack(bgmPlaylist.get(bgmIndex), true);
                return;
            }

            int prev = bgmIndex - 1;
            if (prev >= 0) {
                bgmIndex = prev;
                startBgmTrack(bgmPlaylist.get(bgmIndex), true);
                return;
            }

            if (bgmRepeatMode == RepeatMode.ALL) {
                bgmIndex = bgmPlaylist.size() - 1;
                startBgmTrack(bgmPlaylist.get(bgmIndex), true);
            } else {
                bgmIndex = 0;
                startBgmTrack(bgmPlaylist.get(bgmIndex), true);
            }
        });
    }

    public int getBgmIndex() {
        return bgmIndex;
    }

    public String getCurrentBgmPath() {
        return currentBgmPath;
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

    private void startBgmTrack(String resourcePath, boolean fromStart) {
        Objects.requireNonNull(resourcePath, "resourcePath");

        if (bgmPlayer != null && resourcePath.equals(currentBgmPath)) {
            if (fromStart) {
                try {
                    bgmPlayer.stop();
                } catch (Exception ignored) {
                }
                bgmPlayer.play();
            } else if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                bgmPlayer.play();
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

        bgmPlayer.setMute(muted);
        applyBgmVolume();

        bgmPlayer.setOnEndOfMedia(() -> Platform.runLater(() -> advanceBgm(false)));

        bgmPlayer.setOnError(() -> {
            log.warn("BGM error on track {}: {}", resourcePath,
                    (bgmPlayer.getError() != null ? bgmPlayer.getError().getMessage() : "unknown"));
            Platform.runLater(() -> advanceBgm(false));
        });

        if (log.isInfoEnabled())
            log.info("Started BGM track: {}", resourcePath);

        bgmPlayer.play();
    }

    private void advanceBgm(boolean userInitiated) {
        if (bgmPlaylist.isEmpty()) {
            stopBgmInternal();
            return;
        }

        if (bgmRepeatMode == RepeatMode.ONE && !userInitiated) {
            startBgmTrack(bgmPlaylist.get(bgmIndex), true);
            return;
        }

        if (bgmShuffle) {
            int next = (bgmPlaylist.size() == 1) ? 0 : rng.nextInt(bgmPlaylist.size());
            bgmIndex = next;
            startBgmTrack(bgmPlaylist.get(bgmIndex), true);
            return;
        }

        int nextIndex = bgmIndex + 1;
        if (nextIndex < bgmPlaylist.size()) {
            bgmIndex = nextIndex;
            startBgmTrack(bgmPlaylist.get(bgmIndex), true);
            return;
        }

        if (bgmRepeatMode == RepeatMode.ALL) {
            bgmIndex = 0;
            startBgmTrack(bgmPlaylist.get(bgmIndex), true);
        } else {
            stopBgmInternal();
        }
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
        } else {
            currentBgmPath = null;
        }
    }

    private void applyBgmVolume() {
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(quantize01(masterVolume * bgmVolume));
        }
    }

    /*
     * =======================
     * Ice Slide Loop (SFX persistente)
     * =======================
     */

    /** Inicia (si no está sonando) el loop de deslizamiento. */
    public void startIceSlideLoop(double volumeMultiplier) {
        if (muted)
            return;

        AudioClip loop = getOrLoadSfx(Sound.ICE_SLIDE_LOOP.path());
        loop.setCycleCount(AudioClip.INDEFINITE);

        if (loop.isPlaying())
            return;

        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        loop.play(v);
    }

    /** Overload con volumen por defecto. */
    public void startIceSlideLoop() {
        startIceSlideLoop(0.55);
    }

    /** Para el loop de deslizamiento si está sonando. */
    public void stopIceSlideLoop() {
        AudioClip loop = sfxCache.get(Sound.ICE_SLIDE_LOOP.path());
        if (loop != null)
            loop.stop();
    }

    /*
     * =======================
     * Efectes (SFX)
     * =======================
     */

    public void playSfx(String resourcePath) {
        playSfx(resourcePath, 1.0);
    }

    public void playSfx(String resourcePath, double volumeMultiplier) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        if (muted)
            return;

        AudioClip clip = getOrLoadSfx(resourcePath);
        double v = clamp01(masterVolume * sfxVolume * Math.max(0.0, volumeMultiplier));
        clip.play(v);
    }

    public void playSfx(String resourcePath, double volumeMultiplier, boolean allowSelfOverlap,
            String... mustNotOverlapGroups) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        if (muted)
            return;

        AudioClip clip = getOrLoadSfx(resourcePath);

        if (!allowSelfOverlap && clip.isPlaying())
            return;

        if (mustNotOverlapGroups != null && mustNotOverlapGroups.length > 0) {
            for (String group : mustNotOverlapGroups) {
                if (group == null)
                    continue;
                if (isAnyGroupPlaying(group))
                    return;
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
            if (other != null && other.isPlaying())
                return true;
        }
        return false;
    }

    public void clearSfxCache() {
        sfxCache.clear();
        if (log.isDebugEnabled())
            log.debug("Cleared SFX cache");
    }

    public void dispose() {
        stopIceSlideLoop();
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

    private static double quantize01(double v) {
        v = clamp01(v);
        return Math.round(v * 100.0) / 100.0;
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

        if (!allowSelfOverlap && isSfxPlaying(resourcePath))
            return;

        if (mustNotOverlapGroups != null) {
            for (String group : mustNotOverlapGroups) {
                if (isAnyGroupPlaying(group))
                    return;
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
            if (path != null)
                preloadSfx(path);
        }
    }

    public void preload(Sound... sounds) {
        if (sounds == null)
            return;
        for (Sound s : sounds) {
            if (s != null)
                getOrLoadSfx(s.path());
        }
    }

    public void stopSfx(String resourcePath) {
        AudioClip clip = sfxCache.get(resourcePath);
        if (clip != null)
            clip.stop();
    }

    public void stopGroup(String groupName) {
        Set<String> set = sfxGroups.get(groupName);
        if (set == null)
            return;

        for (String path : set) {
            AudioClip clip = sfxCache.get(path);
            if (clip != null)
                clip.stop();
        }
    }
}
