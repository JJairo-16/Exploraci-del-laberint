package com.jairo.utils.map_generator.map_modifier;

import java.util.Arrays;

import static com.jairo.utils.map_generator.Cells.*;

/**
 * Detecta "bloques grandes" de pared (componentes conectados de WALL) que estén
 * muy rellenos (compactos) y los deja huecos por dentro (convierte interior a PATH).
 *
 * MOD: Cada “habitación” huecada crea 1 a 2 entradas, sustituyendo WALL por '8'
 * (NO por suelo). Las entradas se colocan en la cáscara, conectando interior (PATH)
 * con el exterior (cualquier celda != WALL).
 */
public final class SolidWallHollowifier {
    private SolidWallHollowifier() {}

    // Defaults (ajustables con la sobrecarga)
    private static final int DEFAULT_MIN_AREA = 30;          // mínimo tamaño del componente de WALL
    private static final double DEFAULT_MIN_FILL = 0.92;     // qué tan "relleno" está respecto a su bounding box
    private static final int DEFAULT_NEAR_EXIT_RADIUS = 1;   // no huecar cerca de no-WALL
    private static final int ENTRY_TILE = 8;                 // entrada (reemplaza una WALL del borde)

    // -------------------- Packed position helpers --------------------
    private static int pack(int x, int y, int w) { return y * w + x; }
    private static int unpackX(int p, int w) { return p % w; }
    private static int unpackY(int p, int w) { return p / w; }

    /**
     * Minimal int list with no boxing.
     */
    private static final class IntList {
        private int[] a;
        private int size;

        IntList() { this(16); }
        IntList(int initialCapacity) {
            this.a = new int[Math.max(4, initialCapacity)];
            this.size = 0;
        }

        int size() { return size; }
        boolean isEmpty() { return size == 0; }
        int get(int idx) { return a[idx]; }

        void add(int v) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }
    }

    /**
     * Minimal int queue (FIFO) with no boxing.
     */
    private static final class IntQueue {
        private int[] a;
        private int head, tail, size;

        IntQueue(int initialCapacity) {
            this.a = new int[Math.max(8, initialCapacity)];
        }

        boolean isEmpty() { return size == 0; }

        void add(int v) {
            if (size == a.length) grow();
            a[tail] = v;
            tail = (tail + 1) % a.length;
            size++;
        }

        int poll() {
            int v = a[head];
            head = (head + 1) % a.length;
            size--;
            return v;
        }

        private void grow() {
            int[] b = new int[a.length * 2];
            for (int i = 0; i < size; i++) {
                b[i] = a[(head + i) % a.length];
            }
            a = b;
            head = 0;
            tail = size;
        }
    }

    public static String hollowLargeWallBlocks(String flat, int width, int height) {
        return hollowLargeWallBlocks(flat, width, height, DEFAULT_MIN_AREA, DEFAULT_MIN_FILL, DEFAULT_NEAR_EXIT_RADIUS);
    }

    public static String hollowLargeWallBlocks(
            String flat,
            int width,
            int height,
            int minArea,
            double minFillRatio,
            int nearExitRadius
    ) {
        if (flat == null || flat.length() != width * height) return flat;
        if (width <= 0 || height <= 0) return flat;

        int[][] g = decode(flat, width, height);

        // Protege un anillo alrededor de cualquier celda que NO sea WALL
        boolean[][] protectedZone = computeProtectedZone(g, width, height, nearExitRadius);

        boolean[][] visited = new boolean[height][width];

        // ✅ Reusable "inComp" mark array (no boolean[H][W] per component)
        int[] inCompMark = new int[width * height];
        int inCompStamp = 1;

        // Recorremos componentes conectadas de WALL (4-neighbour)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (g[y][x] != WALL || visited[y][x]) continue;

                Component comp = floodCollectComponent(g, visited, width, height, x, y);
                if (comp.area < minArea) continue;

                int bboxArea = (comp.maxX - comp.minX + 1) * (comp.maxY - comp.minY + 1);
                if (bboxArea <= 0) continue;

                double fillRatio = (double) comp.area / (double) bboxArea;
                if (fillRatio < minFillRatio) continue;

                // ✅ stamp for this component (handle overflow safely)
                int stamp = ++inCompStamp;
                if (stamp == 0) {
                    Arrays.fill(inCompMark, 0);
                    inCompStamp = 1;
                    stamp = 1;
                }

                hollowInteriorAndAddEntries(g, comp, protectedZone, width, height, inCompMark, stamp);
            }
        }

        return encode(g, width, height);
    }

    private static void hollowInteriorAndAddEntries(
            int[][] g,
            Component comp,
            boolean[][] protectedZone,
            int width,
            int height,
            int[] inCompMark,
            int stamp
    ) {
        // 0) Mark membership for this component (WALL original)
        for (int i = 0; i < comp.cells.size(); i++) {
            int p = comp.cells.get(i);
            inCompMark[p] = stamp;
        }

        // 1) Calcular interior (PATH) sin mutar aún
        IntList toCarve = new IntList(comp.cells.size());
        for (int i = 0; i < comp.cells.size(); i++) {
            int p = comp.cells.get(i);
            int x = unpackX(p, width);
            int y = unpackY(p, width);

            if (protectedZone[y][x]) continue; // no huecar cerca de no-WALL
            if (!has4NeighborsInComponent(inCompMark, stamp, width, height, x, y)) continue; // no huecar borde
            toCarve.add(p);
        }

        // Si no hay interior, no tiene sentido poner entradas
        if (toCarve.isEmpty()) return;

        // 2) Aplicar hueco interior
        for (int i = 0; i < toCarve.size(); i++) {
            int p = toCarve.get(i);
            int x = unpackX(p, width);
            int y = unpackY(p, width);
            g[y][x] = PATH;
        }

        // 3) Buscar candidatos a entrada (WALL del borde del componente)
        IntList candidates = collectEntryCandidatesPacked(g, inCompMark, stamp, width, height, comp);

        // 4) Poner 1 o 2 entradas '8' (determinístico: el mejor + el más alejado)
        if (!candidates.isEmpty()) {
            int e1 = pickBestCandidatePacked(g, width, height, candidates);
            placeEntryPacked(g, e1, width);

            if (candidates.size() >= 2) {
                int e2 = pickFarthestFromPacked(e1, candidates, width);
                if (e2 != -1) {
                    int dist = manhattanPacked(e1, e2, width);
                    if (dist >= 4) placeEntryPacked(g, e2, width);
                }
            }
        }
    }

    private static void placeEntryPacked(int[][] g, int packed, int w) {
        if (packed < 0) return;
        int x = unpackX(packed, w);
        int y = unpackY(packed, w);
        g[y][x] = ENTRY_TILE;
    }

    private static boolean inComp(int[] inCompMark, int stamp, int p) {
        return inCompMark[p] == stamp;
    }

    private static boolean inCompXY(int[] inCompMark, int stamp, int w, int x, int y) {
        return inCompMark[pack(x, y, w)] == stamp;
    }

    private static IntList collectEntryCandidatesPacked(
            int[][] g,
            int[] inCompMark,
            int stamp,
            int width,
            int height,
            Component comp
    ) {
        IntList out = new IntList();

        // Escanear solo bounding box del componente para ir más rápido
        for (int y = comp.minY; y <= comp.maxY; y++) {
            for (int x = comp.minX; x <= comp.maxX; x++) {
                int p = pack(x, y, width);
                if (!inComp(inCompMark, stamp, p)) continue;
                if (g[y][x] != WALL) continue; // solo abrir sobre cáscara (WALL)

                boolean touchesInterior = false;
                boolean touchesOutside = false;

                // 4 vecinos (usa inCompMark+stamp en vez de boolean[][])
                if (y > 0) {
                    int pn = pack(x, y - 1, width);
                    if (inComp(inCompMark, stamp, pn) && g[y - 1][x] == PATH) touchesInterior = true;
                    if (!inComp(inCompMark, stamp, pn) && g[y - 1][x] != WALL) touchesOutside = true;
                }
                if (y + 1 < height) {
                    int ps = pack(x, y + 1, width);
                    if (inComp(inCompMark, stamp, ps) && g[y + 1][x] == PATH) touchesInterior = true;
                    if (!inComp(inCompMark, stamp, ps) && g[y + 1][x] != WALL) touchesOutside = true;
                }
                if (x > 0) {
                    int pw = pack(x - 1, y, width);
                    if (inComp(inCompMark, stamp, pw) && g[y][x - 1] == PATH) touchesInterior = true;
                    if (!inComp(inCompMark, stamp, pw) && g[y][x - 1] != WALL) touchesOutside = true;
                }
                if (x + 1 < width) {
                    int pe = pack(x + 1, y, width);
                    if (inComp(inCompMark, stamp, pe) && g[y][x + 1] == PATH) touchesInterior = true;
                    if (!inComp(inCompMark, stamp, pe) && g[y][x + 1] != WALL) touchesOutside = true;
                }

                if (touchesInterior && touchesOutside) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static int pickBestCandidatePacked(int[][] g, int width, int height, IntList candidates) {
        int bestScore = Integer.MIN_VALUE;
        int best = candidates.get(0);

        for (int i = 0; i < candidates.size(); i++) {
            int p = candidates.get(i);
            int x = unpackX(p, width);
            int y = unpackY(p, width);

            int score = countNonWallNeighbors(g, width, height, x, y);

            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) score -= 2;

            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private static int countNonWallNeighbors(int[][] g, int width, int height, int x, int y) {
        int c = 0;
        if (y > 0 && g[y - 1][x] != WALL) c++;
        if (y + 1 < height && g[y + 1][x] != WALL) c++;
        if (x > 0 && g[y][x - 1] != WALL) c++;
        if (x + 1 < width && g[y][x + 1] != WALL) c++;
        return c;
    }

    private static int pickFarthestFromPacked(int fromPacked, IntList candidates, int w) {
        int fx = unpackX(fromPacked, w);
        int fy = unpackY(fromPacked, w);

        int bestD = -1;
        int best = -1;

        for (int i = 0; i < candidates.size(); i++) {
            int p = candidates.get(i);
            if (p == fromPacked) continue;

            int x = unpackX(p, w);
            int y = unpackY(p, w);

            int d = Math.abs(fx - x) + Math.abs(fy - y);
            if (d > bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static int manhattanPacked(int aPacked, int bPacked, int w) {
        int ax = unpackX(aPacked, w), ay = unpackY(aPacked, w);
        int bx = unpackX(bPacked, w), by = unpackY(bPacked, w);
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private static boolean has4NeighborsInComponent(int[] inCompMark, int stamp, int w, int h, int x, int y) {
        if (x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1) return false;
        return inCompXY(inCompMark, stamp, w, x, y - 1)
                && inCompXY(inCompMark, stamp, w, x, y + 1)
                && inCompXY(inCompMark, stamp, w, x - 1, y)
                && inCompXY(inCompMark, stamp, w, x + 1, y);
    }

    private static boolean[][] computeProtectedZone(int[][] g, int width, int height, int r) {
        boolean[][] prot = new boolean[height][width];
        if (r <= 0) return prot;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (g[y][x] == WALL) continue;

                // Marca un diamante Manhattan de radio r alrededor
                for (int dy = -r; dy <= r; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) continue;
                    int rem = r - Math.abs(dy);
                    for (int dx = -rem; dx <= rem; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= width) continue;
                        prot[ny][nx] = true;
                    }
                }
            }
        }
        return prot;
    }

    private static Component floodCollectComponent(int[][] g, boolean[][] visited, int width, int height, int sx, int sy) {
        IntQueue q = new IntQueue(64);
        q.add(pack(sx, sy, width));
        visited[sy][sx] = true;

        Component comp = new Component();
        comp.minX = comp.maxX = sx;
        comp.minY = comp.maxY = sy;

        while (!q.isEmpty()) {
            int p = q.poll();
            int x = unpackX(p, width);
            int y = unpackY(p, width);

            comp.cells.add(p);
            comp.area++;

            if (x < comp.minX) comp.minX = x;
            if (x > comp.maxX) comp.maxX = x;
            if (y < comp.minY) comp.minY = y;
            if (y > comp.maxY) comp.maxY = y;

            if (x > 0) tryPushPacked(g, visited, q, width, x - 1, y);
            if (x + 1 < width) tryPushPacked(g, visited, q, width, x + 1, y);
            if (y > 0) tryPushPacked(g, visited, q, width, x, y - 1);
            if (y + 1 < height) tryPushPacked(g, visited, q, width, x, y + 1);
        }

        return comp;
    }

    private static void tryPushPacked(int[][] g, boolean[][] visited, IntQueue q, int width, int x, int y) {
        if (visited[y][x]) return;
        if (g[y][x] != WALL) return;
        visited[y][x] = true;
        q.add(pack(x, y, width));
    }

    private static int[][] decode(String flat, int width, int height) {
        int[][] g = new int[height][width];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char c = flat.charAt(idx++);
                int v = c - '0';
                if (v < 0 || v > 9) v = 0;
                g[y][x] = v;
            }
        }
        return g;
    }

    private static String encode(int[][] g, int width, int height) {
        StringBuilder sb = new StringBuilder(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = g[y][x];
                if (v < 0) v = 0;
                if (v > 9) v = 9;
                sb.append((char) ('0' + v));
            }
        }
        return sb.toString();
    }

    private static final class Component {
        int minX, maxX, minY, maxY;
        int area = 0;

        // packed positions (pos = y*width + x)
        final IntList cells = new IntList();
    }
}
