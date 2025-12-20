package com.jairo;

import com.jairo.utils.ui.Cleaner;

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

