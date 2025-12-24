package com.jairo;

import java.util.List;

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
    private SimulatorLoader() {
    }

    // Regla (commutador)
    // Default: true
    private static final boolean FORCE_REACHABLE_EXIT = true;

    // Intents per complir la regla
    // Default: 50
    private static final int MAX_TRIES = 50;

    public static Simulator load() {
        Board board = null;
        Player player = null;

        if (!FORCE_REACHABLE_EXIT) {
            // Generació estàndard (una sola vegada)
            board = Board.generateStandard(); // et deixo aquest wrapper a sota
            player = new Player(board);
        } else {
            // Regla: repetim la generació fins que l’spawn REAL arribi a la SORTIDA
            int tries = 0;
            boolean loop = true;

            while (loop) {
                tries++;

                board = Board.generateStandard(); // genera un board (sense filtre)
                player = new Player(board); // spawn aleatori

                if (board.isExitReachableFrom(player.getX(), player.getY())) {
                    loop = false; // OK
                    continue;
                }

                if (tries >= MAX_TRIES) {
                    // No s’ha pogut complir la regla -> retorn a estàndard
                    board = Board.generateStandard();
                    player = new Player(board);
                    loop = false;
                }
            }
        }
        
        int exitX = board.getExitX();
        int exitY = board.getExitY();
        cleanRediusOfGhostRoom(board, exitX, exitY);

        board.updateTile(exitX, exitY, Cells.LOCKED_EXIT, false);

        ItemPlacer placer = new ItemPlacer();

        List<ItemType> items = List.of(
                BasicItemType.COIN,

                SpecialType.CHEATED_BUTTON,
                SpecialType.BOOTS,

                PowerType.PICKAXE,
                PowerType.BLAI_GLASSES,
                PowerType.KEY);

        placer.placeObjects(
                board.getCells(),
                player.getX(),
                player.getY(),
                board.getExitX(),
                board.getExitY(),
                items);

        Simulator simulator = new Simulator(player, board, placer);
        simulator.getInventory().setPowers(List.of(PowerType.PICKAXE));
        return simulator;
    }

    private static void cleanRediusOfGhostRoom(Board board, int exitX, int exitY) {

        int width = Board.BOARD_WIDTH;
        int height = Board.BOARD_HEIGHT;

        // ───────────── borde superior (y = 0) ─────────────
        for (int x = 0; x < width; x++) {
            if (exitY == 0 && exitX == x) continue;
            board.updateTile(x, 0, Cells.WALL, false);
        }

        // ───────────── borde inferior (y = height - 1) ─────────────
        for (int x = 0; x < width; x++) {
            if (exitY == height - 1 && exitX == x) continue;
            board.updateTile(x, height - 1, Cells.WALL, false);
        }

        // ───────────── borde izquierdo (x = 0) ─────────────
        for (int y = 1; y < height - 1; y++) {
            if (exitY == y && exitX == 0) continue;
            board.updateTile(0, y, Cells.WALL, false);
        }

        // ───────────── borde derecho (x = width - 1) ─────────────
        for (int y = 1; y < height - 1; y++) {
            if (exitY == y && exitX == width - 1) continue;
            board.updateTile(width - 1, y, Cells.WALL, false);
        }
    }

}
