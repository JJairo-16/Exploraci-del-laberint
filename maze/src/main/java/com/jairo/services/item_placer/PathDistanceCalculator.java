package com.jairo.services.item_placer;

import com.jairo.utils.map_generator.Cells;

import java.util.Arrays;
import java.util.List;

/**
 * Calcula campos de distancia (BFS) sobre celdas "path".
 * Optimizado para minimizar asignaciones:
 * - Reutiliza dist[][], cola BFS y buffers auxiliares.
 * - Usa epoch-stamping para "vis" en findNearestPathCell (evita limpiar boolean[]).
 *
 * Mantiene la misma API y semántica que tu versión original.
 */
public final class PathDistanceCalculator {

    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    // Buffers reutilizables
    private int cachedW = -1;
    private int cachedH = -1;

    private int[][] dist;       // [h][w], se reutiliza
    private int[] q;            // cola BFS (w*h)

    // Buffers para findNearestPathCell
    private int[] visStamp;     // epoch-stamping (w*h)
    private int visEpoch = 1;
    private int[] q2;           // cola BFS secundaria (w*h)

    /**
     * BFS desde (sx, sy) si es una celda path; si no, devuelve distancias -1.
     */
    public int[][] distFrom(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        ensureCapacity(w, h);

        // reset a -1 (mantenemos semántica original)
        for (int y = 0; y < h; y++) Arrays.fill(dist[y], -1);

        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return dist;
        if (!Cells.isPath(cells.get(sy).get(sx))) return dist;

        int head = 0, tail = 0;

        dist[sy][sx] = 0;
        q[tail++] = pack(sx, sy);

        while (head < tail) {
            int p = q[head++];
            int px = unpackX(p);
            int py = unpackY(p);

            int base = dist[py][px];

            for (int k = 0; k < 4; k++) {
                int nx = px + DX[k];
                int ny = py + DY[k];

                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (dist[ny][nx] != -1) continue;
                if (!Cells.isPath(cells.get(ny).get(nx))) continue;

                dist[ny][nx] = base + 1;
                q[tail++] = pack(nx, ny);
            }
        }

        return dist;
    }

    /**
     * BFS desde el exit si es path; si no, busca la celda path más cercana (BFS por el grid completo)
     * y hace BFS desde ahí. Si no existe ninguna, devuelve t0do -1.
     */
    public int[][] distFromExit(List<List<Integer>> cells, int exitX, int exitY) {
        int h = cells.size();
        int w = cells.get(0).size();

        ensureCapacity(w, h);

        if (exitX >= 0 && exitY >= 0 &&
                exitY < h &&
                exitX < w &&
                Cells.isPath(cells.get(exitY).get(exitX))) {
            return distFrom(cells, exitX, exitY);
        }

        int[] start = findNearestPathCell(cells, exitX, exitY);
        if (start == null) {
            // devolver dist[][] ya reseteado a -1
            for (int y = 0; y < h; y++) Arrays.fill(dist[y], -1);
            return dist;
        }

        return distFrom(cells, start[0], start[1]);
    }

    /**
     * Encuentra la celda path más cercana a (sx,sy) haciendo BFS por el grid (sin restringirse a path).
     * Devuelve {x,y} o null si no hay ninguna path.
     *
     * Optimizado: en vez de boolean[] vis que hay que limpiar, usa visStamp[] + epoch.
     */
    private int[] findNearestPathCell(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        // epoch-stamping: evitar limpiar visStamp[] cada vez
        visEpoch++;
        if (visEpoch == 0) { // overflow raro
            Arrays.fill(visStamp, 0);
            visEpoch = 1;
        }

        int cx = Math.clamp(sx, 0, w - 1);
        int cy = Math.clamp(sy, 0, h - 1);

        int head = 0, tail = 0;
        int start = pack(cx, cy);

        q2[tail++] = start;
        visStamp[cy * w + cx] = visEpoch;

        while (head < tail) {
            int p = q2[head++];
            int px = unpackX(p);
            int py = unpackY(p);

            if (Cells.isPath(cells.get(py).get(px))) {
                return new int[] { px, py };
            }

            for (int k = 0; k < 4; k++) {
                int nx = px + DX[k];
                int ny = py + DY[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;

                int idx = ny * w + nx;
                if (visStamp[idx] == visEpoch) continue;
                visStamp[idx] = visEpoch;

                q2[tail++] = pack(nx, ny);
            }
        }

        return null;
    }

    private void ensureCapacity(int w, int h) {
        if (w == cachedW && h == cachedH && dist != null) return;

        cachedW = w;
        cachedH = h;

        dist = new int[h][w];
        q = new int[w * h];

        visStamp = new int[w * h];
        q2 = new int[w * h];

        visEpoch = 1;
    }

    private static int pack(int x, int y) {
        return (y << 16) | (x & 0xFFFF);
    }

    private static int unpackX(int p) {
        return p & 0xFFFF;
    }

    private static int unpackY(int p) {
        return (p >>> 16);
    }
}
