package com.jairo.app.gfx;

import com.jairo.app.gfx.player_skins.SkinManager;
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
    private static final double OFFSET_MULTIPLIER = -0.05;

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

        entitiesGC.drawImage(images.get(Sprite.PLAYER), screenX, screenY, size, size);

        if (!SkinManager.get().current().needArrow())
            return;

        direction = simulator.getCurrentAction();
        renderArrow(now);
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
}