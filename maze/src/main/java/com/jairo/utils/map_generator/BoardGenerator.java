package com.jairo.utils.map_generator;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import static com.jairo.utils.map_generator.Cells.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitat estàtica per a la generació i inicialització de mapes de joc.
 *
 * <p>
 * Aquesta classe s'encarrega de transformar mapes plans en estructures
 * bidimensionals, inicialitzar la visibilitat del tauler i calcular
 * posicions vàlides per a la col·locació del jugador.
 * </p>
 *
 * <p>
 * No es permet la creació d'instàncies d'aquesta classe.
 * </p>
 */
public class BoardGenerator {
    private static final Logger log = LoggerFactory.getLogger(BoardGenerator.class);

    private BoardGenerator() {
    }

    /** Generador de nombres aleatoris utilitzat per a la selecció de posicions. */
    private static final Random RNG = new Random();

    /** Direccions de moviment (dreta, esquerra, avall, amunt) com a constants (evita alloc per crida). */
    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    /**
     * Estructura immutable que encapsula els mapes del tauler.
     *
     * @param cells       Mapa de cel·les reals del tauler.
     * @param visibility  Mapa de visibilitat associat al tauler.
     * @param secretWalls Llista de posicions (x,y) on hi ha parets secretes.
     * @param doorsCount  Nombre total de portes detectades.
     */
    public static record Maps(List<List<Integer>> cells,
                              List<List<Integer>> visibility,
                              List<int[]> secretWalls,
                              int doorsCount) {
    }

    /**
     * Genera els mapes bidimensionals del tauler a partir d'un mapa pla.
     *
     * @param flat   Representació plana del mapa en format de codi numèric.
     * @param width  Amplada del tauler.
     * @param height Alçada del tauler.
     *
     * @return Conjunt de mapes generats encapsulats dins {@link Maps}.
     */
    public static Maps generateEmptyBoard(String flat, int width, int height) {
        // * Validacions inicials
        if (flat == null) {
            log.error("generateEmptyBoard received null flat map");
            throw new IllegalArgumentException("El mapa està en blanc");
        }

        int expected = width * height;
        if (flat.length() != expected) {
            log.error("Invalid flat map length: expected {}, got {}", expected, flat.length());
            throw new IllegalArgumentException("La mida del mapa no coincideix amb la esperada");
        }

        // * Inicialitzar mapes (API pública: List<List<Integer>>)
        List<List<Integer>> cells = new ArrayList<>(height);
        List<List<Integer>> visibility = new ArrayList<>(height);
        List<int[]> secretWalls = new ArrayList<>();
        int doorsCount = 0;

        // * Generar mapes
        int idx = 0;
        for (int y = 0; y < height; y++) {
            List<Integer> row = new ArrayList<>(width);
            List<Integer> visRow = new ArrayList<>(width);

            for (int x = 0; x < width; x++) {
                int cell = parseCell(flat.charAt(idx));
                row.add(cell);
                visRow.add(UNKNOWN);
                idx++;

                if (cell == SECRET_WALL) {
                    secretWalls.add(new int[] { x, y });
                } else if (isDoor(cell)) {
                    doorsCount++;
                }
            }

            cells.add(row);
            visibility.add(visRow);
        }

        return new Maps(cells, visibility, secretWalls, doorsCount);
    }

    /**
     * Estructura immutable que representa una posició dins del tauler.
     *
     * @param x Coordenada horitzontal.
     * @param y Coordenada vertical.
     */
    public static record PlayerPosition(int x, int y) {
    }

    /**
     * Determina una posició inicial vàlida i aleatòria per al jugador.
     *
     * <p>
     * Optimització: es manté l'API pública amb List<List<Integer>>,
     * però internament es desboxa a int[][] i s'executa el BFS sobre arrays
     * primitius. També s'eviten les divisions/mods del BFS usant dues cues
     * paral·leles (qx/qy), sense mutar l'entrada ni produir efectes secundaris.
     * </p>
     *
     * @param cells               Mapa de cel·les del tauler (List<List<Integer>>).
     * @param minDistanceFromExit Distància mínima respecte la sortida més propera.
     *
     * @return Posició inicial del jugador.
     */
    public static PlayerPosition placePlayer(List<List<Integer>> cells, int minDistanceFromExit) {
        // * Obtenir dimensions del mapa
        final int WIDTH = cells.get(0).size();
        final int HEIGHT = cells.size();

        // * Representació interna (desboxing) per al hot path
        final int[][] board = new int[HEIGHT][WIDTH];

        // * Matriu de distàncies (-1 indica no visitat)
        final int[][] dist = new int[HEIGHT][WIDTH];

        // * Cues BFS sense allocs per node i sense % / /
        final int[] qx = new int[WIDTH * HEIGHT];
        final int[] qy = new int[WIDTH * HEIGHT];
        int head = 0;
        int tail = 0;

        // * Copiar (desboxar) + inicialitzar dist i encolar exits (una sola passada)
        for (int y = 0; y < HEIGHT; y++) {
            final List<Integer> row = cells.get(y);
            for (int x = 0; x < WIDTH; x++) {
                final int v = row.get(x); // unboxing un cop aquí
                board[y][x] = v;

                if (v == EXIT_CONNECTOR) {
                    dist[y][x] = 0;
                    qx[tail] = x;
                    qy[tail] = y;
                    tail++;
                } else {
                    dist[y][x] = -1;
                }
            }
        }

        // * Execució del BFS
        while (head < tail) {
            final int x = qx[head];
            final int y = qy[head];
            head++;

            final int nextDist = dist[y][x] + 1;

            for (int i = 0; i < 4; i++) {
                final int nx = x + DX[i];
                final int ny = y + DY[i];

                // límits
                if (nx < 0 || ny < 0 || nx >= WIDTH || ny >= HEIGHT) {
                    continue;
                }

                // Visitar només PATH no visitat
                if (dist[ny][nx] == -1 && board[ny][nx] == PATH) {
                    dist[ny][nx] = nextDist;
                    qx[tail] = nx;
                    qy[tail] = ny;
                    tail++;
                }
            }
        }

        // * Selecció uniforme sense llistar candidats: reservoir sampling
        //   - candChoice: PATH amb dist >= minDistanceFromExit
        //   - pathChoice: qualsevol PATH (fallback)
        int candCount = 0;
        int candChoiceX = -1;
        int candChoiceY = -1;

        int pathCount = 0;
        int pathChoiceX = -1;
        int pathChoiceY = -1;

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] != PATH) {
                    continue;
                }

                // reservoir per a qualsevol PATH
                pathCount++;
                if (RNG.nextInt(pathCount) == 0) {
                    pathChoiceX = x;
                    pathChoiceY = y;
                }

                // reservoir per a candidats "lluny"
                if (dist[y][x] >= minDistanceFromExit) {
                    candCount++;
                    if (RNG.nextInt(candCount) == 0) {
                        candChoiceX = x;
                        candChoiceY = y;
                    }
                }
            }
        }

        // * Fallback: si no hi ha candidats prou llunyans, qualsevol PATH
        if (candCount > 0) {
            return new PlayerPosition(candChoiceX, candChoiceY);
        }

        log.warn(
            "No valid start position found with minDistanceFromExit={}; falling back to any PATH cell",
            minDistanceFromExit
        );

        if (pathCount == 0) {
            // Si el mapa no conté cap PATH, és un estat inconsistent per a placePlayer
            throw new IllegalStateException("No PATH cells available to place the player");
        }

        return new PlayerPosition(pathChoiceX, pathChoiceY);
    }

    private static boolean isDoor(int tile) {
        return (tile >= DOOR_OPEN_FROM_NORTH && tile <= DOOR_OPEN_FROM_EAST);
    }
}
