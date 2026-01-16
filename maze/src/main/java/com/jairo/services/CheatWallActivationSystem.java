package com.jairo.services;

import static com.jairo.utils.map_generator.Cells.*;

import java.util.List;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.models.Board;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

public final class CheatWallActivationSystem {

    // =========================
    // Dependencies
    // =========================
    private final Board board;

    public CheatWallActivationSystem(Board board) {
        this.board = board;
    }

    // =========================
    // State
    // =========================
    private int activeX = -1;
    private int activeY = -1;

    private int lastPlayerX = -1;
    private int lastPlayerY = -1;

    private int playerX;
    private int playerY;

    // Permite desactivar sin dejar tiles “a medio estado”
    private boolean active = true;
    private boolean pendingSafeOff = false;

    // Timing (en nanosegundos, igual que now)
    private static final long SOLID_DURATION_NS = 1_500_000_000L; // 1.5s
    private long solidStartNs = -1L;

    // =========================
    // Public API
    // =========================
    public void update(List<List<Integer>> cells, int playerX, int playerY, long now) {
        if (cells == null || cells.isEmpty()) return;

        this.playerX = playerX;
        this.playerY = playerY;

        // ✅ si alguien pidió apagar de forma segura, lo hacemos aquí
        if (pendingSafeOff) {
            revertAndClear(cells);
            pendingSafeOff = false;
            active = false;
            return;
        }

        if (!active) return;

        int cell = cells.get(playerY).get(playerX);
        boolean onCheatPath = (isACheatedPath(cell));

        if (cell == HIDDEN_CHEAT_PATH) {
            board.updateTile(playerX, playerY, CHEAT_PATH);
        }

        if (!onCheatPath) {
            revertAndClear(cells);
            return;
        }

        // Si ya había un wall activo pero nos hemos alejado, revertimos
        if (activeX != -1 && !isAdjacent(playerX, playerY, activeX, activeY)) {
            revertAndClear(cells);
        }

        // Primera vez que entramos en zona: buscamos wall adyacente
        if (activeX == -1) {
            int[] pos = findAdjacentCheatWallOrStates(cells, playerX, playerY);
            if (pos.length == 0) return;

            activeX = pos[0];
            activeY = pos[1];

            board.updateTile(activeX, activeY, CHEAT_WALL_SOLID);
            solidStartNs = now;
            playSound();
            return;
        }

        // Ya hay uno activo: gestionamos la transición SOLID -> ACTIVE o reset
        int tile = cells.get(activeY).get(activeX);

        if (tile == CHEAT_WALL_SOLID) {
            if (solidStartNs < 0) {
                solidStartNs = now;
                return;
            }

            long elapsed = now - solidStartNs;
            if (elapsed >= SOLID_DURATION_NS) {
                board.updateTile(activeX, activeY, CHEAT_WALL_ACTIVE);
                playSound();
            }
            return;
        }

        // Si por cualquier razón vuelve a CHEAT_WALL, lo ponemos a SOLID otra vez
        if (tile == CHEAT_WALL) {
            board.updateTile(activeX, activeY, CHEAT_WALL_SOLID);
            solidStartNs = now;
            playSound();
        }
    }

    /**
     * Apaga y limpia de forma inmediata (recomendado si tienes cells en ese momento).
     */
    public void switchOff(List<List<Integer>> cells) {
        if (cells != null && !cells.isEmpty()) {
            revertAndClear(cells);
        } else {
            // si no hay cells, pedimos apagado seguro para el siguiente update()
            pendingSafeOff = true;
        }
        active = false;
    }

    /**
     * Apaga “de forma segura” en el siguiente update() (útil si no tienes cells aquí).
     */
    public void switchOff() {
        pendingSafeOff = true;
    }

    /** Vuelve a activar el sistema (arranca limpio). */
    public void switchOn() {
        active = true;
        pendingSafeOff = false;
        clearState();
    }

    public boolean isActive() {
        return active && !pendingSafeOff;
    }

    // =========================
    // Internal: tiles & state
    // =========================
    private void revertAndClear(List<List<Integer>> cells) {
        if (activeX != -1) {
            int tile = cells.get(activeY).get(activeX);
            if (tile == CHEAT_WALL_SOLID || tile == CHEAT_WALL_ACTIVE) {
                board.updateTile(activeX, activeY, CHEAT_WALL);
                playSound();
            }
        }
        clearState();
    }

    private void clearState() {
        activeX = -1;
        activeY = -1;
        solidStartNs = -1L;
    }

    private static boolean isAdjacent(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x1 - x2);
        int dy = Math.abs(y1 - y2);
        return (dx + dy) == 1;
    }

    private static int[] findAdjacentCheatWallOrStates(List<List<Integer>> cells, int x, int y) {
        // Up
        if (y > 0) {
            int t = cells.get(y - 1).get(x);
            if (isCheatWallOrState(t)) return new int[] { x, y - 1 };
        }
        // Down
        if (y < cells.size() - 1) {
            int t = cells.get(y + 1).get(x);
            if (isCheatWallOrState(t)) return new int[] { x, y + 1 };
        }
        // Left
        if (x > 0) {
            int t = cells.get(y).get(x - 1);
            if (isCheatWallOrState(t)) return new int[] { x - 1, y };
        }
        // Right
        if (x < cells.get(0).size() - 1) {
            int t = cells.get(y).get(x + 1);
            if (isCheatWallOrState(t)) return new int[] { x + 1, y };
        }
        return new int[0];
    }

    private static boolean isCheatWallOrState(int t) {
        return t == CHEAT_WALL || t == CHEAT_WALL_ACTIVE || t == CHEAT_WALL_SOLID;
    }

    // =========================
    // Sound (CHEATED_WALL1 -> CHEATED_WALL2)
    // =========================
    private static final SoundManager sm = SoundManager.get();
    private static final long CHEATED_WALL1_DURATION_NS = 289_000_000L;

    private PauseTransition cheatedWallChain;

    private void playSound() {
        if (playerX == lastPlayerX && playerY == lastPlayerY) return;

        lastPlayerX = playerX;
        lastPlayerY = playerY;

        final String s1 = Sound.CHEATED_WALL1.path();
        final String s2 = Sound.CHEATED_WALL2.path();

        Platform.runLater(() -> {
            // Si alguno está sonando, se detiene
            sm.stopSfx(s1);
            sm.stopSfx(s2);

            // Reproduce el primero desde 0
            sm.playSfx(s1);

            // Cancela un encadenado anterior si lo había
            if (cheatedWallChain != null) cheatedWallChain.stop();

            // Cuando acabe el primero, reproduce el segundo (sin bloquear)
            cheatedWallChain = new PauseTransition(
                    Duration.millis(CHEATED_WALL1_DURATION_NS / 1_000_000.0)
            );
            cheatedWallChain.setOnFinished(e -> sm.playSfx(s2));
            cheatedWallChain.playFromStart();
        });
    }

    private static final List<Integer> CHEATED_PATHS = List.of(
        CHEAT_PATH,
        HIDDEN_CHEAT_PATH
    );
    private boolean isACheatedPath(int cell) {
        return CHEATED_PATHS.contains(cell);
    }
}
