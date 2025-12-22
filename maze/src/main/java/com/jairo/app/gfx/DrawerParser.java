package com.jairo.app.gfx;

import static com.jairo.app.gfx.Sprite.*;

import java.util.Map;
import java.util.HashMap;

public class DrawerParser {
    private DrawerParser() {}

    private static Map<Integer, Sprite> lib = new HashMap<>();

    private static void put(Sprite sprite) {
        lib.put(lib.size(), sprite);
    }

    static {
        // ? < 0-5 >
        put(PATH);
        put(WALL);
        put(EXIT_CONNECTOR);
        put(NONE);
        put(NONE);
        put(EXIT);

        // ? < 6-9 >
        put(DOOR_OPEN_FROM_NORTH);
        put(DOOR_OPEN_FROM_SOUTH);
        put(DOOR_OPEN_FROM_WEST);
        put(DOOR_OPEN_FROM_EAST);

        // ? < 10-13 >
        put(DOOR_OPENED_FROM_NORTH);
        put(DOOR_OPENED_FROM_SOUTH);
        put(DOOR_OPENED_FROM_WEST);
        put(DOOR_OPENED_FROM_EAST);

        // ? 14
        put(DESTROYED_PATH);
    }

    public static Sprite parse(int tile) {
        return lib.getOrDefault(tile, NONE);
    }
}
