package com.jairo.app.gfx;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import static com.jairo.utils.map_generator.Cells.UNKNOWN;
import com.jairo.models.Board;
import com.jairo.services.Simulator;
import com.jairo.utils.PositionHud;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Drawer {
    private static final Logger log = LoggerFactory.getLogger(Drawer.class);

    @FXML
    private Canvas map;
    @FXML
    private Canvas entities;
    @FXML
    private Canvas hud;

    private final ImageStore images;
    private final GraphicsContext mapGC;
    private final GraphicsContext entitiesGC;
    private final GraphicsContext hudGC;

    private final Board board;
    private final Simulator simulator;

    // Càmera en coordenades de tiles
    private double cameraX = 0.0;
    private double cameraY = 0.0;

    // Zoom
    private final double baseZoom = (Board.BOARD_HEIGHT * Board.BOARD_WIDTH) * 1.5 / 1000.0;

    private final double minZoom = baseZoom - 0.5;
    private final double maxZoom = baseZoom + 0.5;

    private double zoom = baseZoom;

    private static final double ZOOM_POINT = 0.1;

    private final PositionHud ph;
    private final double tileSize;

    public Drawer(Canvas map, Canvas entities, Canvas hud, Simulator simulator, double tileSize) {
        this.map = map;
        this.entities = entities;
        this.hud = hud;

        this.simulator = simulator;
        this.board = simulator.getBoardRef();
        this.tileSize = tileSize;

        images = ImageStore.getInstance();
        mapGC = map.getGraphicsContext2D();
        entitiesGC = entities.getGraphicsContext2D();
        hudGC = hud.getGraphicsContext2D();

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
            if (log.isDebugEnabled()) log.debug("Zoom in: {} -> {}", oldZoom, zoom);
        } else {
            if (log.isDebugEnabled()) log.debug("Zoom in ignored (at MAX_ZOOM={})", maxZoom);
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
            if (log.isDebugEnabled()) log.debug("Zoom out: {} -> {}", oldZoom, zoom);
        } else {
            if (log.isDebugEnabled()) log.debug("Zoom out ignored (at MIN_ZOOM={})", minZoom);
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
    private void renderCell(Image sprite, int x, int y) {
        double size = scaledTileSize();
        double screenX = (x - cameraX) * size;
        double screenY = (y - cameraY) * size;
        mapGC.drawImage(sprite, screenX, screenY, size, size);
    }

    private void clearEntities() {
        entitiesGC.clearRect(0, 0, entities.getWidth(), entities.getHeight());
    }

    private void clearMap() {
        mapGC.clearRect(0, 0, map.getWidth(), map.getHeight());
    }

    private void renderPlayer() {
        Simulator.Position pos = simulator.getPlayerPosition();
        double size = scaledTileSize();

        double screenX = (pos.x() - cameraX) * size;
        double screenY = (pos.y() - cameraY) * size;

        entitiesGC.drawImage(images.get(Sprite.PLAYER), screenX, screenY, size, size);
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
        } else if (moved) {
            if (log.isTraceEnabled()) log.trace("Camera moved to ({}, {}) zoom={}", cameraX, cameraY, zoom);
        }
    }

    private double clamp(double v, double min, double max) {
        double minimum = Math.min(max, v);
        return Math.max(min, minimum);
    }

    public void update() {
        updateCamera();

        clearMap();

        List<List<Integer>> visibility = board.getCells(true);

        double tilesInWidth = map.getWidth() / scaledTileSize();
        double tilesInHeight = map.getHeight() / scaledTileSize();

        // Rang visible en tiles (amb marge per evitar talls)
        int startX = (int) Math.floor(cameraX) - 1;
        int startY = (int) Math.floor(cameraY) - 1;
        int endX = (int) Math.ceil(cameraX + tilesInWidth) + 1;
        int endY = (int) Math.ceil(cameraY + tilesInHeight) + 1;

        int h = visibility.size();
        int w = (h > 0) ? visibility.get(0).size() : 0;

        // Clamp al tauler
        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(w - 1, endX);
        endY = Math.min(h - 1, endY);

        int rendered = 0;

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int type = visibility.get(y).get(x);
                if (!isDiscovered(type))
                    continue;

                Sprite sprite = parseType(type);
                renderCell(images.get(sprite), x, y);
                rendered++;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Rendered {} tiles. cam=({}, {}) zoom={} rangeX=[{},{}] rangeY=[{},{}]",
                    rendered, cameraX, cameraY, zoom, startX, endX, startY, endY);
        }

        clearEntities();
        renderPlayer();

        renderHud();
    }

    private void renderHud() {
        hudGC.clearRect(0, 0, hud.getWidth(), hud.getHeight());

        hudGC.setFill(Color.WHITE);
        hudGC.setFont(Font.font("Arial", 20));

        Simulator.Position pos = simulator.getPlayerPosition();
        int x = ph.getX(pos.x());
        int y = ph.getY(pos.y());

        String position = String.format("x: %d%ny: %d", x, y);

        hudGC.fillText(position, 50, 40);
    }

    /**
     * Ajusta això al valor que utilitzi el teu Board per a “no descobert”.
     * Molt habitual: -1, 3, etc.
     */
    private boolean isDiscovered(int type) {
        return type != UNKNOWN;
    }

    private Sprite parseType(int type) {
        return switch (type) {
            case 0 -> Sprite.PATH;
            case 1 -> Sprite.WALL;
            case 2 -> Sprite.EXIT_CONNECTOR;
            case 4 -> Sprite.PLAYER;
            case 5 -> Sprite.EXIT;
            default -> Sprite.PATH;
        };
    }

    public record CameraState(double cameraX, double cameraY, double zoom) {}

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
}
