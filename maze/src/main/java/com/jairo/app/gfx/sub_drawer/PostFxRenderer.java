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
    // Si true -> siempre renderiza (equivale al comportamiento "cada frame")
    // Si false -> usa estrategia por intervalos (one-shot o cada N frames).
    private static final boolean ALWAYS_UPDATE = false;

    // Intervalo por defecto cuando NO es ALWAYS_UPDATE:
    //  0  -> one-shot (solo 1 vez)
    //  1  -> cada frame
    //  N>1-> cada N frames
    private static final int DEFAULT_EVERY_N_FRAMES = 0;

    // Override custom:
    //  -1 -> igual que el resto (usa ALWAYS_UPDATE + DEFAULT_EVERY_N_FRAMES)
    //  N  -> fuerza a renderizar cada N frames (incluye 0 one-shot, 1 cada frame)
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

    // =======================
    // Estado interno
    // =======================
    private long frameCounter = 0;
    private boolean renderedOnce = false;

    // Si el tamaño cambia, forzamos redibujar aunque fuera one-shot o cada N
    private double lastW = -1;
    private double lastH = -1;

    public void render(GraphicsContext postFxGC, double width, double height) {
        if (postFxGC == null) return;

        // Contamos "frames" como llamadas a render()
        frameCounter++;

        // Si cambió el tamaño, invalida (muy importante si usas one-shot o intervalos)
        boolean sizeChanged = (width != lastW) || (height != lastH);
        if (sizeChanged) {
            lastW = width;
            lastH = height;
            renderedOnce = false;
            // no reseteo frameCounter: no hace falta
        }

        // Decide cada cuánto renderizar
        int everyN = resolveEveryNFrames();

        // 0 => one-shot (renderiza solo si nunca se renderizó o si cambió el size)
        if (everyN == 0) {
            if (renderedOnce && !sizeChanged) return;
            doRender(postFxGC, width, height);
            renderedOnce = true;
            return;
        }

        // 1 => cada frame
        if (everyN == 1) {
            doRender(postFxGC, width, height);
            renderedOnce = true;
            return;
        }

        // N>1 => renderiza cada N frames (y siempre en resize)
        if (sizeChanged || (frameCounter % everyN == 0)) {
            doRender(postFxGC, width, height);
            renderedOnce = true;
        }
    }

    private static int resolveEveryNFrames() {
        // Custom override manda si no es -1
        if (customEveryNFrames != -1) {
            return clampEveryN(customEveryNFrames);
        }

        // Si ALWAYS_UPDATE, equivale a 1 (cada frame)
        if (ALWAYS_UPDATE) return 1;

        // Si no, usa el default
        return clampEveryN(DEFAULT_EVERY_N_FRAMES);
    }

    private static int clampEveryN(int n) {
        // - Valores raros: lo llevamos a rango [0..]
        if (n < 0) return 0;
        return n;
    }

    private static void doRender(GraphicsContext postFxGC, double width, double height) {
        postFxGC.clearRect(0, 0, width, height);

        postFxGC.setFill(TINT);
        postFxGC.fillRect(0, 0, width, height);

        postFxGC.setFill(VIGNETTE);
        postFxGC.fillRect(0, 0, width, height);
    }

    public void invalidate() {
        renderedOnce = false; // ? force
    }
}
