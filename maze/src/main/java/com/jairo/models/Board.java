package com.jairo.models;

import java.util.List;
import java.util.ArrayList;

import com.jairo.utils.map_generator.MapGenerator;
import com.jairo.utils.map_generator.map_modifier.CheatTunnelModifier;
import com.jairo.utils.map_generator.map_modifier.MapModifier;
import com.jairo.utils.map_generator.map_modifier.RoomIceFloorModifier;
import com.jairo.utils.map_generator.BoardGenerator;
import com.jairo.utils.map_generator.Cells;

import static com.jairo.utils.map_generator.Cells.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representa el tauler de joc.
 */
public class Board {
    private static final Logger log = LoggerFactory.getLogger(Board.class);

    public static final int BOARD_WIDTH = MapGenerator.BOARD_WIDTH;
    public static final int BOARD_HEIGHT = MapGenerator.BOARD_HEIGHT;

    private static final double MIN_WALKABLE_CELLS_RATIO = 0.3;

    // Mapa (original, para render/visibilidad/etc.)
    private final List<List<Integer>> cells;
    private final List<List<Integer>> visibility;
    private final List<int[]> secretWalls;
    private final int doorsCount;

    // Mapa plano 1D (para accesos rápidos / reachability)
    // idx = y*BOARD_WIDTH + x
    private final int[] grid;

    // Jugador
    private int playerX;
    private int playerY;
    private boolean newDiscover = false;

    // Salida (EXIT)
    private int exitX = -1;
    private int exitY = -1;

    // Reachability (BFS rápido sobre grid 1D)
    private static final BoardReachability REACHABILITY = new BoardReachability(BOARD_WIDTH, BOARD_HEIGHT);

    /**
     * Constructor PRIVADO: crea el board a partir de maps ya generados.
     * La generación y validación se hace en el wrapper estático.
     */
    private Board(BoardGenerator.Maps maps) {
        this.cells = maps.cells();
        this.visibility = maps.visibility();
        this.secretWalls = maps.secretWalls();
        this.grid = flattenToGrid(this.cells);

        this.doorsCount = maps.doorsCount();
        findExitPositionOrThrow();
    }

    private static int[] flattenToGrid(List<List<Integer>> cells) {
        int[] g = new int[BOARD_WIDTH * BOARD_HEIGHT];
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            List<Integer> row = cells.get(y);
            int base = y * BOARD_WIDTH;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                g[base + x] = row.get(x); // unboxing 1 vez por celda
            }
        }
        return g;
    }

    // ------------------------------------------------------------
    // WRAPPERS PÚBLICOS DE GENERACIÓN + DETECCIÓN
    // ------------------------------------------------------------

    /**
     * Genera un Board válido (EXIT alcanzable desde spawn).
     * Si no lo consigue tras maxRetries, lanza excepción.
     */
    public static Board generateReachable(int spawnX, int spawnY, int maxRetries) {
        if (maxRetries <= 0)
            maxRetries = 1;

        int tries = 0;
        while (tries++ < maxRetries) {
            Board b = generateOnce();

            if (!b.isExitReachableFrom(spawnX, spawnY)) {
                log.warn("Generated board rejected (EXIT unreachable). try {}/{}", tries, maxRetries);
                continue;
            }

            // opcional: evitar spawns raros (sin salidas / área mínima)
            if (b.countWalkableNeighborsForValidation(spawnX, spawnY) == 0) {
                log.warn("Generated board rejected (spawn trapped). try {}/{}", tries, maxRetries);
                continue;
            }

            if (!sufficientWalkableCells(b.getCells())) {
                log.warn("Generated board rejected (insufficient walkable cells). try {}/{}", tries, maxRetries);
                continue;
            }

            return b;
        }

        throw new IllegalStateException("Unable to generate a reachable board after " + maxRetries + " tries");
    }

    /**
     * Wrapper de detección: genera un board y devuelve un resultado con:
     * - board
     * - reachable (si se puede llegar a EXIT desde spawn)
     *
     * Útil si NO quieres regenerar automáticamente, solo detectar el caso.
     */
    public static GenerationResult generateAndDetectReachability(int spawnX, int spawnY) {
        Board b = generateOnce();
        boolean reachable = b.isExitReachableFrom(spawnX, spawnY);
        return new GenerationResult(b, reachable);
    }

    /**
     * Resultado simple para "detectar casos donde es imposible llegar a la salida".
     */
    public record GenerationResult(Board board, boolean exitReachable) {
    }

    // Generación "una vez" (reutilizable por los wrappers)
    private static Board generateOnce() {
        try {
            String base = MapGenerator.generateMap();
            String flat = MapModifier.modify(base, BOARD_WIDTH, BOARD_HEIGHT);
            BoardGenerator.Maps maps = BoardGenerator.generateEmptyBoard(flat, BOARD_WIDTH, BOARD_HEIGHT);
            CheatTunnelModifier.apply(maps.cells());
            RoomIceFloorModifier.apply(maps.cells());
            return new Board(maps);
        } catch (Exception e) {
            log.error("Failed to generate board", e);
            throw e;
        }
    }

    // ------------------------------------------------------------
    // DETECCIÓN: REACHABILITY (delegado a grid 1D)
    // ------------------------------------------------------------

    public boolean isExitReachableFrom(int spawnX, int spawnY) {
        return REACHABILITY.isExitReachable(grid, spawnX, spawnY, exitX, exitY);
    }

    private int countWalkableNeighborsForValidation(int x, int y) {
        return REACHABILITY.countWalkableNeighbors(grid, x, y);
    }

    // ------------------------------------------------------------
    // EXIT
    // ------------------------------------------------------------

    private void findExitPositionOrThrow() {
        int foundIdx = -1;
        for (int idx = 0; idx < grid.length; idx++) {
            if (grid[idx] == EXIT) {
                foundIdx = idx;
                break;
            }
        }

        if (foundIdx == -1) {
            log.error("EXIT not found on generated board");
            throw new IllegalStateException("EXIT not found on generated board");
        }

        this.exitY = foundIdx / BOARD_WIDTH;
        this.exitX = foundIdx - (exitY * BOARD_WIDTH);

        log.info("EXIT found at ({},{})", exitX, exitY);
    }

    public int getExitX() {
        return exitX;
    }

    public int getExitY() {
        return exitY;
    }

    // ------------------------------------------------------------
    // API EXISTENTE (sin cambios relevantes)
    // ------------------------------------------------------------

    public List<List<Integer>> getCells(boolean playerPOV) {
        List<List<Integer>> cellsToRender = playerPOV ? visibility : cells;

        List<List<Integer>> copy = new ArrayList<>(cellsToRender.size());
        for (List<Integer> row : cellsToRender) {
            copy.add(List.copyOf(row));
        }
        return List.copyOf(copy);
    }

    public List<List<Integer>> getCells() {
        return getCells(false);
    }

    private int[][] visibilityCache; // última copia generada

    public int[][] getVisibility() {
        if (!newDiscover && visibilityCache != null) {
            return visibilityCache;
        }

        int rows = visibility.size();
        int[][] copy = new int[rows][];

        for (int y = 0; y < rows; y++) {
            List<Integer> row = visibility.get(y);
            int cols = row.size();
            int[] rowCopy = new int[cols];
            for (int x = 0; x < cols; x++) {
                rowCopy[x] = row.get(x);
            }
            copy[y] = rowCopy;
        }

        visibilityCache = copy;
        newDiscover = false;
        return copy;
    }

    public int[][] getVisibilityArea(int startX, int startY, int endX, int endY) {
        newDiscover = false;

        int h = endY - startY + 1;
        int w = endX - startX + 1;

        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            var row = visibility.get(startY + y);
            for (int x = 0; x < w; x++) {
                out[y][x] = row.get(startX + x);
            }
        }
        return out;
    }

    public boolean getIfNewDiscover() {
        return newDiscover;
    }

    public boolean movePlayer(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT) {
            log.trace("Move rejected: out of bounds (x={}, y={})", x, y);
            return false;
        }

        if (Cells.hasCollision(getTile(x, y))) {
            log.trace("Move rejected: hit wall (x={}, y={})", x, y);
            return false;
        }

        this.playerX = x;
        this.playerY = y;
        discoverAroundPlayer();
        return true;
    }

    public boolean canMovePlayer(int dx, int dy) {
        if (dx == 0 && dy == 0)
            return false;

        int newX = playerX + dx;
        int newY = playerY + dy;

        if (newX < 0 || newX >= BOARD_WIDTH || newY < 0 || newY >= BOARD_HEIGHT) {
            return false;
        }

        int cell = getTile(newX, newY);
        return !Cells.hasCollision(cell);
    }

    public void discoverAroundPlayer(int x, int y) {
        playerX = x;
        playerY = y;
        discoverAroundPlayer();
    }

    private int baseMinX = 1;
    private int baseMaxX = 1;
    private int baseMinY = 1;
    private int baseMaxY = 1;

    public void updateDiscoverPower(int[] power) {
        if (power == null || power.length != 4)
            return;

        baseMinX = power[0];
        baseMaxX = power[1];
        baseMinY = power[2];
        baseMaxY = power[3];
    }

    public void discoverAroundPlayer() {
        int minX = Math.max(0, playerX - baseMinX);
        int maxX = Math.min(BOARD_WIDTH - 1, playerX + baseMaxX);

        int minY = Math.max(0, playerY - baseMinY);
        int maxY = Math.min(BOARD_HEIGHT - 1, playerY + baseMaxY);

        for (int y = minY; y <= maxY; y++) {
            List<Integer> visibilityRow = visibility.get(y);
            List<Integer> cellsRow = cells.get(y);

            for (int x = minX; x <= maxX; x++) {
                if (visibilityRow.get(x) == UNKNOWN) {
                    visibilityRow.set(x, cellsRow.get(x));
                    newDiscover = true;
                }
            }
        }
    }

    public void updateTile(int x, int y, int tile, boolean updateVisibility) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return;

        // actualizar estructuras
        cells.get(y).set(x, tile);
        grid[y * BOARD_WIDTH + x] = tile;

        if (updateVisibility) {
            visibility.get(y).set(x, tile);
            newDiscover = true;
        }

        if (tile == EXIT) {
            exitX = x;
            exitY = y;
        }
    }

    public void updateTile(int x, int y, int tile) {
        updateTile(x, y, tile, true);
    }

    /**
     * Lectura rápida desde grid 1D (con bounds).
     */
    public int getTile(int x, int y) {
        if (x < 0 || y < 0 || y >= BOARD_HEIGHT || x >= BOARD_WIDTH) {
            return Cells.WALL;
        }
        return grid[y * BOARD_WIDTH + x];
    }

    public static Board generateStandard() {
        return generateOnce();
    }

    public void openSecretWalls() {
        for (int[] sw : secretWalls) {
            int x = sw[0];
            int y = sw[1];

            // actualizar ambas estructuras
            cells.get(y).set(x, PATH);
            grid[y * BOARD_WIDTH + x] = PATH;

            if (visibility.get(y).get(x) == SECRET_WALL) {
                visibility.get(y).set(x, PATH);
            }
        }
        secretWalls.clear();
    }

    private static boolean sufficientWalkableCells(List<List<Integer>> cells) {
        int walkable = 0;
        int totalCells = 0;

        for (List<Integer> row : cells) {
            totalCells += row.size();
            for (int cell : row) {
                if (Cells.isPath(cell)) {
                    walkable++;
                }
            }
        }

        if (totalCells == 0)
            return false;

        return walkable >= MIN_WALKABLE_CELLS_RATIO * totalCells;
    }

    public int getDoorsCount() {
        return doorsCount;
    }

}
