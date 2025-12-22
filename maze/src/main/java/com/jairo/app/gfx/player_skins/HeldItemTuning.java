package com.jairo.app.gfx.player_skins;

public record HeldItemTuning(
        double baseScale,          // tamaño base relativo al tile (ej 0.95)
        double noCursorScaleMul,   // multiplicador extra si NO hay cursor (ej 1.10)

        double cursorOffsetMulX,   // ox += offset * cursorOffsetMulX
        double cursorOffsetMulY,   // oy += offset * cursorOffsetMulY

        double noCursorOffsetMulX, // ox += offset * noCursorOffsetMulX
        double noCursorOffsetMulY  // oy += offset * noCursorOffsetMulY
) {
    public static HeldItemTuning defaults() {
        return new HeldItemTuning(
                0.95,   // baseScale
                1.10,   // noCursorScaleMul
                1.6,    // cursorOffsetMulX
                0.6,    // cursorOffsetMulY
                1.3,    // noCursorOffsetMulX
                0.0     // noCursorOffsetMulY
        );
    }

    public HeldItemTuning withBaseScale(double v) {
        return new HeldItemTuning(v, noCursorScaleMul, cursorOffsetMulX, cursorOffsetMulY, noCursorOffsetMulX, noCursorOffsetMulY);
    }

    public HeldItemTuning withNoCursorScaleMul(double v) {
        return new HeldItemTuning(baseScale, v, cursorOffsetMulX, cursorOffsetMulY, noCursorOffsetMulX, noCursorOffsetMulY);
    }

    public HeldItemTuning withCursorOffset(double mx, double my) {
        return new HeldItemTuning(baseScale, noCursorScaleMul, mx, my, noCursorOffsetMulX, noCursorOffsetMulY);
    }

    public HeldItemTuning withNoCursorOffset(double mx, double my) {
        return new HeldItemTuning(baseScale, noCursorScaleMul, cursorOffsetMulX, cursorOffsetMulY, mx, my);
    }
}
