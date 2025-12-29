package com.jairo.utils.map_generator;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapGenerator {
    private static final Logger log = LoggerFactory.getLogger(MapGenerator.class);

    private static volatile List<Room> lastRooms = List.of();

    private static final int WIDTH = 70;
    private static final int HEIGHT = 55;

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

    private MapGenerator() {}

    public static List<int[]> getLastRoomsAsRects() {
        List<Room> snapshot = lastRooms;
        List<int[]> out = new ArrayList<>(snapshot.size());
        for (Room r : snapshot) {
            out.add(new int[] { r.x1, r.y1, r.x2, r.y2 });
        }
        return Collections.unmodifiableList(out);
    }

    // ===================== Buffers (ThreadLocal) =====================

    private static final class WorkBuffers {
        int[] g;             // BOARD_WIDTH * BOARD_HEIGHT
        int[] cellMark;      // cellW * cellH (visited del grid de celdas)
        int[] bfsMark;       // BOARD_WIDTH * BOARD_HEIGHT

        BitSet[] blockedRows; // BOARD_HEIGHT filas, cada una con bits [0..BOARD_WIDTH)

        int cellStamp = 1;
        int bfsStamp = 1;

        void ensureG() {
            int n = BOARD_WIDTH * BOARD_HEIGHT;
            if (g == null || g.length != n) g = new int[n];
            if (bfsMark == null || bfsMark.length != n) bfsMark = new int[n];

            if (blockedRows == null || blockedRows.length != BOARD_HEIGHT) {
                blockedRows = new BitSet[BOARD_HEIGHT];
                for (int y = 0; y < BOARD_HEIGHT; y++) {
                    blockedRows[y] = new BitSet(BOARD_WIDTH);
                }
            } else {
                for (int y = 0; y < BOARD_HEIGHT; y++) {
                    if (blockedRows[y] == null) blockedRows[y] = new BitSet(BOARD_WIDTH);
                }
            }
        }

        void ensureCells(int cellW, int cellH) {
            int n = cellW * cellH;
            if (cellMark == null || cellMark.length != n) cellMark = new int[n];
        }

        int nextCellStamp() {
            cellStamp++;
            if (cellStamp == 0) { // overflow
                Arrays.fill(cellMark, 0);
                cellStamp = 1;
            }
            return cellStamp;
        }

        int nextBfsStamp() {
            bfsStamp++;
            if (bfsStamp == 0) {
                Arrays.fill(bfsMark, 0);
                bfsStamp = 1;
            }
            return bfsStamp;
        }

        void clearBlockedRows() {
            for (int y = 0; y < BOARD_HEIGHT; y++) {
                blockedRows[y].clear();
            }
        }
    }

    private static final ThreadLocal<WorkBuffers> BUFFERS =
            ThreadLocal.withInitial(WorkBuffers::new);

    private static int idx(int x, int y) {
        return y * BOARD_WIDTH + x;
    }

    private static int idxCell(int cx, int cy, int cellW) {
        return cy * cellW + cx;
    }

    public static String generateMap() {
        log.debug("Generating maze (width={}, height={})", BOARD_WIDTH, BOARD_HEIGHT);

        final WorkBuffers b = BUFFERS.get();
        b.ensureG();
        b.clearBlockedRows();

        // g = WALL
        Arrays.fill(b.g, WALL);

        int cellW = (BOARD_WIDTH - 1) / 2;
        int cellH = (BOARD_HEIGHT - 1) / 2;
        if (cellW <= 0 || cellH <= 0) {
            lastRooms = List.of();
            return toMapDataString(b.g);
        }

        b.ensureCells(cellW, cellH);

        // stamps (evitan limpiar arrays)
        final int cellStamp = b.nextCellStamp();

        SecureRandom rnd = new SecureRandom();

        List<Room> rooms = carveRooms(b.g, b.cellMark, cellStamp, b.blockedRows, cellW, cellH, rnd);
        lastRooms = Collections.unmodifiableList(new ArrayList<>(rooms));

        int sx = rnd.nextInt(cellW);
        int sy = rnd.nextInt(cellH);
        int guard = 0;
        while (b.cellMark[idxCell(sx, sy, cellW)] == cellStamp && guard++ < 2000) {
            sx = rnd.nextInt(cellW);
            sy = rnd.nextInt(cellH);
        }

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(packCell(sx, sy));
        b.cellMark[idxCell(sx, sy, cellW)] = cellStamp;
        carveCell(b.g, sx, sy);

        final int[] dx = { 1, -1, 0, 0 };
        final int[] dy = { 0, 0, 1, -1 };
        int[] order = { 0, 1, 2, 3 };

        while (!stack.isEmpty()) {
            int cur = stack.peek();
            int cx = unpackCellX(cur);
            int cy = unpackCellY(cur);

            order[0] = 0; order[1] = 1; order[2] = 2; order[3] = 3;
            shuffle(order, rnd);

            int pick = -1;
            for (int i : order) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (nx >= 0 && ny >= 0 && nx < cellW && ny < cellH
                        && b.cellMark[idxCell(nx, ny, cellW)] != cellStamp) {
                    pick = i;
                    break;
                }
            }

            if (pick == -1) {
                stack.pop();
                continue;
            }

            int nx = cx + dx[pick];
            int ny = cy + dy[pick];
            carveCell(b.g, nx, ny);
            carveWallBetween(b.g, cx, cy, nx, ny);
            b.cellMark[idxCell(nx, ny, cellW)] = cellStamp;
            stack.push(packCell(nx, ny));
        }

        connectRooms(b.g, rooms, rnd);
        carveSingleExit(b.g, rnd);
        ensureSingleConnectedComponent(b, b.g);
        braidMaze(b.g, rnd, 0.08);

        return toMapDataString(b.g);
    }

    // =========================================================
    // ===================== helpers ===========================
    // =========================================================

    private static int packCell(int x, int y) { return (y << 16) | (x & 0xFFFF); }
    private static int unpackCellX(int p) { return p & 0xFFFF; }
    private static int unpackCellY(int p) { return p >>> 16; }

    private static void shuffle(int[] a, SecureRandom rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    private static void carveCell(int[] g, int cx, int cy) {
        int x = cx * 2 + 1;
        int y = cy * 2 + 1;
        if (inBounds(x, y)) g[idx(x, y)] = PATH;
    }

    private static void carveWallBetween(int[] g, int ax, int ay, int bx, int by) {
        int x = (ax * 2 + 1 + bx * 2 + 1) / 2;
        int y = (ay * 2 + 1 + by * 2 + 1) / 2;
        if (inBounds(x, y)) g[idx(x, y)] = PATH;
    }

    private static List<Room> carveRooms(
            int[] g,
            int[] cellMark,
            int cellStamp,
            BitSet[] blockedRows,
            int cellW,
            int cellH,
            SecureRandom rnd) {

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

            if (x2 >= BOARD_WIDTH - 1 || y2 >= BOARD_HEIGHT - 1) continue;

            int ax1 = Math.max(0, x1 - ROOM_PADDING);
            int ay1 = Math.max(0, y1 - ROOM_PADDING);
            int ax2 = Math.min(BOARD_WIDTH - 1, x2 + ROOM_PADDING);
            int ay2 = Math.min(BOARD_HEIGHT - 1, y2 + ROOM_PADDING);

            // Colisión exacta con BitSet por fila (sin recorrer todo el rectángulo en x)
            boolean collision = false;
            for (int y = ay1; y <= ay2; y++) {
                int hit = blockedRows[y].nextSetBit(ax1);
                if (hit != -1 && hit <= ax2) {
                    collision = true;
                    break;
                }
            }
            if (collision) continue;

            // carve room
            for (int y = y1; y <= y2; y++) {
                int rowBase = y * BOARD_WIDTH;
                for (int x = x1; x <= x2; x++) {
                    g[rowBase + x] = PATH;
                }
            }

            // mark visited cells in cell grid
            int cx1 = (x1 - 1) / 2;
            int cy1 = (y1 - 1) / 2;
            int cx2 = (x2 - 1) / 2;
            int cy2 = (y2 - 1) / 2;

            for (int cy = cy1; cy <= cy2; cy++) {
                if (cy < 0 || cy >= cellH) continue;
                int base = cy * cellW;
                for (int cx = cx1; cx <= cx2; cx++) {
                    if (cx < 0 || cx >= cellW) continue;
                    cellMark[base + cx] = cellStamp;
                }
            }

            // mark blocked padding (exacto) con set de rango
            for (int y = ay1; y <= ay2; y++) {
                blockedRows[y].set(ax1, ax2 + 1); // fin exclusivo
            }

            rooms.add(new Room(x1, y1, x2, y2));
            carved += (x2 - x1 + 1) * (y2 - y1 + 1);
        }

        return rooms;
    }

    private static void connectRooms(int[] g, List<Room> rooms, SecureRandom rnd) {
        for (Room r : rooms) connectRoom(g, r, rnd);
    }

    private static void connectRoom(int[] g, Room r, SecureRandom rnd) {
        int[] door = pickDoor(r, rnd);
        int dx = door[2], dy = door[3];
        int x = door[0], y = door[1];

        int steps = 0;
        while (inBounds(x, y) && steps++ < (BOARD_WIDTH + BOARD_HEIGHT) * 2) {
            int p = idx(x, y);
            if (g[p] == WALL) g[p] = PATH;
            int nx = x + dx, ny = y + dy;
            if (!inBounds(nx, ny)) break;

            int np = idx(nx, ny);
            int v = g[np];
            if (isWalkable(v) || v == EXIT_CONNECTOR || v == EXIT) {
                g[np] = (v == EXIT) ? EXIT : PATH;
                return;
            }
            x = nx; y = ny;
        }

        int tx = clamp(r.cx(), 1, BOARD_WIDTH - 2);
        int ty = clamp(r.cy(), 1, BOARD_HEIGHT - 2);
        int[] target = findNearestWalkableManhattan(g, tx, ty);
        if (target != null) {
            int cx = tx, cy = ty;
            while (cx != target[0]) { g[idx(cx, cy)] = PATH; cx += (target[0] > cx) ? 1 : -1; }
            while (cy != target[1]) { g[idx(cx, cy)] = PATH; cy += (target[1] > cy) ? 1 : -1; }
            g[idx(cx, cy)] = PATH;
        }
    }

    private static int[] findNearestWalkableManhattan(int[] g, int tx, int ty) {
        int maxD = BOARD_WIDTH + BOARD_HEIGHT;
        for (int d = 1; d <= maxD; d++) {
            for (int dx = -d; dx <= d; dx++) {
                int dy = d - Math.abs(dx);
                int x1 = tx + dx, y1 = ty + dy;
                if (inBounds(x1, y1) && isWalkable(g[idx(x1, y1)])) return new int[] { x1, y1 };
                if (dy != 0) {
                    int x2 = tx + dx, y2 = ty - dy;
                    if (inBounds(x2, y2) && isWalkable(g[idx(x2, y2)])) return new int[] { x2, y2 };
                }
            }
        }
        return null;
    }

    private static int[] pickDoor(Room r, SecureRandom rnd) {
        int side = rnd.nextInt(4);
        if (side == 0) return new int[] { randomOdd(rnd, r.x1, r.x2), r.y1 - 1, 0, -1 };
        if (side == 1) return new int[] { randomOdd(rnd, r.x1, r.x2), r.y2 + 1, 0, 1 };
        if (side == 2) return new int[] { r.x1 - 1, randomOdd(rnd, r.y1, r.y2), -1, 0 };
        return new int[] { r.x2 + 1, randomOdd(rnd, r.y1, r.y2), 1, 0 };
    }

    private static int randomOddBounded(SecureRandom rnd, int min, int max) {
        int a = Math.min(min, max);
        int b = Math.max(min, max);
        if ((a & 1) == 0) a++;
        if ((b & 1) == 0) b--;
        if (a > b) return 3;
        int count = ((b - a) / 2) + 1;
        return a + 2 * rnd.nextInt(count);
    }

    private static int randomOdd(SecureRandom rnd, int min, int max) {
        if ((min & 1) == 0) min++;
        if ((max & 1) == 0) max--;
        int count = ((max - min) / 2) + 1;
        return min + 2 * rnd.nextInt(count);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int[] carveSingleExit(int[] g, SecureRandom rnd) {
        int side = rnd.nextInt(4);
        switch (side) {
            case 0 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[idx(x, 0)] = EXIT;
                g[idx(x, 1)] = EXIT_CONNECTOR;
                return new int[] { 0, x };
            }
            case 1 -> {
                int x = randomOdd(rnd, 1, BOARD_WIDTH - 2);
                g[idx(x, BOARD_HEIGHT - 1)] = EXIT;
                g[idx(x, BOARD_HEIGHT - 2)] = EXIT_CONNECTOR;
                return new int[] { BOARD_HEIGHT - 1, x };
            }
            case 2 -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[idx(0, y)] = EXIT;
                g[idx(1, y)] = EXIT_CONNECTOR;
                return new int[] { y, 0 };
            }
            default -> {
                int y = randomOdd(rnd, 1, BOARD_HEIGHT - 2);
                g[idx(BOARD_WIDTH - 1, y)] = EXIT;
                g[idx(BOARD_WIDTH - 2, y)] = EXIT_CONNECTOR;
                return new int[] { y, BOARD_WIDTH - 1 };
            }
        }
    }

    private static boolean isWalkable(int v) {
        return v == PATH || v == EXIT_CONNECTOR;
    }

    private static int[] findFirstWalkable(int[] g) {
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            int base = y * BOARD_WIDTH;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (isWalkable(g[base + x])) return new int[] { x, y };
            }
        }
        return new int[0];
    }

    /**
     * Igual que antes, pero:
     * - usa bfsMark + stamp (reutilizable) y sin boolean[][] nuevo
     */
    private static void ensureSingleConnectedComponent(WorkBuffers b, int[] g) {
        int totalWalkable = 0;
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            int base = y * BOARD_WIDTH;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (isWalkable(g[base + x])) totalWalkable++;
            }
        }
        if (totalWalkable == 0) return;

        int[] start = findFirstWalkable(g);
        if (start.length == 0) return;

        final int stamp = b.nextBfsStamp();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(packCell(start[0], start[1]));
        b.bfsMark[idx(start[0], start[1])] = stamp;

        final int[] dx = { 1, -1, 0, 0 };
        final int[] dy = { 0, 0, 1, -1 };

        int visitedCount = 1;

        while (!q.isEmpty()) {
            int p = q.poll();
            int x = unpackCellX(p), y = unpackCellY(p);
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i], ny = y + dy[i];
                if (!inBounds(nx, ny)) continue;
                int ip = idx(nx, ny);
                if (b.bfsMark[ip] == stamp) continue;
                if (!isWalkable(g[ip])) continue;
                b.bfsMark[ip] = stamp;
                visitedCount++;
                q.add(packCell(nx, ny));
            }
        }

        if (visitedCount == totalWalkable) return;

        for (int y = 0; y < BOARD_HEIGHT; y++) {
            int base = y * BOARD_WIDTH;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                int p = base + x;
                if (isWalkable(g[p]) && b.bfsMark[p] != stamp) g[p] = WALL;
            }
        }
    }

    private static void braidMaze(int[] g, SecureRandom rnd, double p) {
        for (int y = 1; y < BOARD_HEIGHT - 1; y++) {
            int base = y * BOARD_WIDTH;
            for (int x = 1; x < BOARD_WIDTH - 1; x++) {
                int pos = base + x;
                if (g[pos] != WALL || rnd.nextDouble() > p) continue;
                boolean h = isWalkable(g[pos - 1]) && isWalkable(g[pos + 1]);
                boolean v = isWalkable(g[pos - BOARD_WIDTH]) && isWalkable(g[pos + BOARD_WIDTH]);
                if (h || v) g[pos] = PATH;
            }
        }
    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < BOARD_WIDTH && y < BOARD_HEIGHT;
    }

    private static String toMapDataString(int[] g) {
        StringBuilder sb = new StringBuilder(BOARD_WIDTH * BOARD_HEIGHT);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            int base = y * BOARD_WIDTH;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                sb.append(switch (g[base + x]) {
                    case WALL -> '1';
                    case EXIT_CONNECTOR -> '2';
                    case EXIT -> '3';
                    default -> '0';
                });
            }
        }
        return sb.toString();
    }
}
