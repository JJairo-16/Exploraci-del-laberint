package com.jairo.app.gfx.hud;

import java.util.List;

import com.jairo.app.gfx.ImageStore;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.models.Inventory;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Renderiza el HUD del inventario de powers (slots + selección + contador).
 * No depende de Drawer: solo necesita GC, Inventory, tiempo y ancho del canvas.
 */
public class InventoryHudRenderer {

    private final ImageStore images;
    private final List<ItemType> powers = List.of(PowerType.values());

    public InventoryHudRenderer(ImageStore images) {
        this.images = images;
    }

    public void render(GraphicsContext hudGC, Inventory inventory, long nowNs, double canvasW, Font baseHudFont) {
        if (powers.isEmpty() || inventory == null)
            return;

        final double padding = 25;
        final double slotSize = 40;
        final double gap = 8;
        final double iconPadding = 6;

        int selectedIndex = inventory.getSelectedPowerIndex();
        int selectedI = selectedIndex - 1;

        final double selectedGrow = 4;
        final double baseRadius = 8;
        final double selectedRadius = 10;
        final double iconGrow = 10;

        double totalW = powers.size() * slotSize + (powers.size() - 1) * gap;
        double startX = canvasW - padding - totalW;
        double startY = padding;

        hudGC.setLineWidth(2);

        for (int i = 0; i < powers.size(); i++) {
            ItemType power = powers.get(i);
            int count = inventory.getCount(power);

            double x = startX + i * (slotSize + gap);
            double y = startY;

            boolean isSelected = (i == selectedI);

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
            if (power != null && power.getSprite() != null) {
                Image img = images.get(power.getSprite());
                if (img != null) {
                    double iconSize = s - iconPadding * 2;
                    double iconDrawSize = iconSize + iconGrow;

                    double ix = sx + (s - iconDrawSize) / 2.0 + (0.5 / (isSelected ? 2.0 : 1.0));
                    double iy = sy + (s - iconDrawSize) / 2.0 + (isSelected ? 0.5 : 0);

                    double t = nowNs / 1_000_000_000.0;
                    double phase = i * 0.9;

                    Qualities q = power.getQuality();

                    drawHudBorder(
                            hudGC,
                            iconDrawSize, t, ix, phase, img, iy,
                            q.red, q.green, q.blue,
                            0.65, 0.30,
                            3.5,
                            0.10,
                            0.75);

                    hudGC.drawImage(img, ix, iy, iconDrawSize, iconDrawSize);
                }
            }

            // Contador
            if (count > -1) {
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

                // “Shadow” del texto
                hudGC.setFill(Color.rgb(0, 0, 0, 0.95));
                hudGC.fillText(txt, tx - 1, ty);
                hudGC.fillText(txt, tx + 1, ty);
                hudGC.fillText(txt, tx, ty - 1);
                hudGC.fillText(txt, tx, ty + 1);

                hudGC.setFill(Color.WHITE);
                hudGC.fillText(txt, tx, ty);

                // Restaurar
                hudGC.setFont(baseHudFont);
                hudGC.setLineWidth(2);
            }
        }

        hudGC.setLineWidth(2);
    }

    private void drawHudBorder(
            GraphicsContext hudGC,
            double size,
            double t,
            double screenX,
            double phase,
            Image img,
            double screenY,
            int red, int green, int blue,
            double baseAlpha,
            double pulseAlpha,
            double pulseSpeed,
            double radiusScale,
            double spread) {

        hudGC.save();

        double pulse = 0.5 + 0.5 * Math.sin(t * pulseSpeed + phase);
        double radius = Math.max(1.0, size * radiusScale);

        DropShadow ds = new DropShadow();
        ds.setRadius(radius);
        ds.setSpread(spread);
        ds.setOffsetX(0);
        ds.setOffsetY(0);
        ds.setColor(Color.rgb(
                red, green, blue,
                Math.min(1.0, baseAlpha + pulse * pulseAlpha)));

        hudGC.setEffect(ds);
        hudGC.drawImage(img, screenX, screenY, size, size);

        hudGC.restore();
    }
}
