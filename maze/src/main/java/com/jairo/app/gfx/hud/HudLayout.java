package com.jairo.app.gfx.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HudLayout {

    // =========================
    // Reglas / Constantes HUD
    // =========================
    private static final double HUD_LEFT_X = 50;
    private static final double HUD_TOP_Y = 40;

    // NUEVO: FPS por encima de la posición
    private static final double FPS_LINE_GAP = 22; // separación vertical desde HUD_TOP_Y hacia arriba

    // Separación entre líneas de "x:" y "y:"
    private static final double POSITION_LINE_GAP = 24;

    // Coin: baseline alineada con "x:"
    private static final double COIN_BASELINE_Y = HUD_TOP_Y;
    private static final double COIN_SIZE = 25;
    private static final double COIN_X = 110;

    // Coin: ajuste para dibujar el icono encima de la baseline
    private static final double COIN_ICON_Y_OFFSET = 7.5;
    private static final double COIN_TEXT_OFFSET_X = 28;

    // Items (cheated/boots): tamaño relativo a coin
    private static final double HUD_ITEM_SCALE = 1.7;

    // Apilado items: más compacto
    private static final double HUD_ITEM_PADDING_Y = 3.0;

    // Anclaje de items: “bajo la posición, a la izquierda”, con tweaks
    private static final double ITEMS_SHIFT_RIGHT = 8.0; // un poco más a la derecha
    private static final double ITEMS_SHIFT_DOWN = 12.0; // un poco más abajo
    private static final double ITEMS_RELATIVE_TO_COIN_X = 3; // coinX - coinSize * factor

    // =========================
    // Tipos de salida
    // =========================
    public static final class Rect {
        public final double x, y, w, h;
        public Rect(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public double bottom() { return y + h; }
    }

    public static final class HudModel {
        // NUEVO: FPS
        public final double fpsTextX;
        public final double fpsTextY;

        // Posición
        public final double posXTextX;
        public final double posXTextY;
        public final double posYTextX;
        public final double posYTextY;

        // Coin
        public final Rect coinIcon;
        public final double coinTextX;
        public final double coinTextBaselineY;

        // Items (en el mismo orden que la lista recibida)
        public final List<Rect> hudItems;

        public HudModel(
                double fpsTextX, double fpsTextY,
                double posXTextX, double posXTextY,
                double posYTextX, double posYTextY,
                Rect coinIcon, double coinTextX, double coinTextBaselineY,
                List<Rect> hudItems
        ) {
            this.fpsTextX = fpsTextX;
            this.fpsTextY = fpsTextY;
            this.posXTextX = posXTextX;
            this.posXTextY = posXTextY;
            this.posYTextX = posYTextX;
            this.posYTextY = posYTextY;
            this.coinIcon = coinIcon;
            this.coinTextX = coinTextX;
            this.coinTextBaselineY = coinTextBaselineY;
            this.hudItems = hudItems;
        }
    }

    /**
     * @param hudItemsOrdered lista de items YA CONSEGUIDOS y en el orden en que se consiguieron.
     *                        HudLayout no filtra: si metes algo aquí, se coloca.
     */
    public HudModel compute(List<?> hudItemsOrdered) {
        // ---- FPS ----
        // Lo colocamos encima de la línea de "x:" para que no moleste.
        double fpsTextX = HUD_LEFT_X;
        double fpsTextY = HUD_TOP_Y - FPS_LINE_GAP;

        // ---- Position texts ----
        double posXTextX = HUD_LEFT_X;
        double posXTextY = HUD_TOP_Y;

        double posYTextX = HUD_LEFT_X;
        double posYTextY = HUD_TOP_Y + POSITION_LINE_GAP;

        // ---- Coin ----
        double coinIconX = COIN_X;
        double coinIconY = COIN_BASELINE_Y - COIN_SIZE + COIN_ICON_Y_OFFSET;
        Rect coinIcon = new Rect(coinIconX, coinIconY, COIN_SIZE, COIN_SIZE);

        double coinTextX = COIN_X + COIN_TEXT_OFFSET_X;
        double coinTextBaselineY = COIN_BASELINE_Y;

        // ---- HUD items ----
        if (hudItemsOrdered == null || hudItemsOrdered.isEmpty()) {
            return new HudModel(
                    fpsTextX, fpsTextY,
                    posXTextX, posXTextY,
                    posYTextX, posYTextY,
                    coinIcon, coinTextX, coinTextBaselineY,
                    Collections.emptyList()
            );
        }

        double itemSize = COIN_SIZE * HUD_ITEM_SCALE;

        // Baseline de la segunda línea ("y:") y desplazamos para colocar items debajo
        double startX = (COIN_X - COIN_SIZE * ITEMS_RELATIVE_TO_COIN_X) + ITEMS_SHIFT_RIGHT;
        double startY = (posYTextY + ITEMS_SHIFT_DOWN);

        List<Rect> rects = new ArrayList<>(hudItemsOrdered.size());
        double y = startY;
        for (int i = 0; i < hudItemsOrdered.size(); i++) {
            rects.add(new Rect(startX, y, itemSize, itemSize));
            y += itemSize + HUD_ITEM_PADDING_Y;
        }

        return new HudModel(
                fpsTextX, fpsTextY,
                posXTextX, posXTextY,
                posYTextX, posYTextY,
                coinIcon, coinTextX, coinTextBaselineY,
                rects
        );
    }
}
