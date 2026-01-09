package com.jairo.app.gfx.sub_drawer;

/**
 * Encapsula la lógica de cámara:
 * - seguimiento con dead-zone
 * - clamping con padding
 * - centrado cuando el board es más pequeño que el área visible
 *
 * Mantiene el estado cameraX/cameraY en coordenadas de tiles.
 */
public class CameraSystem {

    private double cameraX = 0.0;
    private double cameraY = 0.0;

    public double getCameraX() {
        return cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public void setCamera(double x, double y) {
        this.cameraX = x;
        this.cameraY = y;
    }

    /**
     * Actualiza la cámara en función de la posición del jugador y del área visible.
     *
     * @param playerX        posición del jugador (tiles)
     * @param playerY        posición del jugador (tiles)
     * @param canvasWidthPx  ancho del canvas (px)
     * @param canvasHeightPx alto del canvas (px)
     * @param scaledTileSize tamaño tile ya escalado por zoom (px)
     * @param boardW         ancho del board (tiles)
     * @param boardH         alto del board (tiles)
     */
    public void updateCamera(
            int playerX,
            int playerY,
            double canvasWidthPx,
            double canvasHeightPx,
            double scaledTileSize,
            int boardW,
            int boardH
    ) {
        // Tiles visibles según el zoom y el canvas
        double tilesInWidth = canvasWidthPx / scaledTileSize;
        double tilesInHeight = canvasHeightPx / scaledTileSize;

        double cameraPadding = Math.max(2.0, tilesInWidth * 0.1);

        // Dead zone (márgenes de movimiento)
        double marginX = tilesInWidth * 0.25;
        double marginY = tilesInHeight * 0.25;

        double left = cameraX + marginX;
        double right = cameraX + tilesInWidth - marginX;
        double top = cameraY + marginY;
        double bottom = cameraY + tilesInHeight - marginY;

        if (playerX < left) {
            cameraX = playerX - marginX;
        } else if (playerX > right) {
            cameraX = playerX - (tilesInWidth - marginX);
        }

        if (playerY < top) {
            cameraY = playerY - marginY;
        } else if (playerY > bottom) {
            cameraY = playerY - (tilesInHeight - marginY);
        }

        // Límites adaptativos según el board y el zoom
        double minX;
        double maxX;
        double minY;
        double maxY;

        // Si el tauler és més petit que el visible, el centrem (evita “buits” estranys)
        if (boardW <= tilesInWidth) {
            minX = maxX = (boardW - tilesInWidth) / 2.0;
        } else {
            minX = -cameraPadding;
            maxX = boardW - tilesInWidth + cameraPadding;
        }

        if (boardH <= tilesInHeight) {
            minY = maxY = (boardH - tilesInHeight) / 2.0;
        } else {
            minY = -cameraPadding;
            maxY = boardH - tilesInHeight + cameraPadding;
        }

        cameraX = Math.clamp(cameraX, minX, maxX);
        cameraY = Math.clamp(cameraY, minY, maxY);
    }
}
