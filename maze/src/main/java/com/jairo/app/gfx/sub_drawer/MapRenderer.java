// File: src/main/java/com/jairo/app/gfx/sub_drawer/MapRenderer.java
package com.jairo.app.gfx.sub_drawer;

import static com.jairo.app.gfx.DrawerParser.parse;
import static com.jairo.utils.map_generator.Cells.UNKNOWN;

import java.util.List;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.models.Board;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class MapRenderer {

    private final Board board;
    private final CameraSystem cameraSystem;
    private final ImageStore images;
    private final GraphicsContext mapGC;

    public MapRenderer(Board board, CameraSystem cameraSystem, ImageStore images, GraphicsContext mapGC) {
        this.board = board;
        this.cameraSystem = cameraSystem;
        this.images = images;
        this.mapGC = mapGC;
    }

    public void render(RenderLoopSystem.Viewport vp, double scaledTileSize) {
        List<List<Integer>> visibility = board.getCells(true);

        for (int y = vp.startY(); y <= vp.endY(); y++) {
            for (int x = vp.startX(); x <= vp.endX(); x++) {
                int type = visibility.get(y).get(x);
                if (!isDiscovered(type)) continue;

                Sprite sprite = parse(type);
                renderCell(sprite, x, y, sprite.rotation, scaledTileSize);
            }
        }
    }

    private void renderCell(Sprite sprite, int x, int y, double rotation, double scaledTileSize) {
        Image img = images.get(sprite);
        if (img == null) return;

        double size = scaledTileSize;
        double screenX = (x - cameraSystem.getCameraX()) * size;
        double screenY = (y - cameraSystem.getCameraY()) * size;

        if (!sprite.getIfIsFullTile()) {
            Image back = images.get(sprite.getBack());
            if (back != null) {
                mapGC.drawImage(back, screenX, screenY, size, size);
            }
        }

        // Regla especial: locked exit rota según borde
        if (sprite == Sprite.LOCKED_EXIT) {
            int exitX = board.getExitX();
            int exitY = board.getExitY();

            if (exitY == Board.BOARD_HEIGHT - 1) {
                rotation = 180;
            } else if (exitX == 0) {
                rotation = -90;
            } else if (exitX == Board.BOARD_WIDTH - 1) {
                rotation = 90;
            }
        }

        if (rotation == 0) {
            mapGC.drawImage(img, screenX, screenY, size, size);
            return;
        }

        mapGC.save();

        double cx = screenX + size / 2.0;
        double cy = screenY + size / 2.0;

        mapGC.translate(cx, cy);
        mapGC.rotate(rotation);
        mapGC.drawImage(img, -size / 2.0, -size / 2.0, size, size);

        mapGC.restore();
    }

    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }
}
