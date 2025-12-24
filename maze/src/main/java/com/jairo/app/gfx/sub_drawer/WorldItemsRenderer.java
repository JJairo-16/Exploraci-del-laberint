package com.jairo.app.gfx.sub_drawer;

import static com.jairo.utils.map_generator.Cells.UNKNOWN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.app.gfx.sub_drawer.tools.ItemAuraRenderer;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.Qualities;
import com.jairo.models.Board;
import com.jairo.services.ItemPlacer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;

public class WorldItemsRenderer {

    // ---------- Constantes ----------
    private static final double FLOAT_SPEED_HZ = 0.9;
    private static final double FLOAT_AMPLITUDE_TILES = 0.12;

    // Aura/border config
    private static final double BORDER_BASE_ALPHA = 0.55;
    private static final double BORDER_PULSE_ALPHA = 0.20;
    private static final double BORDER_PULSE_SPEED = 3.5;
    private static final double BORDER_RADIUS_SCALE = 0.075;
    private static final double BORDER_SPREAD = 0.75;

    // Cache phase by (x,y,typeKey)
    private final Map<Long, Double> phaseCache = new HashMap<>(2048);

    private final ItemPlacer placer;
    private final Board board;
    private final CameraSystem cameraSystem;

    private final ImageStore images;
    private final GraphicsContext entitiesGC;

    // Aura renderer (extraído)
    private final ItemAuraRenderer auraRenderer = new ItemAuraRenderer();

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
        if (items == null || items.isEmpty()) return;

        List<List<Integer>> cells = board.getCells(true);

        final double t = now / 1_000_000_000.0;
        final double omega = 2.0 * Math.PI * FLOAT_SPEED_HZ;
        final double ampPx = scaledTileSize * FLOAT_AMPLITUDE_TILES;

        final double camX = cameraSystem.getCameraX();
        final double camY = cameraSystem.getCameraY();

        // JavaFX version compatibility: this API uses getEffect(Effect) not getEffect()
        final Effect oldEffect = entitiesGC.getEffect(null);

        for (PlacedItem it : items) {
            final int x = it.getX();
            final int y = it.getY();

            final List<Integer> row = cells.get(y);
            final int cellType = row.get(x);
            if (!isDiscovered(cellType)) continue;

            final ItemType type = it.getType();
            final Sprite sprite = spriteForItemType(type);
            if (sprite == null) continue;

            final Image img = images.get(sprite);
            if (img == null) continue;

            final double screenX = (x - camX) * scaledTileSize;
            final double screenY = (y - camY) * scaledTileSize;

            // Cache phase: estable por (x,y,type)
            final double phase = getOrComputePhase(x, y, type);

            double drawY = screenY;
            if (type.shouldFloat()) {
                drawY += Math.sin(t * omega + phase) * ampPx;
            }

            // Aura/border (extraído)
            final Qualities q = it.quality;
            if (q != null) {
                auraRenderer.drawAura(
                        entitiesGC,
                        scaledTileSize,
                        t,
                        screenX,
                        phase,
                        img,
                        drawY,
                        q.red, q.green, q.blue,
                        BORDER_BASE_ALPHA,
                        BORDER_PULSE_ALPHA,
                        BORDER_PULSE_SPEED,
                        BORDER_RADIUS_SCALE,
                        BORDER_SPREAD,
                        oldEffect
                );
            }

            // Sprite normal
            entitiesGC.drawImage(img, screenX, drawY, scaledTileSize, scaledTileSize);
        }

        entitiesGC.setEffect(oldEffect);
    }

    // ---------- Helpers ----------
    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }

    private Sprite spriteForItemType(ItemType type) {
        return (type == null) ? null : type.getSprite();
    }

    private double getOrComputePhase(int x, int y, ItemType type) {
        final int typeKey = (type == null) ? 0 : type.hashCode();
        final long key = (((long) x) << 32) ^ (y & 0xffffffffL) ^ (((long) typeKey) << 1);

        final Double cached = phaseCache.get(key);
        if (cached != null) return cached;

        final int seed = (x * 73856093) ^ (y * 19349663) ^ typeKey;
        final double phase = ((seed & 0xFFFF) / 65535.0) * (2.0 * Math.PI);

        phaseCache.put(key, phase);
        return phase;
    }
}
