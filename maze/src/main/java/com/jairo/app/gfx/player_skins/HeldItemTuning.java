package com.jairo.app.gfx.player_skins;

public record HeldItemTuning(
        double baseScale,          // tamaño base relativo al tile (ej 0.95)
        double noCursorScaleMul,   // multiplicador extra si NO hay cursor (ej 1.10)

        double cursorOffsetMulX,   // ox += offset * cursorOffsetMulX
        double cursorOffsetMulY,   // oy += offset * cursorOffsetMulY

        double noCursorOffsetMulX, // ox += offset * noCursorOffsetMulX
        double noCursorOffsetMulY, // oy += offset * noCursorOffsetMulY

        double rotationDeg,        // rotación cuando hay cursor
        double noCursorRotationDeg // rotación cuando NO hay cursor
) {
    public static HeldItemTuning defaults() {
        return new HeldItemTuning(
                0.95,   // baseScale
                1.10,   // noCursorScaleMul
                1.6,    // cursorOffsetMulX
                0.6,    // cursorOffsetMulY
                1.3,    // noCursorOffsetMulX
                0.0,    // noCursorOffsetMulY
                0.0,    // rotationDeg
                0.0     // noCursorRotationDeg
        );
    }

    public HeldItemTuning withBaseScale(double v) {
        return new HeldItemTuning(
                v, noCursorScaleMul,
                cursorOffsetMulX, cursorOffsetMulY,
                noCursorOffsetMulX, noCursorOffsetMulY,
                rotationDeg, noCursorRotationDeg
        );
    }

    public HeldItemTuning withNoCursorScaleMul(double v) {
        return new HeldItemTuning(
                baseScale, v,
                cursorOffsetMulX, cursorOffsetMulY,
                noCursorOffsetMulX, noCursorOffsetMulY,
                rotationDeg, noCursorRotationDeg
        );
    }

    // ✅ ESTOS SON LOS QUE TE FALTAN (los usa SkinManager)
    public HeldItemTuning withCursorOffset(double mx, double my) {
        return new HeldItemTuning(
                baseScale, noCursorScaleMul,
                mx, my,
                noCursorOffsetMulX, noCursorOffsetMulY,
                rotationDeg, noCursorRotationDeg
        );
    }

    public HeldItemTuning withNoCursorOffset(double mx, double my) {
        return new HeldItemTuning(
                baseScale, noCursorScaleMul,
                cursorOffsetMulX, cursorOffsetMulY,
                mx, my,
                rotationDeg, noCursorRotationDeg
        );
    }

    // ✅ Rotación
    public HeldItemTuning withRotation(double deg) {
        return new HeldItemTuning(
                baseScale, noCursorScaleMul,
                cursorOffsetMulX, cursorOffsetMulY,
                noCursorOffsetMulX, noCursorOffsetMulY,
                deg, noCursorRotationDeg
        );
    }

    public HeldItemTuning withNoCursorRotation(double deg) {
        return new HeldItemTuning(
                baseScale, noCursorScaleMul,
                cursorOffsetMulX, cursorOffsetMulY,
                noCursorOffsetMulX, noCursorOffsetMulY,
                rotationDeg, deg
        );
    }
}
