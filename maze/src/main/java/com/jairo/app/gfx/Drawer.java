package com.jairo.app.gfx;

import static com.jairo.app.gfx.DrawerParser.parse;
import static com.jairo.utils.map_generator.Cells.UNKNOWN;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.models.Board;
import com.jairo.services.ItemPlacer;
import com.jairo.services.Simulator;
import com.jairo.utils.PositionHud;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

public class Drawer {
    private static final Logger log = LoggerFactory.getLogger(Drawer.class);

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

    // ---------- Constantes ----------
    private static final double FLOAT_SPEED_HZ = 0.9;
    private static final double FLOAT_AMPLITUDE_TILES = 0.12;

    private static final double ZOOM_POINT = 0.1;

    private static final double HUD_LEFT_X = 50;
    private static final double HUD_TOP_Y = 40;

    private static final double COIN_BASELINE_Y = 40; // misma línea que "x:"
    private static final double COIN_TEXT_OFFSET_X = 28;

    private static final double COIN_SIZE = 20; // tamaño visual de la moneda
    private static final double COIN_X = 110;

    // ---------- Cámara ----------
    // Càmera en coordenades de tiles
    private double cameraX = 0.0;
    private double cameraY = 0.0;

    // ---------- Zoom ----------
    private final double baseZoom = (Board.BOARD_HEIGHT * Board.BOARD_WIDTH) * 1.5 / 1000.0;
    private final double minZoom = baseZoom - 0.6;
    private final double maxZoom = baseZoom + 1.2;
    private double zoom = baseZoom;

    // ---------- HUD / Renderers ----------
    private final PositionHud ph;
    private final PlayerRenderer playerRenderer;
    private final double tileSize;

    private Font hudFont = Font.loadFont(
            getClass().getResourceAsStream("/fonts/Roboto-Regular.ttf"),
            20);

    // ---------- Estado de render ----------
    private long lastNow = System.nanoTime();

    private int lastStartX;
    private int lastStartY;
    private int lastEndX;
    private int lastEndY;

    // ---------- Ctor ----------
    public Drawer(Canvas map, Canvas entities, Canvas postFx, Canvas hud, Simulator simulator, double tileSize) {
        this.map = map;
        this.entities = entities;
        this.postFx = postFx;
        this.hud = hud;

        this.simulator = simulator;
        this.placer = simulator.getItemPlacer();
        this.board = simulator.getBoardRef();
        this.tileSize = tileSize;

        images = ImageStore.getInstance();

        mapGC = map.getGraphicsContext2D();
        entitiesGC = entities.getGraphicsContext2D();
        postFxGC = postFx.getGraphicsContext2D();
        hudGC = hud.getGraphicsContext2D();

        entitiesGC.setImageSmoothing(false);

        this.playerRenderer = new PlayerRenderer(simulator, entitiesGC, hudGC, images);

        ph = new PositionHud(Board.BOARD_WIDTH, Board.BOARD_HEIGHT);

        if (log.isInfoEnabled()) {
            log.info("Drawer created. canvas=({}x{}), tileSize={}, initialZoom={}, minZoom={}, maxZoom={}",
                    map.getWidth(), map.getHeight(), tileSize, zoom, minZoom, maxZoom);
        }
    }

    // ---------- API de zoom ----------
    public void zoomIn() {
        double oldZoom = zoom;

        if (zoom < maxZoom) {
            zoom = round1(zoom + ZOOM_POINT);
        }

        if (zoom != oldZoom) {
            keepPlayerScreenPositionAfterZoom(oldZoom, zoom);
            if (log.isDebugEnabled())
                log.debug("Zoom in: {} -> {}", oldZoom, zoom);
        } else {
            if (log.isDebugEnabled())
                log.debug("Zoom in ignored (at MAX_ZOOM={})", maxZoom);
        }

        update();
    }

    public void zoomOut() {
        double oldZoom = zoom;

        if (zoom > minZoom) {
            zoom = round1(zoom - ZOOM_POINT);
        }

        if (zoom != oldZoom) {
            keepPlayerScreenPositionAfterZoom(oldZoom, zoom);
            if (log.isDebugEnabled())
                log.debug("Zoom out: {} -> {}", oldZoom, zoom);
        } else {
            if (log.isDebugEnabled())
                log.debug("Zoom out ignored (at MIN_ZOOM={})", minZoom);
        }

        update();
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double scaledTileSize() {
        return tileSize * zoom;
    }

    /**
     * Manté el jugador a la mateixa posició en pantalla quan canvies el zoom,
     * perquè el zoom no faci un “salt”.
     */
    private void keepPlayerScreenPositionAfterZoom(double oldZoom, double newZoom) {
        Simulator.Position p = simulator.getPlayerPosition();

        double oldSize = tileSize * oldZoom;
        double newSize = tileSize * newZoom;

        // Posició del jugador a la pantalla ABANS del zoom (en píxels)
        double px = (p.x() - cameraX) * oldSize;
        double py = (p.y() - cameraY) * oldSize;

        // Reajusta la càmera per mantenir aquests mateixos píxels després del zoom
        cameraX = p.x() - (px / newSize);
        cameraY = p.y() - (py / newSize);
    }

    // ---------- Helpers de renderitzat ----------
    private void renderCell(Sprite sprite, int x, int y, double rotation) {
        Image img = images.get(sprite);

        double size = scaledTileSize();
        double screenX = (x - cameraX) * size;
        double screenY = (y - cameraY) * size;

        if (!sprite.getIfIsFullTile()) {
            mapGC.drawImage(images.get(sprite.getBack()), screenX, screenY, size, size);
        }

        if (sprite == Sprite.LOCKED_EXIT) {
            int exitX = board.getExitX();
            int exitY = board.getExitY();

            // Abajo
            if (exitY == Board.BOARD_HEIGHT - 1) {
                rotation = 180;
            }
            // Izquierda
            else if (exitX == 0) {
                rotation = -90;
            }
            // Derecha
            else if (exitX == Board.BOARD_WIDTH - 1) {
                rotation = 90;
            }
        }

        if (rotation == 0) {
            mapGC.drawImage(img, screenX, screenY, size, size);
            return;
        }

        mapGC.save();

        double cx = screenX + size / 2.0;
        double cy = screenY + size / 2.0;

        mapGC.translate(cx, cy);
        mapGC.rotate(rotation);
        mapGC.drawImage(images.get(sprite), -size / 2.0, -size / 2.0, size, size);

        mapGC.restore();
    }

    private void clearEntities() {
        entitiesGC.clearRect(0, 0, entities.getWidth(), entities.getHeight());
    }

    private void clearMap() {
        mapGC.clearRect(0, 0, map.getWidth(), map.getHeight());
    }

    private void clearPostFx() {
        postFxGC.clearRect(0, 0, postFx.getWidth(), postFx.getHeight());
    }

    private void renderPlayer() {
        double size = scaledTileSize();
        playerRenderer.renderPlayer(size, cameraX, cameraY);
    }

    public void renderArrow(long now) {
        playerRenderer.renderArrow(now);
    }

    public void renderItems(long now) {
        lastNow = now;
        renderObjects(lastStartX, lastStartY, lastEndX, lastEndY, now);
    }

    public void renderFrame(long now) {
        // Solo la capa dinámica (entities): limpiar + player + items
        clearEntities();
        renderPlayer();
        renderItems(now);
    }

    // ---------- Càmera ----------
    private void updateCamera() {
        Simulator.Position pos = simulator.getPlayerPosition();

        // Tiles visibles segons el zoom i el canvas
        double tilesInWidth = map.getWidth() / scaledTileSize();
        double tilesInHeight = map.getHeight() / scaledTileSize();

        double cameraPadding = Math.max(2.0, tilesInWidth * 0.1);

        // Guardar per als logs
        double beforeX = cameraX;
        double beforeY = cameraY;

        // Dead zone
        double marginX = tilesInWidth * 0.25;
        double marginY = tilesInHeight * 0.25;

        double left = cameraX + marginX;
        double right = cameraX + tilesInWidth - marginX;
        double top = cameraY + marginY;
        double bottom = cameraY + tilesInHeight - marginY;

        if (pos.x() < left) {
            cameraX = pos.x() - marginX;
        } else if (pos.x() > right) {
            cameraX = pos.x() - (tilesInWidth - marginX);
        }

        if (pos.y() < top) {
            cameraY = pos.y() - marginY;
        } else if (pos.y() > bottom) {
            cameraY = pos.y() - (tilesInHeight - marginY);
        }

        // Límits adaptatius segons el board i el zoom
        List<List<Integer>> visibility = board.getCells(true);
        int boardH = visibility.size();
        int boardW = (boardH > 0) ? visibility.get(0).size() : 0;

        double minX;
        double maxX;
        double minY;
        double maxY;

        // Si el tauler és més petit que el visible, el centrem (evita “buits” estranys)
        if (boardW <= tilesInWidth) {
            minX = maxX = (boardW - tilesInWidth) / 2.0;
        } else {
            minX = -cameraPadding;
            maxX = boardW - tilesInWidth + cameraPadding;
        }

        if (boardH <= tilesInHeight) {
            minY = maxY = (boardH - tilesInHeight) / 2.0;
        } else {
            minY = -cameraPadding;
            maxY = boardH - tilesInHeight + cameraPadding;
        }

        double unclampedX = cameraX;
        double unclampedY = cameraY;

        cameraX = clamp(cameraX, minX, maxX);
        cameraY = clamp(cameraY, minY, maxY);

        boolean moved = (cameraX != beforeX) || (cameraY != beforeY);
        boolean clamped = (cameraX != unclampedX) || (cameraY != unclampedY);

        if (clamped) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Camera clamped. cam=({}, {}) limitsX=[{}, {}] limitsY=[{}, {}] tilesIn=({},{}) board=({},{}) zoom={}",
                        cameraX, cameraY, minX, maxX, minY, maxY,
                        tilesInWidth, tilesInHeight, boardW, boardH, zoom);
            }
        } else if (moved && log.isTraceEnabled()) {
            log.trace("Camera moved to ({}, {}) zoom={}", cameraX, cameraY, zoom);
        }
    }

    private double clamp(double v, double min, double max) {
        double minimum = Math.min(max, v);
        return Math.max(min, minimum);
    }

    // ---------- Update principal ----------
    public void update() {
        updateCamera();

        clearMap();

        List<List<Integer>> visibility = board.getCells(true);

        double tilesInWidth = map.getWidth() / scaledTileSize();
        double tilesInHeight = map.getHeight() / scaledTileSize();

        int startX = (int) Math.floor(cameraX) - 1;
        int startY = (int) Math.floor(cameraY) - 1;
        int endX = (int) Math.ceil(cameraX + tilesInWidth) + 1;
        int endY = (int) Math.ceil(cameraY + tilesInHeight) + 1;

        int h = visibility.size();
        int w = (h > 0) ? visibility.get(0).size() : 0;

        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(w - 1, endX);
        endY = Math.min(h - 1, endY);

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int type = visibility.get(y).get(x);
                if (!isDiscovered(type))
                    continue;

                Sprite sprite = parse(type);
                renderCell(sprite, x, y, sprite.rotation);
            }
        }

        lastStartX = startX;
        lastStartY = startY;
        lastEndX = endX;
        lastEndY = endY;

        clearEntities();
        renderPlayer();
        renderItems(lastNow);

        renderPostFx();
        renderHud();

        if (SkinManager.get().current().needArrow()) {
            playerRenderer.renderArrow();
        }
    }

    // ---------- PostFX ----------
    private void renderPostFx() {
        clearPostFx();

        double w = postFx.getWidth();
        double h = postFx.getHeight();

        // 1) TINTE global (ajusta el color/alpha a tu gusto)
        postFxGC.setFill(Color.rgb(30, 80, 120, 0.14)); // azulito atmosférico
        postFxGC.fillRect(0, 0, w, h);

        // 2) VIGNETTE (oscurece bordes)
        Paint vignette = new RadialGradient(
                0, 0,
                0.5, 0.5,
                0.85, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.45)));
        postFxGC.setFill(vignette);
        postFxGC.fillRect(0, 0, w, h);
    }

    // ---------- HUD ----------
    public void renderHud() {
        hudGC.clearRect(0, 0, hud.getWidth(), hud.getHeight());

        renderPosition();
        renderCoins();
        renderInventory();
    }

    private void renderPosition() {
        hudGC.setFill(Color.WHITE);
        hudGC.setFont(hudFont);

        Simulator.Position pos = simulator.getPlayerPosition();
        int x = ph.getX(pos.x());
        int y = ph.getY(pos.y());

        hudGC.fillText("x: " + x, HUD_LEFT_X, HUD_TOP_Y);
        hudGC.fillText("y: " + y, HUD_LEFT_X, HUD_TOP_Y + 24);
    }

    private void renderCoins() {
        hudGC.setFill(Color.WHITE);
        hudGC.setFont(hudFont);

        int coins = simulator.getInventory()
                .getCount(BasicItemType.COIN);

        // Icono de la moneda (alineado con baseline del texto)
        hudGC.drawImage(
                images.get(Sprite.COIN),
                COIN_X,
                COIN_BASELINE_Y - COIN_SIZE + 5, // ajuste visual fino
                COIN_SIZE,
                COIN_SIZE);

        // Cantidad
        hudGC.fillText(
                "x" + coins,
                COIN_X + COIN_TEXT_OFFSET_X,
                COIN_BASELINE_Y);
    }

    private final List<ItemType> powers = List.of(PowerType.values());

    private void renderInventory() {
        if (powers == null || powers.isEmpty())
            return;

        // --- Layout (arriba derecha) ---
        final double padding = 25;
        final double slotSize = 40;
        final double gap = 8;
        final double iconPadding = 6;

        // --- Selección ---
        int selectedIndex = simulator.getInventory().getSelectedPowerIndex(); // 0..N
        int selectedI = selectedIndex - 1;

        final double selectedGrow = 4;
        final double baseRadius = 8;
        final double selectedRadius = 10;
        final double iconGrow = 10;

        double canvasW = hud.getWidth();

        double totalW = powers.size() * slotSize + (powers.size() - 1) * gap;
        double startX = canvasW - padding - totalW;
        double startY = padding;

        hudGC.setLineWidth(2);

        for (int i = 0; i < powers.size(); i++) {
            ItemType power = powers.get(i);
            int count = simulator.getInventory().getCount(power);

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

            // Borde
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

            // Icono
            if (power != null && power.getSprite() != null) {
                Image img = images.get(power.getSprite());
                if (img != null) {
                    double iconSize = s - iconPadding * 2;

                    double iconDrawSize = iconSize + iconGrow;

                    double ix = sx + (s - iconDrawSize) / 2.0 + (0.5 / (isSelected ? 2.0 : 1.0));
                    double iy = sy + (s - iconDrawSize) / 2.0 + (isSelected ? 0.5 : 0);

                    // --- borde púrpura como en el suelo (solo si quieres para powers, o solo
                    // seleccionado, etc.) ---
                    double t = lastNow / 1_000_000_000.0; // igual que en renderObjects
                    double phase = i * 0.9; // fase simple para que no pulsen igual

                    // Ejemplo: aplicar púrpura a TODOS los powers del inventario
                    Qualities q = power.getQuality();
                    int red = q.red;
                    int green = q.green;
                    int blue = q.blue;

                    drawHudBorder(
                            iconDrawSize, t, ix, phase, img, iy,
                            red, green, blue,
                            0.65, 0.30,
                            3.5,
                            0.10, // un pelín más que en suelo porque icono es más pequeño
                            0.75);

                    // Sprite normal encima
                    hudGC.drawImage(img, ix, iy, iconDrawSize, iconDrawSize);
                }
            }

            // --- Cantidad (abajo derecha, visible + micro-placa) ---
            if (count > -1) {
                String txt = String.valueOf(count);

                Font countFont = Font.font(hudFont.getFamily(), javafx.scene.text.FontWeight.BOLD, 16);
                hudGC.setFont(countFont);

                // Medidas aproximadas (suficiente para 1-2 dígitos)
                double tw = txt.length() * 9.0;
                double th = 16.0;

                double margin = 3;

                // Posición del texto (abajo derecha)
                double tx = sx + s - margin - tw;
                double ty = sy + s - margin;

                // Micro-placa detrás del texto (muy pequeña, no invade el icono)
                double padX = 4;
                double padY = 2;

                double bx = tx - padX;
                double by = ty - th + padY; // sube la placa al baseline del texto
                double bw = tw + padX * 2;
                double bh = th + padY;

                hudGC.setFill(Color.rgb(0, 0, 0, 0.35));
                hudGC.fillRoundRect(bx, by, bw, bh, 6, 6);

                // Borde sutil (hereda el color si está seleccionado)
                hudGC.setStroke(isSelected ? Color.rgb(90, 200, 255, 0.55) : Color.rgb(255, 255, 255, 0.18));
                hudGC.setLineWidth(1);
                hudGC.strokeRoundRect(bx, by, bw, bh, 6, 6);

                // Stroke falso del texto (negro)
                hudGC.setFill(Color.rgb(0, 0, 0, 0.95));
                hudGC.fillText(txt, tx - 1, ty);
                hudGC.fillText(txt, tx + 1, ty);
                hudGC.fillText(txt, tx, ty - 1);
                hudGC.fillText(txt, tx, ty + 1);

                // Texto principal (blanco)
                hudGC.setFill(Color.WHITE);
                hudGC.fillText(txt, tx, ty);

                // Restore
                hudGC.setFont(hudFont);
                hudGC.setLineWidth(2); // por si venías de 1
            }
        }

        hudGC.setLineWidth(2);
    }

    // ---------- Helpers / Estado ----------
    /**
     * Ajusta això al valor que utilitzi el teu Board per a “no descobert”.
     * Molt habitual: -1, 3, etc.
     */
    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }

    public record CameraState(double cameraX, double cameraY, double zoom) {
    }

    public CameraState getCameraState() {
        return new CameraState(cameraX, cameraY, zoom);
    }

    public void setCameraState(CameraState state) {
        if (state == null)
            return;
        this.cameraX = state.cameraX();
        this.cameraY = state.cameraY();
        this.zoom = state.zoom();
    }

    // ---------- Items ----------
    private void renderObjects(int startX, int startY, int endX, int endY, long now) {
        double size = scaledTileSize();

        List<PlacedItem> items = placer.getPlacedItems();
        if (items == null || items.isEmpty())
            return;

        // Tiempo en segundos
        double t = now / 1_000_000_000.0;

        // Frecuencia angular (rad/s)
        double omega = 2.0 * Math.PI * FLOAT_SPEED_HZ;

        // Amplitud en píxeles (escala con el zoom)
        double ampPx = size * FLOAT_AMPLITUDE_TILES;

        // Cache de celdas visibles
        List<List<Integer>> cells = board.getCells(true);

        // Recomendado para pixel art (si tu versión de JavaFX lo soporta)
        entitiesGC.setImageSmoothing(false);

        for (PlacedItem it : items) {
            int x = it.getX();
            int y = it.getY();

            if (x < startX || x > endX || y < startY || y > endY)
                continue;

            int cellType = cells.get(y).get(x);
            if (!isDiscovered(cellType))
                continue;

            Sprite sprite = spriteForItemType(it.getType());
            if (sprite == null)
                continue;

            double screenX = (x - cameraX) * size;
            double screenY = (y - cameraY) * size;

            int seed = (x * 73856093) ^ (y * 19349663)
                    ^ (it.getType() != null ? it.getType().hashCode() : 0);
            double phase = (seed & 0xFFFF) / 65535.0 * (2.0 * Math.PI);

            double yOffset = 0.0;
            if (shouldFloat(it.getType())) {
                yOffset = Math.sin(t * omega + phase) * ampPx;
            }

            Image img = images.get(sprite);
            double drawY = screenY + yOffset;

            // Outline/glow SOLO para powerups (silueta real)
            drawQualityBorder(size, t, screenX, phase, img, drawY, it.quality);

            // Sprite normal encima (sin efecto)
            entitiesGC.drawImage(img, screenX, drawY, size, size);
        }
    }

    private void drawQualityBorder(double size, double t, double screenX, double phase, Image img, double drawY,
            Qualities q) {
        int red = q.red;
        int green = q.green;
        int blue = q.blue;

        drawBorder(
                size, t, screenX, phase, img, drawY,
                red, green, blue,
                0.55, 0.20, // alpha base / pulso
                3.5, // velocidad pulso
                0.075, // radius scale
                0.75 // spread
        );
    }

    private void drawBorder(
            double size,
            double t,
            double screenX,
            double phase,
            Image img,
            double drawY,
            int red, int green, int blue,
            double baseAlpha, // opacidad base
            double pulseAlpha, // cuánto suma el pulso
            double pulseSpeed, // velocidad del pulso
            double radiusScale, // tamaño del borde (relativo al tile)
            double spread // “borde” vs “blur”
    ) {
        entitiesGC.save();

        double pulse = 0.5 + 0.5 * Math.sin(t * pulseSpeed + phase);
        double radius = Math.max(1.0, size * radiusScale);

        javafx.scene.effect.DropShadow ds = new javafx.scene.effect.DropShadow();
        ds.setRadius(radius);
        ds.setSpread(spread);
        ds.setOffsetX(0);
        ds.setOffsetY(0);
        ds.setColor(javafx.scene.paint.Color.rgb(
                red, green, blue,
                Math.min(1.0, baseAlpha + pulse * pulseAlpha)));

        entitiesGC.setEffect(ds);
        entitiesGC.drawImage(img, screenX, drawY, size, size);

        entitiesGC.restore();
    }

    private Sprite spriteForItemType(ItemType type) {
        return (type == null) ? null : type.getSprite();
    }

    private boolean shouldFloat(ItemType type) {
        return true; // o filtra por tipo
    }

    private void drawHudBorder(
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

        javafx.scene.effect.DropShadow ds = new javafx.scene.effect.DropShadow();
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
