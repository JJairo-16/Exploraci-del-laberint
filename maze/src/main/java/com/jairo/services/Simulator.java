package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;

import static com.jairo.utils.KeyBind.Action;
import static com.jairo.utils.map_generator.Cells.*;

import java.util.Random;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.i18n.LanguageManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulator {
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    private Player player;
    private Board board;
    private Drawer drawer;
    private static final SoundManager sm = SoundManager.get();

    private static final String LOCKED_DOOR_SOUND = Sound.LOCKED_DOOR.path();
    private static final String TOCTOC_SOUND = Sound.TOCTOC.path();
    private static final String OPEN_DOOR_SOUND = Sound.OPEN_DOOR.path();

    private static final String JIJI_SOUND_CA = Sound.JIJI_CA.path();
    private static final String JIJI_SOUND_ES = Sound.JIJI_ES.path();
    private static final String JIJI_SOUND_EN = Sound.JIJI_EN.path();

    static {
        sm.defineGroup("lockedDoor",
                LOCKED_DOOR_SOUND,
                TOCTOC_SOUND,
                JIJI_SOUND_CA,
                JIJI_SOUND_EN,
                JIJI_SOUND_ES
            );
    }

    private boolean continuity = true;
    private Action currentAction = Action.UP;

    private static final long DELAY_MS = 200L;
    private static final long TOC_TOC_DELAY_MS = 100L;

    private static final int TOC_TOC_PROBABLY = 10; // %
    private static final int JI_JI_PROBABLY_RECURSIVE = 20; // %

    private static final Random RANDOM = new Random();

    private boolean randomWithProbably(int probably) {
        return RANDOM.nextInt(100) < probably;
    }

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

            case Action.USE:
                use();
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

            if (board.getTile(x, y) == EXIT && (sideX || sideY)) {
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

    private void use() {
        tryToOpenDoor();
    }

    private void tryToOpenDoor() {
        int x = player.getX();
        int y = player.getY();

        int dx = 0;
        int dy = 0;

        switch (currentAction) {
            case Action.UP:
                dy = -1;
                break;
            case Action.DOWN:
                dy = 1;
                break;
            case Action.LEFT:
                dx = -1;
                break;
            case Action.RIGHT:
                dx = 1;
                break;
            default:
                break;
        }

        int nx = x + dx;
        int ny = y + dy;

        int cell = board.getTile(nx, ny);
        boolean isDoor = (cell >= DOOR_OPEN_FROM_NORTH && cell <= DOOR_OPEN_FROM_EAST);

        if (!isDoor)
            return;

        boolean canOpen = false;

        switch (cell) {
            case DOOR_OPEN_FROM_NORTH:
                canOpen = dy == 1;
                break;
            case DOOR_OPEN_FROM_SOUTH:
                canOpen = dy == -1;
                break;
            case DOOR_OPEN_FROM_WEST:
                canOpen = dx == 1;
                break;
            case DOOR_OPEN_FROM_EAST:
                canOpen = dx == -1;
                break;
            default:
                break;
        }

        if (canOpen) {
            board.updateTile(nx, ny, cell + 4);
            sm.playSfx(OPEN_DOOR_SOUND);
            return;
        }

        if (sm.isGroupPlaying("lockedDoor"))
            return;

        String path = getLockedDoorPath();
        long delay = path.equals(TOCTOC_SOUND) ? TOC_TOC_DELAY_MS : DELAY_MS;
        sm.playSfxWithTailDelay(path, 1.0, false, delay);
    }

    private String getLockedDoorPath() {
        if (!randomWithProbably(TOC_TOC_PROBABLY)) {
            return LOCKED_DOOR_SOUND;
        }

        if (!randomWithProbably(JI_JI_PROBABLY_RECURSIVE)) {
            return TOCTOC_SOUND;
        }

        return getJiJiPath();
    }

    private String getJiJiPath() {
        String lang = LanguageManager.getCurrentLanguageCode();
        return switch(lang) {
            case "ca" -> JIJI_SOUND_CA;
            case "es" -> JIJI_SOUND_ES;
            case "en" -> JIJI_SOUND_EN;
            default -> JIJI_SOUND_CA;
        };
    }
}