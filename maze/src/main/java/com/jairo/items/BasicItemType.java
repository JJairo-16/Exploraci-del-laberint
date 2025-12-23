package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import com.jairo.models.Board;

import static com.jairo.items.Qualities.*;

public enum BasicItemType implements ItemType {
    COIN(Sprite.COIN, 0.12, 5, 4, Sound.COIN),
    CHEATED_BUTTON(Sprite.CHEATED_BUTTON, 0, 40, 1, Sound.CHEATED_BUTTON, EPIC);
    
    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;

    BasicItemType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities quality) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx.path();
        this.quality = quality;
    }

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
            case CHEATED_BUTTON -> 1;
            default -> Integer.MAX_VALUE;
        };
    }

    @Override public int getMinCount() {
        return switch (this) {
            case CHEATED_BUTTON -> 1;
            case COIN -> 30;
            default -> 0;
        };
    }
}
