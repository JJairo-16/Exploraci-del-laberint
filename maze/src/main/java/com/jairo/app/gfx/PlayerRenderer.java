package com.jairo.app.gfx;

import com.jairo.app.gfx.player_skins.HeldItemTuning;
import com.jairo.app.gfx.player_skins.HeldItemTuningStore;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.models.Inventory;
import com.jairo.services.Simulator;
import com.jairo.utils.KeyBind.Action;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class PlayerRenderer {
    // #region Player and others
    private final Simulator simulator;
    private final GraphicsContext entitiesGC;
    private final GraphicsContext hudGC;
    private final ImageStore images;

    private static final double SIZE_MULTIPLIER = 0.9;
    private static final double OFFSET_MULTIPLIER = 0.1;

    private double screenX;
    private double screenY;
    private double size;
    private Action direction;

    private long now;

    public PlayerRenderer(Simulator simulator, GraphicsContext entitiesGC, GraphicsContext hudGC, ImageStore images) {
        this.simulator = simulator;
        this.entitiesGC = entitiesGC;
        this.hudGC = hudGC;
        this.images = images;
    }

    public void renderPlayer(double size, double cameraX, double cameraY) {
        cleanArrow();
        Simulator.Position pos = simulator.getPlayerPosition();

        screenX = (pos.x() - cameraX) * size;
        screenY = (pos.y() - cameraY) * size;
        this.size = size;

        // Player
        entitiesGC.drawImage(images.get(Sprite.PLAYER), screenX, screenY, size, size);

        boolean hasCursor = SkinManager.get().current().needArrow();

        if (hasCursor) {
            direction = simulator.getCurrentAction();
        }

        Inventory inv = simulator.getInventory();
        PowerType item = (PowerType) inv.getSelectedPower(); // ahora es PowerType
        if (item == null || !inv.has(item))
            return;

        // ---- TUNING base (por skin+item) + override (por jugador+item) ----
        HeldItemTuning baseTuning = SkinManager.get().heldItemTuning(item);
        HeldItemTuning helItemTuning = HeldItemTuningStore.get().get(inv, item, baseTuning);

        // Tamaño
        double baseItemSize = size * helItemTuning.baseScale();
        double itemSize = hasCursor ? baseItemSize : (baseItemSize * helItemTuning.noCursorScaleMul());

        // Centro del jugador
        double cx = screenX + size / 2.0;
        double cy = screenY + size / 2.0;

        // Offset base
        double offset = size * 0.16;

        // Base: centrado por tamaño REAL
        double ox = cx - itemSize / 2.0;
        double oy = cy - itemSize / 2.0;

        // Offset según modo
        if (hasCursor) {
            ox += offset * helItemTuning.cursorOffsetMulX();
            oy += offset * helItemTuning.cursorOffsetMulY();
        } else {
            ox += offset * helItemTuning.noCursorOffsetMulX();
            oy += offset * helItemTuning.noCursorOffsetMulY();
        }

        // Rotación según modo (grados)
        double rotation = hasCursor ? helItemTuning.rotationDeg() : helItemTuning.noCursorRotationDeg();

        Image itemImg = images.get(item.getSprite());

        // tiempo
        double t = System.nanoTime() / 1_000_000_000.0;
        double phase = item.hashCode() * 0.001;

        // --- BORDE CUANDO ESTÁ EN LA MANO (ROTADO) ---
        drawHeldItemBorder(
                entitiesGC,
                itemImg,
                ox,
                oy,
                itemSize,
                rotation,
                t,
                phase,
                item.getQuality()
        );

        // --- SPRITE NORMAL ENCIMA (ROTADO) ---
        entitiesGC.save();
        entitiesGC.translate(ox + itemSize / 2.0, oy + itemSize / 2.0);
        entitiesGC.rotate(rotation);
        entitiesGC.drawImage(itemImg, -itemSize / 2.0, -itemSize / 2.0, itemSize, itemSize);
        entitiesGC.restore();
    }
    // #endregion

    // #region Arrow
    private static final double ANIM_SPEED_HZ = 1.2;
    private static final double ANIM_OFFSET_MAX = 0.05;

    private static final double OPACITY_MIN = 0.8;
    private static final double OPACITY_MAX = 0.95;

    private double padding = 4;
    private double arrowSize;
    private double arrowX;
    private double arrowY;

    public void renderArrow() {
        renderArrow(now);
    }

    public void renderArrow(long now) {
        if (direction == null)
            return;

        this.now = now;

        double t = now / 1_000_000_000.0;
        double wave = Math.sin(t * Math.PI * 2.0 * ANIM_SPEED_HZ);

        double animOffset = Math.sin(t * Math.PI * 2.0 * ANIM_SPEED_HZ) * (size * ANIM_OFFSET_MAX);
        double alpha = OPACITY_MIN + (wave + 1.0) * 0.5 * (OPACITY_MAX - OPACITY_MIN);

        Image arrow = images.get(Sprite.ARROW);

        arrowSize = size * SIZE_MULTIPLIER;
        double offset = size * OFFSET_MULTIPLIER;
        double rotation;

        switch (direction) {
            case UP -> {
                arrowX = screenX + (size - arrowSize) / 2.0;
                arrowY = screenY - offset - arrowSize - animOffset;
                rotation = 0;
            }
            case RIGHT -> {
                arrowX = screenX + size + offset + animOffset;
                arrowY = screenY + (size - arrowSize) / 2.0;
                rotation = 90;
            }
            case DOWN -> {
                arrowX = screenX + (size - arrowSize) / 2.0;
                arrowY = screenY + size + offset + animOffset;
                rotation = 180;
            }
            case LEFT -> {
                arrowX = screenX - offset - arrowSize - animOffset;
                arrowY = screenY + (size - arrowSize) / 2.0;
                rotation = -90;
            }
            default -> {
                return;
            }
        }

        cleanArrow();

        hudGC.save();
        hudGC.setGlobalAlpha(alpha);
        hudGC.translate(arrowX + arrowSize / 2.0, arrowY + arrowSize / 2.0);
        hudGC.rotate(rotation);
        hudGC.drawImage(arrow, -arrowSize / 2.0, -arrowSize / 2.0, arrowSize, arrowSize);
        hudGC.restore();
    }

    private void cleanArrow() {
        double clearX = arrowX - padding;
        double clearY = arrowY - padding;
        double clearW = arrowSize + padding * 2;
        double clearH = arrowSize + padding * 2;

        hudGC.clearRect(clearX, clearY, clearW, clearH);
    }

    // #endregion

    private void drawHeldItemBorder(
            GraphicsContext gc,
            Image img,
            double x,
            double y,
            double size,
            double rotationDeg,
            double t,
            double phase,
            Qualities q) {

        int red = q.red;
        int green = q.green;
        int blue = q.blue;

        gc.save();

        double pulse = 0.5 + 0.5 * Math.sin(t * 3.5 + phase);
        double radius = Math.max(1.0, size * 0.06);

        javafx.scene.effect.DropShadow ds = new javafx.scene.effect.DropShadow();
        ds.setRadius(radius);
        ds.setSpread(0.65);
        ds.setOffsetX(0);
        ds.setOffsetY(0);
        ds.setColor(javafx.scene.paint.Color.rgb(
                red, green, blue,
                Math.min(1.0, 0.55 + pulse * 0.20)));

        gc.setEffect(ds);

        // Dibujo rotado del "borde"
        gc.translate(x + size / 2.0, y + size / 2.0);
        gc.rotate(rotationDeg);
        gc.drawImage(img, -size / 2.0, -size / 2.0, size, size);

        gc.restore();
    }
}
