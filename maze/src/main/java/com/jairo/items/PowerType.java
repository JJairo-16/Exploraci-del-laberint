package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import static com.jairo.items.Qualities.*;

/**
 * Powers/herramientas seleccionables (inventario tipo "slot").
 * Implementa ItemType para poder reusar conteos, sprites y pickup.
 */
public enum PowerType implements ItemType {
    PICKAXE(Sprite.PICKAXE, 0.016, 3, 6, Sound.POWERUP, EPIC), // ? 0.01 3 6
    BLAI_GLASSES(Sprite.BLAI_GLASSES, 0.008, 10, 25, Sound.POWERUP, LEGENDARY), // ? 0.008 10 25
    KEY(Sprite.KEY, 0, 40, 1, Sound.COIN, LEGENDARY, 160); // ? 0 40 1 160

    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;
    private final int minExitDistance;

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
        this.quality = q;
        this.minExitDistance = 0;
    }

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q, int minExitDistance) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
        this.quality = q;
        this.minExitDistance = minExitDistance;
    }

    @Override public String getId() { return name(); }
    @Override public Sprite getSprite() { return sprite; }
    @Override public double getDensity() { return density; }
    @Override public int getMinDistFromPlayer() { return minPlayer; }
    @Override public int getMinDistBetweenItems() { return minBetween; }
    @Override public String getPickupSoundPath() { return pickupSfx; }
    @Override public boolean isAPower() { return true; }
    @Override public Qualities getQuality() { return quality; }
    @Override public int getMinDistFromExit() { return minExitDistance; }

    // Opcional: limites
    @Override public int getMaxCount() {
        return switch (this) {
            case KEY -> 1; // como mucho 1 llave
            default -> Integer.MAX_VALUE;
        };
    }

    @Override public int getMinCount() {
        return switch (this) {
            case KEY -> 1; // asegúrate de que exista
            case PICKAXE -> 20;
            case BLAI_GLASSES -> 10;
            default -> 2;
        };
    }
}
