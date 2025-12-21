package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;

import static com.jairo.utils.KeyBind.Action;
import static com.jairo.utils.map_generator.Cells.EXIT;

import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.player_skins.SkinManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulator {
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    private Player player;
    private Board board;
    private Drawer drawer;

    private boolean continuity = true;

    private Action currentAction = Action.UP;

    public Action getCurrentAction() {
        return currentAction;
    }

    public boolean getContinue() {
        return continuity;
    }

    public Board getBoardRef() {
        return board;
    }

    public Simulator(Player player, Board board) {
        this.player = player;
        this.board = board;
        log.info("Simulator created");
    }

    public void loadDrawer(Drawer drawer) {
        this.drawer = drawer;
        log.info("Drawer loaded into Simulator: {}", drawer);
    }

    public void simulate(Action action) {
        if (action.isAMovement)
            currentAction = action;

        int dx = switch (action) {
            case LEFT -> -1;
            case RIGHT -> 1;
            default -> 0;
        };

        int dy = switch (action) {
            case UP -> -1;
            case DOWN -> 1;
            default -> 0;
        };

        if (dx != 0 || dy != 0) {
            boolean moved = simulatePlayerMovement(dx, dy);
            if (moved)
                Steps.playRandomStep();
            log.debug("Move dx={}, dy={} -> moved={}, pos=({}, {})",
                    dx, dy, moved, player.getX(), player.getY());
            return;
        }

        switch (action) {
            case Action.ZOOM_IN:
                drawer.zoomIn();
                log.info("Zoom in");
                break;

            case Action.ZOOM_OUT:
                drawer.zoomOut();
                log.info("Zoom out");
                break;

            case Action.NEXT_SKIN:
                SkinManager.get().next();
                break;
            
            case Action.PREVIOUS_SKIN:
                SkinManager.get().previous();
                break;

            default:
                break;
        }
    }

    public boolean simulatePlayerMovement(int dx, int dy) {
        try {
            return player.move(dx, dy);
        } catch (Exception e) {
            int x = player.getX();
            int y = player.getY();

            boolean sideX = (x == 0 || x == Board.BOARD_WIDTH - 1);
            boolean sideY = (y == 0 || y == Board.BOARD_HEIGHT - 1);

            if (board.getCells().get(y).get(x) == EXIT && (sideX || sideY)) {
                log.info("Player wins the game.");
                continuity = false;
            }
        }

        return true;
    }

    public record Position(int x, int y) {
    }

    public Position getPlayerPosition() {
        return new Position(player.getX(), player.getY());
    }
}
