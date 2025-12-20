package com.jairo.utils.map_generator;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapGenerator {
    private static final Logger log = LoggerFactory.getLogger(MapGenerator.class);

    public static final int BOARD_WIDTH = 40;
    public static final int BOARD_HEIGHT = 25;

    // Tipos de celda
    private static final int PATH = 0;
    private static final int WALL = 1;
    private static final int EXIT_CONNECTOR = 2;

    // Impide instanciación
    private MapGenerator() {
    }

    /**
     * ÚNICO MÉTODO PÚBLICO
     * Genera un laberinto válido y devuelve el mapa como String (0/1/2)
     */
    public static String generateMap() {
        log.debug("Generating maze (width={}, height={})", BOARD_WIDTH, BOARD_HEIGHT);
        int[][] g = new int[BOARD_HEIGHT][BOARD_WIDTH];

        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                g[y][x] = WALL;
            }
        }

        int cellW = (BOARD_WIDTH - 1) / 2;
        int cellH = (BOARD_HEIGHT - 1) / 2;
        if (cellW <= 0 || cellH <= 0) {
            log.warn("Maze generation skipped: invalid cell grid (cellW={}, cellH={})", cellW, cellH);
            return toMapDataString(g);
        }

        SecureRandom rnd = new SecureRandom();
        boolean[][] visited = new boolean[cellH][cellW];

        int sx = rnd.nextInt(cellW);
        int sy = rnd.nextInt(cellH);

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] { sx, sy });
        visited[sy][sx] = true;

        carveCell(g, sx, sy);

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            int cx = cur[0];
            int cy = cur[1];

            int[] order = { 0, 1, 2, 3 };
            shuffle(order, rnd);

            int pick = -1;
            for (int i : order) {
                int nx = cx + dirs[i][0];
                int ny = cy + dirs[i][1];
                if (nx >= 0 && ny >= 0 && nx < cellW && ny < cellH && !visited[ny][nx]) {
                    pick = i;
                    break;
                }
            }

            if (pick == -1) {
                stack.pop();
                continue;
            }

            int nx = cx + dirs[pick][0];
            int ny = cy + dirs[pick][1];

            carveCell(g, nx, ny);
            carveWallBetween(g, cx, cy, nx, ny);

            visited[ny][nx] = true;
            stack.push(new int[] { nx, ny });
        }

        int[] exit = carveSingleExit(g, rnd);
        log.debug("Exit carved at (x={}, y={})", exit[0], exit[1]);
        ensureSingleConnectedComponent(g);
        braidMaze(g, rnd, 0.08);

        return toMapDataString(g);
    }

    // ===================== helpers privados =====================

    private static void shuffle(int[] a, SecureRandom rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static void carveCell(int[][] g, int cx, int cy) {
        int x = cx * 2 + 1;
        int y = cy * 2 + 1;
        if (inBounds(x, y)) {
            g[y][x] = PATH;
        }
    }

    private static void carveWallBetween(int[][] g, int ax, int ay, int bx, int by) {
        int x = (ax * 2 + 1 + bx * 2 + 1) / 2;
        int y = (ay * 2 + 1 + by * 2 + 1) / 2;
        if (inBounds(x, y)) {
            g[y][x] = PATH;
        }
    }

    private static int[] carveSingleExit(int[][] g, SecureRandom rnd) {
        int side = rnd.nextInt(4);

        switch (side) {
            case 0 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[0][x] = PATH;
                g[1][x] = EXIT_CONNECTOR;
                return new int[]{0, x};
            }
            case 1 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[BOARD_HEIGHT - 1][x] = PATH;
                g[BOARD_HEIGHT - 2][x] = EXIT_CONNECTOR;
                return new int[]{BOARD_HEIGHT - 1, x};
            }
            case 2 -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[y][0] = PATH;
                g[y][1] = EXIT_CONNECTOR;
                return new int[]{y, 0};
            }
            default -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[y][BOARD_WIDTH - 1] = PATH;
                g[y][BOARD_WIDTH - 2] = EXIT_CONNECTOR;
                return new int[]{y, BOARD_HEIGHT - 1};
            }
        }
    }

    private static int randomOdd(SecureRandom rnd, int min, int max) {
        if ((min & 1) == 0) {
            min++;
        }
        if ((max & 1) == 0) {
            max--;
        }

        int count = ((max - min) / 2) + 1;
        return min + 2 * rnd.nextInt(count);
    }

    private static boolean isWalkable(int v) {
        return v == PATH || v == EXIT_CONNECTOR;
    }

    private static int[] findFirstWalkable(int[][] g) {
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (isWalkable(g[y][x])) {
                    return new int[] { x, y };
                }

            }
        }

        return new int[0];
    }

    private static void ensureSingleConnectedComponent(int[][] g) {
        boolean[][] vis = new boolean[BOARD_HEIGHT][BOARD_WIDTH];

        int[] start = findFirstWalkable(g);
        if (start.length == 0) {
            return;
        }

        int sx = start[0];
        int sy = start[1];

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        vis[sy][sx] = true;

        final int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (inBounds(nx, ny) && !vis[ny][nx] && isWalkable(g[ny][nx])) {
                    vis[ny][nx] = true;
                    q.add(new int[] { nx, ny });
                }
            }
        }

        for (int y = 0; y < BOARD_HEIGHT; y++)
            for (int x = 0; x < BOARD_WIDTH; x++)
                if (isWalkable(g[y][x]) && !vis[y][x]) {
                    g[y][x] = WALL;
                }

    }

    private static void braidMaze(int[][] g, SecureRandom rnd, double p) {
        log.debug("Braiding maze (p={})", p);
        int opened = 0;

        for (int y = 1; y < BOARD_HEIGHT - 1; y++) {
            for (int x = 1; x < BOARD_WIDTH - 1; x++) {
                if (g[y][x] != WALL || rnd.nextDouble() > p) {
                    continue;
                }

                boolean horizontal = isWalkable(g[y][x - 1]) && isWalkable(g[y][x + 1]);
                boolean vertical = isWalkable(g[y - 1][x]) && isWalkable(g[y + 1][x]);

                if (horizontal || vertical) {
                    g[y][x] = PATH;
                    opened++;
                    log.debug("Ensured single connected component");
                }

            }
        }
        log.debug("Braiding opened {} walls", opened);

    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < BOARD_WIDTH && y < BOARD_HEIGHT;
    }

    private static String toMapDataString(int[][] g) {
        StringBuilder sb = new StringBuilder(BOARD_WIDTH * BOARD_HEIGHT);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                String cell = switch (g[y][x]) {
                    case WALL -> "1";
                    case EXIT_CONNECTOR -> "2";
                    default -> "0";
                };
                sb.append(cell);
            }
        }
        return sb.toString();
    }
}
