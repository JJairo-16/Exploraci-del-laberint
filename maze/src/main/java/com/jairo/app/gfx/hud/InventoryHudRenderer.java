package com.jairo.app.gfx.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.sub_drawer.GlowEffectRenderer;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.models.Inventory;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Renderiza el HUD del inventario de powers (slots + selección + contador).
 *
 * - Solo renderiza un objeto si se tiene en inventario (count > 0),
 *   salvo los que estén en ALWAYS_RENDER.
 * - ALWAYS_RENDER: objetos que se renderizan siempre.
 * - NO_COUNTER: objetos a los que NO se les dibuja contador.
 *
 * - Si se supera MAX_ITEMS_PER_ROW, continúa en la fila de abajo (wrap por filas).
 */
public class InventoryHudRenderer {

    private final ImageStore images;

    // Reutiliza la lista visible para evitar alloc por frame
    private final List<ItemType> visible = new ArrayList<>(Math.max(8, PowerType.values().length));

    // =========================
    // Listas de control
    // =========================

    /** Objetos que siempre se renderizan, aunque no tengas. */
    private static final Set<ItemType> ALWAYS_RENDER = Set.of(
    );

    /** Objetos que NO deben mostrar contador (aunque tengan count). */
    private static final Set<ItemType> NO_COUNTER = Set.of(
            PowerType.KEY
    );

    // =========================
    // Layout por filas
    // =========================
    private static final int MAX_ITEMS_PER_ROW = 4;

    // ---------- Tiempo estable de animación ----------
    private static final long MAX_DT_NS = 100_000_000L; // 0.10s clamp
    private long lastNowNs = 0L;
    private double animTimeSec = 0.0;

    // Glow params
    private static final GlowEffectRenderer.GlowParams HUD_GLOW = new GlowEffectRenderer.GlowParams(
            0.65, 0.30,
            3.5,
            0.10,
            0.75);

    public InventoryHudRenderer(ImageStore images) {
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

    public void render(GraphicsContext hudGC, Inventory inventory, long nowNs, double canvasW, Font baseHudFont) {
        List<ItemType> powers = inventory.snapshotPowers();
        if (powers.isEmpty() || inventory == null || hudGC == null)
            return;

        updateAnimClock(nowNs);
        double t = animTimeSec;

        // =========================
        // Determinar seleccionado (1-based -> 0-based)
        // =========================
        int selectedIndex = inventory.getSelectedPowerIndex();
        int selectedI = selectedIndex - 1;

        ItemType selectedPower = null;
        if (selectedI >= 0 && selectedI < powers.size()) {
            selectedPower = powers.get(selectedI);
        }

        // =========================
        // Filtrar visibles (reutilizando lista) + calcular selectedVisibleIndex sin indexOf
        // =========================
        visible.clear();
        int selectedVisibleIndex = -1;

        for (ItemType p : powers) {
            if (p == null) continue;

            int count = inventory.getCount(p);
            boolean hasIt = count > 0;

            if (hasIt || ALWAYS_RENDER.contains(p)) {
                if (p == selectedPower) {
                    selectedVisibleIndex = visible.size(); // índice antes de añadir
                }
                visible.add(p);
            }
        }

        if (visible.isEmpty()) return;

        final double padding = 25;
        final double slotSize = 40;
        final double gap = 8;
        final double iconPadding = 6;

        // separación vertical entre filas
        final double rowGap = 10;
        final double rowStep = slotSize + rowGap;

        final double selectedGrow = 4;
        final double baseRadius = 8;
        final double selectedRadius = 10;
        final double iconGrow = 10;

        // calcular columnas/filas
        int cols = Math.max(1, Math.min(MAX_ITEMS_PER_ROW, visible.size()));

        // Ancho de UNA fila (máximo cols)
        double rowW = cols * slotSize + (cols - 1) * gap;

        // Arranque: alineado a la derecha usando el ancho de fila
        double startX = canvasW - padding - rowW;
        double startY = padding;

        hudGC.setLineWidth(2);

        for (int i = 0; i < visible.size(); i++) {
            ItemType power = visible.get(i);
            int count = inventory.getCount(power);

            // posición por grid
            int col = i % cols;
            int row = i / cols;

            double x = startX + col * (slotSize + gap);
            double y = startY + row * rowStep;

            boolean isSelected = (i == selectedVisibleIndex);

            double s = isSelected ? (slotSize + selectedGrow) : slotSize;
            double offset = isSelected ? (selectedGrow / 2.0) : 0.0;
            double sx = x - offset;
            double sy = y - offset;

            // Fondo
            hudGC.setFill(isSelected ? Color.rgb(0, 0, 0, 0.45) : Color.rgb(0, 0, 0, 0.35));
            hudGC.fillRoundRect(
                    sx, sy, s, s,
                    isSelected ? selectedRadius : baseRadius,
                    isSelected ? selectedRadius : baseRadius);

            // Borde slot
            if (isSelected) {
                hudGC.setStroke(Color.rgb(90, 200, 255, 0.95));
                hudGC.setLineWidth(3);
            } else {
                hudGC.setStroke(Color.rgb(255, 255, 255, 0.55));
                hudGC.setLineWidth(2);
            }
            hudGC.strokeRoundRect(
                    sx, sy, s, s,
                    isSelected ? selectedRadius : baseRadius,
                    isSelected ? selectedRadius : baseRadius);

            // Icono + glow
            if (power.getSprite() != null) {
                Image img = images.get(power.getSprite());
                if (img != null) {
                    double iconSize = s - iconPadding * 2;
                    double iconDrawSize = iconSize + iconGrow;

                    double ix = sx + (s - iconDrawSize) / 2.0 + (0.5 / (isSelected ? 2.0 : 1.0));
                    double iy = sy + (s - iconDrawSize) / 2.0 + (isSelected ? 0.5 : 0);

                    double phase = i * 0.9;

                    Qualities q = power.getQuality();

                    GlowEffectRenderer.applyRgb(
                            hudGC,
                            img,
                            ix,
                            iy,
                            iconDrawSize,
                            t,
                            phase,
                            q.red, q.green, q.blue,
                            HUD_GLOW);

                    hudGC.drawImage(img, ix, iy, iconDrawSize, iconDrawSize);
                }
            }

            // Contador: solo si count >= 2 y no está en NO_COUNTER
            boolean drawCounter = !NO_COUNTER.contains(power) && count >= 2;

            if (drawCounter) {
                String txt = String.valueOf(count);

                Font countFont = Font.font(baseHudFont.getFamily(), FontWeight.BOLD, 16);
                hudGC.setFont(countFont);

                double tw = txt.length() * 9.0;
                double th = 16.0;

                double margin = 3;

                double tx = sx + s - margin - tw;
                double ty = sy + s - margin;

                double padX = 4;
                double padY = 2;

                double bx = tx - padX;
                double by = ty - th + padY;
                double bw = tw + padX * 2;
                double bh = th + padY;

                hudGC.setFill(Color.rgb(0, 0, 0, 0.35));
                hudGC.fillRoundRect(bx, by, bw, bh, 6, 6);

                hudGC.setStroke(isSelected ? Color.rgb(90, 200, 255, 0.55) : Color.rgb(255, 255, 255, 0.18));
                hudGC.setLineWidth(1);
                hudGC.strokeRoundRect(bx, by, bw, bh, 6, 6);

                hudGC.setFill(Color.rgb(0, 0, 0, 0.95));
                hudGC.fillText(txt, tx - 1, ty);
                hudGC.fillText(txt, tx + 1, ty);
                hudGC.fillText(txt, tx, ty - 1);
                hudGC.fillText(txt, tx, ty + 1);

                hudGC.setFill(Color.WHITE);
                hudGC.fillText(txt, tx, ty);

                hudGC.setFont(baseHudFont);
                hudGC.setLineWidth(2);
            }
        }

        hudGC.setLineWidth(2);
    }

    // ====== FPS ======
    private String cachedFpsFamily;
    private Font cachedFpsFont;

    private final Text fpsMeasure = new Text();
    private String lastFpsText;
    private Font lastMeasureFont;
    private double lastTextW;
    private double lastTextH;

    private static final Color FPS_BG = Color.rgb(0, 0, 0, 0.30);
    private static final Color FPS_STROKE = Color.rgb(255, 255, 255, 0.20);
    private static final Color TEXT_SHADOW = Color.rgb(0, 0, 0, 0.95);
    private static final Color TEXT_WHITE = Color.WHITE;

    private Font getFpsFont(Font baseHudFont) {
        String fam = (baseHudFont != null && baseHudFont.getFamily() != null) ? baseHudFont.getFamily() : "System";
        if (cachedFpsFont == null || !fam.equals(cachedFpsFamily)) {
            cachedFpsFamily = fam;
            cachedFpsFont = Font.font(fam, FontWeight.BOLD, 14);
        }
        return cachedFpsFont;
    }

    public void renderFps(GraphicsContext hudGC, String fpsText, double centerX, double baselineY, Font baseHudFont) {
        if (hudGC == null || fpsText == null || fpsText.isBlank())
            return;

        Font fpsFont = getFpsFont(baseHudFont);

        if (lastFpsText == null || !lastFpsText.equals(fpsText) || lastMeasureFont != fpsFont) {
            fpsMeasure.setFont(fpsFont);
            fpsMeasure.setText(fpsText);

            var b = fpsMeasure.getLayoutBounds();
            lastTextW = Math.ceil(b.getWidth());
            lastTextH = Math.ceil(b.getHeight());

            lastFpsText = fpsText;
            lastMeasureFont = fpsFont;
        }

        hudGC.setFont(fpsFont);

        final double padX = 10;
        final double padY = 6;

        double boxW = lastTextW + padX * 2;
        double boxH = lastTextH + padY;

        double bx = centerX - boxW / 2.0;
        double by = baselineY - lastTextH + (padY / 2.0);

        hudGC.setFill(FPS_BG);
        hudGC.fillRoundRect(bx, by, boxW, boxH, 8, 8);

        hudGC.setStroke(FPS_STROKE);
        hudGC.setLineWidth(1);
        hudGC.strokeRoundRect(bx, by, boxW, boxH, 8, 8);

        double textX = centerX - lastTextW / 2.0;

        hudGC.setFill(TEXT_SHADOW);
        hudGC.fillText(fpsText, textX - 1, baselineY);
        hudGC.fillText(fpsText, textX + 1, baselineY);
        hudGC.fillText(fpsText, textX, baselineY - 1);
        hudGC.fillText(fpsText, textX, baselineY + 1);

        hudGC.setFill(TEXT_WHITE);
        hudGC.fillText(fpsText, textX, baselineY);

        hudGC.setFont(baseHudFont);
        hudGC.setLineWidth(2);
    }
}
