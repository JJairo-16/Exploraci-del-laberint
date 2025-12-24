package com.jairo.app.gfx.sub_drawer;

import static com.jairo.utils.map_generator.Cells.UNKNOWN;

import java.util.List;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.Qualities;
import com.jairo.models.Board;
import com.jairo.services.ItemPlacer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;

public class WorldItemsRenderer {

    // ---------- Constantes (antes en Drawer) ----------
    private static final double FLOAT_SPEED_HZ = 0.9;
    private static final double FLOAT_AMPLITUDE_TILES = 0.12;

    private final ItemPlacer placer;
    private final Board board;
    private final CameraSystem cameraSystem;

    private final ImageStore images;
    private final GraphicsContext entitiesGC;

    public WorldItemsRenderer(
            ItemPlacer placer,
            Board board,
            CameraSystem cameraSystem,
            ImageStore images,
            GraphicsContext entitiesGC) {
        this.placer = placer;
        this.board = board;
        this.cameraSystem = cameraSystem;
        this.images = images;
        this.entitiesGC = entitiesGC;
        entitiesGC.setImageSmoothing(false);
    }

    public void render(int startX, int startY, int endX, int endY, long now, double scaledTileSize) {
        List<PlacedItem> items = placer.getPlacedItems(startX, startY, endX, endY);
        if (items == null || items.isEmpty())
            return;

        List<List<Integer>> cells = board.getCells(true);

        double t = now / 1_000_000_000.0;
        double omega = 2.0 * Math.PI * FLOAT_SPEED_HZ;
        double ampPx = scaledTileSize * FLOAT_AMPLITUDE_TILES;

        final double camX = cameraSystem.getCameraX();
        final double camY = cameraSystem.getCameraY();

        for (PlacedItem it : items) {
            int x = it.getX();
            int y = it.getY();

            int cellType = cells.get(y).get(x);
            if (!isDiscovered(cellType))
                continue;

            ItemType type = it.getType();
            Sprite sprite = spriteForItemType(type);
            if (sprite == null)
                continue;

            double screenX = (x - camX) * scaledTileSize;
            double screenY = (y - camY) * scaledTileSize;

            int seed = (x * 73856093) ^ (y * 19349663) ^ (type.hashCode());
            double phase = (seed & 0xFFFF) / 65535.0 * (2.0 * Math.PI);

            double yOffset = 0.0;
            if (type.shouldFloat()) {
                yOffset = Math.sin(t * omega + phase) * ampPx;
            }

            Image img = images.get(sprite);
            if (img == null)
                continue;

            double drawY = screenY + yOffset;

            drawQualityBorder(scaledTileSize, t, screenX, phase, img, drawY, it.quality);
            entitiesGC.drawImage(img, screenX, drawY, scaledTileSize, scaledTileSize);
        }
    }

    // ---------- Helpers / Estado ----------
    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }

    private Sprite spriteForItemType(ItemType type) {
        return (type == null) ? null : type.getSprite();
    }

    private void drawQualityBorder(
            double size,
            double t,
            double screenX,
            double phase,
            Image img,
            double drawY,
            Qualities q) {
        drawBorder(
                size, t, screenX, phase, img, drawY,
                q.red, q.green, q.blue,
                0.55, 0.20,
                3.5,
                0.075,
                0.75);
    }

    private static final DropShadow SHARED_DROP_SHADOW = new DropShadow();
    static {
        SHARED_DROP_SHADOW.setOffsetX(0);
        SHARED_DROP_SHADOW.setOffsetY(0);
    }

    private void drawBorder(
            double size,
            double t,
            double screenX,
            double phase,
            Image img,
            double drawY,
            int red, int green, int blue,
            double baseAlpha,
            double pulseAlpha,
            double pulseSpeed,
            double radiusScale,
            double spread) {
        entitiesGC.save();

        double pulse = 0.5 + 0.5 * Math.sin(t * pulseSpeed + phase);
        double radius = Math.max(1.0, size * radiusScale);

        SHARED_DROP_SHADOW.setRadius(radius);
        SHARED_DROP_SHADOW.setSpread(spread);
        SHARED_DROP_SHADOW.setColor(javafx.scene.paint.Color.rgb(
                red, green, blue,
                Math.min(1.0, baseAlpha + pulse * pulseAlpha)));

        entitiesGC.setEffect(SHARED_DROP_SHADOW);
        entitiesGC.drawImage(img, screenX, drawY, size, size);

        entitiesGC.restore();
    }
}
