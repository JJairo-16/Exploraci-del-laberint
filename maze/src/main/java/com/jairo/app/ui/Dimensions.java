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

    private final double rightPanelWidthRatio;
    private final double mainPanelWidthRatio;

    private double tileSize = 0;

    public Dimensions(double rightPanelWidthRatio, double mainPanelWidthRatio) {
        this.rightPanelWidthRatio = rightPanelWidthRatio;
        this.mainPanelWidthRatio = mainPanelWidthRatio;
    }

    /**
     * Aplica els bindings d’amplada al rightPanel i al leftPane
     * basant-se en el HBox arrel (root).
     */
    public void bindPanels(HBox root, VBox rightPanel, StackPane leftPane) {
        // * Amplada disponible real: amplada del root menys l’espaiat entre columnes
        // (hi ha 2 fills)
        var availableWidth = root.widthProperty().subtract(root.spacingProperty());

        // * Panell dret
        rightPanel.prefWidthProperty().bind(availableWidth.multiply(rightPanelWidthRatio));
        rightPanel.minWidthProperty().bind(rightPanel.prefWidthProperty());
        rightPanel.maxWidthProperty().bind(rightPanel.prefWidthProperty());

        // * Panell esquerre
        leftPane.prefWidthProperty().bind(availableWidth.multiply(mainPanelWidthRatio));
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
        ChangeListener<Number> listener = (obs, oldV, newV) -> recalcAndResize(leftPane, boardWidth, boardHeight,
                canvases);

        leftPane.widthProperty().addListener(listener);
        leftPane.heightProperty().addListener(listener);

        // * Primer càlcul (per si ja té mida)
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

        // ? En JavaFX, a l’inici pot retornar 0/1 mentre es munta el layout
        if (paneW <= 1 || paneH <= 1) {
            log.debug(
                    "LeftPane has no valid size yet (width={}, height={})",
                    paneW, paneH);
            return;
        }

        double tileX = paneW / boardWidth;
        double tileY = paneH / boardHeight;

        double tile = Math.min(tileX, tileY);
        if (tile <= 0)
            return;

        // * Ajustar a múltiples sencers del tile per evitar "restes" / buits
        double newWidth = paneW - (paneW % tile);
        double newHeight = paneH - (paneH % tile);

        for (Canvas c : canvases) {
            c.setWidth(newWidth);
            c.setHeight(newHeight);
        }

        this.tileSize = tile;
        
        log.trace("Canvas resized to {}x{} (pane size: {}x{})", newWidth, newHeight, paneW, paneH);
    }

    public double getTileSize() {
        return tileSize;
    }
}
