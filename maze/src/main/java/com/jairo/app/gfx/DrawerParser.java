package com.jairo.app.gfx;

import static com.jairo.app.gfx.Sprite.*;

public final class DrawerParser {
    private DrawerParser() {
    }

    // Importante: el índice es el tile id.
    private static final Sprite[] LIB;

    static {
        // Ajusta el tamaño al mayor id que uses + 1
        LIB = new Sprite[] {
                // 0-5
                PATH, WALL, EXIT_CONNECTOR, NONE, NONE, EXIT,

                // 6-9
                DOOR_OPEN_FROM_NORTH,
                DOOR_OPEN_FROM_SOUTH,
                DOOR_OPEN_FROM_WEST,
                DOOR_OPEN_FROM_EAST,

                // 10-13
                DOOR_OPENED_FROM_NORTH,
                DOOR_OPENED_FROM_SOUTH,
                DOOR_OPENED_FROM_WEST,
                DOOR_OPENED_FROM_EAST,

                // 14-...
                DESTROYED_PATH,
                LOCKED_EXIT,

                CHEATED_PATH,
                CHEATED_WALL,
                CHEAT_WALL_ACTIVE,
                CHEATED_WALL_SOLID,

                ICE
        };
    }

    public static Sprite parse(int tile) {
        // Maneja negativos o ids fuera de rango
        return (tile >= 0 && tile < LIB.length) ? LIB[tile] : NONE;
    }
}
