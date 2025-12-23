package com.jairo.app.gfx.player_skins;

import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.models.Inventory;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Overrides de tuning por jugador (Inventory) y por item (ItemType).
 * Fallback: si no hay override, usa el tuning base (por skin o default).
 */
public final class HeldItemTuningStore {

    private static final HeldItemTuningStore INSTANCE = new HeldItemTuningStore();

    // IdentityHashMap: cada Inventory es “ese jugador” (por identidad, no equals()).
    private final Map<Inventory, EnumMap<PowerType, HeldItemTuning>> perPlayer = new IdentityHashMap<>();

    private HeldItemTuningStore() {}

    public static HeldItemTuningStore get() {
        return INSTANCE;
    }

    /** Devuelve tuning efectivo: override si existe; si no, baseTuning. */
    public HeldItemTuning get(Inventory playerInv, ItemType item, HeldItemTuning baseTuning) {
        if (playerInv == null || item == null) return baseTuning;

        EnumMap<PowerType, HeldItemTuning> map = perPlayer.get(playerInv);
        if (map == null) return baseTuning;

        HeldItemTuning t = map.get(item);
        return (t != null) ? t : baseTuning;
    }

    /** Setea override para (jugador,item). */
    public void set(Inventory playerInv, PowerType item, HeldItemTuning tuning) {
        if (playerInv == null || item == null) return;

        EnumMap<PowerType, HeldItemTuning> map =
                perPlayer.computeIfAbsent(playerInv, k -> new EnumMap<>(PowerType.class));

        if (tuning == null) {
            map.remove(item);
            if (map.isEmpty()) perPlayer.remove(playerInv);
        } else {
            map.put(item, tuning);
        }
    }

    /** Borra todos los overrides de un jugador (útil si se destruye el player). */
    public void clearPlayer(Inventory playerInv) {
        if (playerInv != null) perPlayer.remove(playerInv);
    }
}
