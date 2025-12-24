package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jairo.utils.map_generator.MapGenerator;

import static com.jairo.utils.map_generator.Cells.*;

public final class RoomIceFloorModifier {

    private RoomIceFloorModifier() {
    }

    private static final double MIN_RATIO = 1; // 0.20
    private static final double MAX_RATIO = 1; // 0.30

    public static void apply(List<List<Integer>> cells) {
        apply(cells, new SecureRandom());
    }

    public static void apply(List<List<Integer>> cells, SecureRandom rnd) {
        if (cells == null || cells.isEmpty() || cells.get(0).isEmpty())
            return;

        // Rects: {x1,y1,x2,y2} (viene de la lista estática del MapGenerator)
        List<int[]> rooms = MapGenerator.getLastRoomsAsRects();
        if (rooms == null || rooms.isEmpty())
            return;

        // Elegir ratio random en [0.20, 0.30]
        double ratio = MIN_RATIO + rnd.nextDouble() * (MAX_RATIO - MIN_RATIO);

        int target = (int) Math.round(rooms.size() * ratio);
        target = Math.min(target, rooms.size());
        target = Math.max(1, target);

        // Selección aleatoria de habitaciones
        List<int[]> pick = new ArrayList<>(rooms);
        Collections.shuffle(pick, rnd);

        final int h = cells.size();
        final int w = cells.get(0).size();

        for (int i = 0; i < target; i++) {
            int[] r = pick.get(i);
            int x1 = r[0], y1 = r[1], x2 = r[2], y2 = r[3];

            // Clamp por seguridad
            x1 = clamp(x1, 0, w - 1);
            x2 = clamp(x2, 0, w - 1);
            y1 = clamp(y1, 0, h - 1);
            y2 = clamp(y2, 0, h - 1);

            // Convertir suelos de la habitación a H_ROOM_FLOOR
            for (int y = y1; y <= y2; y++) {
                List<Integer> row = cells.get(y);
                for (int x = x1; x <= x2; x++) {
                    int tile = row.get(x);

                    // Solo tocar suelos transitables y evitar salida/conector
                    if (!isPath(tile) ||
                            tile == EXIT || tile == EXIT_CONNECTOR)
                        continue;

                    row.set(x, ICE);
                }
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
