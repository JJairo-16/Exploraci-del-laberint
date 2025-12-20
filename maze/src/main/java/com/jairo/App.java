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

        com.jairo.app.Main.launchGui();
    }
}

