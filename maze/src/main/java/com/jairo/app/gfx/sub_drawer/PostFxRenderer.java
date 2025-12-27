package com.jairo.app.gfx.sub_drawer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

public final class PostFxRenderer {

    // =======================
    // Reglas HARDCODEADAS
    // =======================
    private static final boolean ALWAYS_UPDATE = false;
    private static final int DEFAULT_EVERY_N_FRAMES = 0;

    // Override custom:
    private static int customEveryNFrames = -1;

    // =======================
    // Recursos cacheados
    // =======================
    private static final Paint TINT = Color.rgb(30, 80, 120, 0.14);

    private static final Paint VIGNETTE = new RadialGradient(
            0, 0,
            0.5, 0.5,
            0.85, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
            new Stop(1.0, Color.rgb(0, 0, 0, 0.45))
    );

    // Para dim (se crea dinámico según alpha, así no hardcodeas)
    private static final int DIM_RGB = 0; // negro

    // =======================
    // Estado interno
    // =======================
    private long frameCounter = 0;
    private boolean renderedOnce = false;

    private double lastW = -1;
    private double lastH = -1;

    // Si el usuario cambia dim entre renders y estás en one-shot,
    // con esto forzamos redraw cuando el dim cambia.
    private double lastDimAlpha = 0.0;

    // -----------------------
    // API pública
    // -----------------------

    /** Render normal: NO oscurece (por defecto). */
    public void render(GraphicsContext postFxGC, double width, double height) {
        render(postFxGC, width, height, 0.0);
    }

    /**
     * Render con dim opcional encima del postfx.
     * @param dimAlpha 0..1 (0 = nada, 1 = negro completo)
     */
    public void render(GraphicsContext postFxGC, double width, double height, double dimAlpha) {
        if (postFxGC == null) return;

        dimAlpha = clamp01(dimAlpha);

        // Contamos "frames" como llamadas a render()
        frameCounter++;

        boolean sizeChanged = (width != lastW) || (height != lastH);
        if (sizeChanged) {
            lastW = width;
            lastH = height;
            renderedOnce = false;
        }

        // Si cambió el dim y estabas en one-shot o intervalos, forzar redraw
        boolean dimChanged = Math.abs(dimAlpha - lastDimAlpha) > 0.0001;
        if (dimChanged) {
            lastDimAlpha = dimAlpha;
            renderedOnce = false;
        }

        int everyN = resolveEveryNFrames();

        if (everyN == 0) {
            if (renderedOnce && !sizeChanged && !dimChanged) return;
            doRender(postFxGC, width, height, dimAlpha);
            renderedOnce = true;
            return;
        }

        if (everyN == 1) {
            doRender(postFxGC, width, height, dimAlpha);
            renderedOnce = true;
            return;
        }

        if (sizeChanged || dimChanged || (frameCounter % everyN == 0)) {
            doRender(postFxGC, width, height, dimAlpha);
            renderedOnce = true;
        }
    }

    public void invalidate() {
        renderedOnce = false;
    }

    // =======================
    // Internals
    // =======================

    private static int resolveEveryNFrames() {
        if (customEveryNFrames != -1) {
            return clampEveryN(customEveryNFrames);
        }
        if (ALWAYS_UPDATE) return 1;
        return clampEveryN(DEFAULT_EVERY_N_FRAMES);
    }

    private static int clampEveryN(int n) {
        if (n < 0) return 0;
        return n;
    }

    private static void doRender(GraphicsContext postFxGC, double width, double height, double dimAlpha) {
        postFxGC.clearRect(0, 0, width, height);

        // Base postfx
        postFxGC.setFill(TINT);
        postFxGC.fillRect(0, 0, width, height);

        postFxGC.setFill(VIGNETTE);
        postFxGC.fillRect(0, 0, width, height);

        // ✅ Dim encima del postfx (opcional)
        if (dimAlpha > 0.0) {
            postFxGC.setFill(Color.rgb(DIM_RGB, DIM_RGB, DIM_RGB, dimAlpha));
            postFxGC.fillRect(0, 0, width, height);
        }
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // (Opcional) si quieres exponer el override:
    public static void setCustomEveryNFrames(int n) {
        customEveryNFrames = n;
    }

    public static void clearCustomEveryNFrames() {
        customEveryNFrames = -1;
    }
}
