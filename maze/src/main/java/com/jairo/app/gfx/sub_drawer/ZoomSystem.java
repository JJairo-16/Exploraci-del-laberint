package com.jairo.app.gfx.sub_drawer;

import com.jairo.models.Board;

/**
 * Encapsula toda la lógica de zoom:
 * - estado zoom + límites
 * - zoomIn/zoomOut con velocidad regulada por curva según el zoom actual
 * - scaledTileSize
 * - mantener la posición del jugador en pantalla al cambiar zoom
 */
public class ZoomSystem {

    // Paso "máximo" (en la zona media). La curva lo reduce cerca de min/max.
    private static final double ZOOM_POINT = 0.045;

    // Qué tanto se reduce el paso en los extremos (0.15 = 15% del paso máximo)
    private static final double MIN_STEP_FACTOR = 0.2;

    private final double tileSize;

    private final double baseZoom;
    private final double minZoom;
    private final double maxZoom;

    private double zoom;

    public ZoomSystem(double tileSize) {
        this.tileSize = tileSize;

        this.baseZoom = (Board.BOARD_HEIGHT * Board.BOARD_WIDTH) * 1.5 / 1000.0;
        this.minZoom = baseZoom - 0.9;
        this.maxZoom = baseZoom + 1.5;

        this.zoom = baseZoom;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.clamp(zoom, minZoom, maxZoom);
    }

    public double getMinZoom() {
        return minZoom;
    }

    public double getMaxZoom() {
        return maxZoom;
    }

    public double scaledTileSize() {
        return tileSize * zoom;
    }

    public boolean zoomIn(int playerX, int playerY, CameraSystem cameraSystem) {
        return applyZoomDelta(+1, playerX, playerY, cameraSystem);
    }

    public boolean zoomOut(int playerX, int playerY, CameraSystem cameraSystem) {
        return applyZoomDelta(-1, playerX, playerY, cameraSystem);
    }

    private boolean applyZoomDelta(int direction, int playerX, int playerY, CameraSystem cameraSystem) {
        if ((zoom == minZoom && direction < 0) || (zoom == maxZoom && direction > 0) || direction == 0) return false;
        
        double oldZoom = zoom;

        double step = computeCurvedStep(oldZoom); // <-- aquí está la magia
        double target = oldZoom + direction * step;

        zoom = round4(Math.clamp(target, minZoom, maxZoom));

        if (zoom != oldZoom) {
            keepPlayerScreenPositionAfterZoom(playerX, playerY, oldZoom, zoom, cameraSystem);
            return true;
        }
        return false;
    }

    /**
     * Paso variable según el zoom actual:
     * - Normalizamos zoom a t en [0..1]
     * - Usamos una "campana" suave: máximo en el centro, mínimo en los extremos.
     *
     * Campana elegida:
     *   bell = 1 - (2t - 1)^2    -> 0 en extremos, 1 en el centro
     * Luego garantizamos mínimo:
     *   factor = MIN_STEP_FACTOR + (1 - MIN_STEP_FACTOR) * bell
     */
    private double computeCurvedStep(double currentZoom) {
        double t = (currentZoom - minZoom) / (maxZoom - minZoom);
        t = Math.clamp(t, 0.0, 1.0);

        double x = 2.0 * t - 1.0;           // [-1..1]
        double bell = 1.0 - (x * x);        // [0..1] (parábola)
        bell = Math.sqrt(bell);
        bell = smoothstep(bell);

        double factor = MIN_STEP_FACTOR + (1.0 - MIN_STEP_FACTOR) * bell;
        return ZOOM_POINT * factor;
    }

    private double smoothstep(double v) {
        v = Math.clamp(v, 0.0, 1.0);
        return v * v * (3.0 - 2.0 * v);
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * Mantiene el jugador en la misma posición de pantalla (px) tras cambiar zoom,
     * ajustando la cámara.
     */
    private void keepPlayerScreenPositionAfterZoom(
            int playerX,
            int playerY,
            double oldZoom,
            double newZoom,
            CameraSystem cameraSystem
    ) {
        double oldSize = tileSize * oldZoom;
        double newSize = tileSize * newZoom;

        // Posición del jugador en pantalla ANTES del zoom (en píxels)
        double px = (playerX - cameraSystem.getCameraX()) * oldSize;
        double py = (playerY - cameraSystem.getCameraY()) * oldSize;

        // Reajusta cámara para mantener esos mismos píxels DESPUÉS del zoom
        double newCamX = playerX - (px / newSize);
        double newCamY = playerY - (py / newSize);

        cameraSystem.setCamera(newCamX, newCamY);
    }
}
