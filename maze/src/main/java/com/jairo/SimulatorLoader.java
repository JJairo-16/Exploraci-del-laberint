package com.jairo;

import java.util.List;

import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.models.Board;
import com.jairo.models.Inventory;
import com.jairo.models.Player;
import com.jairo.services.ItemPlacer;
import com.jairo.services.Simulator;

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

        ItemPlacer placer = new ItemPlacer();

        List<ItemType> items = List.of(
                BasicItemType.COIN,
                PowerType.PICKAXE,
                PowerType.BLAI_GLASSES);

        placer.placeObjects(
                board.getCells(),
                player.getX(),
                player.getY(),
                items);

        Simulator simulator = new Simulator(player, board, placer);
        simulator.getInventory().setPowers(List.of(PowerType.PICKAXE));
        return simulator;
    }
}
