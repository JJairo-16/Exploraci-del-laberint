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
        // allocate fresh; matches prior semantics (new map run => empty buckets)
        this.buckets = new ArrayList[bucketsW * bucketsH];
    }

    public void add(PlacedItem it) {
        int bi = bIdx(bX(it.getX()), bY(it.getY()));
        @SuppressWarnings("unchecked")
        ArrayList<PlacedItem> list = buckets[bi];
        if (list == null) {
            list = new ArrayList<>();
            buckets[bi] = list;
        }
        list.add(it);
    }

    public void remove(PlacedItem it) {
        int bi = bIdx(bX(it.getX()), bY(it.getY()));
        @SuppressWarnings("unchecked")
        ArrayList<PlacedItem> list = buckets[bi];
        if (list != null) list.remove(it);
    }

    public boolean respectsMinDistBetween(int x, int y, int minDistBetween, ItemType placingType, RelaxPlan plan) {
        if (minDistBetween <= 0) return true;

        int cbx = bX(x), cby = bY(y);
        int r = (minDistBetween + bucketSize - 1) / bucketSize;

        for (int oy = -r; oy <= r; oy++) {
            int by = cby + oy;
            if (by < 0 || by >= bucketsH) continue;

            for (int ox = -r; ox <= r; ox++) {
                int bx = cbx + ox;
                if (bx < 0 || bx >= bucketsW) continue;

                @SuppressWarnings("unchecked")
                ArrayList<PlacedItem> list = buckets[bIdx(bx, by)];
                if (list == null) continue;

                for (PlacedItem it : list) {
                    if (plan != null && !plan.distConflicts(placingType, it.getType())) continue;

                    int d = Math.abs(it.getX() - x) + Math.abs(it.getY() - y);
                    if (d < minDistBetween) return false;
                }
            }
        }
        return true;
    }

    private int bX(int x) {
        return x / bucketSize;
    }

    private int bY(int y) {
        return y / bucketSize;
    }

    private int bIdx(int bx, int by) {
        return by * bucketsW + bx;
    }
}
