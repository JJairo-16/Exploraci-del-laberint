package com.jairo.app.gfx;

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

import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

public class Drawer {
    private static final Logger log = LoggerFactory.getLogger(Drawer.class);

    // HUD glow (extraído)
    private static final GlowParams HUD_GLOW = new GlowParams(
            0.65, 0.30,
            3.5,
            0.12,
            0.75);

    // HUD float (solo COINS_POWER cuando está enabled)
    private static final double HUD_FLOAT_SPEED_HZ = 1.05;
    private static final double HUD_FLOAT_AMPLITUDE_PX = 4.0;

    // Suavizado al activar/desactivar (más alto = más rápido converge)
    private static final double HUD_FLOAT_SMOOTHING = 11.0;

    // Estado persistente del offset del icono (para que vuelva suave)
    private double coinsHudFloatOffsetPx = 0.0;
    private double lastHudT = -1.0;

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

    private boolean renderFps = false;

    public void switchFps() {
        renderFps = !renderFps;
    }

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
        if (renderFps)
            renderFps();

        renderPosition(model);
        renderCoins(model);
        renderHudOrderedItems(model);

        renderInventory();
    }

    private void renderFps() {
        long now = renderLoop.getLastNow(); // mismo "now" que usa el renderLoop
        updateFps(now);

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
        hudGC.fillText("x" + coins, model.coinTextX,
                model.coinTextBaselineY);
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

        // ✅ baseY: posición fija (para barra)
        // ✅ iconY: posición que puede flotar (solo icono)
        double baseY = r.y;
        double iconY = r.y;

        // --- flotación con retorno suave solo para COINS_POWER (solo icono) ---
        if (type == SpecialType.COINS_POWER) {
            double dt = 0.0;
            if (lastHudT >= 0.0) {
                dt = Math.max(0.0, t - lastHudT);
            }
            lastHudT = t;

            boolean enabled = simulator.isCoinsPowerSprintBoostEnabled();

            double target = 0.0;
            if (enabled) {
                double omega = 2.0 * Math.PI * HUD_FLOAT_SPEED_HZ;
                target = Math.sin(t * omega) * HUD_FLOAT_AMPLITUDE_PX;
            }

            double alpha = 1.0 - Math.exp(-HUD_FLOAT_SMOOTHING * dt);
            coinsHudFloatOffsetPx = coinsHudFloatOffsetPx + (target - coinsHudFloatOffsetPx) * alpha;

            iconY += coinsHudFloatOffsetPx; // ✅ solo se mueve el icono
        }

        // Glow + icono (usa iconY)
        GlowEffectRenderer.applyRgb(
                hudGC,
                img,
                r.x,
                iconY,
                r.w,
                t,
                0.0,
                q.red, q.green, q.blue,
                HUD_GLOW);

        hudGC.drawImage(img, r.x, iconY, r.w, r.h);

        // Solo COINS_POWER tiene barra
        if (type != SpecialType.COINS_POWER)
            return;

        double radar = simulator.getRadar();
        if (radar == -1.0)
            return;

        double p = normalizeRadarTo01(radar); // 0..1

        double barH = r.h * RADAR_BAR_H_RATIO;

        // ✅ barY fijo: usa baseY, NO iconY
        double barY = baseY + (r.h - barH) * 0.5;

        double barX = r.x + r.w + RADAR_BAR_GAP_PX;
        double barW = RADAR_BAR_W_PX;

        // fondo
        hudGC.setFill(Color.rgb(0, 0, 0, RADAR_BAR_ALPHA_BG));
        hudGC.fillRoundRect(barX, barY, barW, barH, 4, 4);

        // relleno (de abajo hacia arriba)
        double fillH = barH * p;
        double fillY = barY + (barH - fillH);

        // ✅ gradiente verde abajo -> rojo arriba
        hudGC.setFill(RADAR_GRADIENT);
        hudGC.fillRoundRect(barX, fillY, barW, fillH, 4, 4);

        // borde sutil
        hudGC.setStroke(Color.rgb(255, 255, 255, 0.25));
        hudGC.strokeRoundRect(barX, barY, barW, barH, 4, 4);
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

    // ----- COINS_POWER radar bar -----
    private static final double RADAR_BAR_GAP_PX = 6.0;
    private static final double RADAR_BAR_W_PX = 8.0;
    private static final double RADAR_BAR_H_RATIO = 0.90; // % de la altura del icono
    private static final double RADAR_BAR_ALPHA_BG = 0.35;
    private static final double RADAR_BAR_ALPHA_FG = 0.85;

    private static final LinearGradient RADAR_GRADIENT = new LinearGradient(
            0, 1, 0, 0, // y: 1 (abajo) -> 0 (arriba)
            true, // proportional al bounding box del shape
            CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(0, 255, 0, RADAR_BAR_ALPHA_FG)), // abajo
            new Stop(1.0, Color.rgb(255, 0, 0, RADAR_BAR_ALPHA_FG)) // arriba
    );

    private static double normalizeRadarTo01(double radar) {
        // radar > -1 significa "activo"
        // Soporta dos formatos típicos:
        // - [0..1]
        // - [0..100]
        if (radar <= 1.0)
            return Math.max(0.0, Math.min(1.0, radar));
        return Math.max(0.0, Math.min(1.0, radar / 100.0));
    }
}
