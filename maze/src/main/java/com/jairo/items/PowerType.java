package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import static com.jairo.items.Qualities.*;

/**
 * Powers/herramientas seleccionables (inventario tipo "slot").
 * Implementa ItemType para poder reusar conteos, sprites y pickup.
 */
public enum PowerType implements ItemType {
    PICKAXE(Sprite.PICKAXE, 0.015, 3, 6, Sound.POWERUP, EPIC), // ? 0.01 3 6
    BLAI_GLASSES(Sprite.BLAI_GLASSES, 0.0025, 10, 25, Sound.POWERUP, LEGENDARY); // ? 0.005 10 25

    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
        this.quality = q;
    }

    @Override public String getId() { return name(); }
    @Override public Sprite getSprite() { return sprite; }
    @Override public double getDensity() { return density; }
    @Override public int getMinDistFromPlayer() { return minPlayer; }
    @Override public int getMinDistBetweenItems() { return minBetween; }
    @Override public String getPickupSoundPath() { return pickupSfx; }
    @Override public boolean isAPower() { return true; }
    @Override public Qualities getQuality() { return quality; }
}
