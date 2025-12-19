package utils.ui;

public class Cleaner {
    private ProcessBuilder cls;
    private static final int DEFAULT_AUX = 20;

    public Cleaner() {
        this.cls = getCls();
    }

    /** Obté el método per netejar la consola. */
    private static ProcessBuilder getCls() {
        boolean isWindows = System.getProperty("os.name", "").startsWith("Windows");
        ProcessBuilder processBuilder;
        
        if (isWindows) {
            processBuilder = new ProcessBuilder("cmd", "/c", "cls");
        } else {
            processBuilder = new ProcessBuilder("clear");
        }

        return processBuilder.inheritIO();
    }

    /** Neteja la consola. En cas d'error, escriu líneas per emular-ho.*/
    public void clear(int aux) {
        try {
            cls.start().waitFor();

        } catch (Exception e) {
            System.out.println("\n".repeat(aux - 1)); // ? Emulació
        }
    }

    /** Neteja la consola. En cas d'error, escriu {@value Cleaner#DEFAULT_AUX} líneas per emular-ho.*/
    public void clear() {
        clear(DEFAULT_AUX);
    }
}
