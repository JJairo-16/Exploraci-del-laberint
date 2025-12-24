package com.jairo.app.gfx.sub_drawer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public final class GlowEffectRenderer {

    private GlowEffectRenderer() {}

    public static record GlowParams(
            double baseAlpha,
            double pulseAlpha,
            double pulseSpeed,
            double radiusScale,
            double spread
    ) {}

    /**
     * DropShadow reutilizable.
     * En JavaFX todo el render se hace en el FX thread,
     * así que NO es necesario ThreadLocal.
     */
    private static final DropShadow SHARED_DROP_SHADOW = new DropShadow();

    public static void applyRgb(
            GraphicsContext gc,
            Image img,
            double x,
            double y,
            double size,
            double t,
            double phase,
            int red,
            int green,
            int blue,
            GlowParams params
    ) {
        if (gc == null || img == null || params == null) return;

        gc.save();

        double pulse = 0.5 + 0.5 * Math.sin(t * params.pulseSpeed() + phase);
        double radius = Math.max(1.0, size * params.radiusScale());
        double alpha = Math.min(1.0, params.baseAlpha() + pulse * params.pulseAlpha());

        // ✅ OPTI sin ThreadLocal ni warnings
        SHARED_DROP_SHADOW.setRadius(radius);
        SHARED_DROP_SHADOW.setSpread(params.spread());
        SHARED_DROP_SHADOW.setOffsetX(0);
        SHARED_DROP_SHADOW.setOffsetY(0);
        SHARED_DROP_SHADOW.setColor(Color.rgb(red, green, blue, alpha));

        gc.setEffect(SHARED_DROP_SHADOW);
        gc.drawImage(img, x, y, size, size);

        // restore revierte el effect
        gc.restore();
    }
}
