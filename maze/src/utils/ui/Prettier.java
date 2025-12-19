package utils.ui;

import static utils.ui.Ansi.*;

public class Prettier {
    private Prettier() {}

    private static final String INFO_ICO = CYAN + BOLD + "[i]" + RESET;
    private static final String WARN_ICO = ORANGE + BOLD + "[WARN]" + RESET;
    private static final String ERROR_ICO = RED + BOLD + "[ERR]" + RESET;

    public static void info(String message) {
        System.out.printf("%n%s %s%n%n", INFO_ICO, message);
    }

    public static void warn(String message) {
        System.out.printf("%n%s %s%n%n", WARN_ICO, message);
    }

    public static void error(String message) {
        System.out.printf("%n%s %s%n%n", ERROR_ICO, message);
    }
}
