package com.jairo.app.gfx.sub_drawer;

import com.jairo.models.Board;

/**
 * Encapsula toda la lógica de zoom:
 * - estado zoom + límites
 * - zoomIn/zoomOut
 * - scaledTileSize
 * - mantener la posición del jugador en pantalla al cambiar zoom
 */
public class ZoomSystem {

    private static final double ZOOM_POINT = 0.1;

    private final double tileSize;

    private final double baseZoom;
    private final double minZoom;
    private final double maxZoom;

    private double zoom;

    public ZoomSystem(double tileSize) {
        this.tileSize = tileSize;

        this.baseZoom = (Board.BOARD_HEIGHT * Board.BOARD_WIDTH) * 1.5 / 1000.0;
        this.minZoom = baseZoom - 0.6;
        this.maxZoom = baseZoom + 1.2;

        this.zoom = baseZoom;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
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
        double oldZoom = zoom;

        if (zoom < maxZoom) {
            zoom = round1(zoom + ZOOM_POINT);
        }

        if (zoom != oldZoom) {
            keepPlayerScreenPositionAfterZoom(playerX, playerY, oldZoom, zoom, cameraSystem);
            return true;
        }
        return false;
    }

    public boolean zoomOut(int playerX, int playerY, CameraSystem cameraSystem) {
        double oldZoom = zoom;

        if (zoom > minZoom) {
            zoom = round1(zoom - ZOOM_POINT);
        }

        if (zoom != oldZoom) {
            keepPlayerScreenPositionAfterZoom(playerX, playerY, oldZoom, zoom, cameraSystem);
            return true;
        }
        return false;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
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
