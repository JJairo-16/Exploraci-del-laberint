package com.jairo.services.item_placer.cache;

import com.jairo.utils.map_generator.Cells;

import java.util.Arrays;
import java.util.List;

public final class MapCache {
    private int w;
    private int h;

    // Flattened per-cell data
    private int[] cellValue;      // cells[y][x] flattened: pos=y*w+x
    private int[] distToBorder;   // precomputed min distance to border for each pos

    // Path cells as linear positions (pos=y*w+x)
    private int[] pathPositions;
    private int pathCount;

    public void rebuild(List<List<Integer>> cells) {
        this.h = cells.size();
        this.w = cells.isEmpty() ? 0 : cells.get(0).size();

        int n = w * h;
        this.cellValue = new int[n];
        this.distToBorder = new int[n];

        IntBag path = new IntBag(Math.max(64, n / 4));

        for (int y = 0; y < h; y++) {
            List<Integer> row = cells.get(y);

            int top = y;
            int bottom = h - 1 - y;

            for (int x = 0; x < w; x++) {
                int pos = y * w + x;

                int v = row.get(x);
                cellValue[pos] = v;

                int left = x;
                int right = w - 1 - x;
                distToBorder[pos] = Math.min(Math.min(left, right), Math.min(top, bottom));

                if (Cells.isPath(v)) {
                    path.add(pos);
                }
            }
        }

        this.pathPositions = Arrays.copyOf(path.data, path.size);
        this.pathCount = path.size;
    }

    public int w() { return w; }
    public int h() { return h; }
    public int size() { return w * h; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    public int idx(int x, int y) {
        return y * w + x;
    }

    public int xOf(int pos) {
        return pos % w;
    }

    public int yOf(int pos) {
        return pos / w;
    }

    public int cellValueAtPos(int pos) {
        return cellValue[pos];
    }

    public int distToBorderAtPos(int pos) {
        return distToBorder[pos];
    }

    public int[] pathPositions() {
        return pathPositions;
    }

    public int pathCount() {
        return pathCount;
    }

    /* -------- tiny int bag -------- */
    private static final class IntBag {
        int[] data;
        int size;

        IntBag(int cap) {
            data = new int[Math.max(16, cap)];
        }

        void add(int v) {
            if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }
    }
}
