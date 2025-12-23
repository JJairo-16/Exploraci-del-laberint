package com.jairo.app.gfx.sub_drawer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

public class PostFxRenderer {

    public void render(GraphicsContext postFxGC, double width, double height) {
        if (postFxGC == null) return;

        postFxGC.clearRect(0, 0, width, height);

        postFxGC.setFill(Color.rgb(30, 80, 120, 0.14));
        postFxGC.fillRect(0, 0, width, height);

        Paint vignette = new RadialGradient(
                0, 0,
                0.5, 0.5,
                0.85, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.45))
        );

        postFxGC.setFill(vignette);
        postFxGC.fillRect(0, 0, width, height);
    }
}
