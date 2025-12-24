// File: src/main/java/com/jairo/app/gfx/Drawer.java
package com.jairo.app.gfx;

import static com.jairo.utils.map_generator.Cells.UNKNOWN;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.app.gfx.hud.HudLayout;
import com.jairo.app.gfx.hud.HudLayout.HudModel;
import com.jairo.app.gfx.hud.HudLayout.Rect;
import com.jairo.app.gfx.hud.InventoryHudRenderer;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.gfx.sub_drawer.CameraSystem;
import com.jairo.app.gfx.sub_drawer.GlowEffectRenderer;
import com.jairo.app.gfx.sub_drawer.GlowEffectRenderer.GlowParams;
import com.jairo.app.gfx.sub_drawer.MapRenderer;
import com.jairo.app.gfx.sub_drawer.PlayerRenderer;
import com.jairo.app.gfx.sub_drawer.PostFxRenderer;
import com.jairo.app.gfx.sub_drawer.RenderLoopSystem;
import com.jairo.app.gfx.sub_drawer.RenderLoopSystem.Viewport;
import com.jairo.app.gfx.sub_drawer.WorldItemsRenderer;
import com.jairo.app.gfx.sub_drawer.ZoomSystem;
import com.jairo.items.BasicItemType;
import com.jairo.items.Qualities;
import com.jairo.items.SpecialType;
import com.jairo.models.Board;
import com.jairo.services.ItemPlacer;
import com.jairo.services.Simulator;
import com.jairo.utils.PositionHud;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class Drawer {
    private static final Logger log = LoggerFactory.getLogger(Drawer.class);

    // HUD glow (extraído)
    private static final GlowParams HUD_GLOW = new GlowParams(
            0.65, 0.30,
            3.5,
            0.12,
            0.75);

    // ---------- FXML ----------
    @FXML
    private Canvas map;
    @FXML
    private Canvas entities;
    @FXML
    private Canvas postFx;
    @FXML
    private Canvas hud;

    // ---------- Stores / GCs ----------
    private final ImageStore images;

    private final GraphicsContext mapGC;
    private final GraphicsContext entitiesGC;
    private final GraphicsContext postFxGC;
    private final GraphicsContext hudGC;

    // ---------- Refs ----------
    private final Board board;
    private final Simulator simulator;
    private final ItemPlacer placer;

    // HUD items (orden de obtención)
    private final List<SpecialType> hudOrderedItems = new ArrayList<>();

    // Layout centralizado (reglas dentro del layout)
    private final HudLayout hudLayout = new HudLayout();

    // ---------- Cámara / Zoom / RenderLoop ----------
    private final CameraSystem cameraSystem = new CameraSystem();
    private final ZoomSystem zoomSystem;
    private final RenderLoopSystem renderLoop = new RenderLoopSystem();

    // ---------- Renderers ----------
    private final PositionHud ph;
    private final PlayerRenderer playerRenderer;
    private final InventoryHudRenderer inventoryHudRenderer;

    private final WorldItemsRenderer worldItemsRenderer; // (1) ya extraído
    private final PostFxRenderer postFxRenderer; // (3) nuevo
    private final MapRenderer mapRenderer; // (4) nuevo

    private static boolean renderFps = true;

    private Font hudFont = Font.loadFont(
            getClass().getResourceAsStream("/fonts/Roboto-Regular.ttf"),
            20);

    // ---------- Ctor ----------
    public Drawer(Canvas map, Canvas entities, Canvas postFx, Canvas hud, Simulator simulator, double tileSize) {
        this.map = map;
        this.entities = entities;
        this.postFx = postFx;
        this.hud = hud;

        this.simulator = simulator;
        this.placer = simulator.getItemPlacer();
        this.board = simulator.getBoardRef();

        this.zoomSystem = new ZoomSystem(tileSize);

        images = ImageStore.getInstance();

        mapGC = map.getGraphicsContext2D();
        entitiesGC = entities.getGraphicsContext2D();
        postFxGC = postFx.getGraphicsContext2D();
        hudGC = hud.getGraphicsContext2D();

        entitiesGC.setImageSmoothing(false);

        this.playerRenderer = new PlayerRenderer(simulator, entitiesGC, hudGC, images);
        this.inventoryHudRenderer = new InventoryHudRenderer(images);

        this.worldItemsRenderer = new WorldItemsRenderer(placer, board, cameraSystem, images, entitiesGC);
        this.postFxRenderer = new PostFxRenderer();
        this.mapRenderer = new MapRenderer(board, cameraSystem, images, mapGC);

        ph = new PositionHud(Board.BOARD_WIDTH, Board.BOARD_HEIGHT);

        if (log.isInfoEnabled()) {
            log.info("Drawer created. canvas=({}x{}), tileSize={}, initialZoom={}, minZoom={}, maxZoom={}",
                    map.getWidth(), map.getHeight(), tileSize,
                    zoomSystem.getZoom(), zoomSystem.getMinZoom(), zoomSystem.getMaxZoom());
        }
    }

    // ---------- HUD items ordering ----------
    private void syncHudOrderedItems() {
        for (SpecialType type : SpecialType.values()) {
            syncHudItem(type);
        }
    }

    private void syncHudItem(SpecialType type) {
        boolean has = simulator.getInventory().has(type);
        if (has) {
            if (!hudOrderedItems.contains(type)) {
                hudOrderedItems.add(type);
            }
        } else {
            hudOrderedItems.remove(type);
        }
    }

    // ---------- API de zoom ----------
    public void zoomIn() {
        Simulator.Position p = simulator.getPlayerPosition();
        zoomSystem.zoomIn(p.x(), p.y(), cameraSystem);
        update();
    }

    public void zoomOut() {
        Simulator.Position p = simulator.getPlayerPosition();
        zoomSystem.zoomOut(p.x(), p.y(), cameraSystem);
        update();
    }

    private double scaledTileSize() {
        return zoomSystem.scaledTileSize();
    }

    // ---------- Helpers ----------
    private void clearEntities() {
        entitiesGC.clearRect(0, 0, entities.getWidth(), entities.getHeight());
    }

    private void clearMap() {
        mapGC.clearRect(0, 0, map.getWidth(), map.getHeight());
    }

    private void renderPlayer() {
        double size = scaledTileSize();
        playerRenderer.renderPlayer(size, cameraSystem.getCameraX(), cameraSystem.getCameraY());
    }

    public void renderArrow(long now) {
        playerRenderer.renderArrow(now);
    }

    // ✅ dinámico (solo entities)
    public void renderFrame(long now) {
        renderLoop.renderDynamicFrame(
                now,
                this::clearEntities,
                this::renderPlayer,
                (vp, t) -> worldItemsRenderer.render(
                        vp.startX(), vp.startY(), vp.endX(), vp.endY(),
                        t,
                        scaledTileSize()));
    }

    // ---------- Cámara ----------
    private void updateCamera() {
        Simulator.Position pos = simulator.getPlayerPosition();

        List<List<Integer>> visibility = board.getCells(true);
        int boardH = visibility.size();
        int boardW = (boardH > 0) ? visibility.get(0).size() : 0;

        cameraSystem.updateCamera(
                pos.x(),
                pos.y(),
                map.getWidth(),
                map.getHeight(),
                scaledTileSize(),
                boardW,
                boardH);
    }

    // ---------- Update principal ----------
    public void update() {
        updateCamera();

        List<List<Integer>> visibility = board.getCells(true);
        int boardH = visibility.size();
        int boardW = (boardH > 0) ? visibility.get(0).size() : 0;

        Viewport vp = renderLoop.computeViewport(
                cameraSystem.getCameraX(),
                cameraSystem.getCameraY(),
                scaledTileSize(),
                map.getWidth(),
                map.getHeight(),
                boardW,
                boardH);

        long now = renderLoop.getLastNow();

        renderLoop.renderFullFrame(
                now,
                vp,
                this::clearMap,
                (v, t) -> mapRenderer.render(v, scaledTileSize()),
                this::clearEntities,
                this::renderPlayer,
                (v, t) -> worldItemsRenderer.render(
                        v.startX(), v.startY(), v.endX(), v.endY(),
                        t,
                        scaledTileSize()),
                () -> postFxRenderer.render(postFxGC, postFx.getWidth(), postFx.getHeight()),
                this::renderHud,
                () -> {
                    if (SkinManager.get().current().needArrow()) {
                        playerRenderer.renderArrow();
                    }
                });
    }

    // ---------- FPS HUD ----------
    private long fpsWindowStartNs = 0L;
    private int fpsFrames = 0;
    private double fpsValue = 0.0;

    // Actualiza 4 veces/seg (estable y sin parpadeo)
    private static final long FPS_WINDOW_NS = 250_000_000L; // 0.25s

    // ---------- HUD ----------
    public void renderHud() {
        hudGC.clearRect(0, 0, hud.getWidth(), hud.getHeight());

        syncHudOrderedItems();
        HudModel model = hudLayout.compute(hudOrderedItems);

        // ✅ FPS
        if (renderFps) renderFps();

        renderPosition(model);
        renderCoins(model);
        renderHudOrderedItems(model);

        renderInventory();
    }

    private void renderFps() {
        long now = renderLoop.getLastNow(); // mismo "now" que usa el renderLoop
        updateFps(now);

        // Si ya has añadido fpsTextX/fpsTextY en HudLayout:
        String fpsText = "FPS: " + (int) Math.round(fpsValue);
        double margin = 18;

        double fpsX = hud.getWidth() - margin - 60; // ancho aprox del texto
        double fpsY = margin + 74; // baseline del texto

        inventoryHudRenderer.renderFps(
                hudGC,
                fpsText,
                fpsX,
                fpsY,
                hudFont);
    }

    private void renderPosition(HudModel model) {
        hudGC.setFill(Color.WHITE);
        hudGC.setFont(hudFont);

        Simulator.Position pos = simulator.getPlayerPosition();
        int x = ph.getX(pos.x());
        int y = ph.getY(pos.y());

        hudGC.fillText("x: " + x, model.posXTextX, model.posXTextY);
        hudGC.fillText("y: " + y, model.posYTextX, model.posYTextY);
    }

    private void renderCoins(HudModel model) {
        hudGC.setFill(Color.WHITE);
        hudGC.setFont(hudFont);

        int coins = simulator.getInventory().getCount(BasicItemType.COIN);

        Image coinImg = images.get(Sprite.COIN);
        double t = renderLoop.getLastNow() / 1_000_000_000.0;

        Qualities q = BasicItemType.COIN.getQuality();
        Rect r = model.coinIcon;

        GlowEffectRenderer.applyRgb(
                hudGC,
                coinImg,
                r.x,
                r.y,
                r.w,
                t,
                0.0,
                q.red, q.green, q.blue,
                HUD_GLOW);

        hudGC.drawImage(coinImg, r.x, r.y, r.w, r.h);
        hudGC.fillText("x" + coins, model.coinTextX, model.coinTextBaselineY);
    }

    private void renderHudOrderedItems(HudModel model) {
        if (hudOrderedItems.isEmpty() || model.hudItems == null || model.hudItems.isEmpty())
            return;

        double t = renderLoop.getLastNow() / 1_000_000_000.0;

        int n = Math.min(hudOrderedItems.size(), model.hudItems.size());
        for (int i = 0; i < n; i++) {
            SpecialType type = hudOrderedItems.get(i);
            Rect r = model.hudItems.get(i);

            Sprite sprite = type.getSprite();
            renderHudItem(type, sprite, r, t);
        }
    }

    private void renderHudItem(SpecialType type, Sprite sprite, Rect r, double t) {
        if (sprite == null)
            return;

        Image img = images.get(sprite);
        if (img == null)
            return;

        Qualities q = type.getQuality();

        GlowEffectRenderer.applyRgb(
                hudGC,
                img,
                r.x,
                r.y,
                r.w,
                t,
                0.0,
                q.red, q.green, q.blue,
                HUD_GLOW);

        hudGC.drawImage(img, r.x, r.y, r.w, r.h);
    }

    private void renderInventory() {
        inventoryHudRenderer.render(
                hudGC,
                simulator.getInventory(),
                renderLoop.getLastNow(),
                hud.getWidth(),
                hudFont);
    }

    // ---------- Helpers / Estado ----------
    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }

    public record CameraState(double cameraX, double cameraY, double zoom) {
    }

    public CameraState getCameraState() {
        return new CameraState(cameraSystem.getCameraX(), cameraSystem.getCameraY(), zoomSystem.getZoom());
    }

    public void setCameraState(CameraState state) {
        if (state == null)
            return;
        cameraSystem.setCamera(state.cameraX(), state.cameraY());
        zoomSystem.setZoom(state.zoom());
    }

    private void updateFps(long nowNs) {
        if (fpsWindowStartNs == 0L) {
            fpsWindowStartNs = nowNs;
            fpsFrames = 0;
            fpsValue = 0.0;
            return;
        }

        fpsFrames++;

        long elapsed = nowNs - fpsWindowStartNs;
        if (elapsed >= FPS_WINDOW_NS) {
            fpsValue = (fpsFrames * 1_000_000_000.0) / elapsed;

            // Reiniciar ventana
            fpsWindowStartNs = nowNs;
            fpsFrames = 0;
        }
    }

}
