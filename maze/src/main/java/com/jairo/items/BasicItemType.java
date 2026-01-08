package com.jairo.items;

import java.util.Arrays;
import java.util.List;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;
import com.jairo.items.placement.RelaxPlan.DistComparisonMode;
import com.jairo.items.placement.RelaxPlan.ScanMode;

import static com.jairo.utils.map_generator.Cells.*;

public enum BasicItemType implements ItemType {
    COIN(Sprite.COIN, 0.225, 5, 4, Sound.COIN),
    MAP(Sprite.MAP, 0.008, 30, 20, Sound.MAP_WRITING, Qualities.EPIC), // ? 0.008 40 15
    PORTAL_GUN(Sprite.PORTAL_GUN, 0.0018, 40, 40, Sound.PORTAL_GUN, Qualities.EPIC); // ? 0.0018 40 40

    private final Sprite sprite;
    private final double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;

    private int minDistFromBorder = 0;
    private List<Integer> spawnBlacklist = List.of(CHEAT_WALL);
    private boolean pick = true;
    private int maxUses = -1;

    static {
        MAP.shouldDuplicatePickup = false;
        MAP.minDistFromBorder = 3;

        PORTAL_GUN.shouldDuplicatePickup = false;
        PORTAL_GUN.setMaxUses(3);
        PORTAL_GUN.setSpawnBlacklist(
                CHEAT_PATH,
                HIDDEN_CHEAT_PATH,
                ICE);
    }

    private boolean shouldDuplicatePickup = true;

    BasicItemType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx.path();
        this.quality = Qualities.COMMON;
    }

    BasicItemType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx.path();
        this.quality = q;
    }

    @Override
    public String getId() {
        return name();
    }

    @Override
    public Sprite getSprite() {
        return sprite;
    }

    @Override
    public double getDensity() {
        return density;
    }

    @Override
    public int getMinDistFromPlayer() {
        return minPlayer;
    }

    @Override
    public int getMinDistBetweenItems() {
        return minBetween;
    }

    @Override
    public String getPickupSoundPath() {
        return pickupSfx;
    }

    @Override
    public Qualities getQuality() {
        return quality;
    }

    @Override
    public boolean shouldDuplicatePickup() {
        return shouldDuplicatePickup;
    }

    @Override
    public int getMinDistFromBorder() {
        return minDistFromBorder;
    }

    @Override
    public List<Integer> getSpawnBlacklist() {
        return spawnBlacklist;
    }

    @Override
    public boolean getIfRemovePlaced() {
        if (maxUses > 0)
            maxUses--;
        else
            pick = true;
        return pick;
    }

    @Override
    public int getMaxCount() {
        return switch (this) {
            default -> Integer.MAX_VALUE;
        };
    }

    @Override
    public int getMinCount() {
        return switch (this) {
            case COIN -> 50;
            case MAP -> 2;
            case PORTAL_GUN -> 1;
            default -> 0;
        };
    }

    private void setSpawnBlacklist(int... cellTypes) {
        if (cellTypes == null || cellTypes.length == 0) {
            this.spawnBlacklist = List.of();
            return;
        }

        this.spawnBlacklist = Arrays.stream(cellTypes).boxed().toList();
    }

    private void setMaxUses(int n) {
        this.maxUses = n - 1;
        this.pick = false;
    }

    @Override
    public RelaxPlan getRelaxPlan() {
        return switch (this) {
            case PORTAL_GUN -> portalGunRelaxPlan;
            case MAP -> mapRelaxPlan;
            default -> defaultRelaxPlan;
        };
    }

    private static final RelaxPlan defaultRelaxPlan = RelaxPlan.builder()
            .weightDecay(0.75)
            .build();

    private static final RelaxPlan portalGunRelaxPlan = RelaxPlan.builder()
            .weightDecay(0.75)
            .floor(Constraint.BETWEEN, 20)
            .scanMode(ScanMode.ANY_TYPE)
            .precheckPlayerDistance(true)
            .build();

    private static final RelaxPlan mapRelaxPlan = RelaxPlan.builder()
            .weightDecay(0.75)
            .distComparisonMode(DistComparisonMode.SAME_TYPE_EXACT)
            .build();
}
