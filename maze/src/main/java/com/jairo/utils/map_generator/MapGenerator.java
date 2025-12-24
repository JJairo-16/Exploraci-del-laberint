package com.jairo.utils.map_generator;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapGenerator {
    private static final Logger log = LoggerFactory.getLogger(MapGenerator.class);
    private static boolean debugSingleConnection = false;

    private static volatile List<Room> lastRooms = List.of();

    private static final int WIDTH = 70; // 60
    private static final int HEIGHT = 55; // 45

    public static final int BOARD_WIDTH = makeOdd(WIDTH);
    public static final int BOARD_HEIGHT = makeOdd(HEIGHT);

    private static int makeOdd(int n) {
        return n + (n % 2 == 0 ? 1 : 0);
    }

    // Tipos de celda
    private static final int PATH = 0;
    private static final int WALL = 1;
    private static final int EXIT_CONNECTOR = 2;
    private static final int EXIT = 3;

    private static final double ROOM_DENSITY = 0.08;
    private static final int ROOM_ATTEMPTS = 450;

    private static final int ROOM_MIN_W = 3;
    private static final int ROOM_MAX_W = 7;
    private static final int ROOM_MIN_H = 3;
    private static final int ROOM_MAX_H = 7;

    private static final int ROOM_PADDING = 5;

    public static final class Room {
        final int x1, y1, x2, y2;

        Room(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        int area() {
            return (x2 - x1 + 1) * (y2 - y1 + 1);
        }

        int cx() {
            return (x1 + x2) / 2;
        }

        int cy() {
            return (y1 + y2) / 2;
        }
    }

    // Impide instanciación
    private MapGenerator() {
    }

    public static List<int[]> getLastRoomsAsRects() {
        // Devuelve cada room como {x1,y1,x2,y2}
        List<Room> snapshot = lastRooms; // snapshot rápido (por si cambia en otro hilo)
        List<int[]> out = new ArrayList<>(snapshot.size());
        for (Room r : snapshot) {
            out.add(new int[] { r.x1, r.y1, r.x2, r.y2 });
        }
        return Collections.unmodifiableList(out);
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
            lastRooms = List.of();
            return toMapDataString(g);
        }

        SecureRandom rnd = new SecureRandom();
        boolean[][] visited = new boolean[cellH][cellW];

        List<Room> rooms = carveRooms(g, visited, rnd);
        
        lastRooms = Collections.unmodifiableList(new ArrayList<>(rooms));

        int sx = rnd.nextInt(cellW);
        int sy = rnd.nextInt(cellH);

        int guard = 0;
        while (visited[sy][sx] && guard++ < 2000) {
            sx = rnd.nextInt(cellW);
            sy = rnd.nextInt(cellH);
        }

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

        connectRooms(g, rooms, rnd);

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

    private static List<Room> carveRooms(int[][] g, boolean[][] visited, SecureRandom rnd) {
        List<Room> rooms = new ArrayList<>(32);
        int target = (int) Math.round(BOARD_WIDTH * BOARD_HEIGHT * ROOM_DENSITY);
        int carved = 0;

        for (int a = 0; a < ROOM_ATTEMPTS && carved < target; a++) {
            int rw = randomOddBounded(rnd, ROOM_MIN_W, ROOM_MAX_W);
            int rh = randomOddBounded(rnd, ROOM_MIN_H, ROOM_MAX_H);

            int x1 = randomOdd(rnd, 1, BOARD_WIDTH - 2 - (rw - 1));
            int y1 = randomOdd(rnd, 1, BOARD_HEIGHT - 2 - (rh - 1));
            int x2 = x1 + rw - 1;
            int y2 = y1 + rh - 1;

            if (x2 >= BOARD_WIDTH - 1 || y2 >= BOARD_HEIGHT - 1) {
                continue;
            }

            Room candidate = new Room(x1, y1, x2, y2);
            if (tooClose(candidate, rooms, ROOM_PADDING)) {
                continue;
            }

            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    g[y][x] = PATH;
                }
            }

            int cellW = visited[0].length;
            int cellH = visited.length;

            int cx1 = (x1 - 1) / 2;
            int cy1 = (y1 - 1) / 2;
            int cx2 = (x2 - 1) / 2;
            int cy2 = (y2 - 1) / 2;

            for (int cy = cy1; cy <= cy2; cy++) {
                if (cy < 0 || cy >= cellH)
                    continue;
                for (int cx = cx1; cx <= cx2; cx++) {
                    if (cx < 0 || cx >= cellW)
                        continue;
                    visited[cy][cx] = true;
                }
            }

            rooms.add(candidate);
            carved += candidate.area();
        }

        return rooms;
    }

    private static boolean tooClose(Room c, List<Room> rooms, int pad) {
        int ax1 = c.x1 - pad;
        int ay1 = c.y1 - pad;
        int ax2 = c.x2 + pad;
        int ay2 = c.y2 + pad;

        for (Room r : rooms) {
            if (ax1 <= r.x2 && ax2 >= r.x1 && ay1 <= r.y2 && ay2 >= r.y1) {
                return true;
            }
        }
        return false;
    }

    private static void connectRooms(int[][] g, List<Room> rooms, SecureRandom rnd) {
        for (Room r : rooms) {
            connectRoom(g, r, rnd);
        }
    }

    private static void connectRoom(int[][] g, Room r, SecureRandom rnd) {
        int[] door = pickDoor(r, rnd);
        int dx = door[2];
        int dy = door[3];

        int x = door[0];
        int y = door[1];

        int steps = 0;
        while (inBounds(x, y) && steps++ < (BOARD_WIDTH + BOARD_HEIGHT) * 2) {
            if (g[y][x] == WALL) {
                g[y][x] = PATH;
            }

            int nx = x + dx;
            int ny = y + dy;

            if (!inBounds(nx, ny)) {
                break;
            }

            if (isWalkable(g[ny][nx]) || g[ny][nx] == EXIT_CONNECTOR || g[ny][nx] == EXIT) {
                g[ny][nx] = (g[ny][nx] == EXIT) ? EXIT : PATH;
                return;
            }

            x = nx;
            y = ny;
        }

        int tx = clamp(r.cx(), 1, BOARD_WIDTH - 2);
        int ty = clamp(r.cy(), 1, BOARD_HEIGHT - 2);

        int bestX = -1, bestY = -1, bestD = Integer.MAX_VALUE;
        for (int yy = 1; yy < BOARD_HEIGHT - 1; yy++) {
            for (int xx = 1; xx < BOARD_WIDTH - 1; xx++) {
                if (isWalkable(g[yy][xx])) {
                    int d = Math.abs(xx - tx) + Math.abs(yy - ty);
                    if (d < bestD) {
                        bestD = d;
                        bestX = xx;
                        bestY = yy;
                    }
                }
            }
        }

        if (bestX >= 0) {
            int cx = tx;
            int cy = ty;
            while (cx != bestX) {
                g[cy][cx] = PATH;
                cx += (bestX > cx) ? 1 : -1;
            }
            while (cy != bestY) {
                g[cy][cx] = PATH;
                cy += (bestY > cy) ? 1 : -1;
            }
            g[cy][cx] = PATH;
        }
    }

    private static int[] pickDoor(Room r, SecureRandom rnd) {
        int side = rnd.nextInt(4);
        if (side == 0) {
            int x = randomOdd(rnd, r.x1, r.x2);
            return new int[] { x, r.y1 - 1, 0, -1 };
        } else if (side == 1) {
            int x = randomOdd(rnd, r.x1, r.x2);
            return new int[] { x, r.y2 + 1, 0, 1 };
        } else if (side == 2) {
            int y = randomOdd(rnd, r.y1, r.y2);
            return new int[] { r.x1 - 1, y, -1, 0 };
        } else {
            int y = randomOdd(rnd, r.y1, r.y2);
            return new int[] { r.x2 + 1, y, 1, 0 };
        }
    }

    private static int randomOddBounded(SecureRandom rnd, int min, int max) {
        int a = Math.min(min, max);
        int b = Math.max(min, max);
        if ((a & 1) == 0)
            a++;
        if ((b & 1) == 0)
            b--;
        if (a > b) {
            return 3;
        }
        int count = ((b - a) / 2) + 1;
        return a + 2 * rnd.nextInt(count);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int[] carveSingleExit(int[][] g, SecureRandom rnd) {
        int side = rnd.nextInt(4);

        switch (side) {
            case 0 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[0][x] = EXIT;
                g[1][x] = EXIT_CONNECTOR;
                return new int[] { 0, x };
            }
            case 1 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[BOARD_HEIGHT - 1][x] = EXIT;
                g[BOARD_HEIGHT - 2][x] = EXIT_CONNECTOR;
                return new int[] { BOARD_HEIGHT - 1, x };
            }
            case 2 -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[y][0] = EXIT;
                g[y][1] = EXIT_CONNECTOR;
                return new int[] { y, 0 };
            }
            default -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[y][BOARD_WIDTH - 1] = EXIT;
                g[y][BOARD_WIDTH - 2] = EXIT_CONNECTOR;
                // return new int[] { y, BOARD_HEIGHT - 1 };
                return new int[] { y, BOARD_WIDTH - 1 };

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
                    if (debugSingleConnection)
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
                    case EXIT -> "3";
                    default -> "0";
                };
                sb.append(cell);
            }
        }
        return sb.toString();
    }
}
