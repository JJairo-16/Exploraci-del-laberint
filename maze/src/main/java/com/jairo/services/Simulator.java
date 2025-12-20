package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;
import static com.jairo.utils.KeyBind.Action;;

public class Simulator {
    private boolean confirm = true;

    public Simulator(Player player, Board board) {
        
    }

    public void simulate(Action action) {
        if (action == Action.SWITCH_CONFIRM) {
            confirm = !confirm;
        }
    }

    public boolean getConfirm() {
        return confirm;
    }
}
