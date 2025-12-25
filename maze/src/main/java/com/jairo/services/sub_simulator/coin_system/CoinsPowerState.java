package com.jairo.services.sub_simulator.coin_system;

import static com.jairo.items.Qualities.*;

import java.util.List;
import java.util.Map;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.items.Qualities;
import com.jairo.items.SpecialType;
import com.jairo.models.Inventory;
import com.jairo.services.ItemPlacer;

public final class CoinsPowerState {
    private final Searcher searcher;

    private static final Map<ItemType, Integer> priorities = Map.of(
            SpecialType.CHEATED_BUTTON, 100,
            SpecialType.BOOTS, 100,
            PowerType.PICKAXE, 90,
            BasicItemType.COIN, 10,
            PowerType.KEY, 1);

    private static final SoundManager sm = SoundManager.get();
    private static final String SOUND_PATH = Sound.COINS_POWER.path();

    public CoinsPowerState(ItemPlacer placer) {
        searcher = new Searcher(placer);
    }

    private static final int[] LEVEL_THRESHOLDS = {
            CoinSpeedSystem.getMinPercentage(),
            CoinDuplicateSystem.getMinPercentage(),
            40,
            45
    };

    private static int level = 0;
    private boolean enabled = false;

    private double cooldownMultiplier = 1.0;
    private double duplicateChance = 0.0;

    public static int getLevel() {
        return level;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCooldownMultiplier() {
        return (level >= 1 && enabled) ? cooldownMultiplier : 1.0;
    }

    /**
     * Probabilidad de duplicar (solo cuando el poder esté activo en nivel 2+ y
     * enabled).
     */
    public double getDuplicateChance() {
        return (level >= 2) ? duplicateChance : 0.0;
    }

    public double getRadar(int playerX, int playerY) {
        if (level < 3)
            return -1;

        return searcher.signal(playerX, playerY, priorities);
    }

    public void toggle() {
        if (level >= 1)
            enabled = !enabled;
    }

    public void update(int currentCoins, int totalCoins, Inventory inventory) {
        // porcentaje 0..100
        double pct = (totalCoins <= 0) ? 0.0 : (currentCoins * 100.0) / totalCoins;

        // nivel alcanzado
        int newLevel = 0;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (pct >= LEVEL_THRESHOLDS[i])
                newLevel = i + 1;
        }

        // subir nivel -> recompensas únicas
        if (newLevel > level) {
            onLevelUp(level, newLevel, inventory);
            level = newLevel;

            // al desbloquear por primera vez, lo encendemos (si quieres que sea manual,
            // quita esto)
            if (level >= 1 && !enabled)
                enabled = true;
        }

        // Beneficios continuos (no cambies fórmulas internas)
        cooldownMultiplier = CoinSpeedSystem.cooldownMultiplier(currentCoins, totalCoins);

        // Nivel 2+: duplicado
        if (level >= 2) {
            duplicateChance = CoinDuplicateSystem.duplicateChance(currentCoins, totalCoins);
        } else {
            duplicateChance = 0.0;
        }
    }

    private void onLevelUp(int oldLevel, int newLevel, Inventory inventory) {
        // Nivel 1: desbloqueo del poder (entregar item una sola vez)
        if (levelUpped(oldLevel, newLevel, 1)) {
            inventory.add(SpecialType.COINS_POWER);
            playSound();
        }

        // Niveles 2..4: calidad del poder (EPIC, LEGENDARY, UNIQUE)
        // Mapeo correcto:
        // 2 -> EPIC
        // 3 -> LEGENDARY
        // 4 -> UNIQUE
        List<Qualities> qs = List.of(EPIC, LEGENDARY, UNIQUE);

        for (int i = 0; i < qs.size(); i++) {
            int targetLevel = i + 2;
            if (!levelUpped(oldLevel, newLevel, targetLevel))
                continue;

            SpecialType.COINS_POWER.updateQuality(qs.get(i));
            playSound();
        }
    }

    private boolean levelUpped(int oldLevel, int newLevel, int level) {
        return (oldLevel < level && newLevel >= level);
    }

    private void playSound() {
        sm.playSfx(SOUND_PATH);
    }
}
