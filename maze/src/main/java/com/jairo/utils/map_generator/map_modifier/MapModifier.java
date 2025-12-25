package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;

public class MapModifier {
    private MapModifier() {
    }

    private static final double DOOR_DENSITY = 0.6;
    private static final int MIN_DOOR_SPACING = 20;

    private static boolean useHollowifier = true;

    public static String modify(String flat, int boardWidth, int boardHeight) {
        String hollowed;

        if (useHollowifier) {
            hollowed = SolidWallHollowifier.hollowLargeWallBlocks(flat, boardWidth, boardHeight);
        } else {
            hollowed = flat;
        }
        return DoorConstructor.addOneWayLockedDoors(hollowed, boardWidth, boardHeight, DOOR_DENSITY,
                MIN_DOOR_SPACING, new SecureRandom());
    }
}
