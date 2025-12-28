package com.jairo.services.item_placer;

import com.jairo.utils.map_generator.Cells;

import java.util.Arrays;
import java.util.List;

/**
 * Calcula campos de distancia (BFS) sobre celdas "path".
 * Diseñado para evitar asignaciones por nodo (usa colas con arrays).
 */
public final class PathDistanceCalculator {

    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    /**
     * BFS desde (sx, sy) si es una celda path; si no, devuelve distancias -1.
     */
    public int[][] distFrom(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        int[][] dist = new int[h][w];
        for (int y = 0; y < h; y++) Arrays.fill(dist[y], -1);

        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return dist;
        if (!Cells.isPath(cells.get(sy).get(sx))) return dist;

        // Cola BFS: guardamos posiciones empaquetadas en un int: (y << 16) | x
        // Asume w,h <= 65535 (más que suficiente para mapas normales).
        int[] q = new int[w * h];
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
     * BFS desde el exit si es path; si no, busca la celda path más cercana (en BFS por el grid completo)
     * y hace BFS desde ahí. Si no existe ninguna, devuelve t0do -1.
     */
    public int[][] distFromExit(List<List<Integer>> cells, int exitX, int exitY) {
        int h = cells.size();
        int w = cells.get(0).size();

        if (exitX >= 0 && exitY >= 0 &&
                exitY < h &&
                exitX < w &&
                Cells.isPath(cells.get(exitY).get(exitX))) {
            return distFrom(cells, exitX, exitY);
        }

        int[] start = findNearestPathCell(cells, exitX, exitY);
        if (start == null) {
            int[][] dist = new int[h][w];
            for (int[] row : dist) Arrays.fill(row, -1);
            return dist;
        }

        return distFrom(cells, start[0], start[1]);
    }

    /**
     * Encuentra la celda path más cercana a (sx,sy) haciendo BFS por el grid (sin restringirse a path).
     * Devuelve {x,y} o null si no hay ninguna path.
     */
    private int[] findNearestPathCell(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        // Visitados como array plano (más cache-friendly).
        boolean[] vis = new boolean[w * h];

        int cx = clamp(sx, 0, w - 1);
        int cy = clamp(sy, 0, h - 1);

        int[] q = new int[w * h];
        int head = 0, tail = 0;

        int start = pack(cx, cy);
        q[tail++] = start;
        vis[cy * w + cx] = true;

        while (head < tail) {
            int p = q[head++];
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
                if (vis[idx]) continue;
                vis[idx] = true;

                q[tail++] = pack(nx, ny);
            }
        }

        return null;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
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
