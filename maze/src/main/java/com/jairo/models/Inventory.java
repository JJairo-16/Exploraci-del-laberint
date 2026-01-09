package com.jairo.models;

import com.jairo.items.ItemType;
import com.jairo.items.PowerType;

import java.util.*;

/**
 * Inventario simple: cuenta objetos por tipo y gestiona selección de "powers".
 *
 * Reglas:
 * - selectedPowerIndex == 0 -> ninguno seleccionado
 * - selectedPowerIndex == 1..powers.size() -> powers.get(selectedPowerIndex -
 * 1)
 */
public class Inventory {
    private static final List<String> order = List.of(
            PowerType.values())
            .stream()
            .map(PowerType::getId)
            .toList();

    public Inventory() {
        setFixedPowerOrderIds(order);
    }

    private final Map<String, Integer> countsByTypeId = new HashMap<>();

    // Lista ordenada de poderes/herramientas que se pueden seleccionar
    private final List<ItemType> powers = new ArrayList<>();

    private final List<String> fixedPowerOrderIds = new ArrayList<>();
    private final Map<String, Integer> orderIndexById = new HashMap<>();

    // 0 = nada, 1..N = powers[index-1]
    private int selectedPowerIndex = 0;

    // ------------------------
    // Conteos
    // ------------------------
    public void add(ItemType type) {
        if (type == null)
            return;
        countsByTypeId.merge(type.getId(), 1, Integer::sum);
    }

    public int getCount(ItemType type) {
        if (type == null)
            return 0;
        return countsByTypeId.getOrDefault(type.getId(), 0);
    }

    public int getCountById(String typeId) {
        return countsByTypeId.getOrDefault(typeId, 0);
    }

    public Map<String, Integer> snapshotCounts() {
        return Collections.unmodifiableMap(new HashMap<>(countsByTypeId));
    }

    public boolean has(ItemType type) {
        return getCount(type) > 0;
    }

    // ------------------------
    // Powers list (inventario de selección)
    // ------------------------

    /** Devuelve una vista inmutable del orden actual de powers. */
    public List<ItemType> snapshotPowers() {
        return Collections.unmodifiableList(new ArrayList<>(powers));
    }

    /** Reemplaza la lista completa de powers manteniendo el orden dado. */
    public void setPowers(List<ItemType> newPowers) {
        powers.clear();
        if (newPowers != null) {
            for (ItemType t : newPowers) {
                if (t != null)
                    powers.add(t);
            }
        }
        reorderPowersToFixedOrder();
        clampSelectionAfterListChange();
    }

    /** Añade un power si no está ya (por id) y lo coloca según el orden fijo. */
    public boolean addPower(ItemType type) {
        if (type == null)
            return false;
        if (containsPower(type))
            return false;

        // Inserta en la posición correcta según fixedPowerOrderIds
        int insertPos = powers.size(); // por defecto al final
        int newIdx = getOrderIndex(type);

        if (newIdx != Integer.MAX_VALUE) {
            // busca el primer elemento cuyo orden sea mayor, e inserta antes
            for (int i = 0; i < powers.size(); i++) {
                if (getOrderIndex(powers.get(i)) > newIdx) {
                    insertPos = i;
                    break;
                }
            }
        }

        powers.add(insertPos, type);

        clampSelectionAfterListChange();
        return true;
    }

    /** Elimina un power (por id). */
    public boolean removePower(ItemType type) {
        if (type == null)
            return false;
        boolean removed = powers.removeIf(t -> t != null && Objects.equals(t.getId(), type.getId()));
        if (removed)
            clampSelectionAfterListChange();
        return removed;
    }

    /** true si el power ya está en la lista (por id). */
    public boolean containsPower(ItemType type) {
        if (type == null)
            return false;
        String id = type.getId();
        for (ItemType t : powers) {
            if (t != null && Objects.equals(t.getId(), id))
                return true;
        }
        return false;
    }

    // ------------------------
    // Selección
    // ------------------------

    /** Índice seleccionado: 0 = ninguno; 1..N = powers.get(index-1) */
    public int getSelectedPowerIndex() {
        return selectedPowerIndex;
    }

    /** Selecciona explícitamente (0..N). Si se pasa algo fuera, se corrige. */
    public void setSelectedPowerIndex(int index) {
        int n = powers.size();
        if (n <= 0) {
            selectedPowerIndex = 0;
            return;
        }
        if (index < 0)
            index = 0;
        if (index > n)
            index = n;
        selectedPowerIndex = index;
    }

    /** Devuelve el ItemType seleccionado o null si selectedPowerIndex == 0. */
    public ItemType getSelectedPower() {
        if (selectedPowerIndex <= 0)
            return null;
        int i = selectedPowerIndex - 1;
        if (i < 0 || i >= powers.size())
            return null;
        return powers.get(i);
    }

    /** Desselecciona (vuelve a 0). */
    public void clearSelectedPower() {
        selectedPowerIndex = 0;
    }

    /**
     * Avanza la selección: 0 -> 1 -> 2 ... -> N -> 0 (wrap)
     * Si no hay powers, se queda en 0.
     */
    public void selectNextPower() {
        int n = powers.size();
        if (n <= 0) {
            selectedPowerIndex = 0;
            return;
        }
        selectedPowerIndex++;
        if (selectedPowerIndex > n)
            selectedPowerIndex = 0;
    }

    /**
     * Selecciona el siguiente power disponible (que tengas: count > 0),
     * omitiendo los que no tengas.
     *
     * Reglas:
     * - Si no tienes ningún power con count > 0 -> selectedPowerIndex = 0
     * - Si hay alguno, hace wrap y elige el siguiente disponible.
     * - La búsqueda es cíclica y como máximo recorre N elementos.
     */
    public void selectNextPowerWithJump() {
        do {
            selectNextPower();
        } while (!has(getSelectedPower()) && selectedPowerIndex != 0);
    }

    /**
     * Retrocede la selección: 0 <- 1 <- 2 ... <- N
     * Con wrap: 0 -> N, y luego N-1 ... 1 -> 0
     * Si no hay powers, se queda en 0.
     */
    public void selectPrevPower() {
        int n = powers.size();
        if (n <= 0) {
            selectedPowerIndex = 0;
            return;
        }
        selectedPowerIndex--;
        if (selectedPowerIndex < 0)
            selectedPowerIndex = n;
    }

    public void selectPrevPowerWithJump() {
        do {
            selectPrevPower();
        } while (!has(getSelectedPower()) && selectedPowerIndex != 0);
    }

    // ------------------------
    // Helpers internos
    // ------------------------
    private void clampSelectionAfterListChange() {
        int n = powers.size();
        if (n <= 0) {
            selectedPowerIndex = 0;
            return;
        }
        if (selectedPowerIndex > n)
            selectedPowerIndex = n;
        if (selectedPowerIndex < 0)
            selectedPowerIndex = 0;
    }

    public boolean consume(ItemType type, int amount) {
        if (type == null || amount <= 0)
            return false;

        String id = type.getId();
        int have = countsByTypeId.getOrDefault(id, 0);
        if (have < amount)
            return false;

        int left = have - amount;
        if (left <= 0) {
            countsByTypeId.remove(id);
            if (type.isAPower())
                selectNextPowerWithJump();
        } else
            countsByTypeId.put(id, left);

        return true;
    }

    public boolean consumeOne(ItemType type) {
        return consume(type, 1);
    }

    /** Define el orden fijo por IDs. Ej: ["bomb", "hook", "wand"] */
    public void setFixedPowerOrderIds(List<String> ids) {
        fixedPowerOrderIds.clear();
        orderIndexById.clear();

        if (ids != null) {
            int i = 0;
            for (String id : ids) {
                if (id == null)
                    continue;

                // si ya estaba, no lo añadimos ni incrementamos i
                if (orderIndexById.putIfAbsent(id, i) != null) {
                    continue;
                }

                fixedPowerOrderIds.add(id);
                i++;
            }
        }

        reorderPowersToFixedOrder();
    }

    /** Reordena la lista powers actual para respetar fixedPowerOrderIds. */
    private void reorderPowersToFixedOrder() {
        if (powers.isEmpty())
            return;

        // Mantén referencia del seleccionado actual para no “cambiar” el power
        // seleccionado
        ItemType selected = getSelectedPower();

        powers.sort((a, b) -> Integer.compare(getOrderIndex(a), getOrderIndex(b)));

        // Restaura selección al mismo ItemType (si existe)
        if (selected != null) {
            int idx = indexOfPowerById(selected.getId());
            selectedPowerIndex = (idx >= 0) ? (idx + 1) : 0;
        } else {
            clampSelectionAfterListChange();
        }
    }

    /**
     * Devuelve el índice de orden del id; si no está en la lista fija, lo manda al
     * final.
     */
    private int getOrderIndex(ItemType t) {
        if (t == null || t.getId() == null)
            return Integer.MAX_VALUE;
        Integer idx = orderIndexById.get(t.getId());
        return (idx != null) ? idx : Integer.MAX_VALUE;
    }

    /**
     * Busca un power por id y devuelve su índice en powers (0..N-1), o -1 si no
     * está.
     */
    private int indexOfPowerById(String id) {
        if (id == null)
            return -1;
        for (int i = 0; i < powers.size(); i++) {
            ItemType t = powers.get(i);
            if (t != null && Objects.equals(t.getId(), id))
                return i;
        }
        return -1;
    }

}
