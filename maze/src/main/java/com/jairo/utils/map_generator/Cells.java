package com.jairo.utils.map_generator;

import java.util.List;

public class Cells {

    private Cells() {
    }

    public static final int PATH = 0;
    public static final int WALL = 1;
    public static final int EXIT_CONNECTOR = 2;
    public static final int UNKNOWN = 3;
    public static final int PLAYER = 4;
    public static final int EXIT = 5;

    public static final int DOOR_OPEN_FROM_NORTH = 6;
    public static final int DOOR_OPEN_FROM_SOUTH = 7;
    public static final int DOOR_OPEN_FROM_WEST = 8;
    public static final int DOOR_OPEN_FROM_EAST = 9;

    public static final int COLUMN = 15;

    public static int parseCell(char c) {
        return switch (c) {
            case '1' -> WALL;
            case '2' -> EXIT_CONNECTOR;
            case '3' -> EXIT;
            case '4' -> DOOR_OPEN_FROM_NORTH;
            case '5' -> DOOR_OPEN_FROM_SOUTH;
            case '6' -> DOOR_OPEN_FROM_WEST;
            case '7' -> DOOR_OPEN_FROM_EAST;
            default -> PATH;
        };
    }

    private static final List<Integer> COLLISION = List.of(
        WALL,
        DOOR_OPEN_FROM_NORTH,
        DOOR_OPEN_FROM_SOUTH,
        DOOR_OPEN_FROM_WEST,
        DOOR_OPEN_FROM_EAST
    );

    public static boolean hasCollision(int tile) {
        if (COLLISION.contains(tile)) {
            if (tile != WALL) {
                System.out.println("door");
            }

            return true;
        }

        return false;
    }

    public static final List<String> SYMBOLS = List.of(
            "░░",
            "██",
            "░░",
            "  ",
            "PL",
            "EX",
            "╥╥",
            "╨╨",
            "╞═",
            "═╡"
    );
}
