package com.jairo.services.sub_simulator.coin_system;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.services.ItemPlacer;

import java.util.List;
import java.util.Map;

public class Searcher {
    private final ItemPlacer placer;

    /**
     * Controla qué tan rápido cae la señal con la distancia.
     * - Valores pequeños => cae más rápido
     * - Valores grandes => cae más lento
     */
    private final double distanceFalloff;

    public Searcher(ItemPlacer placer) {
        this(placer, 10.0);
    }

    public Searcher(ItemPlacer placer, double distanceFalloff) {
        this.placer = placer;
        this.distanceFalloff = Math.max(0.0001, distanceFalloff);
    }

    /**
     * Devuelve una señal 1..100:
     * 1) Se elige el "mejor" item por prioridad (mayor primero)
     * 2) En empate de prioridad, se elige el más cercano
     * 3) La señal final se basa en ese ganador (prioridad manda, distancia solo modula)
     *
     * @param priorities ItemType -> prioridad (mayor = más prioritario)
     */
    public double signal(int x, int y, Map<ItemType, Integer> priorities) {
        if (priorities == null || priorities.isEmpty()) return 1.0;

        // Normalización de prioridad
        int maxPriority = Integer.MIN_VALUE;
        for (int p : priorities.values()) maxPriority = Math.max(maxPriority, p);
        if (maxPriority <= 0) maxPriority = 1;

        List<PlacedItem> all = placer.getPlacedItems();
        if (all == null || all.isEmpty()) return 1.0;

        // Elegimos ganador por: (prioridad desc, distancia asc)
        PlacedItem bestItem = null;
        int bestPriority = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (PlacedItem it : all) {
            Integer pr = priorities.get(it.getType());
            if (pr == null) continue;

            int dist = manhattan(it.getX(), it.getY(), x, y);

            if (bestItem == null
                    || pr > bestPriority
                    || (pr == bestPriority && dist < bestDist)) {
                bestItem = it;
                bestPriority = pr;
                bestDist = dist;
            }
        }

        if (bestItem == null) return 1.0;

        // Señal basada en el ganador:
        // - prioridad manda (lineal 0..1)
        // - distancia solo modula un poco (0.6..1.0), pero NO cambia el "ganador"
        double priorityFactor = clamp01(bestPriority / (double) maxPriority);
        double distanceFactor = Math.exp(-bestDist / distanceFalloff);

        double score01 = priorityFactor * (0.6 + 0.4 * distanceFactor); // prioridad-first
        double raw = score01 * 100.0;

        return clamp(raw, 1.0, 100.0);
    }

    private static int manhattan(int ax, int ay, int bx, int by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }
}
