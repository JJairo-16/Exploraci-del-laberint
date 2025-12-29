package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jairo.utils.map_generator.Cells.*;

/**
 * Sustituye algunos suelos de túnel por CHEAT_PATH y exactamente
 * un suelo adyacente y continuo por CHEAT_WALL.
 *
 * Además: convierte aleatoriamente entre un 20% y un 30% de los CHEAT_PATH
 * colocados en HIDDEN_CHEAT_PATH.
 *
 * OJO: Opera sobre el board 2D (List<List<Integer>>), NO sobre el string plano,
 * porque CHEAT_* son valores > 9.
 */
public final class CheatTunnelModifier {

    private CheatTunnelModifier() {}

    // Por defecto: pocos "cheats"
    private static final double DEFAULT_DENSITY = 0.02;
    private static final int MAX_ATTEMPTS_MULT = 8;

    // Ratio de CHEAT_PATH que pasarán a HIDDEN_CHEAT_PATH
    private static final double MIN_HIDDEN_RATIO = 0.2; // 0.2
    private static final double MAX_HIDDEN_RATIO = 0.3; // 0.3

    public static void apply(List<List<Integer>> cells) {
        apply(cells, DEFAULT_DENSITY, new SecureRandom());
    }

    public static void apply(List<List<Integer>> cells, double density, SecureRandom rnd) {
        if (cells == null || cells.isEmpty() || cells.get(0).isEmpty()) return;

        final int h = cells.size();
        final int w = cells.get(0).size();

        // 1) recolectar candidatos: PATH que estén en túnel
        List<int[]> candidates = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 1; x < w - 1; x++) {
                int v = row.get(x);

                if (v != PATH) continue;
                if (!isTunnelCell(cells, x, y)) continue;
                if (nearExit(cells, x, y)) continue;
                
                candidates.add(new int[] {x, y});
            }
        }

        if (candidates.isEmpty()) return;

        // objetivo aproximado
        int target = Math.max(1, (int) Math.round(candidates.size() * density));

        Collections.shuffle(candidates, rnd);

        int placed = 0;
        int attempts = Math.min(candidates.size() * MAX_ATTEMPTS_MULT, 9000);

        // Guardamos dónde colocamos CHEAT_PATH para luego ocultar un % de ellos
        List<int[]> placedCheatPaths = new ArrayList<>(target);

        for (int i = 0; i < attempts && placed < target; i++) {
            int[] p = candidates.get(rnd.nextInt(candidates.size()));
            int x = p[0], y = p[1];

            // Puede haber cambiado por colocaciones anteriores
            if (cells.get(y).get(x) != PATH) continue;
            if (!isTunnelCell(cells, x, y) || nearExit(cells, x, y)) continue;

            // 2) elegir 1 vecino continuo (adyacente) también de túnel
            List<int[]> neigh = tunnelNeighborsAsPath(cells, x, y);
            if (neigh.isEmpty()) continue;

            int[] q = neigh.get(rnd.nextInt(neigh.size()));
            int nx = q[0], ny = q[1];

            // 3) aplicar exactamente lo pedido
            cells.get(y).set(x, CHEAT_PATH);
            cells.get(ny).set(nx, CHEAT_WALL);

            placedCheatPaths.add(new int[] {x, y});
            placed++;
        }

        // 4) Convertir entre el 20% y el 30% de los CHEAT_PATH colocados a HIDDEN_CHEAT_PATH
        if (!placedCheatPaths.isEmpty()) {
            // Elegimos un ratio aleatorio en [0.20, 0.30]
            double ratio = MIN_HIDDEN_RATIO + rnd.nextDouble() * (MAX_HIDDEN_RATIO - MIN_HIDDEN_RATIO);

            int hiddenTarget = (int) Math.round(placedCheatPaths.size() * ratio);
            // Por seguridad: acotar a [0, placedCheatPaths.size()]
            if (hiddenTarget < 0) hiddenTarget = 0;
            if (hiddenTarget > placedCheatPaths.size()) hiddenTarget = placedCheatPaths.size();

            Collections.shuffle(placedCheatPaths, rnd);

            for (int i = 0; i < hiddenTarget; i++) {
                int[] p = placedCheatPaths.get(i);
                int x = p[0], y = p[1];

                // Si por cualquier razón cambió, no lo forzamos
                if (cells.get(y).get(x) == CHEAT_PATH) {
                    cells.get(y).set(x, HIDDEN_CHEAT_PATH);
                }
            }
        }
    }

    // --- Helpers ---

    private static boolean nearExit(List<List<Integer>> c, int x, int y) {
        // evita tocar EXIT / EXIT_CONNECTOR y alrededores inmediatos
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int v = c.get(y + dy).get(x + dx);
                if (v == EXIT || v == EXIT_CONNECTOR) return true;
            }
        }
        return false;
    }

    private static boolean isTunnelCell(List<List<Integer>> c, int x, int y) {
        int v = c.get(y).get(x);
        // Túnel: debe ser suelo "normal" (PATH/DESTROYED/CHEAT...), no salida/unknown/etc.
        if (!isPath(v)) return false;
        if (v == EXIT_CONNECTOR || v == EXIT) return false;

        int deg = 0;
        // cuenta vecinos "suelo"
        if (isTunnelFloor(c.get(y - 1).get(x))) deg++;
        if (isTunnelFloor(c.get(y + 1).get(x))) deg++;
        if (isTunnelFloor(c.get(y).get(x - 1))) deg++;
        if (isTunnelFloor(c.get(y).get(x + 1))) deg++;

        // En un túnel típico (no sala), el grado suele ser 1 o 2.
        // Queremos "túneles" => 2 (corredor / esquina) o 1 (cul-de-sac).
        // Para el requisito de "suelo continuo", con 2 es más estable.
        return deg == 2;
    }

    private static boolean isTunnelFloor(int tile) {
        // Consideramos "suelo" los PATH_TYPES (incluye CHEAT_* si ya aplicaste el cambio en Cells)
        // y también puertas abiertas/abiertas-del-t0do si las tienes como suelos transitables.
        // Pero para túnel, evitamos EXIT/EXIT_CONNECTOR.
        if (tile == EXIT || tile == EXIT_CONNECTOR) return false;
        return isPath(tile) || isOpenedDoor(tile);
    }

    private static boolean isOpenedDoor(int tile) {
        // En tu Cells existen DOOR_OPENED_* (10..13) que son suelos transitables.
        return tile == DOOR_OPENED_FROM_NORTH
            || tile == DOOR_OPENED_FROM_SOUTH
            || tile == DOOR_OPENED_FROM_WEST
            || tile == DOOR_OPENED_FROM_EAST;
    }

    private static List<int[]> tunnelNeighborsAsPath(List<List<Integer>> c, int x, int y) {
        List<int[]> out = new ArrayList<>(4);

        // solo escoger un vecino que sea suelo "normal" (PATH) y además sea túnel
        // así garantizas que CHEAT_WALL se coloca “en túneles” y es continuo.
        if (c.get(y - 1).get(x) == PATH && isTunnelCell(c, x, y - 1) && !nearExit(c, x, y - 1)) out.add(new int[]{x, y - 1});
        if (c.get(y + 1).get(x) == PATH && isTunnelCell(c, x, y + 1) && !nearExit(c, x, y + 1)) out.add(new int[]{x, y + 1});
        if (c.get(y).get(x - 1) == PATH && isTunnelCell(c, x - 1, y) && !nearExit(c, x - 1, y)) out.add(new int[]{x - 1, y});
        if (c.get(y).get(x + 1) == PATH && isTunnelCell(c, x + 1, y) && !nearExit(c, x + 1, y)) out.add(new int[]{x + 1, y});

        return out;
    }
}
