package com.jairo;

import java.util.List;

import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.services.ItemPlacer;
import com.jairo.services.Simulator;

public class SimulatorLoader {
    private SimulatorLoader() {
    }

    public static Simulator load() {
        Board board = new Board();
        Player player = new Player(board);
        ItemPlacer placer = new ItemPlacer();

        List<ItemType> items = List.of(
            BasicItemType.COIN
        );

        placer.placeObjects(
            board.getCells(),
            player.getX(),
            player.getY(),
            items
        );

        return new Simulator(player, board, placer);
    }
}
