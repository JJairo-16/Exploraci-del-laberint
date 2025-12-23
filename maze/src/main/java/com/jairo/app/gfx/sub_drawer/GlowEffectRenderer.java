// File: src/main/java/com/jairo/app/gfx/sub_drawer/GlowEffectRenderer.java
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

        DropShadow ds = new DropShadow();
        ds.setRadius(radius);
        ds.setSpread(params.spread());
        ds.setOffsetX(0);
        ds.setOffsetY(0);
        ds.setColor(Color.rgb(red, green, blue, alpha));

        gc.setEffect(ds);
        gc.drawImage(img, x, y, size, size);

        gc.restore();
    }
}
