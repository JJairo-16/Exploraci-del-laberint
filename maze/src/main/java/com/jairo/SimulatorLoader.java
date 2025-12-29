package com.jairo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.items.SpecialType;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.services.ItemPlacer;
import com.jairo.services.Simulator;
import com.jairo.utils.map_generator.Cells;

public class SimulatorLoader {

    private static final Logger log = LoggerFactory.getLogger(SimulatorLoader.class);

    private SimulatorLoader() {
    }

    private static final boolean FORCE_REACHABLE_EXIT = true;
    private static final int MAX_TRIES = 50;

    private static final List<ItemType> items = List.of(
            BasicItemType.COIN,
            BasicItemType.MAP,
            BasicItemType.PORTAL_GUN,
            SpecialType.CHEATED_BUTTON,
            SpecialType.BOOTS,
            PowerType.PICKAXE,
            PowerType.BLAI_GLASSES,
            PowerType.KEY,
            PowerType.BROKEN_KEY);

    public static Simulator load() {

        long t0 = System.nanoTime();

        Board board = null;
        Player player = null;

        // ───────────── Board + Player (con subfases) ─────────────
        long bpStart = System.nanoTime();

        long sumGenNs = 0L;
        long sumPlayerNs = 0L;
        long sumReachableNs = 0L;

        if (!FORCE_REACHABLE_EXIT) {

            long g0 = System.nanoTime();
            board = Board.generateStandard();
            long g1 = System.nanoTime();
            sumGenNs += (g1 - g0);

            long p0 = System.nanoTime();
            player = new Player(board);
            long p1 = System.nanoTime();
            sumPlayerNs += (p1 - p0);

            logTimeMs("Board+Player (1 intento)", bpStart, System.nanoTime());
            log.info("[TIME]   - generateStandard(): {} ms", toMs(sumGenNs));
            log.info("[TIME]   - new Player(board):  {} ms", toMs(sumPlayerNs));

        } else {

            int tries = 0;
            boolean loop = true;

            while (loop) {
                tries++;

                long tryStart = System.nanoTime();

                long g0 = System.nanoTime();
                board = Board.generateStandard();
                long g1 = System.nanoTime();
                long genNs = (g1 - g0);
                sumGenNs += genNs;

                long p0 = System.nanoTime();
                player = new Player(board);
                long p1 = System.nanoTime();
                long playerNs = (p1 - p0);
                sumPlayerNs += playerNs;

                long r0 = System.nanoTime();
                boolean reachable = board.isExitReachableFrom(player.getX(), player.getY());
                long r1 = System.nanoTime();
                long reachNs = (r1 - r0);
                sumReachableNs += reachNs;

                long tryEnd = System.nanoTime();

                // Log por intento (útil si MAX_TRIES es pequeño; si no, puedes bajarlo a DEBUG)
                log.info("[TIME] Intento {}/{}: total={} ms | gen={} ms | player={} ms | reachable={} ms | ok={}",
                        tries, MAX_TRIES,
                        toMs(tryEnd - tryStart),
                        toMs(genNs),
                        toMs(playerNs),
                        toMs(reachNs),
                        reachable);

                if (reachable) {
                    loop = false;
                } else if (tries >= MAX_TRIES) {
                    // fallback a estándar
                    long fg0 = System.nanoTime();
                    board = Board.generateStandard();
                    long fg1 = System.nanoTime();
                    sumGenNs += (fg1 - fg0);

                    long fp0 = System.nanoTime();
                    player = new Player(board);
                    long fp1 = System.nanoTime();
                    sumPlayerNs += (fp1 - fp0);

                    loop = false;
                }
            }

            long bpEnd = System.nanoTime();
            logTimeMs("Board+Player (TOTAL)", bpStart, bpEnd);
            log.info("[TIME]   - generateStandard() TOTAL: {} ms", toMs(sumGenNs));
            log.info("[TIME]   - new Player(board) TOTAL:  {} ms", toMs(sumPlayerNs));
            log.info("[TIME]   - isExitReachableFrom TOTAL: {} ms", toMs(sumReachableNs));
        }

        // ───────────── Limpieza salida ─────────────
        long cleanStart = System.nanoTime();

        int exitX = board.getExitX();
        int exitY = board.getExitY();
        cleanRediusOfGhostRoom(board, exitX, exitY);
        board.updateTile(exitX, exitY, Cells.LOCKED_EXIT, false);

        long cleanEnd = System.nanoTime();
        logTimeMs("Limpieza salida", cleanStart, cleanEnd);

        // ───────────── Densidad Broken Key ─────────────
        long densityStart = System.nanoTime();
        setBrokenKeyDensity(board);
        long densityEnd = System.nanoTime();
        logTimeMs("Densidad BrokenKey", densityStart, densityEnd);

        // ───────────── Colocación items ─────────────
        long itemsStart = System.nanoTime();

        ItemPlacer placer;
        int count;

        do {
            placer = new ItemPlacer();
            count = placer.placeObjects(
                    board.getCells(),
                    player.getX(),
                    player.getY(),
                    board.getExitX(),
                    board.getExitY(),
                    items);
        } while (count < items.size());

        long itemsEnd = System.nanoTime();
        logTimeMs("Colocación items", itemsStart, itemsEnd);

        // ───────────── Creación simulator ─────────────
        long simStart = System.nanoTime();

        Simulator simulator = new Simulator(player, board, placer);
        simulator.getInventory().setPowers(List.of(PowerType.PICKAXE));

        long simEnd = System.nanoTime();
        logTimeMs("Creación Simulator", simStart, simEnd);

        // ───────────── TOTAL ─────────────
        long t1 = System.nanoTime();
        logTimeMs("TOTAL load()", t0, t1);

        return simulator;
    }

    private static void logTimeMs(String phase, long startNs, long endNs) {
        log.info("[TIME] {}: {} ms", phase, toMs(endNs - startNs));
    }

    private static double toMs(long ns) {
        return ns / 1_000_000.0;
    }

    // ---- resto de tu clase tal cual ----

    private static void cleanRediusOfGhostRoom(Board board, int exitX, int exitY) {

        int width = Board.BOARD_WIDTH;
        int height = Board.BOARD_HEIGHT;

        for (int x = 0; x < width; x++) {
            if (exitY == 0 && exitX == x)
                continue;
            board.updateTile(x, 0, Cells.WALL, false);
        }

        for (int x = 0; x < width; x++) {
            if (exitY == height - 1 && exitX == x)
                continue;
            board.updateTile(x, height - 1, Cells.WALL, false);
        }

        for (int y = 1; y < height - 1; y++) {
            if (exitY == y && exitX == 0)
                continue;
            board.updateTile(0, y, Cells.WALL, false);
        }

        for (int y = 1; y < height - 1; y++) {
            if (exitY == y && exitX == width - 1)
                continue;
            board.updateTile(width - 1, y, Cells.WALL, false);
        }
    }

    private static final double BROKEN_KEY_RATIO = 0.10;

    private static void setBrokenKeyDensity(Board board) {
        int doors = board.getDoorsCount();
        double density = parse3((doors * BROKEN_KEY_RATIO) / 100);

        double totalDensity = PowerType.BROKEN_KEY.getDensity() + density;
        double avgDensity = parse3(totalDensity / 2);
        PowerType.BROKEN_KEY.setDensity(avgDensity);
    }

    private static double parse3(double n) {
        return Math.floor(n * 1000) / 1000;
    }
}
