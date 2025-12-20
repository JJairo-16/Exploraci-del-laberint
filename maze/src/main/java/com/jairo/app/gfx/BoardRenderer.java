package com.jairo.app.gfx;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public final class BoardRenderer {

    private final ImageStore images;

    public BoardRenderer(ImageStore images) {
        this.images = images;
    }

    public void drawBorder(GraphicsContext gc, double canvasW, double canvasH,
                           int boardW, int boardH, double tileSize, Sprite sprite) {
        if (gc == null || tileSize <= 0) return;

        Image img = images.get(sprite);
        gc.clearRect(0, 0, canvasW, canvasH);

        // top + bottom
        for (int x = 0; x < boardW; x++) {
            gc.drawImage(img, x * tileSize, 0, tileSize, tileSize);
            gc.drawImage(img, x * tileSize, (boardH - 1) * tileSize, tileSize, tileSize);
        }

        // left + right
        for (int y = 0; y < boardH; y++) {
            gc.drawImage(img, 0, y * tileSize, tileSize, tileSize);
            gc.drawImage(img, (boardW - 1) * tileSize, y * tileSize, tileSize, tileSize);
        }
    }
}
