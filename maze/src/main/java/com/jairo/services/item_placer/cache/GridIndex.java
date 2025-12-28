package com.jairo.services.item_placer.cache;

import com.jairo.items.PlacedItem;

public final class GridIndex {
    private boolean[] occ;
    private PlacedItem[] at;

    public void init(int size) {
        occ = new boolean[size];
        at = new PlacedItem[size];
    }

    public void clear() {
        if (occ == null || at == null) return;
        // Arrays.fill aquí es OK; size suele ser pequeño/medio.
        java.util.Arrays.fill(occ, false);
        java.util.Arrays.fill(at, null);
    }

    public boolean isOccupied(int pos) {
        return occ[pos];
    }

    public void setOccupied(int pos, boolean v) {
        occ[pos] = v;
    }

    public PlacedItem get(int pos) {
        return at[pos];
    }

    public void put(int pos, PlacedItem it) {
        occ[pos] = true;
        at[pos] = it;
    }

    public void remove(int pos) {
        occ[pos] = false;
        at[pos] = null;
    }
}
