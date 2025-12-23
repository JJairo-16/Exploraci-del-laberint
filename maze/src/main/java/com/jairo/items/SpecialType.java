package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;

import static com.jairo.items.Qualities.*;

public enum SpecialType implements ItemType {
    CHEATED_BUTTON(Sprite.CHEATED_BUTTON, 1, 1, 5, Sound.CHEATED_BUTTON, EPIC), // ? 0 40 1
    BOOTS(Sprite.PLAYER, 1, 1, 5, Sound.POWERUP, EPIC);

    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;

    private int minCount = 1;
    private int maxCount = 1;

    SpecialType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities quality) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx.path();
        this.quality = quality;
    }

    @Override public String getId() { return name(); }
    @Override public Sprite getSprite() { return sprite; }
    @Override public double getDensity() { return density; }
    @Override public int getMinDistFromPlayer() { return minPlayer; }
    @Override public int getMinDistBetweenItems() { return minBetween; }
    @Override public String getPickupSoundPath() { return pickupSfx; }
    @Override public Qualities getQuality() { return quality; }
    @Override public int getMinCount() { return minCount; }
    @Override public int getMaxCount() { return maxCount; }

    static {
        BOOTS.debug();
    }

    private void debug() {
        minCount = 100;
        maxCount = 100;
    }
}
