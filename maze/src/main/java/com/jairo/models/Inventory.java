package com.jairo.models;

import com.jairo.items.ItemType;

import java.util.*;

/**
 * Inventario simple: cuenta objetos por tipo.
 */
public class Inventory {

    private final Map<String, Integer> countsByTypeId = new HashMap<>();

    public void add(ItemType type) {
        if (type == null) return;
        countsByTypeId.merge(type.getId(), 1, Integer::sum);
    }

    public int getCount(ItemType type) {
        if (type == null) return 0;
        return countsByTypeId.getOrDefault(type.getId(), 0);
    }

    public int getCountById(String typeId) {
        return countsByTypeId.getOrDefault(typeId, 0);
    }

    public Map<String, Integer> snapshotCounts() {
        return Collections.unmodifiableMap(new HashMap<>(countsByTypeId));
    }

    public boolean has(ItemType type) {
        return getCount(type) > 0;
    }
}
