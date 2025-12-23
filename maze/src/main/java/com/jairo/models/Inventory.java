package com.jairo.models;

import com.jairo.items.ItemType;

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

    private final Map<String, Integer> countsByTypeId = new HashMap<>();

    // Lista ordenada de poderes/herramientas que se pueden seleccionar
    private final List<ItemType> powers = new ArrayList<>();

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
        clampSelectionAfterListChange();
    }

    /** Añade un power si no está ya (por id). */
    public boolean addPower(ItemType type) {
        if (type == null)
            return false;
        if (containsPower(type))
            return false;
        powers.add(type);
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

    public void selectNextPowerWithJump() {
        int n = powers.size();
        if (n <= 0) {
            selectedPowerIndex = 0;
            return;
        }

        for (int attempts = 0; attempts < n; attempts++) {
            selectNextPower(); // avanza con wrap
            ItemType sel = getSelectedPower();

            // si llega a 0, sigue buscando
            if (selectedPowerIndex == 0)
                continue;

            // nos quedamos en el primero que realmente tenemos
            if (sel != null && has(sel))
                return;
        }

        // no hay ninguno que tengas
        selectedPowerIndex = 0;
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

}
