package com.jairo.app.input;

import com.jairo.app.gfx.player_skins.HeldItemTuning;
import com.jairo.app.gfx.player_skins.Skin;
import com.jairo.app.gfx.player_skins.SkinManager;
import javafx.scene.input.KeyCode;

public class HeldItemTuningAdjuster {

    private HeldItemTuningAdjuster() {
    }

    private static boolean status = true;

    public static void adjust(KeyCode key) {
        if (!status) return;

        SkinManager sm = SkinManager.get();
        boolean hasCursor = sm.current().needArrow();

        switch (key) {
            // --- MOVER OBJETO ---
            case J -> { // izquierda
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(-0.1, 0.0);
                else
                    sm.tweakHeldItemNoCursorOffset(-0.1, 0.0);
            }

            case L -> { // derecha
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(0.1, 0.0);
                else
                    sm.tweakHeldItemNoCursorOffset(0.1, 0.0);
            }

            case I -> { // arriba
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(0.0, -0.1);
                else
                    sm.tweakHeldItemNoCursorOffset(0.0, -0.1);
            }

            case K -> { // abajo
                if (hasCursor)
                    sm.tweakHeldItemCursorOffset(0.0, 0.1);
                else
                    sm.tweakHeldItemNoCursorOffset(0.0, 0.1);
            }

            // --- ESCALA ---
            case U -> sm.tweakHeldItemBaseScale(-0.05);
            case O -> sm.tweakHeldItemBaseScale(0.05);

            // --- RESET ---
            case B -> sm.resetHeldItemTuning();
            case M -> saveConfig();

            default -> {
            }
        }
    }

    private static void saveConfig() {
        SkinManager sm = SkinManager.get();

        HeldItemTuning t = sm.heldItemTuning();
        Skin skin = sm.current();
        String id = skin.id;

        StringBuilder sb = new StringBuilder();

        sb.append("\n\n\n\n\n");
        sb.append("===== HELD ITEM TUNING CONFIG (JSON) =====\n");
        sb.append("Skin: ").append(skin.name()).append(" (id: ").append(id).append(")\n\n");

        sb.append("{\n");
        sb.append("  \"id\": \"").append(id).append("\",\n");
        sb.append("  \"baseScale\": ").append(fmt(t.baseScale())).append(",\n");
        sb.append("  \"noCursorScaleMul\": ").append(fmt(t.noCursorScaleMul())).append(",\n");
        sb.append("  \"cursorOffsetMulX\": ").append(fmt(t.cursorOffsetMulX())).append(",\n");
        sb.append("  \"cursorOffsetMulY\": ").append(fmt(t.cursorOffsetMulY())).append(",\n");
        sb.append("  \"noCursorOffsetMulX\": ").append(fmt(t.noCursorOffsetMulX())).append(",\n");
        sb.append("  \"noCursorOffsetMulY\": ").append(fmt(t.noCursorOffsetMulY())).append("\n");
        sb.append("}\n");

        System.out.println(sb);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
