package com.jairo.services.sub_simulator;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.i18n.LanguageManager;
import com.jairo.models.Board;
import com.jairo.utils.KeyBind.Action;

import java.util.Random;

import static com.jairo.utils.map_generator.Cells.*;

/**
 * Encapsula toda la lógica de puertas:
 * - detectar tipos (cerrada/abierta/abrible)
 * - abrir puerta "en una coordenada" (con dx/dy)
 * - abrir puerta "delante del jugador" (con currentAction)
 * - sonidos de puerta bloqueada (lockedDoor group + probabilidades)
 */
public class DoorSystem {

    private static final SoundManager sm = SoundManager.get();

    private static final String LOCKED_DOOR_SOUND = Sound.LOCKED_DOOR.path();
    private static final String TOCTOC_SOUND = Sound.TOCTOC.path();
    private static final String OPEN_DOOR_SOUND = Sound.OPEN_DOOR.path();

    private static final String JIJI_SOUND_CA = Sound.JIJI_CA.path();
    private static final String JIJI_SOUND_ES = Sound.JIJI_ES.path();
    private static final String JIJI_SOUND_EN = Sound.JIJI_EN.path();

    public static final long DELAY_MS = 200L;
    private static final long TOC_TOC_DELAY_MS = 100L;

    private static final int TOC_TOC_PROBABLY = 20; // %
    private static final int JI_JI_PROBABLY_RECURSIVE = 35; // %

    private static final Random RANDOM = new Random();

    private final Board board;

    public DoorSystem(Board board) {
        this.board = board;

        // Definir grupo una vez (idempotente si tu SoundManager lo soporta; si no,
        // mantén un flag)
        sm.defineGroup("lockedDoor",
                LOCKED_DOOR_SOUND,
                TOCTOC_SOUND,
                JIJI_SOUND_CA,
                JIJI_SOUND_EN,
                JIJI_SOUND_ES);
    }

    // =========================
    // Helpers (estado puerta)
    // =========================

    public boolean isDoorClosed(int cell) {
        return isAnyDoor(cell) && !isDoorOpened(cell);
    }

    public boolean isAnyDoor(int cell) {
        return isDoorClosedButOpenable(cell) || isDoorOpened(cell);
    }

    public boolean isDoorClosedButOpenable(int cell) {
        return cell == DOOR_OPEN_FROM_NORTH
                || cell == DOOR_OPEN_FROM_SOUTH
                || cell == DOOR_OPEN_FROM_WEST
                || cell == DOOR_OPEN_FROM_EAST;
    }

    public boolean isDoorOpened(int cell) {
        return cell == DOOR_OPENED_FROM_NORTH
                || cell == DOOR_OPENED_FROM_SOUTH
                || cell == DOOR_OPENED_FROM_WEST
                || cell == DOOR_OPENED_FROM_EAST;
    }

    // =========================
    // Abrir puerta en (nx,ny)
    // =========================

    public boolean tryOpenDoorAt(int nx, int ny, int dx, int dy, int cell, boolean force) {
        if (!isDoorClosedButOpenable(cell))
            return false;

        boolean canOpen = switch (cell) {
            case DOOR_OPEN_FROM_NORTH -> dy == 1;
            case DOOR_OPEN_FROM_SOUTH -> dy == -1;
            case DOOR_OPEN_FROM_WEST -> dx == 1;
            case DOOR_OPEN_FROM_EAST -> dx == -1;
            default -> false;
        };

        canOpen = canOpen || force;

        if (!canOpen)
            return false;

        int opened = switch (cell) {
            case DOOR_OPEN_FROM_NORTH -> DOOR_OPENED_FROM_NORTH;
            case DOOR_OPEN_FROM_SOUTH -> DOOR_OPENED_FROM_SOUTH;
            case DOOR_OPEN_FROM_WEST -> DOOR_OPENED_FROM_WEST;
            case DOOR_OPEN_FROM_EAST -> DOOR_OPENED_FROM_EAST;
            default -> cell;
        };

        board.updateTile(nx, ny, opened);
        sm.playSfx(OPEN_DOOR_SOUND);
        return true;
    }

    public boolean tryOpenDoorAt(int nx, int ny, int dx, int dy, int cell) {
        return tryOpenDoorAt(nx, ny, dx, dy, cell, false);
    }

    // =========================
    // Abrir puerta delante
    // =========================

    public boolean tryToOpenDoor(Action currentAction,
            int playerX,
            int playerY,
            boolean force,
            boolean playNoOpenedSound) {
        int dx = 0;
        int dy = 0;

        switch (currentAction) {
            case UP -> dy = -1;
            case DOWN -> dy = 1;
            case LEFT -> dx = -1;
            case RIGHT -> dx = 1;
            default -> {
            }
        }

        int nx = playerX + dx;
        int ny = playerY + dy;

        if (nx < 0 || ny < 0 || nx >= Board.BOARD_WIDTH || ny >= Board.BOARD_HEIGHT)
            return false;

        int cell = board.getTile(nx, ny);

        // Si no es una puerta cerrada abrible, no hacemos nada
        if (!isDoorClosedButOpenable(cell))
            return false;

        boolean canOpen = switch (cell) {
            case DOOR_OPEN_FROM_NORTH -> dy == 1;
            case DOOR_OPEN_FROM_SOUTH -> dy == -1;
            case DOOR_OPEN_FROM_WEST -> dx == 1;
            case DOOR_OPEN_FROM_EAST -> dx == -1;
            default -> force;
        };

        if (canOpen) {
            int opened = switch (cell) {
                case DOOR_OPEN_FROM_NORTH -> DOOR_OPENED_FROM_NORTH;
                case DOOR_OPEN_FROM_SOUTH -> DOOR_OPENED_FROM_SOUTH;
                case DOOR_OPEN_FROM_WEST -> DOOR_OPENED_FROM_WEST;
                case DOOR_OPEN_FROM_EAST -> DOOR_OPENED_FROM_EAST;
                default -> cell;
            };

            board.updateTile(nx, ny, opened);
            sm.playSfx(OPEN_DOOR_SOUND);
            return false; // (mantengo tu comportamiento original)
        }

        // No se puede abrir: NO reproducir sonido si está desactivado
        if (!playNoOpenedSound)
            return true;

        // No se puede abrir: reproducir sonido “locked” (evitar solapar)
        if (sm.isGroupPlaying("lockedDoor"))
            return false;

        String path = getLockedDoorPath();
        long delay = path.equals(TOCTOC_SOUND) ? TOC_TOC_DELAY_MS : DELAY_MS;
        sm.playSfxWithTailDelay(path, 1.0, false, delay);

        return true;
    }

    public boolean tryToOpenDoor(Action currentAction, int playerX, int playerY) {
        return tryToOpenDoor(currentAction, playerX, playerY, false, true);
    }

    private boolean randomWithProbably(int probably) {
        return RANDOM.nextInt(100) < probably;
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

    public String getJiJiPath() {
        String lang = LanguageManager.getCurrentLanguageCode();
        return switch (lang) {
            case "ca" -> JIJI_SOUND_CA;
            case "es" -> JIJI_SOUND_ES;
            case "en" -> JIJI_SOUND_EN;
            default -> JIJI_SOUND_CA;
        };
    }
}
