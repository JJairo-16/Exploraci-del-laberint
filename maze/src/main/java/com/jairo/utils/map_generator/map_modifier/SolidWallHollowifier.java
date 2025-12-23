package com.jairo.utils.map_generator.map_modifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
    private static final int DEFAULT_MIN_AREA = 140;         // mínimo tamaño del componente de WALL
    private static final double DEFAULT_MIN_FILL = 0.92;     // qué tan "relleno" está respecto a su bounding box
    private static final int DEFAULT_NEAR_EXIT_RADIUS = 1;   // no huecar cerca de no-WALL
    private static final int ENTRY_TILE = 8;                 // entrada (reemplaza una WALL del borde)

    public static String hollowLargeWallBlocks(String flat, int width, int height) {
        return hollowLargeWallBlocks(flat, width, height, DEFAULT_MIN_AREA, DEFAULT_MIN_FILL, DEFAULT_NEAR_EXIT_RADIUS);
    }

    /**
     * @param flat              mapa plano (len = width*height), chars '0'..'9'
     * @param width             ancho
     * @param height            alto
     * @param minArea           área mínima del componente de pared para considerarlo "grande"
     * @param minFillRatio      ratio mínimo area/boundingBoxArea para considerarlo "relleno"
     * @param nearExitRadius    radio Manhattan (0..N) alrededor de celdas != WALL que se protege (no se hueca ahí)
     */
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

        // Protege un anillo alrededor de cualquier celda que NO sea WALL (salidas, caminos, doors, etc.)
        boolean[][] protectedZone = computeProtectedZone(g, width, height, nearExitRadius);

        boolean[][] visited = new boolean[height][width];

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

                // Huecamos el interior manteniendo cáscara 1-tile y luego ponemos 1-2 entradas '8'
                hollowInteriorAndAddEntries(g, comp, protectedZone, width, height);
            }
        }

        return encode(g, width, height);
    }

    private static void hollowInteriorAndAddEntries(int[][] g, Component comp, boolean[][] protectedZone, int width, int height) {
        // Máscara de pertenencia al componente (WALL original)
        boolean[][] inComp = new boolean[height][width];
        for (int i = 0; i < comp.cells.size(); i++) {
            int[] p = comp.cells.get(i);
            inComp[p[1]][p[0]] = true;
        }

        // 1) Calcular interior (PATH) sin mutar aún
        List<int[]> toCarve = new ArrayList<>(comp.cells.size());
        for (int i = 0; i < comp.cells.size(); i++) {
            int[] p = comp.cells.get(i);
            int x = p[0], y = p[1];

            if (protectedZone[y][x]) continue; // no huecar cerca de no-WALL
            if (!has4NeighborsInComponent(inComp, width, height, x, y)) continue; // no huecar borde
            toCarve.add(p);
        }

        // Si no hay interior, no tiene sentido poner entradas
        if (toCarve.isEmpty()) return;

        // 2) Aplicar hueco interior
        for (int i = 0; i < toCarve.size(); i++) {
            int[] p = toCarve.get(i);
            g[p[1]][p[0]] = PATH;
        }

        // 3) Buscar candidatos a entrada (WALL del borde del componente) que:
        //    - estén en el componente (inComp)
        //    - sigan siendo WALL (no se han huecado)
        //    - tengan al menos 1 vecino interior PATH
        //    - tengan al menos 1 vecino exterior != WALL
        List<int[]> candidates = collectEntryCandidates(g, inComp, width, height, comp);

        // 4) Poner 1 o 2 entradas '8' (determinístico: el mejor + el más alejado)
        if (!candidates.isEmpty()) {
            int[] e1 = pickBestCandidate(g, width, height, candidates);
            placeEntry(g, e1);

            // intentar segunda entrada si hay suficientes y realmente está separada
            if (candidates.size() >= 2) {
                int[] e2 = pickFarthestFrom(e1, candidates);
                if (e2 != null) {
                    int dist = manhattan(e1, e2);
                    // separación mínima simple para evitar dobles puertas pegadas
                    if (dist >= 4) {
                        placeEntry(g, e2);
                    }
                }
            }
        }
    }

    private static void placeEntry(int[][] g, int[] p) {
        if (p == null) return;
        g[p[1]][p[0]] = ENTRY_TILE;
    }

    private static List<int[]> collectEntryCandidates(int[][] g, boolean[][] inComp, int width, int height, Component comp) {
        List<int[]> out = new ArrayList<>();

        // Escanear solo bounding box del componente para ir más rápido
        for (int y = comp.minY; y <= comp.maxY; y++) {
            for (int x = comp.minX; x <= comp.maxX; x++) {
                if (!inComp[y][x]) continue;
                if (g[y][x] != WALL) continue; // solo abrir sobre cáscara (WALL)

                boolean touchesInterior = false;
                boolean touchesOutside = false;

                // 4 vecinos
                if (y > 0) {
                    if (inComp[y - 1][x] && g[y - 1][x] == PATH) touchesInterior = true;
                    if (!inComp[y - 1][x] && g[y - 1][x] != WALL) touchesOutside = true;
                }
                if (y + 1 < height) {
                    if (inComp[y + 1][x] && g[y + 1][x] == PATH) touchesInterior = true;
                    if (!inComp[y + 1][x] && g[y + 1][x] != WALL) touchesOutside = true;
                }
                if (x > 0) {
                    if (inComp[y][x - 1] && g[y][x - 1] == PATH) touchesInterior = true;
                    if (!inComp[y][x - 1] && g[y][x - 1] != WALL) touchesOutside = true;
                }
                if (x + 1 < width) {
                    if (inComp[y][x + 1] && g[y][x + 1] == PATH) touchesInterior = true;
                    if (!inComp[y][x + 1] && g[y][x + 1] != WALL) touchesOutside = true;
                }

                if (touchesInterior && touchesOutside) {
                    out.add(new int[] { x, y });
                }
            }
        }
        return out;
    }

    private static int[] pickBestCandidate(int[][] g, int width, int height, List<int[]> candidates) {
        // Heurística: preferir donde haya más "exterior" accesible (más vecinos != WALL fuera)
        int bestScore = Integer.MIN_VALUE;
        int[] best = candidates.get(0);

        for (int i = 0; i < candidates.size(); i++) {
            int[] p = candidates.get(i);
            int x = p[0], y = p[1];

            int score = 0;

            // Cuenta vecinos exteriores no-wall (da preferencia a una salida más "abierta")
            score += countNonWallNeighbors(g, width, height, x, y);

            // Evita bordes del mapa (menos deseable)
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

    private static int[] pickFarthestFrom(int[] from, List<int[]> candidates) {
        int bestD = -1;
        int[] best = null;
        for (int i = 0; i < candidates.size(); i++) {
            int[] p = candidates.get(i);
            // no repetir la misma celda
            if (p[0] == from[0] && p[1] == from[1]) continue;
            int d = manhattan(from, p);
            if (d > bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static int manhattan(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    private static boolean has4NeighborsInComponent(boolean[][] inComp, int width, int height, int x, int y) {
        // fuera de límites => no es interior
        if (x <= 0 || y <= 0 || x >= width - 1 || y >= height - 1) return false;
        return inComp[y - 1][x] && inComp[y + 1][x] && inComp[y][x - 1] && inComp[y][x + 1];
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
        Deque<int[]> dq = new ArrayDeque<>();
        dq.add(new int[] { sx, sy });
        visited[sy][sx] = true;

        Component comp = new Component();
        comp.minX = comp.maxX = sx;
        comp.minY = comp.maxY = sy;

        while (!dq.isEmpty()) {
            int[] p = dq.removeFirst();
            int x = p[0], y = p[1];

            comp.cells.add(p);
            comp.area++;

            if (x < comp.minX) comp.minX = x;
            if (x > comp.maxX) comp.maxX = x;
            if (y < comp.minY) comp.minY = y;
            if (y > comp.maxY) comp.maxY = y;

            // 4-direcciones
            if (x > 0) tryPush(g, visited, dq, x - 1, y);
            if (x + 1 < width) tryPush(g, visited, dq, x + 1, y);
            if (y > 0) tryPush(g, visited, dq, x, y - 1);
            if (y + 1 < height) tryPush(g, visited, dq, x, y + 1);
        }

        return comp;
    }

    private static void tryPush(int[][] g, boolean[][] visited, Deque<int[]> dq, int x, int y) {
        if (visited[y][x]) return;
        if (g[y][x] != WALL) return;
        visited[y][x] = true;
        dq.addLast(new int[] { x, y });
    }

    private static int[][] decode(String flat, int width, int height) {
        int[][] g = new int[height][width];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char c = flat.charAt(idx++);
                int v = c - '0';
                // Si por algún motivo llega algo fuera de '0'..'9', lo tratamos como 0.
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
        final List<int[]> cells = new ArrayList<>();
    }
}
