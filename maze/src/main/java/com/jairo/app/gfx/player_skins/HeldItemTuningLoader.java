package com.jairo.app.gfx.player_skins;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HeldItemTuningLoader {
    private HeldItemTuningLoader() {}

    // Carga el JSON una vez y devuelve un mapa: id -> HeldItemTuning
    public static Map<String, HeldItemTuning> loadFromResources(String resourceName) {
        try {
            InputStream is = HeldItemTuningLoader.class.getClassLoader().getResourceAsStream(resourceName);
            if (is == null) {
                throw new IllegalStateException("No se encontró el recurso: " + resourceName);
            }

            ObjectMapper mapper = new ObjectMapper();
            List<HeldItemTuningConfig> configs =
                    mapper.readValue(is,
                            mapper.getTypeFactory().constructCollectionType(List.class, HeldItemTuningConfig.class));

            Map<String, HeldItemTuning> map = new HashMap<>();
            for (HeldItemTuningConfig c : configs) {
                map.put(c.id, c.toTuning());
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException("Error cargando held item tuning desde JSON", e);
        }
    }

    // DTO del JSON
    public static class HeldItemTuningConfig {
        public String id;
        public double baseScale;
        public double noCursorScaleMul;
        public double cursorOffsetMulX;
        public double cursorOffsetMulY;
        public double noCursorOffsetMulX;
        public double noCursorOffsetMulY;

        public HeldItemTuning toTuning() {
            return new HeldItemTuning(
                    baseScale,
                    noCursorScaleMul,
                    cursorOffsetMulX,
                    cursorOffsetMulY,
                    noCursorOffsetMulX,
                    noCursorOffsetMulY
            );
        }
    }

    // Helper: obtener tuning por skin id con fallback a "default"
    public static HeldItemTuning getOrDefault(Map<String, HeldItemTuning> map, String skinId) {
        HeldItemTuning t = map.get(skinId);
        if (t != null) return t;

        HeldItemTuning def = map.get("default");
        if (def != null) return def;

        throw new IllegalStateException("No existe tuning para '" + skinId + "' ni para 'default'");
    }
}
