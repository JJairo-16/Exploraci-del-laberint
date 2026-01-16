package com.jairo.app.gfx.sub_drawer.tools;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Encapsula la lógica del "aura" (DropShadow pulsante) + caches de color.
 * Pensado para usarse en el hilo de JavaFX.
 */
public final class ItemAuraRenderer {

    // Cache color by rgb + quantized alpha level
    private static final int ALPHA_LEVELS = 64;

    // Cache arrays of colors per RGB (each array indexed by alpha level)
    private final Map<Integer, Color[]> colorCache = HashMap.newHashMap(128);

    // Shared DropShadow instance (JavaFX thread only)
    private static final DropShadow SHARED_DROP_SHADOW = new DropShadow();
    static {
        SHARED_DROP_SHADOW.setOffsetX(0);
        SHARED_DROP_SHADOW.setOffsetY(0);
    }

    public void drawAura(
            GraphicsContext gc,
            double size,
            double t,
            double screenX,
            double phase,
            Image img,
            double drawY,
            int red, int green, int blue,
            double baseAlpha,
            double pulseAlpha,
            double pulseSpeed,
            double radiusScale,
            double spread,
            Effect oldEffect) {
        final double pulse = 0.5 + 0.5 * Math.sin(t * pulseSpeed + phase);
        final double alpha = clamp01(baseAlpha + pulse * pulseAlpha);

        // Quantize alpha to reduce Color allocations
        final int aIdx = quantizeAlpha(alpha);

        final double radius = Math.max(1.0, size * radiusScale);

        SHARED_DROP_SHADOW.setRadius(radius);
        SHARED_DROP_SHADOW.setSpread(spread);
        SHARED_DROP_SHADOW.setColor(getCachedColor(red, green, blue, aIdx));

        gc.setEffect(SHARED_DROP_SHADOW);
        gc.drawImage(img, screenX, drawY, size, size);
        gc.setEffect(oldEffect);
    }

    private Color getCachedColor(int r, int g, int b, int alphaIdx) {
        final int rgbKey = (r << 16) | (g << 8) | b;

        // Obtiene o crea el array de colores para este RGB
        Color[] arr = colorCache.computeIfAbsent(
                rgbKey,
                k -> new Color[ALPHA_LEVELS]);

        Color c = arr[alphaIdx];
        if (c != null) {
            return c;
        }

        final double a = alphaIdx / (double) (ALPHA_LEVELS - 1);
        c = Color.rgb(r, g, b, a);
        arr[alphaIdx] = c;
        return c;
    }

    private static int quantizeAlpha(double a) {
        final int idx = (int) Math.round(a * (ALPHA_LEVELS - 1));
        if (idx < 0)
            return 0;
        if (idx >= ALPHA_LEVELS)
            return ALPHA_LEVELS - 1;
        return idx;
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }
}
