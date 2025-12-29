package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jairo.utils.map_generator.MapGenerator;

import static com.jairo.utils.map_generator.Cells.*;

public final class RoomIceFloorModifier {

    private RoomIceFloorModifier() {}

    private static final double MIN_RATIO = 0.20;
    private static final double MAX_RATIO = 0.35;

    // Cuántas "capas" de PATH adyacente a ICE convertimos (para cubrir entradas/puertas)
    private static final int ICE_EXPAND_STEPS = 1;

    public static void apply(List<List<Integer>> cells) {
        apply(cells, new SecureRandom());
    }

    public static void apply(List<List<Integer>> cells, SecureRandom rnd) {
        if (cells == null || cells.isEmpty() || cells.get(0).isEmpty()) return;

        // Rects: {x1,y1,x2,y2}
        List<int[]> rooms = MapGenerator.getLastRoomsAsRects();
        if (rooms == null || rooms.isEmpty()) return;

        final int h = cells.size();
        final int w = cells.get(0).size();
        if (h <= 0 || w <= 0) return;

        // ------------- Punto 1: trabajar con primitivos internamente -------------
        // Copiamos a int[][] para evitar boxing/unboxing y mejorar cache locality
        final int[][] grid = toPrimitiveGrid(cells, h, w);

        double ratio = MIN_RATIO + rnd.nextDouble() * (MAX_RATIO - MIN_RATIO);
        int target = (int) Math.round(rooms.size() * ratio);
        target = Math.min(target, rooms.size());
        target = Math.max(1, target);

        List<int[]> pick = new ArrayList<>(rooms);
        Collections.shuffle(pick, rnd);

        // 1) Convertir PATH -> ICE dentro de las rooms seleccionadas
        for (int i = 0; i < target; i++) {
            int[] r = pick.get(i);
            int x1 = clamp(r[0], 0, w - 1);
            int y1 = clamp(r[1], 0, h - 1);
            int x2 = clamp(r[2], 0, w - 1);
            int y2 = clamp(r[3], 0, h - 1);

            for (int y = y1; y <= y2; y++) {
                int[] row = grid[y];
                for (int x = x1; x <= x2; x++) {
                    if (row[x] == PATH) {
                        row[x] = ICE;
                    }
                }
            }
        }

        // 2) Expandir el hielo comiéndose entradas (PATH pegado a ICE) en 1-2 capas
        for (int step = 0; step < ICE_EXPAND_STEPS; step++) {
            expandIceOneStep(grid, w, h);
        }

        // 3) Si en ninguna esquina hay salida (PATH), añadir una aleatoria que conecte a walkable
        ensureCornerExit(grid, w, h, rnd);

        // Volcar cambios al List<List<Integer>> original
        writeBack(grid, cells, h, w);
    }

    // ------------------------ Implementación con primitivos ------------------------

    private static int[][] toPrimitiveGrid(List<List<Integer>> cells, int h, int w) {
        int[][] g = new int[h][w];
        for (int y = 0; y < h; y++) {
            List<Integer> srcRow = cells.get(y);
            // Asumimos rectangularidad según w; si alguna fila es más corta, rellenamos con 0
            int limit = Math.min(w, srcRow.size());
            for (int x = 0; x < limit; x++) {
                Integer v = srcRow.get(x);
                g[y][x] = (v != null) ? v : 0;
            }
            // si la fila es más corta que w, lo restante queda en 0
        }
        return g;
    }

    private static void writeBack(int[][] grid, List<List<Integer>> cells, int h, int w) {
        for (int y = 0; y < h; y++) {
            List<Integer> row = cells.get(y);
            // Si alguien te pasa filas no-rectangulares, respetamos el tamaño real para no reventar
            int limit = Math.min(w, row.size());
            for (int x = 0; x < limit; x++) {
                row.set(x, grid[y][x]); // autoboxing aquí solo una vez al final
            }
        }
    }

    private static void expandIceOneStep(int[][] grid, int w, int h) {
        // Marcamos cambios sin interferir con el barrido (coordenadas empaquetadas en int)
        // pack = y*w + x
        int[] toIce = new int[256];
        int count = 0;

        for (int y = 0; y < h; y++) {
            int[] row = grid[y];
            for (int x = 0; x < w; x++) {
                if (row[x] != PATH) continue;

                if (hasNeighborIce(grid, x, y, w, h)) {
                    if (count == toIce.length) {
                        // crecer (sin usar List para evitar objetos)
                        int[] bigger = new int[toIce.length << 1];
                        System.arraycopy(toIce, 0, bigger, 0, toIce.length);
                        toIce = bigger;
                    }
                    toIce[count++] = y * w + x;
                }
            }
        }

        for (int i = 0; i < count; i++) {
            int pack = toIce[i];
            int y = pack / w;
            int x = pack - (y * w);
            grid[y][x] = ICE;
        }
    }

    private static boolean hasNeighborIce(int[][] grid, int x, int y, int w, int h) {
        return isIceSafe(grid, x + 1, y, w, h)
            || isIceSafe(grid, x - 1, y, w, h)
            || isIceSafe(grid, x, y + 1, w, h)
            || isIceSafe(grid, x, y - 1, w, h);
    }

    private static boolean isIceSafe(int[][] grid, int x, int y, int w, int h) {
        if (y < 0 || y >= h) return false;
        if (x < 0 || x >= w) return false;
        return grid[y][x] == ICE;
    }

    private static void ensureCornerExit(int[][] grid, int w, int h, SecureRandom rnd) {
        if (h < 2 || w < 2) return;

        // Si ya hay PATH en alguna esquina, ya hay salida
        if (grid[0][0] == PATH ||
            grid[0][w - 1] == PATH ||
            grid[h - 1][0] == PATH ||
            grid[h - 1][w - 1] == PATH) {
            return;
        }

        // Candidatas: esquina cuyo vecino hacia dentro sea walkable (PATH o ICE)
        int[][] candidates = new int[4][2];
        int cCount = 0;

        cCount = addCornerIfValid(candidates, cCount, grid, w, h, 0, 0, 1, 0, 0, 1);
        cCount = addCornerIfValid(candidates, cCount, grid, w, h, w - 1, 0, w - 2, 0, w - 1, 1);
        cCount = addCornerIfValid(candidates, cCount, grid, w, h, 0, h - 1, 1, h - 1, 0, h - 2);
        cCount = addCornerIfValid(candidates, cCount, grid, w, h, w - 1, h - 1, w - 2, h - 1, w - 1, h - 2);

        if (cCount > 0) {
            int idx = rnd.nextInt(cCount);
            int ex = candidates[idx][0];
            int ey = candidates[idx][1];
            grid[ey][ex] = PATH;
            return;
        }

        // Fallback: cualquier borde con vecino hacia dentro walkable
        int pack = findValidBorderExitSpot(grid, w, h, rnd);
        if (pack != -1) {
            int y = pack / w;
            int x = pack - (y * w);
            grid[y][x] = PATH;
        }
    }

    private static int addCornerIfValid(int[][] out, int count,
                                        int[][] grid, int w, int h,
                                        int ex, int ey,
                                        int nx1, int ny1,
                                        int nx2, int ny2) {
        if (isWalkableSafe(grid, nx1, ny1, w, h) || isWalkableSafe(grid, nx2, ny2, w, h)) {
            out[count][0] = ex;
            out[count][1] = ey;
            return count + 1;
        }
        return count;
    }

    private static boolean isWalkableSafe(int[][] grid, int x, int y, int w, int h) {
        if (y < 0 || y >= h) return false;
        if (x < 0 || x >= w) return false;
        int t = grid[y][x];
        return t == PATH || t == ICE;
    }

    /**
     * Devuelve un "pack" y*w + x, o -1 si no hay spot.
     */
    private static int findValidBorderExitSpot(int[][] grid, int w, int h, SecureRandom rnd) {
        // Mantenemos lista de spots, pero como ints (pack) para evitar new int[]{...}
        int[] spots = new int[Math.max(8, (w + h) * 2)];
        int count = 0;

        // Superior y=0 vecino (x,1)
        for (int x = 0; x < w; x++) {
            if (isWalkableSafe(grid, x, 1, w, h)) {
                if (count == spots.length) spots = grow(spots);
                spots[count++] = 0 * w + x;
            }
        }
        // Inferior y=h-1 vecino (x,h-2)
        for (int x = 0; x < w; x++) {
            if (isWalkableSafe(grid, x, h - 2, w, h)) {
                if (count == spots.length) spots = grow(spots);
                spots[count++] = (h - 1) * w + x;
            }
        }
        // Izquierdo x=0 vecino (1,y)
        for (int y = 0; y < h; y++) {
            if (isWalkableSafe(grid, 1, y, w, h)) {
                if (count == spots.length) spots = grow(spots);
                spots[count++] = y * w + 0;
            }
        }
        // Derecho x=w-1 vecino (w-2,y)
        for (int y = 0; y < h; y++) {
            if (isWalkableSafe(grid, w - 2, y, w, h)) {
                if (count == spots.length) spots = grow(spots);
                spots[count++] = y * w + (w - 1);
            }
        }

        if (count == 0) return -1;
        return spots[rnd.nextInt(count)];
    }

    private static int[] grow(int[] a) {
        int[] b = new int[a.length << 1];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
