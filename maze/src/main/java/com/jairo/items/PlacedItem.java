package com.jairo.items;

/**
 * Representa un objeto ya colocado en el mapa.
 */
public final class PlacedItem {

    private final ItemType type;
    private final int x;
    private final int y;
    public final Qualities quality;

    public PlacedItem(ItemType type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.quality = type.getQuality();

        if (type == PowerType.KEY) {
            System.out.println("x: " + x);
            System.out.println("y: " + y);
        }
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
