package com.jairo.app.ui;

import javafx.beans.value.ChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsula la lògica de les dimensions:
 * - Repartiment de l’amplada entre el panell esquerre i el panell dret
 * - Càlcul del tileSize segons la mida real del panell esquerre
 * - Ajust de la mida d’un o diversos Canvas perquè encaixin amb tiles sencers
 */
public final class Dimensions {
    private static final Logger log = LoggerFactory.getLogger(Dimensions.class);

    // * Ràtios fixes
    private static final double LEFT_RATIO = 0.65;
    private static final double RIGHT_RATIO = 0.35;

    private double tileSize = 0;

    /**
     * Aplica els bindings d’amplada al rightPanel i al leftPane
     * basant-se en el HBox arrel (root).
     */
    public void bindPanels(HBox root, VBox rightPanel, StackPane leftPane) {
        // * Amplada disponible real (tenint en compte l’spacing)
        var availableWidth = root.widthProperty()
                .subtract(root.spacingProperty());

        // * Panell esquerre (60%)
        leftPane.prefWidthProperty().bind(availableWidth.multiply(LEFT_RATIO));
        leftPane.minWidthProperty().bind(leftPane.prefWidthProperty());
        leftPane.maxWidthProperty().bind(leftPane.prefWidthProperty());

        // * Panell dret (40%)
        rightPanel.prefWidthProperty().bind(availableWidth.multiply(RIGHT_RATIO));
        rightPanel.minWidthProperty().bind(rightPanel.prefWidthProperty());
        rightPanel.maxWidthProperty().bind(rightPanel.prefWidthProperty());
    }

    /**
     * Connecta un listener per recalcular el tileSize i reajustar
     * els canvas quan canvia la mida del leftPane.
     */
    public void attachTileResizer(
            StackPane leftPane,
            int boardWidth,
            int boardHeight,
            Canvas... canvases) {

        ChangeListener<Number> listener =
                (obs, oldV, newV) -> recalcAndResize(
                        leftPane, boardWidth, boardHeight, canvases);

        leftPane.widthProperty().addListener(listener);
        leftPane.heightProperty().addListener(listener);

        // * Primer càlcul
        recalcAndResize(leftPane, boardWidth, boardHeight, canvases);
    }

    /**
     * Recalcula el tileSize i ajusta els canvas perquè la seva mida
     * sigui múltiple del tile.
     */
    public void recalcAndResize(
            StackPane leftPane,
            int boardWidth,
            int boardHeight,
            Canvas... canvases) {

        double paneW = leftPane.getWidth();
        double paneH = leftPane.getHeight();

        // ? Durant el layout inicial
        if (paneW <= 1 || paneH <= 1) {
            log.debug(
                    "LeftPane has no valid size yet (width={}, height={})",
                    paneW, paneH);
            return;
        }

        double tileX = paneW / boardWidth;
        double tileY = paneH / boardHeight;

        double tile = Math.min(tileX, tileY);
        if (tile <= 0) return;

        double newWidth = paneW - (paneW % tile);
        double newHeight = paneH - (paneH % tile);

        for (Canvas c : canvases) {
            c.setWidth(newWidth);
            c.setHeight(newHeight);
        }

        this.tileSize = tile;

        log.trace(
                "Canvas resized to {}x{} (pane size: {}x{})",
                newWidth, newHeight, paneW, paneH);
    }

    public double getTileSize() {
        return tileSize;
    }
}
