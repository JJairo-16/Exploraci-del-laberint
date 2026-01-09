package com.jairo.app.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioConfigLoader {
    private AudioConfigLoader() {
    }

    // Defaults (UI 0..1)
    public static final double DEFAULT_MASTER_UI = 0.90;
    public static final double DEFAULT_BGM_UI = 0.60;
    public static final double DEFAULT_SFX_UI = 0.80;
    public static final boolean DEFAULT_MUTED = false;

    /**
     * Carga desde un archivo (writable). Si no existe o falla,
     * devuelve defaults.
     */
    public static AudioConfig loadPreferFile(Path filePath) {
        try {
            if (filePath != null && Files.exists(filePath)) {
                String json = Files.readString(filePath, StandardCharsets.UTF_8);
                return getOrDefault(mapper().readValue(json, RootConfig.class));
            }
        } catch (Exception ignore) {
            // archivo corrupto / JSON inválido -> defaults
        }
        return AudioConfig.defaults();
    }

    /**
     * Guarda la config en el archivo.
     * Formato:
     * { "audio": { ... } }
     */
    public static void saveToFile(Path filePath, AudioConfig cfg) {
        if (filePath == null)
            throw new IllegalArgumentException("filePath is null");
        if (cfg == null)
            cfg = AudioConfig.defaults();

        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            RootConfig root = new RootConfig();
            root.audio = new AudioSection();
            root.audio.master = quantize01(cfg.masterUi);
            root.audio.bgm = quantize01(cfg.bgmUi);
            root.audio.sfx = quantize01(cfg.sfxUi);
            root.audio.muted = cfg.muted;

            String out = mapper().writeValueAsString(root);
            Files.writeString(filePath, out + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error guardando audio config en: " + filePath, e);
        }
    }

    private static double quantize01(double v) {
        v = clamp01(v);
        return Math.round(v * 100.0) / 100.0;
    }

    /** Convierte RootConfig parcial -> AudioConfig con defaults */
    public static AudioConfig getOrDefault(RootConfig root) {
        if (root == null || root.audio == null)
            return AudioConfig.defaults();

        AudioSection a = root.audio;

        double master = quantize01(orDefault(a.master, DEFAULT_MASTER_UI));
        double bgm = quantize01(orDefault(a.bgm, DEFAULT_BGM_UI));
        double sfx = quantize01(orDefault(a.sfx, DEFAULT_SFX_UI));
        boolean muted = (a.muted != null) ? a.muted : DEFAULT_MUTED;

        return new AudioConfig(master, bgm, sfx, muted);
    }

    private static double orDefault(Double v, double def) {
        return (v != null) ? v : def;
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    private static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        return m;
    }

    // ========= DTOs =========

    public static class RootConfig {
        public AudioSection audio;
    }

    public static class AudioSection {
        public Double master;
        public Double bgm;
        public Double sfx;
        public Boolean muted;
    }

    // ========= Modelo final =========

    public static final class AudioConfig {
        public final double masterUi;
        public final double bgmUi;
        public final double sfxUi;
        public final boolean muted;

        public AudioConfig(double masterUi, double bgmUi, double sfxUi, boolean muted) {
            this.masterUi = masterUi;
            this.bgmUi = bgmUi;
            this.sfxUi = sfxUi;
            this.muted = muted;
        }

        public static AudioConfig defaults() {
            return new AudioConfig(DEFAULT_MASTER_UI, DEFAULT_BGM_UI, DEFAULT_SFX_UI, DEFAULT_MUTED);
        }
    }
}
