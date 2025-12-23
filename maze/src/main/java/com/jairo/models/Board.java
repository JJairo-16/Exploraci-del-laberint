package com.jairo.models;

import java.util.List;
import java.util.ArrayList;

import com.jairo.utils.map_generator.MapGenerator;
import com.jairo.utils.map_generator.map_modifier.MapModifier;
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

    // Mapa
    private final List<List<Integer>> cells;
    private final List<List<Integer>> visibility;

    // Jugador
    private int playerX;
    private int playerY;
    private static boolean cornerPeek = true;

    // Salida (EXIT)
    private int exitX = -1;
    private int exitY = -1;

    /**
     * Constructor PRIVADO: crea el board a partir de maps ya generados.
     * La generación y validación se hace en el wrapper estático.
     */
    private Board(BoardGenerator.Maps maps) {
        this.cells = maps.cells();
        this.visibility = maps.visibility();
        findExitPositionOrThrow();
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
            return new Board(maps);
        } catch (Exception e) {
            log.error("Failed to generate board", e);
            throw e;
        }
    }

    // ------------------------------------------------------------
    // DETECCIÓN: REACHABILITY (BFS)
    // ------------------------------------------------------------

    /**
     * Devuelve true si el EXIT es alcanzable desde (spawnX, spawnY).
     * (No consume puertas DOOR_OPEN_FROM_*, solo permite caminar por celdas
     * transitables
     * y por puertas ya abiertas DOOR_OPENED_FROM_*).
     */
    public boolean isExitReachableFrom(int spawnX, int spawnY) {
        if (exitX < 0 || exitY < 0)
            return false;
        if (spawnX < 0 || spawnY < 0 || spawnX >= BOARD_WIDTH || spawnY >= BOARD_HEIGHT)
            return false;

        boolean[][] vis = new boolean[BOARD_HEIGHT][BOARD_WIDTH];
        int[] qx = new int[BOARD_WIDTH * BOARD_HEIGHT];
        int[] qy = new int[BOARD_WIDTH * BOARD_HEIGHT];
        int head = 0, tail = 0;

        if (!isWalkableForValidation(getTile(spawnX, spawnY)))
            return false;

        vis[spawnY][spawnX] = true;
        qx[tail] = spawnX;
        qy[tail] = spawnY;
        tail++;

        while (head < tail) {
            int x = qx[head];
            int y = qy[head];
            head++;

            if (x == exitX && y == exitY)
                return true;

            // 4 vecinos
            // (sin crear arrays dentro del loop para evitar basura)
            // derecha
            if (x + 1 < BOARD_WIDTH && !vis[y][x + 1] && isWalkableForValidation(getTile(x + 1, y))) {
                vis[y][x + 1] = true;
                qx[tail] = x + 1;
                qy[tail] = y;
                tail++;
            }
            // izquierda
            if (x - 1 >= 0 && !vis[y][x - 1] && isWalkableForValidation(getTile(x - 1, y))) {
                vis[y][x - 1] = true;
                qx[tail] = x - 1;
                qy[tail] = y;
                tail++;
            }
            // abajo
            if (y + 1 < BOARD_HEIGHT && !vis[y + 1][x] && isWalkableForValidation(getTile(x, y + 1))) {
                vis[y + 1][x] = true;
                qx[tail] = x;
                qy[tail] = y + 1;
                tail++;
            }
            // arriba
            if (y - 1 >= 0 && !vis[y - 1][x] && isWalkableForValidation(getTile(x, y - 1))) {
                vis[y - 1][x] = true;
                qx[tail] = x;
                qy[tail] = y - 1;
                tail++;
            }
        }

        return false;
    }

    private boolean isWalkableForValidation(int cell) {
        // caminable si NO hay colisión
        if (!Cells.hasCollision(cell))
            return true;

        // puertas ya abiertas: caminables
        return cell == DOOR_OPENED_FROM_NORTH
                || cell == DOOR_OPENED_FROM_SOUTH
                || cell == DOOR_OPENED_FROM_WEST
                || cell == DOOR_OPENED_FROM_EAST;
    }

    private int countWalkableNeighborsForValidation(int x, int y) {
        int c = 0;
        if (x + 1 < BOARD_WIDTH && isWalkableForValidation(getTile(x + 1, y)))
            c++;
        if (x - 1 >= 0 && isWalkableForValidation(getTile(x - 1, y)))
            c++;
        if (y + 1 < BOARD_HEIGHT && isWalkableForValidation(getTile(x, y + 1)))
            c++;
        if (y - 1 >= 0 && isWalkableForValidation(getTile(x, y - 1)))
            c++;
        return c;
    }

    // ------------------------------------------------------------
    // EXIT
    // ------------------------------------------------------------

    private void findExitPositionOrThrow() {
        int foundX = -1, foundY = -1;

        for (int y = 0; y < BOARD_HEIGHT; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (row.get(x) == EXIT) {
                    foundX = x;
                    foundY = y;
                    break;
                }
            }
            if (foundX != -1)
                break;
        }

        if (foundX == -1) {
            log.error("EXIT not found on generated board");
            throw new IllegalStateException("EXIT not found on generated board");
        }

        this.exitX = foundX;
        this.exitY = foundY;
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

    public String toString(boolean visibilityMode) {
        int cap = (BOARD_WIDTH * 2 + 1) * BOARD_HEIGHT;
        StringBuilder sb = new StringBuilder(cap);

        List<List<Integer>> renderCells = visibilityMode ? cells : visibility;

        for (int y = 0; y < BOARD_HEIGHT; y++) {
            List<Integer> row = renderCells.get(y);

            for (int x = 0; x < BOARD_WIDTH; x++) {
                int cell = needToLoadPlayer(x, y) ? PLAYER : row.get(x);
                sb.append(SYMBOLS.get(cell));
            }

            sb.append('\n');
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(true);
    }

    private boolean needToLoadPlayer(int x, int y) {
        if (y == 0 || y == BOARD_HEIGHT - 1)
            return false;
        if (x == 0 || x == BOARD_WIDTH - 1)
            return false;
        return (playerX == x && playerY == y);
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

    private void discoverAroundPlayer() {
        if (cornerPeek) {
            discoverAroundPlayerWithCornerPeek();
        } else {
            discoverAroundPlayerWithoutCornerPeek();
        }
    }

    private void discoverAroundPlayerWithCornerPeek() {
        for (int dy = -1; dy <= 1; dy++) {
            int ny = playerY + dy;
            List<Integer> visibilityRow = visibility.get(ny);
            List<Integer> cellsRow = cells.get(ny);
            for (int dx = -1; dx <= 1; dx++) {
                int nx = playerX + dx;
                if (visibilityRow.get(nx) == UNKNOWN) {
                    visibilityRow.set(nx, cellsRow.get(nx));
                }
            }
        }
    }

    private void discoverAroundPlayerWithoutCornerPeek() {
        int py = playerY;
        int px = playerX;

        for (int dy = -1; dy <= 1; dy++) {
            int ny = py + dy;
            if (ny < 0 || ny >= visibility.size())
                continue;

            List<Integer> visibilityRow = visibility.get(ny);
            List<Integer> cellsRow = cells.get(ny);

            for (int dx = -1; dx <= 1; dx++) {
                int nx = px + dx;
                if (nx < 0 || nx >= visibilityRow.size())
                    continue;

                if (visibilityRow.get(nx) != UNKNOWN)
                    continue;

                // Evitar descubrir diagonales si la esquina está bloqueada por ambos lados
                if (dx != 0 && dy != 0) {
                    int sideX = px + dx; // (px+dx, py)
                    int sideY = py + dy; // (px, py+dy)

                    // bounds de los lados
                    if (sideX >= 0 && sideX < visibilityRow.size() && sideY >= 0 && sideY < visibility.size()) {
                        int cellSideX = cells.get(py).get(sideX);
                        int cellSideY = cells.get(sideY).get(px);

                        boolean blockX = Cells.hasCollision(cellSideX);
                        boolean blockY = Cells.hasCollision(cellSideY);

                        // Si ambos lados están bloqueados, NO revelar la diagonal
                        if (blockX && blockY) {
                            continue;
                        }
                    }
                }

                visibilityRow.set(nx, cellsRow.get(nx));
            }
        }
    }

    public void updateTile(int x, int y, int tile, boolean updateVisibility) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return;
        cells.get(y).set(x, tile);
        if (updateVisibility) visibility.get(y).set(x, tile);

        if (tile == EXIT) {
            exitX = x;
            exitY = y;
        }
    }

    public void updateTile(int x, int y, int tile) {
        updateTile(x, y, tile, true);
    }

    public int getTile(int x, int y) {
        if (x < 0 || y < 0 || y >= BOARD_HEIGHT || x >= BOARD_WIDTH) {
            return Cells.WALL;
        }
        return cells.get(y).get(x);
    }

    public static Board generateStandard() {
        return generateOnce(); // el método privado que ya tenías para generar 1 vez
    }

}
