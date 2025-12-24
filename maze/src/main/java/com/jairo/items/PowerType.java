package com.jairo.items;

import com.jairo.app.audio.Sound;
import com.jairo.app.gfx.Sprite;
import static com.jairo.items.Qualities.*;

/**
 * Powers/herramientas seleccionables (inventario tipo "slot").
 * Implementa ItemType para poder reusar conteos, sprites y pickup.
 */
public enum PowerType implements ItemType {
    PICKAXE(Sprite.PICKAXE, 0.0125, 3, 6, Sound.POWERUP, EPIC), // ? 0.0125 3 6
    BLAI_GLASSES(Sprite.BLAI_GLASSES, 0.006, 10, 20, Sound.POWERUP, LEGENDARY), // ? 0.006 10 20
    KEY(Sprite.KEY, 0, 40, 1, Sound.COIN, LEGENDARY, 90); // ? 0 40 1 90

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

    @SuppressWarnings("unused")
    private static double[] recommendedDensityRange(int minDistBetween, int minDistFromPlayer) {
        if (minDistBetween <= 0 || minDistFromPlayer <= 0) {
            throw new IllegalArgumentException("Distances must be > 0");
        }

        double minDensity = 1.0 / (minDistBetween * minDistBetween);
        double maxDensity = 1.0 / (minDistBetween * minDistFromPlayer);

        // Por seguridad, aseguramos orden correcto
        if (minDensity > maxDensity) {
            double tmp = minDensity;
            minDensity = maxDensity;
            maxDensity = tmp;
        }

        return new double[] { minDensity, maxDensity };
    }

    @SuppressWarnings("unused")
    private static double recommendedDensity(
            int minDistBetween,
            int minDistFromPlayer) {
        if (minDistBetween <= 0 || minDistFromPlayer <= 0) {
            throw new IllegalArgumentException("Distances must be > 0");
        }

        double min = 1.0 / (minDistBetween * minDistBetween);
        double max = 1.0 / (minDistBetween * minDistFromPlayer);

        // Centro del rango, sesgado hacia el mínimo (60% seguro)
        return min + (max - min) * 0.4;
    }

}
