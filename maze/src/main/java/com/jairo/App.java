package com.jairo;

import com.jairo.utils.ui.Cleaner;

import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.services.Simulator;

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.run();
    }

    public void run() {
        Cleaner cls = new Cleaner();
        cls.clear();

        Board board = new Board();

        Player player = new Player(board);

        // System.out.println(board);

        Simulator simulator = new Simulator(player, board);
        com.jairo.app.Controller.load(simulator);
        com.jairo.app.Main.launchGui();
    }
}

