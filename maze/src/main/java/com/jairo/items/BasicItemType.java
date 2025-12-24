package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;

public enum BasicItemType implements ItemType {
    COIN(Sprite.COIN, 0.2, 5, 4, Sound.COIN);
    
    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;

    BasicItemType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx.path();
        this.quality = Qualities.COMMON;
    }

    @Override public String getId() { return name(); }
    @Override public Sprite getSprite() { return sprite; }
    @Override public double getDensity() { return density; }
    @Override public int getMinDistFromPlayer() { return minPlayer; }
    @Override public int getMinDistBetweenItems() { return minBetween; }
    @Override public String getPickupSoundPath() { return pickupSfx; }
    @Override public Qualities getQuality() { return quality; }

    // Opcional: limites
    @Override public int getMaxCount() {
        return switch (this) {
            default -> Integer.MAX_VALUE;
        };
    }

    @Override public int getMinCount() {
        return switch (this) {
            case COIN -> 50;
            default -> 0;
        };
    }
}
