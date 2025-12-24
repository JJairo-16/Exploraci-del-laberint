package com.jairo.models;

import com.jairo.utils.map_generator.Cells;

/**
 * Encapsula la lógica de validación de alcanzabilidad (spawn -> EXIT).
 * Versión optimizada para leer directamente de un grid 1D (int[]):
 *   tile = grid[y*width + x]
 *
 * Mantiene la misma semántica actual:
 * - BFS 4-dir
 * - caminable si NO hay colisión (Cells.hasCollision(tile) == false)
 */
public final class BoardReachability {

    private final int width;
    private final int height;

    public BoardReachability(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Devuelve true si el EXIT (exitX, exitY) es alcanzable desde (spawnX, spawnY).
     *
     * @param grid 1D: tamaño width*height, index = y*width + x
     */
    public boolean isExitReachable(int[] grid,
                                   int spawnX, int spawnY,
                                   int exitX, int exitY) {
        if (grid == null || grid.length != width * height) return false;

        if (exitX < 0 || exitY < 0 || exitX >= width || exitY >= height) return false;
        if (spawnX < 0 || spawnY < 0 || spawnX >= width || spawnY >= height) return false;

        final int spawnIdx = spawnY * width + spawnX;
        if (Cells.hasCollision(grid[spawnIdx])) return false;

        final int exitIdx = exitY * width + exitX;
        final int n = width * height;

        final boolean[] vis = new boolean[n];
        final int[] q = new int[n];
        int head = 0, tail = 0;

        vis[spawnIdx] = true;
        q[tail++] = spawnIdx;

        while (head < tail) {
            final int idx = q[head++];

            if (idx == exitIdx) return true;

            final int y = idx / width;
            final int x = idx - (y * width);
            final int row = y * width;

            // derecha (x+1)
            if (x + 1 < width) {
                final int nIdx = row + (x + 1);
                if (!vis[nIdx] && !Cells.hasCollision(grid[nIdx])) {
                    vis[nIdx] = true;
                    q[tail++] = nIdx;
                }
            }

            // izquierda (x-1)
            if (x - 1 >= 0) {
                final int nIdx = row + (x - 1);
                if (!vis[nIdx] && !Cells.hasCollision(grid[nIdx])) {
                    vis[nIdx] = true;
                    q[tail++] = nIdx;
                }
            }

            // abajo (y+1)
            if (y + 1 < height) {
                final int nIdx = row + width + x; // (y+1)*width + x
                if (!vis[nIdx] && !Cells.hasCollision(grid[nIdx])) {
                    vis[nIdx] = true;
                    q[tail++] = nIdx;
                }
            }

            // arriba (y-1)
            if (y - 1 >= 0) {
                final int nIdx = row - width + x; // (y-1)*width + x
                if (!vis[nIdx] && !Cells.hasCollision(grid[nIdx])) {
                    vis[nIdx] = true;
                    q[tail++] = nIdx;
                }
            }
        }

        return false;
    }

    /**
     * Cuenta vecinos caminables (4-dir) para validar si el spawn está "atrapado".
     */
    public int countWalkableNeighbors(int[] grid, int x, int y) {
        if (grid == null || grid.length != width * height) return 0;
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;

        int c = 0;

        if (x + 1 < width && !Cells.hasCollision(grid[y * width + (x + 1)])) c++;
        if (x - 1 >= 0 && !Cells.hasCollision(grid[y * width + (x - 1)])) c++;
        if (y + 1 < height && !Cells.hasCollision(grid[(y + 1) * width + x])) c++;
        if (y - 1 >= 0 && !Cells.hasCollision(grid[(y - 1) * width + x])) c++;

        return c;
    }
}
