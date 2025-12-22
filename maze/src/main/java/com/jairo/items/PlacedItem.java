package com.jairo.items;

/**
 * Representa un objeto ya colocado en el mapa.
 */
public final class PlacedItem {

    private final ItemType type;
    private final int x;
    private final int y;

    public PlacedItem(ItemType type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public ItemType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isAPower() {
        return type.isAPower();
    }
}
