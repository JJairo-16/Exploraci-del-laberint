package utils.ui;

public class Ansi {
    private Ansi() {}

    // * ESC
    private static final String ESC = "\u001b[";

    // #region Formato
    public static final String RESET = ESC + "0m";
    public static final String BOLD = ESC + "1m";

    // #endregion

    // #region Colores
    public static final String RED = ESC + "31m";
    public static final String GREEN = ESC + "32m";
    public static final String YELLOW = ESC + "33m";
    public static final String BLUE = ESC + "34m";
    public static final String MAGENTA = ESC + "35m";
    public static final String CYAN = ESC + "36m";
    public static final String WHITE = ESC + "37m";

    public static final String ORANGE = ESC + "38;2;255;165;0m";

    // #endregion
}
