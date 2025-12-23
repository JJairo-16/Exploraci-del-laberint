package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;

public class MapModifier {
    private MapModifier() {}

    private static final double DOOR_DENSITY = 0.6;
    private static final int MIN_DOOR_SPACING = 20;

    public static String modify(String flat, int boardWidth, int boardHeight) {
        String hollowed = SolidWallHollowifier.hollowLargeWallBlocks(flat, boardWidth, boardHeight);
        String doors = DoorConstructor.addOneWayLockedDoors(hollowed, boardWidth, boardHeight, DOOR_DENSITY, MIN_DOOR_SPACING, new SecureRandom());
        return doors;
    }
}
