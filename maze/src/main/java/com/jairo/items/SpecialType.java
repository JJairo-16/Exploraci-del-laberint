package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;

import static com.jairo.items.Qualities.*;
import static com.jairo.utils.map_generator.Cells.ICE;

import java.util.Arrays;
import java.util.List;

public enum SpecialType implements ItemType {
    CHEATED_BUTTON(Sprite.CHEATED_BUTTON, 50, Sound.CHEATED_BUTTON, EPIC),
    BOOTS(Sprite.BOOTS, 50, Sound.BOOTS, EPIC),
    COINS_POWER(Sprite.PLAYER, 0, Sound.COIN, COMMON);

    private final Sprite sprite;
    private final double density = 1;
    private final int minPlayer;
    private int minBetween = 2;
    private final String pickupSfx;
    private Qualities quality;

    private int minCount = 1;
    private int maxCount = 1;
    private List<Integer> spawnBlacklist;
    private boolean removeRemaining = false;

    SpecialType(Sprite sprite, int minPlayer, Sound pickupSfx, Qualities quality) {
        this.sprite = sprite;
        this.minPlayer = minPlayer;
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
    @Override public List<Integer> getSpawnBlacklist() { return spawnBlacklist; }
    @Override public boolean removeRemaining() { return removeRemaining; }

    static {
        BOOTS.setSpawnBlacklist(ICE);
        COINS_POWER.updateCount(0, 0, false);

        CHEATED_BUTTON.updateCount(2, 2, true);
        BOOTS.updateCount(2, 2, true);
    }

    @SuppressWarnings("unused")
    private void debug() {
        minCount = 100;
        maxCount = 100;
    }

    private void setSpawnBlacklist(int... cellTypes) {
        if (cellTypes == null || cellTypes.length == 0) {
            this.spawnBlacklist = List.of();
            return;
        }

        this.spawnBlacklist = Arrays.stream(cellTypes).boxed().toList();
    }

    @SuppressWarnings("unused")
    private void updateCount(int min, int max, boolean removeRemaining) {
        this.minCount = min;
        this.maxCount = max;
        this.removeRemaining = removeRemaining;

        this.minBetween = 30;
    }

    public void updateQuality(Qualities q) {
        this.quality = q;
    }

    @Override
    public RelaxPlan getRelaxPlan() {
        return switch (this) {
            default -> defaultRelaxPlan;
        };
    }

    private static final RelaxPlan defaultRelaxPlan = RelaxPlan.builder()
            .cooldown(Constraint.PLAYER, 1)
            .floor(Constraint.PLAYER, 30)
            .cooldown(Constraint.BETWEEN, 1)
            .floor(Constraint.BETWEEN, 25)
            .weightDecay(0.75)
            .build();
}
