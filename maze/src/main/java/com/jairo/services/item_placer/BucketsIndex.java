// File: BucketsIndex.java
package com.jairo.services.item_placer;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.placement.RelaxPlan;

import java.util.ArrayList;

public final class BucketsIndex {
    private final int bucketSize;

    private int bucketsW, bucketsH;

    @SuppressWarnings("rawtypes")
    private ArrayList[] buckets; // ArrayList<PlacedItem>[] but stored raw to avoid generic array warnings

    public BucketsIndex(int bucketSize) {
        if (bucketSize <= 0) throw new IllegalArgumentException("bucketSize must be > 0");
        this.bucketSize = bucketSize;
    }

    public int bucketSize() {
        return bucketSize;
    }

    public void init(int mapW, int mapH) {
        this.bucketsW = (mapW + bucketSize - 1) / bucketSize;
        this.bucketsH = (mapH + bucketSize - 1) / bucketSize;
        this.buckets = new ArrayList[bucketsW * bucketsH];
    }

    public void add(PlacedItem it) {
        final int bx = it.getX() / bucketSize;
        final int by = it.getY() / bucketSize;
        final int bi = by * bucketsW + bx;

        @SuppressWarnings("unchecked")
        ArrayList<PlacedItem> list = buckets[bi];
        if (list == null) {
            // Default capacity is fine; changing capacity doesn't change results, but avoid guessing.
            list = new ArrayList<>();
            buckets[bi] = list;
        }
        list.add(it);
    }

    public void remove(PlacedItem it) {
        final int bx = it.getX() / bucketSize;
        final int by = it.getY() / bucketSize;
        final int bi = by * bucketsW + bx;

        @SuppressWarnings("unchecked")
        ArrayList<PlacedItem> list = buckets[bi];
        if (list != null) list.remove(it); // keep semantics (removes first match)
    }

    /**
     * Optimizations applied with NO change in result:
     * - cheap distance checks before plan.distConflicts(...)
     * - index-based loops over ArrayList (no Iterator)
     * - compute bucket index directly (by*bucketsW + bx)
     * - prune buckets that cannot possibly contain a conflicting item (safe via minManhattanToBucket)
     * - fewer method calls inside hot loops
     */
    @SuppressWarnings("rawtypes")
    public boolean respectsMinDistBetween(int x, int y, int minDistBetween, ItemType placingType, RelaxPlan plan) {
        if (minDistBetween <= 0) return true;

        final int bs = this.bucketSize;
        final int bw = this.bucketsW;
        final int bh = this.bucketsH;
        final ArrayList[] localBuckets = this.buckets;

        final int cbx = x / bs;
        final int cby = y / bs;

        // radius in buckets
        final int r = (minDistBetween + bs - 1) / bs;

        for (int oy = -r; oy <= r; oy++) {
            final int by = cby + oy;

            // fast bounds check: equivalent to (by < 0 || by >= bh)
            if ((by | (bh - 1 - by)) < 0) continue;

            final int rowBase = by * bw;

            for (int ox = -r; ox <= r; ox++) {
                final int bx = cbx + ox;

                // fast bounds check: equivalent to (bx < 0 || bx >= bw)
                if ((bx | (bw - 1 - bx)) < 0) continue;

                // Safe pruning: if the MIN possible Manhattan distance from (x,y) to this bucket
                // is already >= minDistBetween, no item inside can violate.
                if (minManhattanToBucket(x, y, bx, by, bs) >= minDistBetween) continue;

                @SuppressWarnings("unchecked")
                final ArrayList<PlacedItem> list = localBuckets[rowBase + bx];
                if (list == null) continue;

                // index loop avoids Iterator overhead
                for (int i = 0, n = list.size(); i < n; i++) {
                    final PlacedItem it = list.get(i);

                    // Distance check first (cheap) to skip most items before calling plan
                    int dx = it.getX() - x;
                    if (dx < 0) dx = -dx;
                    if (dx >= minDistBetween) continue;

                    int dy = it.getY() - y;
                    if (dy < 0) dy = -dy;

                    if (dx + dy >= minDistBetween) continue;

                    // plan check only when distance is already conflicting (same result, less work)
                    if (plan != null && !plan.distConflicts(placingType, it.getType())) continue;

                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Minimum Manhattan distance from point (x,y) to ANY cell inside bucket (bx,by),
     * where the bucket covers rectangle:
     *   [bx*bs, bx*bs+bs-1] x [by*bs, by*bs+bs-1]
     *
     * If this minimum is >= minDistBetween, the bucket cannot contain a conflicting item.
     */
    private static int minManhattanToBucket(int x, int y, int bx, int by, int bs) {
        final int x0 = bx * bs;
        final int y0 = by * bs;
        final int x1 = x0 + bs - 1;
        final int y1 = y0 + bs - 1;

        int dx = 0;
        if (x < x0) dx = x0 - x;
        else if (x > x1) dx = x - x1;

        int dy = 0;
        if (y < y0) dy = y0 - y;
        else if (y > y1) dy = y - y1;

        return dx + dy;
    }
}
