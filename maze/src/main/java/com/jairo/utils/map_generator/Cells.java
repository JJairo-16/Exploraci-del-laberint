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

    public static final int DOOR_OPENED_FROM_NORTH = 10;
    public static final int DOOR_OPENED_FROM_SOUTH = 11;
    public static final int DOOR_OPENED_FROM_WEST = 12;
    public static final int DOOR_OPENED_FROM_EAST = 13;

    public static final int DESTROYED_PATH = 14;
    public static final int LOCKED_EXIT = 15;

    public static final int CHEAT_PATH = 16;
    public static final int CHEAT_WALL = 17;
    public static final int CHEAT_WALL_ACTIVE = 18;
    public static final int CHEAT_WALL_SOLID = 19;

    public static final int SECRET_WALL = 20;
    public static final int ICE = 21;

    public static int parseCell(char c) {
        return switch (c) {
            case '1' -> WALL;
            case '2' -> EXIT_CONNECTOR;
            case '3' -> EXIT;
            case '4' -> DOOR_OPEN_FROM_NORTH;
            case '5' -> DOOR_OPEN_FROM_SOUTH;
            case '6' -> DOOR_OPEN_FROM_WEST;
            case '7' -> DOOR_OPEN_FROM_EAST;
            case '8' -> SECRET_WALL;
            case 'h' -> ICE;
            default -> PATH;
        };
    }

    private static final List<Integer> COLLISION = List.of(
            WALL,
            DOOR_OPEN_FROM_NORTH,
            DOOR_OPEN_FROM_SOUTH,
            DOOR_OPEN_FROM_WEST,
            DOOR_OPEN_FROM_EAST,
            LOCKED_EXIT,
            CHEAT_WALL_SOLID,
            SECRET_WALL);

    public static boolean hasCollision(int tile) {
        return COLLISION.contains(tile);
    }

    private static final List<Integer> PATH_TYPES = List.of(
            PATH,
            DESTROYED_PATH,
            CHEAT_PATH,
            CHEAT_WALL,
            CHEAT_WALL_ACTIVE,
            ICE);

    public static boolean isPath(int tile) {
        return PATH_TYPES.contains(tile);
    }

    private static final List<Integer> metalSound = List.of(
            CHEAT_WALL_SOLID);

    public static boolean playMetalSound(int tile) {
        return metalSound.contains(tile);
    }

    private static final List<Integer> breakables = List.of(
            WALL,
            SECRET_WALL);

    public static boolean isBreakable(int tile) {
        return breakables.contains(tile);
    }
}
