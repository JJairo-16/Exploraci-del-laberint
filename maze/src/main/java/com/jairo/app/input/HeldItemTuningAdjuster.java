package com.jairo.app.input;

import com.jairo.app.gfx.player_skins.HeldItemTuning;
import com.jairo.app.gfx.player_skins.HeldItemTuningStore;
import com.jairo.app.gfx.player_skins.Skin;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.items.PowerType;
import com.jairo.models.Inventory;
import javafx.scene.input.KeyCode;

public class HeldItemTuningAdjuster {

    private HeldItemTuningAdjuster() {
    }

    private static boolean status = false;

    /**
     * Compatibilidad: ajusta el tuning del skin para un item "por defecto".
     * Como ya no existe un tuning global único, usamos PICKAXE como default.
     */
    public static void adjust(KeyCode key) {
        adjust(key, null, PowerType.PICKAXE);
    }

    /**
     * Nuevo: cada jugador (Inventory) tiene su propio tuning para cada item
     * (PowerType).
     */
    public static void adjust(KeyCode key, Inventory inv, PowerType item) {
        if (!status)
            return;
    
        if (key == KeyCode.DIGIT3) {
            for (PowerType it : PowerType.values()) {
                inv.addPower(it);
                inv.add(it);
            }
            return;
        }

        SkinManager sm = SkinManager.get();
        boolean hasCursor = sm.current().needArrow();

        // Si no tenemos jugador, ajustamos el tuning del skin para ese PowerType (modo
        // "legacy" por item).
        if (inv == null || item == null) {
            adjustLegacy(key, sm, hasCursor, item != null ? item : PowerType.PICKAXE);
            return;
        }

        // Tuning base (por skin+item) + override por (jugador,item)
        HeldItemTuning base = sm.heldItemTuning(item);
        HeldItemTuningStore store = HeldItemTuningStore.get();
        HeldItemTuning t = store.get(inv, item, base);

        switch (key) {
            // --- MOVER OBJETO ---
            case J -> { // izquierda
                t = hasCursor
                        ? withCursorOffsetDelta(t, -0.1, 0.0)
                        : withNoCursorOffsetDelta(t, -0.1, 0.0);
                store.set(inv, item, t);
            }

            case L -> { // derecha
                t = hasCursor
                        ? withCursorOffsetDelta(t, 0.1, 0.0)
                        : withNoCursorOffsetDelta(t, 0.1, 0.0);
                store.set(inv, item, t);
            }

            case I -> { // arriba
                t = hasCursor
                        ? withCursorOffsetDelta(t, 0.0, -0.1)
                        : withNoCursorOffsetDelta(t, 0.0, -0.1);
                store.set(inv, item, t);
            }

            case K -> { // abajo
                t = hasCursor
                        ? withCursorOffsetDelta(t, 0.0, 0.1)
                        : withNoCursorOffsetDelta(t, 0.0, 0.1);
                store.set(inv, item, t);
            }

            // --- ESCALA ---
            case U -> { // -
                t = copyWithBaseScale(t, t.baseScale() - 0.05);
                store.set(inv, item, t);
            }

            case O -> { // +
                t = copyWithBaseScale(t, t.baseScale() + 0.05);
                store.set(inv, item, t);
            }

            // --- ROTACIÓN ---
            case NUMPAD1 -> { // rotar izquierda
                t = hasCursor
                        ? t.withRotation(t.rotationDeg() - 2)
                        : t.withNoCursorRotation(t.noCursorRotationDeg() - 2);
                store.set(inv, item, t);
            }

            case NUMPAD2 -> { // rotar derecha
                t = hasCursor
                        ? t.withRotation(t.rotationDeg() + 2)
                        : t.withNoCursorRotation(t.noCursorRotationDeg() + 2);
                store.set(inv, item, t);
            }

            // --- RESET (solo este jugador+item) ---
            case B -> store.set(inv, item, null);

            // --- "GUARDAR" (imprime JSON del override actual jugador+item) ---
            case M -> saveConfig(inv, item, t);

            default -> {
            }
        }
    }

    // =======================
    // Legacy (por skin + PowerType)
    // =======================
    private static void adjustLegacy(KeyCode key, SkinManager sm, boolean hasCursor, PowerType item) {
        switch (key) {
            case J -> {
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(item, -0.1, 0.0);
                else
                    sm.tweakHeldItemNoCursorOffset(item, -0.1, 0.0);
            }
            case L -> {
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(item, 0.1, 0.0);
                else
                    sm.tweakHeldItemNoCursorOffset(item, 0.1, 0.0);
            }
            case I -> {
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(item, 0.0, -0.1);
                else
                    sm.tweakHeldItemNoCursorOffset(item, 0.0, -0.1);
            }
            case K -> {
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(item, 0.0, 0.1);
                else
                    sm.tweakHeldItemNoCursorOffset(item, 0.0, 0.1);
            }
            case U -> sm.tweakHeldItemBaseScale(item, -0.05);
            case O -> sm.tweakHeldItemBaseScale(item, 0.05);

            case NUMPAD1 -> { // rotar izquierda
                if (hasCursor) sm.tweakHeldItemRotation(item, -2);
                else sm.tweakHeldItemNoCursorRotation(item, -2);
            }

            case NUMPAD2 -> { // rotar derecha
                if (hasCursor) sm.tweakHeldItemRotation(item, +2);
                else sm.tweakHeldItemNoCursorRotation(item, +2);
            }

            case B -> sm.resetHeldItemTuning(item);
            case M -> saveConfigLegacy(item);
            default -> {
            }
        }
    }

    // =======================
    // Helpers de copia (record)
    // OJO: ahora también copiamos rotationDeg / noCursorRotationDeg
    // =======================
    private static HeldItemTuning withCursorOffsetDelta(HeldItemTuning t, double dx, double dy) {
        return new HeldItemTuning(
                t.baseScale(),
                t.noCursorScaleMul(),
                t.cursorOffsetMulX() + dx,
                t.cursorOffsetMulY() + dy,
                t.noCursorOffsetMulX(),
                t.noCursorOffsetMulY(),
                t.rotationDeg(),
                t.noCursorRotationDeg()
        );
    }

    private static HeldItemTuning withNoCursorOffsetDelta(HeldItemTuning t, double dx, double dy) {
        return new HeldItemTuning(
                t.baseScale(),
                t.noCursorScaleMul(),
                t.cursorOffsetMulX(),
                t.cursorOffsetMulY(),
                t.noCursorOffsetMulX() + dx,
                t.noCursorOffsetMulY() + dy,
                t.rotationDeg(),
                t.noCursorRotationDeg()
        );
    }

    private static HeldItemTuning copyWithBaseScale(HeldItemTuning t, double baseScale) {
        return new HeldItemTuning(
                baseScale,
                t.noCursorScaleMul(),
                t.cursorOffsetMulX(),
                t.cursorOffsetMulY(),
                t.noCursorOffsetMulX(),
                t.noCursorOffsetMulY(),
                t.rotationDeg(),
                t.noCursorRotationDeg()
        );
    }

    // =======================
    // Save config (override jugador+item) -> imprime un OBJETO compatible con el JSON del loader
    // =======================
    private static void saveConfig(Inventory inv, PowerType item, HeldItemTuning t) {
        SkinManager sm = SkinManager.get();
        Skin skin = sm.current();

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n\n\n\n");
        sb.append("===== HELD ITEM TUNING OVERRIDE (JSON ENTRY) =====\n");
        sb.append("Skin: ").append(skin.name()).append(" (id: ").append(skin.id).append(")\n");
        sb.append("Item: ").append(item.name()).append("\n");
        sb.append("PlayerKey(Inventory identity): ").append(System.identityHashCode(inv)).append("\n\n");

        sb.append("{\n");
        sb.append("  \"id\": \"").append(skin.id).append("\",\n");
        sb.append("  \"items\": {\n");
        sb.append("    \"").append(item.name()).append("\": {\n");
        sb.append("      \"baseScale\": ").append(fmt(t.baseScale())).append(",\n");
        sb.append("      \"noCursorScaleMul\": ").append(fmt(t.noCursorScaleMul())).append(",\n");
        sb.append("      \"cursorOffsetMulX\": ").append(fmt(t.cursorOffsetMulX())).append(",\n");
        sb.append("      \"cursorOffsetMulY\": ").append(fmt(t.cursorOffsetMulY())).append(",\n");
        sb.append("      \"noCursorOffsetMulX\": ").append(fmt(t.noCursorOffsetMulX())).append(",\n");
        sb.append("      \"noCursorOffsetMulY\": ").append(fmt(t.noCursorOffsetMulY())).append(",\n");
        sb.append("      \"rotationDeg\": ").append(fmt(t.rotationDeg())).append(",\n");
        sb.append("      \"noCursorRotationDeg\": ").append(fmt(t.noCursorRotationDeg())).append("\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");

        System.out.println(sb);
    }

    // Save config "legacy" (por skin + PowerType) -> mismo formato del JSON
    private static void saveConfigLegacy(PowerType item) {
        SkinManager sm = SkinManager.get();

        HeldItemTuning t = sm.heldItemTuning(item);
        Skin skin = sm.current();

        StringBuilder sb = new StringBuilder();

        sb.append("\n\n\n\n\n");
        sb.append("===== HELD ITEM TUNING CONFIG (JSON ENTRY) =====\n");
        sb.append("Skin: ").append(skin.name()).append(" (id: ").append(skin.id).append(")\n");
        sb.append("Item: ").append(item.name()).append("\n\n");

        sb.append("{\n");
        sb.append("  \"id\": \"").append(skin.id).append("\",\n");
        sb.append("  \"items\": {\n");
        sb.append("    \"").append(item.name()).append("\": {\n");
        sb.append("      \"baseScale\": ").append(fmt(t.baseScale())).append(",\n");
        sb.append("      \"noCursorScaleMul\": ").append(fmt(t.noCursorScaleMul())).append(",\n");
        sb.append("      \"cursorOffsetMulX\": ").append(fmt(t.cursorOffsetMulX())).append(",\n");
        sb.append("      \"cursorOffsetMulY\": ").append(fmt(t.cursorOffsetMulY())).append(",\n");
        sb.append("      \"noCursorOffsetMulX\": ").append(fmt(t.noCursorOffsetMulX())).append(",\n");
        sb.append("      \"noCursorOffsetMulY\": ").append(fmt(t.noCursorOffsetMulY())).append(",\n");
        sb.append("      \"rotationDeg\": ").append(fmt(t.rotationDeg())).append(",\n");
        sb.append("      \"noCursorRotationDeg\": ").append(fmt(t.noCursorRotationDeg())).append("\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");

        System.out.println(sb);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
