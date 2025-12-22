package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;

/**
 * Powers/herramientas seleccionables (inventario tipo "slot").
 * Implementa ItemType para poder reusar conteos, sprites y pickup.
 */
public enum PowerType implements ItemType {
    PICKAXE(Sprite.PICKAXE, 0.01, 3, 6, Sound.POWERUP); // ? 0.01 3 6

    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
    }

    @Override public String getId() { return name(); }
    @Override public Sprite getSprite() { return sprite; }
    @Override public double getDensity() { return density; }
    @Override public int getMinDistFromPlayer() { return minPlayer; }
    @Override public int getMinDistBetweenItems() { return minBetween; }
    @Override public String getPickupSoundPath() { return pickupSfx; }
    @Override public boolean isAPower() { return true; }
}
