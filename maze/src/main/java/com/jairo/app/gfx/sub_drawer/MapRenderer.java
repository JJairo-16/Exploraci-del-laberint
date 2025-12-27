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
    private final GraphicsContext mapGC;

    private final double lockedExitRotation;

    // ---- Hot-path caches (avoid repeated lookups / virtual calls) ----
    private final Sprite[] sprites = Sprite.values();
    private final Image[] spriteImgCache;
    private final Image[] backImgCache;
    private final boolean[] fullTileCache;
    private final double[] baseRotationCache;

    public MapRenderer(Board board, CameraSystem cameraSystem, ImageStore images, GraphicsContext mapGC) {
        this.board = board;
        this.cameraSystem = cameraSystem;
        this.mapGC = mapGC;

        lockedExitRotation = getLockedExitRotation();

        // Build caches once: images.get(sprite) might be a map lookup, so cache per Sprite.
        int n = sprites.length;
        spriteImgCache = new Image[n];
        backImgCache = new Image[n];
        fullTileCache = new boolean[n];
        baseRotationCache = new double[n];

        for (int i = 0; i < n; i++) {
            Sprite s = sprites[i];
            spriteImgCache[i] = images.get(s);
            backImgCache[i] = images.get(s.getBack());
            fullTileCache[i] = s.getIfIsFullTile();
            baseRotationCache[i] = s.getRotation();
        }
    }

    public void render(RenderLoopSystem.Viewport vp, double scaledTileSize) {
        // Board method likely allocates; if you can change Board later, make it fill a reused buffer.
        final int[][] visArea = board.getVisibilityArea(vp.startX(), vp.startY(), vp.endX(), vp.endY());

        final double camX = cameraSystem.getCameraX();
        final double camY = cameraSystem.getCameraY();
        final double size = scaledTileSize;

        final int startX = vp.startX();
        final int startY = vp.startY();
        final int endX = vp.endX();
        final int endY = vp.endY();

        // Compute first row screenY once, then increment per row (saves many mults).
        double screenY = (startY - camY) * size;

        for (int y = startY; y <= endY; y++) {
            final int[] row = visArea[y - startY];

            // Compute first tile screenX once, then increment per tile (saves many mults).
            double screenX = (startX - camX) * size;

            for (int x = startX; x <= endX; x++) {
                final int type = row[x - startX];
                if (type == UNKNOWN) {
                    screenX += size;
                    continue;
                }

                final Sprite sprite = parse(type);
                renderCellCached(sprite, screenX, screenY, size);

                screenX += size;
            }

            screenY += size;
        }
    }

    /**
     * Optimized render: expects screenX/screenY already computed.
     * Uses cached images/flags/rotations per Sprite.
     */
    private void renderCellCached(Sprite sprite, double screenX, double screenY, double size) {
        final int idx = sprite.ordinal();

        final Image img = spriteImgCache[idx];
        if (img == null) return;

        // Draw background first for non-full tiles (cached)
        if (!fullTileCache[idx]) {
            final Image back = backImgCache[idx];
            if (back != null) {
                mapGC.drawImage(back, screenX, screenY, size, size);
            }
        }

        // Rotation: cached base rotation, with LOCKED_EXIT override.
        double rotation = (sprite == Sprite.LOCKED_EXIT) ? lockedExitRotation : baseRotationCache[idx];

        if (rotation == 0.0) {
            mapGC.drawImage(img, screenX, screenY, size, size);
            return;
        }

        // Rotated draw: avoid extra conditionals and recomputations.
        final double half = size * 0.5;
        final double cx = screenX + half;
        final double cy = screenY + half;

        mapGC.save();
        mapGC.translate(cx, cy);
        mapGC.rotate(rotation);
        mapGC.drawImage(img, -half, -half, size, size);
        mapGC.restore();
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
