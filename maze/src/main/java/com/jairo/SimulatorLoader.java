package com.jairo;

import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.services.Simulator;

public class SimulatorLoader {
    private SimulatorLoader() {
    }

    public static Simulator load() {
        Board board = new Board();
        Player player = new Player(board);

        return new Simulator(player, board);
    }
}
