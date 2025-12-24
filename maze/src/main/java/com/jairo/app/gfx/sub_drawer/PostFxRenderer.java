package com.jairo.app.gfx.sub_drawer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

public final class PostFxRenderer {

    // Constantes: no crear cada frame
    private static final Paint TINT = Color.rgb(30, 80, 120, 0.14);

    // El gradiente en coords proporcionales (proportional = true) NO depende de width/height
    // así que se puede reutilizar siempre.
    private static final Paint VIGNETTE = new RadialGradient(
            0, 0,
            0.5, 0.5,
            0.85, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
            new Stop(1.0, Color.rgb(0, 0, 0, 0.45))
    );

    public void render(GraphicsContext postFxGC, double width, double height) {
        if (postFxGC == null) return;

        postFxGC.clearRect(0, 0, width, height);

        postFxGC.setFill(TINT);
        postFxGC.fillRect(0, 0, width, height);

        postFxGC.setFill(VIGNETTE);
        postFxGC.fillRect(0, 0, width, height);
    }
}
