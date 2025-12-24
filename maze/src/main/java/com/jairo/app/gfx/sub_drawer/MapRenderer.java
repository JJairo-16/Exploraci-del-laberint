// File: src/main/java/com/jairo/app/gfx/sub_drawer/MapRenderer.java
package com.jairo.app.gfx.sub_drawer;

import static com.jairo.app.gfx.DrawerParser.parse;
import static com.jairo.utils.map_generator.Cells.UNKNOWN;

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

    private final double lockedExitRotation;

    public MapRenderer(Board board, CameraSystem cameraSystem, ImageStore images, GraphicsContext mapGC) {
        this.board = board;
        this.cameraSystem = cameraSystem;
        this.images = images;
        this.mapGC = mapGC;
        lockedExitRotation = getLockedExitRotation();
    }

    public void render(RenderLoopSystem.Viewport vp, double scaledTileSize) {
        int[][] visArea = board.getVisibilityArea(vp.startX(), vp.startY(), vp.endX(), vp.endY());

        double camX = cameraSystem.getCameraX();
        double camY = cameraSystem.getCameraY();

        for (int y = vp.startY(); y <= vp.endY(); y++) {
            int[] row = visArea[y - vp.startY()];
            for (int x = vp.startX(); x <= vp.endX(); x++) {
                int type = row[x - vp.startX()];
                if (!isDiscovered(type))
                    continue;

                Sprite sprite = parse(type);
                double rot = (sprite == Sprite.LOCKED_EXIT) ? lockedExitRotation : sprite.rotation;

                renderCell(sprite, x, y, rot, scaledTileSize, camX, camY);
            }
        }
    }

    private void renderCell(Sprite sprite, int x, int y, double rotation,
            double scaledTileSize, double camX, double camY) {
        Image img = images.get(sprite);
        if (img == null)
            return;

        double size = scaledTileSize;
        double screenX = (x - camX) * size;
        double screenY = (y - camY) * size;

        if (!sprite.getIfIsFullTile()) {
            Image back = images.get(sprite.getBack());
            if (back != null) {
                mapGC.drawImage(back, screenX, screenY, size, size);
            }
        }

        if (Sprite.LOCKED_EXIT == sprite) {
            rotation = lockedExitRotation;
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

    private double getLockedExitRotation() {
        int exitX = board.getExitX();
        int exitY = board.getExitY();

        if (exitY == Board.BOARD_HEIGHT - 1) {
            return 180;
        }
        if (exitX == 0) {
            return -90;
        }
        if (exitX == Board.BOARD_WIDTH - 1) {
            return 90;
        }
        return 0;
    }

}
