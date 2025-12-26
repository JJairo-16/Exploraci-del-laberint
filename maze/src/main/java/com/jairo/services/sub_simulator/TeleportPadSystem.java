package com.jairo.services.sub_simulator;

import com.jairo.items.BasicItemType;
import com.jairo.items.PlacedItem;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.services.ItemPlacer;
import com.jairo.utils.map_generator.Cells;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.jairo.utils.map_generator.Cells.*;

public class TeleportPadSystem {

    private final Board board;
    private final Player player;
    private final ItemPlacer placer;

    private final int minDistFromCurrentPos;
    private final int minDistFromAnyPad;
    private final int randomTries;

    // NUEVO: recordar último destino para evitar repetirlo (si hay alternativas)
    private int lastDestX = Integer.MIN_VALUE;
    private int lastDestY = Integer.MIN_VALUE;

    public TeleportPadSystem(Board board, Player player, ItemPlacer placer) {
        this(board, player, placer, 30, 30, 2500);
    }

    public TeleportPadSystem(Board board, Player player, ItemPlacer placer,
                             int minDistFromCurrentPos, int minDistFromAnyPad, int randomTries) {
        this.board = board;
        this.player = player;
        this.placer = placer;
        this.minDistFromCurrentPos = minDistFromCurrentPos;
        this.minDistFromAnyPad = minDistFromAnyPad;
        this.randomTries = randomTries;
    }

    public record Destination(boolean found, int x, int y) {}

    public Destination findAndPrintDestination() {
        int fromX = player.getX();
        int fromY = player.getY();
        List<int[]> pads = getAllPadPositions();

        int dPlayer = minDistFromCurrentPos;
        int dPad = minDistFromAnyPad;

        boolean reducePlayerNext = true;

        while (dPlayer >= 0 && dPad >= 0) {
            int[] dest = pickDestination(fromX, fromY, pads, dPlayer, dPad);
            if (dest != null) {
                int distFromPlayer = manhattan(fromX, fromY, dest[0], dest[1]);
                int distFromNearestPad = distanceToNearestPad(dest[0], dest[1], pads);

                System.out.println(
                        "[TeleportPadSystem] Destino válido encontrado: (" + dest[0] + ", " + dest[1] + ")"
                                + " | distJugador=" + distFromPlayer
                                + " | distPadMasCercano=" + distFromNearestPad
                                + " | minUsadaJugador=" + dPlayer
                                + " | minUsadaPad=" + dPad
                );

                // NUEVO: guardar último destino elegido
                lastDestX = dest[0];
                lastDestY = dest[1];

                return new Destination(true, dest[0], dest[1]);
            }

            if (reducePlayerNext) {
                if (dPlayer > 0) dPlayer--;
                else if (dPad > 0) dPad--;
            } else {
                if (dPad > 0) dPad--;
                else if (dPlayer > 0) dPlayer--;
            }

            reducePlayerNext = !reducePlayerNext;
        }

        System.out.println("[TeleportPadSystem] No se encontró destino válido ni relajando distancias hasta 0.");
        return new Destination(false, 0, 0);
    }

    private int[] pickDestination(int fromX, int fromY, List<int[]> pads, int minPlayerDist, int minPadDist) {
        // Intentos aleatorios rápidos
        for (int i = 0; i < randomTries; i++) {
            int x = ThreadLocalRandom.current().nextInt(Board.BOARD_WIDTH);
            int y = ThreadLocalRandom.current().nextInt(Board.BOARD_HEIGHT);

            if (x == fromX && y == fromY) continue;
            if (!isWalkable(x, y)) continue;
            if (isTeleportPadTile(x, y, pads)) continue;

            if (manhattan(fromX, fromY, x, y) < minPlayerDist) continue;
            if (tooCloseToAnyPad(x, y, pads, minPadDist)) continue;

            // Nota: aquí NO evitamos el último destino porque no sabemos si existen alternativas.
            // La garantía se aplica en el fallback donde sí conocemos candidates.size().
            return new int[]{x, y};
        }

        // Fallback: escaneo completo
        List<int[]> candidates = new ArrayList<>();
        for (int y = 0; y < Board.BOARD_HEIGHT; y++) {
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                if (x == fromX && y == fromY) continue;
                if (!isWalkable(x, y)) continue;
                if (isTeleportPadTile(x, y, pads)) continue;

                if (manhattan(fromX, fromY, x, y) < minPlayerDist) continue;
                if (tooCloseToAnyPad(x, y, pads, minPadDist)) continue;

                candidates.add(new int[]{x, y});
            }
        }

        if (candidates.isEmpty()) return null;

        // NUEVO: si hay 2+ candidatos, no permitir repetir el último destino
        if (candidates.size() >= 2 && lastDestX != Integer.MIN_VALUE) {
            // eliminar UNA coincidencia (si está)
            for (int i = 0; i < candidates.size(); i++) {
                int[] c = candidates.get(i);
                if (c[0] == lastDestX && c[1] == lastDestY) {
                    candidates.remove(i);
                    break;
                }
            }
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean tooCloseToAnyPad(int x, int y, List<int[]> pads, int minPadDist) {
        for (int[] p : pads) {
            if (manhattan(p[0], p[1], x, y) < minPadDist) return true;
        }
        return false;
    }

    private boolean isTeleportPadTile(int x, int y, List<int[]> pads) {
        for (int[] p : pads) {
            if (p[0] == x && p[1] == y) return true;
        }
        return false;
    }

    private int distanceToNearestPad(int x, int y, List<int[]> pads) {
        if (pads.isEmpty()) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (int[] p : pads) {
            int d = manhattan(p[0], p[1], x, y);
            if (d < best) best = d;
        }
        return best;
    }

    private List<int[]> getAllPadPositions() {
        List<int[]> out = new ArrayList<>();
        for (PlacedItem it : placer.getPlacedItems()) {
            if (it.getType() == BasicItemType.PORTAL_GUN) {
                out.add(new int[]{it.getX(), it.getY()});
            }
        }
        return out;
    }

    private boolean isWalkable(int x, int y) {
        int tile = board.getTile(x, y);
        if (Cells.hasCollision(tile)) return false;

        switch (tile) {
            case EXIT:
                return false;
            case EXIT_CONNECTOR:
                return false;
            default:
                return true;
        }
    }

    private int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
