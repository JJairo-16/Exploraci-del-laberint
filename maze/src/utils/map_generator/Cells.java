package utils.map_generator;

import java.util.List;

public class Cells {

    private Cells() {
    }

    // * Tipus de cel·la
    public static final int PATH = 0;
    public static final int WALL = 1;
    public static final int EXIT_CONNECTOR = 2;
    public static final int UNKNOWN = 3;
    public static final int PLAYER = 4;

    public static int parseCell(char c) {
        return switch (c) {
            case '1' -> WALL;
            case '2' -> EXIT_CONNECTOR;
            default -> PATH;
        };
    }

    public static final List<String> SYMBOLS = List.of(
            "░░", // PATH
            "██", // WALL
            "░░", // EXIT_CONNECTOR
            "  ", // UNKNOWN
            "PL" // PLAYER
    );
}
