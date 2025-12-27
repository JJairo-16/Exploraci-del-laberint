package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;

import static com.jairo.items.Qualities.*;

/**
 * Powers/herramientas seleccionables (inventario tipo "slot").
 * Implementa ItemType para poder reusar conteos, sprites y pickup.
 */
public enum PowerType implements ItemType {
    PICKAXE(Sprite.PICKAXE, 0.0225, 3, 6, Sound.POWERUP, EPIC), // ? 0.0125 3 6
    BLAI_GLASSES(Sprite.BLAI_GLASSES, 0.008, 10, 20, Sound.POWERUP, LEGENDARY), // ? 0.006 10 20
    KEY(Sprite.KEY, 0, 40, 1, Sound.COIN, LEGENDARY, 90), // ? 0 40 1 90
    BROKEN_KEY(Sprite.BROKEN_KEY, 0.01, 10, 20, Sound.POWERUP, COMMON);

    private final Sprite sprite;
    private double density;
    private final int minPlayer;
    private final int minBetween;
    private final String pickupSfx;
    private final Qualities quality;
    private final int minExitDistance;

    private boolean shouldDuplicatePickup = false;

    static {
        PICKAXE.updateShouldDuplicatePickup();
        BLAI_GLASSES.updateShouldDuplicatePickup();
    }

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
        this.quality = q;
        this.minExitDistance = 0;
    }

    PowerType(Sprite sprite, double density, int minPlayer, int minBetween, Sound pickupSfx, Qualities q,
            int minExitDistance) {
        this.sprite = sprite;
        this.density = density;
        this.minPlayer = minPlayer;
        this.minBetween = minBetween;
        this.pickupSfx = pickupSfx != null ? pickupSfx.path() : null;
        this.quality = q;
        this.minExitDistance = minExitDistance;
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
    public boolean isAPower() {
        return true;
    }

    @Override
    public Qualities getQuality() {
        return quality;
    }

    @Override
    public int getMinDistFromExit() {
        return minExitDistance;
    }

    // Opcional: limites
    @Override
    public int getMaxCount() {
        return switch (this) {
            case KEY -> 1; // como mucho 1 llave
            default -> Integer.MAX_VALUE;
        };
    }

    @Override
    public int getMinCount() {
        return switch (this) {
            case KEY -> 1; // asegúrate de que exista
            case PICKAXE -> 20;
            case BLAI_GLASSES -> 10;
            default -> 2;
        };
    }

    @Override
    public boolean shouldDuplicatePickup() {
        return shouldDuplicatePickup;
    }

    private void updateShouldDuplicatePickup() {
        shouldDuplicatePickup = true;
    }

    public void setDensity(double d) {
        this.density = d;
    }

    @Override
    public RelaxPlan getRelaxPlan() {
        return switch (this) {
            case KEY -> keyRelaxPlan();
            default -> defaultRelaxPlan();
        };
    }

    private RelaxPlan defaultRelaxPlan() {
        return RelaxPlan.builder()
                .weightDecay(0.75)
                .build();
    }

    private RelaxPlan keyRelaxPlan() {
        return RelaxPlan.builder()
                .cooldown(Constraint.PLAYER, 2)
                .floor(Constraint.PLAYER, 30)
                .cooldown(Constraint.EXIT, 1)
                .floor(Constraint.EXIT, 50)
                .weightDecay(0.75)
                .build();
    }
}
