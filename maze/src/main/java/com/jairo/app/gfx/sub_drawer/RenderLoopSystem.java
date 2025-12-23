// File: src/main/java/com/jairo/app/gfx/sub_drawer/RenderLoopSystem.java
package com.jairo.app.gfx.sub_drawer;

/**
 * Encapsula:
 * - cálculo del viewport visible (startX/startY/endX/endY)
 * - cache del último viewport + lastNow
 * - orquestación de frames: completo (map+entities+postFx+hud+arrow) y dinámico (entities)
 *
 * No conoce JavaFX ni Board/Cells: trabaja con números y callbacks.
 */
public class RenderLoopSystem {

    public record Viewport(int startX, int startY, int endX, int endY) { }

    @FunctionalInterface
    public interface ViewportRenderer {
        void render(Viewport vp, long now);
    }

    private long lastNow = System.nanoTime();
    private Viewport lastViewport = new Viewport(0, 0, 0, 0);

    public long getLastNow() {
        return lastNow;
    }

    public Viewport getLastViewport() {
        return lastViewport;
    }

    public Viewport computeViewport(
            double cameraX,
            double cameraY,
            double scaledTileSize,
            double canvasWidthPx,
            double canvasHeightPx,
            int boardW,
            int boardH
    ) {
        double tilesInWidth = canvasWidthPx / scaledTileSize;
        double tilesInHeight = canvasHeightPx / scaledTileSize;

        int startX = (int) Math.floor(cameraX) - 1;
        int startY = (int) Math.floor(cameraY) - 1;
        int endX = (int) Math.ceil(cameraX + tilesInWidth) + 1;
        int endY = (int) Math.ceil(cameraY + tilesInHeight) + 1;

        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(boardW - 1, endX);
        endY = Math.min(boardH - 1, endY);

        return new Viewport(startX, startY, endX, endY);
    }

    /**
     * Frame completo:
     * - map (usa vp)
     * - entities (usa vp)
     * - postFx
     * - hud
     * - arrow (opcional, lo decides tú en el callback)
     */
    public void renderFullFrame(
            long now,
            Viewport vp,
            Runnable clearMap,
            ViewportRenderer renderMapWithViewport,
            Runnable clearEntities,
            Runnable renderPlayer,
            ViewportRenderer renderItemsWithViewport,
            Runnable renderPostFx,
            Runnable renderHud,
            Runnable renderArrowOptional
    ) {
        this.lastNow = now;
        this.lastViewport = vp;

        clearMap.run();
        renderMapWithViewport.render(vp, now);

        clearEntities.run();
        renderPlayer.run();
        renderItemsWithViewport.render(vp, now);

        renderPostFx.run();
        renderHud.run();

        renderArrowOptional.run();
    }

    /**
     * Frame dinámico (solo entities): limpiar + player + items
     * Reutiliza el último viewport cacheado.
     */
    public void renderDynamicFrame(
            long now,
            Runnable clearEntities,
            Runnable renderPlayer,
            ViewportRenderer renderItemsWithViewport
    ) {
        this.lastNow = now;

        clearEntities.run();
        renderPlayer.run();
        renderItemsWithViewport.render(lastViewport, now);
    }
}
