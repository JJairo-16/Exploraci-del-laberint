package com.jairo.app.gfx.sub_drawer;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.app.gfx.player_skins.HeldItemTuning;
import com.jairo.app.gfx.player_skins.HeldItemTuningStore;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.gfx.sub_drawer.GlowEffectRenderer.GlowParams;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.models.Inventory;
import com.jairo.services.Simulator;
import com.jairo.services.sub_simulator.coin_system.CoinsPowerState;
import com.jairo.utils.KeyBind.Action;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class PlayerRenderer {
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

    private static final long MAX_DT_NS = 100_000_000L;
    private long lastNowNs = 0L;
    private double animTimeSec = 0.0;

    private static final GlowParams HELD_ITEM_GLOW = new GlowParams(
            0.55, 0.20,
            3.5,
            0.06,
            0.65);

    private static final GlowParams TRANSCENDENT_HELD_ITEM_GLOW = new GlowParams(
            0.72,
            0.28,
            5.0,
            0.075,
            0.78);

    // ✅ Aura del jugador: parpadeo visible
    // baseAlpha = brillo base
    // pulseAlpha = cuánto sube/baja el brillo (más alto => más parpadeo)
    // pulseSpeed = velocidad
    private static final GlowParams PLAYER_AURA_BLINK = new GlowParams(
            0.18, // base (bajo)
            0.85, // pulso (alto => parpadea)
            2.0,  // velocidad del parpadeo
            0.30, // radio relativo al size
            0.55);

    public PlayerRenderer(Simulator simulator, GraphicsContext entitiesGC, GraphicsContext hudGC, ImageStore images) {
        this.simulator = simulator;
        this.entitiesGC = entitiesGC;
        this.hudGC = hudGC;
        this.images = images;
    }

    private void updateAnimClock(long nowNs) {
        if (lastNowNs == 0L) {
            lastNowNs = nowNs;
            return;
        }

        long dt = nowNs - lastNowNs;
        lastNowNs = nowNs;

        if (dt < 0L) dt = 0L;
        if (dt > MAX_DT_NS) dt = MAX_DT_NS;

        animTimeSec += dt / 1_000_000_000.0;
    }

    // ✅ helper: rota un vector (x,y) en grados
    private static double[] rotateVec(double x, double y, double deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[] { x * cos - y * sin, x * sin + y * cos };
    }

    // ✅ misma rotación que usa el jugador al dibujarse rotado
    private double getPlayerRotationDeg() {
        return switch (simulator.getLastMovement()) {
            case UP -> 180;
            case RIGHT -> -90;
            case LEFT -> 90;
            default -> 0;
        };
    }

    public void renderPlayer(double size, double cameraX, double cameraY) {
        cleanArrow();

        // ✅ IMPORTANTE: si esto está comentado, el aura NO “late” a menos que se
        // renderice la flecha
        updateAnimClock(now);

        Simulator.Position pos = simulator.getPlayerPosition();
        screenX = (pos.x() - cameraX) * size;
        screenY = (pos.y() - cameraY) * size;
        this.size = size;

        Image playerImg = images.get(Sprite.PLAYER);
        if (playerImg == null) return;

        double t = animTimeSec;

        // ✅ rotación actual del jugador (solo si tu skin rota al jugador)
        boolean playerUsesRotation = (Sprite.PLAYER.getRotation() == 180);
        double playerRotDeg = playerUsesRotation ? getPlayerRotationDeg() : 0.0;

        // Player + aura parpadeante
        if (playerUsesRotation) drawPlayerWithRotation(playerImg, t);
        else entitiesGC.drawImage(playerImg, screenX, screenY, size, size);

        boolean hasCursor = SkinManager.get().current().needArrow();
        if (hasCursor) direction = simulator.getCurrentAction();

        Inventory inv = simulator.getInventory();
        PowerType item = (PowerType) inv.getSelectedPower();
        if (item == null || !inv.has(item)) return;

        HeldItemTuning baseTuning = SkinManager.get().heldItemTuning(item);
        HeldItemTuning helItemTuning = HeldItemTuningStore.get().get(inv, item, baseTuning);

        double baseItemSize = size * helItemTuning.baseScale();
        double itemSize = hasCursor ? baseItemSize : (baseItemSize * helItemTuning.noCursorScaleMul());

        double cx = screenX + size / 2.0;
        double cy = screenY + size / 2.0;

        double offset = size * 0.16;

        // Posición "sin rotar" (como estaba antes)
        double ox = cx - itemSize / 2.0;
        double oy = cy - itemSize / 2.0;

        if (hasCursor) {
            ox += offset * helItemTuning.cursorOffsetMulX();
            oy += offset * helItemTuning.cursorOffsetMulY();
        } else {
            ox += offset * helItemTuning.noCursorOffsetMulX();
            oy += offset * helItemTuning.noCursorOffsetMulY();
        }

        // Rotación "sin rotar" del item (como estaba antes)
        double itemRotDeg = hasCursor ? helItemTuning.rotationDeg() : helItemTuning.noCursorRotationDeg();

        // ✅ Adaptar posición y rotación del ítem si el jugador está rotado
        if (playerUsesRotation && playerRotDeg != 0.0) {
            // Centro del item
            double itemCx = ox + itemSize / 2.0;
            double itemCy = oy + itemSize / 2.0;

            // Vector desde el centro del jugador al centro del item
            double vx = itemCx - cx;
            double vy = itemCy - cy;

            // Rotar ese vector según la rotación del jugador
            double[] rv = rotateVec(vx, vy, playerRotDeg);

            // Nuevo centro del item (ya rotado alrededor del jugador)
            double newItemCx = cx + rv[0];
            double newItemCy = cy + rv[1];

            // Recalcular esquina superior izq
            ox = newItemCx - itemSize / 2.0;
            oy = newItemCy - itemSize / 2.0;

            // El item acompaña la rotación del jugador
            itemRotDeg += playerRotDeg;
        }

        Image itemImg = images.get(item.getSprite());
        if (itemImg == null) return;

        double phase = item.hashCode() * 0.001;

        drawHeldItemBorder(
                entitiesGC,
                itemImg,
                ox,
                oy,
                itemSize,
                itemRotDeg,
                t,
                phase,
                item.getQuality());

        entitiesGC.save();
        entitiesGC.translate(ox + itemSize / 2.0, oy + itemSize / 2.0);
        entitiesGC.rotate(itemRotDeg);
        entitiesGC.drawImage(itemImg, -itemSize / 2.0, -itemSize / 2.0, itemSize, itemSize);
        entitiesGC.restore();
    }

    // Arrow (sin cambios)
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
        if (direction == null) return;

        this.now = now;
        updateAnimClock(now);

        double t = animTimeSec;
        double wave = Math.sin(t * Math.PI * 2.0 * ANIM_SPEED_HZ);

        double animOffset = wave * (size * ANIM_OFFSET_MAX);
        double alpha = OPACITY_MIN + (wave + 1.0) * 0.5 * (OPACITY_MAX - OPACITY_MIN);

        Image arrow = images.get(Sprite.ARROW);
        if (arrow == null) return;

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
        if (img == null || q == null) return;

        gc.save();
        gc.translate(x + size / 2.0, y + size / 2.0);
        gc.rotate(rotationDeg);

        GlowParams glow = (CoinsPowerState.getLevel() == 5) ? TRANSCENDENT_HELD_ITEM_GLOW : HELD_ITEM_GLOW;

        GlowEffectRenderer.applyRgb(
                gc,
                img,
                -size / 2.0,
                -size / 2.0,
                size,
                t,
                phase,
                q.red, q.green, q.blue,
                glow);

        gc.restore();
    }

    private void drawPlayerWithRotation(Image playerImg, double t) {
        double rotationDeg = getPlayerRotationDeg();

        // Centro del jugador
        double cx = screenX + size / 2.0;
        double cy = screenY + size / 2.0;

        entitiesGC.save();
        entitiesGC.translate(cx, cy);
        entitiesGC.rotate(rotationDeg);

        // Dibuja en coords locales centradas
        double x = -size / 2.0;
        double y = -size / 2.0;

        // Aura parpadeante (detrás)
        Qualities q = Qualities.TRANSCENDENT;
        double phase = 1.234;

        GlowEffectRenderer.applyRgb(
                entitiesGC,
                playerImg,
                x, y,
                size,
                t,
                phase,
                q.red, q.green, q.blue,
                PLAYER_AURA_BLINK);

        // Sprite normal encima
        entitiesGC.drawImage(playerImg, x, y, size, size);

        entitiesGC.restore();
    }
}
