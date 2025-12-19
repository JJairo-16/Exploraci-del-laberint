package maze;

import utils.ui.Cleaner;

import models.Board;
import models.Player;

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
        System.out.println(board);
    }
}
