package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jairo.utils.map_generator.MapGenerator;

import static com.jairo.utils.map_generator.Cells.*;

public final class RoomIceFloorModifier {

    private RoomIceFloorModifier() {}

    // En tu ejemplo estaban a 1, lo dejo igual
    private static final double MIN_RATIO = 0.20;
    private static final double MAX_RATIO = 0.35;

    // Cuántas "capas" de PATH adyacente a ICE convertimos (para cubrir entradas/puertas)
    private static final int ICE_EXPAND_STEPS = 1;

    public static void apply(List<List<Integer>> cells) {
        apply(cells, new SecureRandom());
    }

    public static void apply(List<List<Integer>> cells, SecureRandom rnd) {
        if (cells == null || cells.isEmpty() || cells.get(0).isEmpty()) return;

        // Rects: {x1,y1,x2,y2}
        List<int[]> rooms = MapGenerator.getLastRoomsAsRects();
        if (rooms == null || rooms.isEmpty()) return;

        final int h = cells.size();
        final int w = cells.get(0).size();

        double ratio = MIN_RATIO + rnd.nextDouble() * (MAX_RATIO - MIN_RATIO);
        int target = (int) Math.round(rooms.size() * ratio);
        target = Math.min(target, rooms.size());
        target = Math.max(1, target);

        List<int[]> pick = new ArrayList<>(rooms);
        Collections.shuffle(pick, rnd);

        // 1) Convertir PATH -> ICE dentro de las rooms seleccionadas
        for (int i = 0; i < target; i++) {
            int[] r = pick.get(i);
            int x1 = clamp(r[0], 0, w - 1);
            int y1 = clamp(r[1], 0, h - 1);
            int x2 = clamp(r[2], 0, w - 1);
            int y2 = clamp(r[3], 0, h - 1);

            for (int y = y1; y <= y2; y++) {
                List<Integer> row = cells.get(y);
                for (int x = x1; x <= x2; x++) {
                    if (row.get(x) == PATH) {
                        row.set(x, ICE);
                    }
                }
            }
        }

        // 2) Expandir el hielo comiéndose entradas (PATH pegado a ICE) en 1-2 capas
        for (int step = 0; step < ICE_EXPAND_STEPS; step++) {
            expandIceOneStep(cells);
        }

        // 3) Si en ninguna esquina hay salida (PATH), añadir una aleatoria que conecte a walkable
        ensureCornerExit(cells, rnd);
    }

    private static void expandIceOneStep(List<List<Integer>> cells) {
        final int h = cells.size();
        final int w = cells.get(0).size();

        // marcamos cambios sin interferir con el barrido
        List<int[]> toIce = new ArrayList<>(256);

        for (int y = 0; y < h; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 0; x < w; x++) {
                if (row.get(x) != PATH) continue;

                // si este PATH toca (4-dir) a ICE, lo convertimos
                if (hasNeighborIce(cells, x, y)) {
                    toIce.add(new int[] { x, y });
                }
            }
        }

        for (int[] p : toIce) {
            cells.get(p[1]).set(p[0], ICE);
        }
    }

    private static boolean hasNeighborIce(List<List<Integer>> cells, int x, int y) {
        return isIceSafe(cells, x + 1, y)
            || isIceSafe(cells, x - 1, y)
            || isIceSafe(cells, x, y + 1)
            || isIceSafe(cells, x, y - 1);
    }

    private static boolean isIceSafe(List<List<Integer>> cells, int x, int y) {
        if (y < 0 || y >= cells.size()) return false;
        List<Integer> row = cells.get(y);
        if (x < 0 || x >= row.size()) return false;
        return row.get(x) == ICE;
    }

    private static void ensureCornerExit(List<List<Integer>> cells, SecureRandom rnd) {
        final int h = cells.size();
        final int w = cells.get(0).size();
        if (h < 2 || w < 2) return;

        // Si ya hay PATH en alguna esquina, ya hay salida
        if (cells.get(0).get(0) == PATH ||
            cells.get(0).get(w - 1) == PATH ||
            cells.get(h - 1).get(0) == PATH ||
            cells.get(h - 1).get(w - 1) == PATH) {
            return;
        }

        // Candidatas: esquina cuyo vecino hacia dentro sea walkable (PATH o ICE)
        List<int[]> candidates = new ArrayList<>(4);

        addCornerIfValid(candidates, cells, 0, 0, 1, 0, 0, 1);
        addCornerIfValid(candidates, cells, w - 1, 0, w - 2, 0, w - 1, 1);
        addCornerIfValid(candidates, cells, 0, h - 1, 1, h - 1, 0, h - 2);
        addCornerIfValid(candidates, cells, w - 1, h - 1, w - 2, h - 1, w - 1, h - 2);

        if (!candidates.isEmpty()) {
            int[] c = candidates.get(rnd.nextInt(candidates.size()));
            cells.get(c[1]).set(c[0], PATH);
            return;
        }

        // Fallback: cualquier borde con vecino hacia dentro walkable
        int[] border = findValidBorderExitSpot(cells, rnd);
        if (border != null) {
            cells.get(border[1]).set(border[0], PATH);
        }
    }

    private static void addCornerIfValid(List<int[]> list,
                                         List<List<Integer>> cells,
                                         int ex, int ey,
                                         int nx1, int ny1,
                                         int nx2, int ny2) {
        if (isWalkableSafe(cells, nx1, ny1) || isWalkableSafe(cells, nx2, ny2)) {
            list.add(new int[] { ex, ey });
        }
    }

    private static boolean isWalkableSafe(List<List<Integer>> cells, int x, int y) {
        if (y < 0 || y >= cells.size()) return false;
        List<Integer> row = cells.get(y);
        if (x < 0 || x >= row.size()) return false;
        int t = row.get(x);
        return t == PATH || t == ICE;
    }

    private static int[] findValidBorderExitSpot(List<List<Integer>> cells, SecureRandom rnd) {
        final int h = cells.size();
        final int w = cells.get(0).size();

        List<int[]> spots = new ArrayList<>();

        // Superior y=0 vecino (x,1)
        for (int x = 0; x < w; x++) if (isWalkableSafe(cells, x, 1)) spots.add(new int[] { x, 0 });
        // Inferior y=h-1 vecino (x,h-2)
        for (int x = 0; x < w; x++) if (isWalkableSafe(cells, x, h - 2)) spots.add(new int[] { x, h - 1 });
        // Izquierdo x=0 vecino (1,y)
        for (int y = 0; y < h; y++) if (isWalkableSafe(cells, 1, y)) spots.add(new int[] { 0, y });
        // Derecho x=w-1 vecino (w-2,y)
        for (int y = 0; y < h; y++) if (isWalkableSafe(cells, w - 2, y)) spots.add(new int[] { w - 1, y });

        if (spots.isEmpty()) return null;
        return spots.get(rnd.nextInt(spots.size()));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
