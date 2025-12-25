package com.jairo.items;

import com.jairo.utils.map_generator.Cells;

import java.util.List;

import com.jairo.app.gfx.Sprite;

public interface ItemType {

    String getId();

    /** Mantener getSprite como pediste */
    Sprite getSprite();

    /**
     * Densidad (porción del total de PATH).
     * Ej: 0.01 => 1% de las celdas PATH serán de este item (aprox).
     */
    double getDensity();

    /** Distancia mínima al jugador (en tiles, Manhattan) */
    default int getMinDistFromPlayer() {
        return 0;
    }

    /** Distancia mínima entre items (en tiles, Manhattan) */
    default int getMinDistBetweenItems() {
        return 0;
    }

    /**
     * Límite mínimo opcional (por si densidad da 0 pero quieres mínimo 1)
     */
    default int getMinCount() {
        return 0;
    }

    /**
     * Límite máximo opcional (para no llenar el mapa si densidad es alta)
     */
    default int getMaxCount() {
        return Integer.MAX_VALUE;
    }

    /** Sonido al recoger (opcional) */
    default String getPickupSoundPath() {
        return null;
    }

    default boolean isAPower() {
        return false;
    }

    default Qualities getQuality() {
        return Qualities.COMMON;
    }

    /** Distancia mínima desde la salida (en tiles, Manhattan via BFS) */
    default int getMinDistFromExit() {
        return 0;
    }

    /**
     * Blacklist de tipos de celda donde NO se debe spawnear este item.
     * Ej: List.of(WATER, LAVA, SPIKES)
     *
     * Por defecto, vacío => no filtra nada extra.
     */
    default List<Integer> getSpawnBlackList() {
        return List.of(Cells.CHEAT_WALL);
    }

    default boolean shouldFloat() {
        return true;
    }

    default boolean removeRemaining() {
        return false;
    }

    default boolean shouldDuplicatePickup() {
        return false;
    }

    default int getMinDistFromBorder() {
        return 0;
    }
}
