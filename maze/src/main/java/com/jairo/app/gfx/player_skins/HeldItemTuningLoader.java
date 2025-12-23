package com.jairo.app.gfx.player_skins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jairo.items.PowerType;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HeldItemTuningLoader {
    private HeldItemTuningLoader() {}

    /**
     * Carga el JSON genérico y devuelve:
     * skinId -> (PowerType -> HeldItemTuning)
     *
     * Formato esperado:
     * [
     *   { "id":"default", "items": { "PICKAXE": {..}, "BLAI_GLASSES": {..} } },
     *   { "id":"blai",    "items": { "PICKAXE": {..} } }
     * ]
     */
    public static Map<String, EnumMap<PowerType, HeldItemTuning>> loadAllFromResources(String resourceName) {
        try {
            InputStream is = HeldItemTuningLoader.class.getClassLoader().getResourceAsStream(resourceName);
            if (is == null) {
                throw new IllegalStateException("No se encontró el recurso: " + resourceName);
            }

            ObjectMapper mapper = new ObjectMapper();
            List<SkinItemsConfig> configs =
                    mapper.readValue(is,
                            mapper.getTypeFactory().constructCollectionType(List.class, SkinItemsConfig.class));

            Map<String, EnumMap<PowerType, HeldItemTuning>> out = new HashMap<>();

            for (SkinItemsConfig c : configs) {
                if (c == null || c.id == null) continue;

                EnumMap<PowerType, HeldItemTuning> perItem = new EnumMap<>(PowerType.class);

                if (c.items != null) {
                    for (Map.Entry<String, ItemTuningConfig> e : c.items.entrySet()) {
                        if (e.getKey() == null || e.getValue() == null) continue;

                        // La key debe ser EXACTAMENTE el name() del enum PowerType
                        PowerType pt;
                        try {
                            pt = PowerType.valueOf(e.getKey());
                        } catch (IllegalArgumentException ex) {
                            // Si en el JSON hay un item desconocido, lo ignoramos para no romper el juego.
                            continue;
                        }

                        perItem.put(pt, e.getValue().toTuning());
                    }
                }

                out.put(c.id, perItem);
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException("Error cargando held item tunings desde JSON", e);
        }
    }

    // DTO principal: cada entrada del array
    public static class SkinItemsConfig {
        public String id;
        public Map<String, ItemTuningConfig> items; // "PICKAXE" -> {...}
    }

    // DTO del tuning de un item
    public static class ItemTuningConfig {
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

    /**
     * Helper genérico:
     * skinId + power -> tuning, con fallback a "default" y luego HeldItemTuning.defaults()
     */
    public static HeldItemTuning getOrDefault(
            Map<String, EnumMap<PowerType, HeldItemTuning>> map,
            String skinId,
            PowerType power
    ) {
        if (map == null || power == null) return HeldItemTuning.defaults();

        EnumMap<PowerType, HeldItemTuning> perSkin = map.get(skinId);
        if (perSkin != null) {
            HeldItemTuning t = perSkin.get(power);
            if (t != null) return t;
        }

        EnumMap<PowerType, HeldItemTuning> def = map.get("default");
        if (def != null) {
            HeldItemTuning t = def.get(power);
            if (t != null) return t;
        }

        return HeldItemTuning.defaults();
    }
}
