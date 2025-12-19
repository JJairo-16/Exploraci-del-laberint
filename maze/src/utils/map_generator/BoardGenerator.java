package utils.map_generator;

// * Llistes
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Random;

import static utils.map_generator.Cells.*;

/**
 * Utilitat estàtica per a la generació i inicialització de mapes de joc.
 *
 * <p>Aquesta classe s'encarrega de transformar mapes plans en estructures
 * bidimensionals, inicialitzar la visibilitat del tauler i calcular
 * posicions vàlides per a la col·locació del jugador.</p>
 *
 * <p>No es permet la creació d'instàncies d'aquesta classe.</p>
 */
public class BoardGenerator {

    /**
     * Constructor privat per evitar la instanciació de la classe.
     */
    private BoardGenerator() {
    }

    /**
     * Generador de nombres aleatoris utilitzat per a la selecció de posicions.
     */
    private static final Random RNG = new Random();

    /**
     * Estructura immutable que encapsula els mapes del tauler.
     *
     * @param cells
     *     Mapa de cel·les reals del tauler.
     *
     * @param visibility
     *     Mapa de visibilitat associat al tauler.
     */
    public static record Maps(List<List<Integer>> cells,
                              List<List<Integer>> visibility) {
    }

    /**
     * Genera els mapes bidimensionals del tauler a partir d'un mapa pla.
     *
     * <p>El mapa pla es recorre seqüencialment i es transforma en una
     * estructura de cel·les 2D. Paral·lelament, es crea un mapa de
     * visibilitat inicial, on els límits del tauler són visibles i la resta
     * de cel·les queden ocultes.</p>
     *
     * @param flat
     *     Representació plana del mapa en format de codi numèric.
     *
     * @param width
     *     Amplada del tauler.
     *
     * @param height
     *     Alçada del tauler.
     *
     * @return
     *     Conjunt de mapes generats encapsulats dins {@link Maps}.
     */
    public static Maps generateEmptyBoard(String flat, int width, int height) {

        // * Inicialitzar mapes
        List<List<Integer>> cells = new ArrayList<>(height);
        List<List<Integer>> visibility = new ArrayList<>(height);

        // * Generar mapes
        int idx = 0;
        for (int y = 0; y < height; y++) {
            List<Integer> row = new ArrayList<>(width);
            List<Integer> visRow = new ArrayList<>(width);

            for (int x = 0; x < width; x++) {
                int cell = parseCell(flat.charAt(idx));
                row.add(cell);

                boolean xSide = (x == 0 || x == width - 1);
                boolean ySide = (y == 0 || y == height - 1);

                int type = (xSide || ySide) ? WALL : UNKNOWN;
                visRow.add(type);

                idx++;
            }

            cells.add(row);
            visibility.add(visRow);
        }

        return new Maps(cells, visibility);
    }

    /**
     * Estructura immutable que representa una posició dins del tauler.
     *
     * @param x
     *     Coordenada horitzontal.
     *
     * @param y
     *     Coordenada vertical.
     */
    public static record PlayerPosition(int x, int y) {
    }

    /**
     * Determina una posició inicial vàlida i aleatòria per al jugador.
     *
     * <p>La posició seleccionada garanteix una distància mínima respecte
     * a qualsevol sortida del mapa. Per aconseguir-ho, es realitza una
     * cerca en amplada (BFS) a partir de totes les cel·les de sortida,
     * calculant la distància mínima a cada cel·la transitable.</p>
     *
     * <p>Si no existeix cap cel·la que compleixi la distància mínima
     * requerida, es selecciona una posició aleatòria entre totes les
     * cel·les transitables com a mecanisme de seguretat.</p>
     *
     * @param cells
     *     Mapa de cel·les del tauler. Cada valor indica el tipus de cel·la
     *     (per exemple {@code PATH}, {@code WALL} o {@code EXIT_CONNECTOR}).
     *
     * @param minDistanceFromExit
     *     Distància mínima (en passos ortogonals) entre el jugador i la
     *     sortida més propera.
     *
     * @return
     *     Posició inicial del jugador encapsulada dins {@link PlayerPosition}.
     */
    public static PlayerPosition placePlayer(List<List<Integer>> cells,
                                             int minDistanceFromExit) {

        // * Obtenir dimensions del mapa
        final int WIDTH = cells.get(0).size();
        final int HEIGHT = cells.size();

        // * Matriu de distàncies (-1 indica no visitat)
        int[][] dist = new int[HEIGHT][WIDTH];

        // * Cua per a la cerca en amplada (BFS)
        ArrayDeque<int[]> q = new ArrayDeque<>();

        // * Inicialització de la matriu de distàncies
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                dist[y][x] = -1;
            }
        }

        // * Afegir totes les sortides com a punts inicials del BFS
        for (int y = 0; y < HEIGHT; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 0; x < WIDTH; x++) {
                if (row.get(x) == EXIT_CONNECTOR) {
                    dist[y][x] = 0;
                    q.add(new int[] { x, y });
                }
            }
        }

        // * Direccions de moviment (dreta, esquerra, avall, amunt)
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        // * Execució del BFS
        while (!q.isEmpty()) {
            int[] p = q.poll();

            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];

                // Comprovar límits del mapa
                if (nx < 0 || ny < 0 || nx >= WIDTH || ny >= HEIGHT) {
                    continue;
                }

                // Visitar només cel·les transitables no visitades
                if (dist[ny][nx] == -1 && cells.get(ny).get(nx) == PATH) {
                    dist[ny][nx] = dist[p[1]][p[0]] + 1;
                    q.add(new int[] { nx, ny });
                }
            }
        }

        // * Recollir posicions candidates
        List<int[]> candidates = new ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 0; x < WIDTH; x++) {
                if (row.get(x) == PATH && dist[y][x] >= minDistanceFromExit) {
                    candidates.add(new int[] { x, y });
                }
            }
        }

        // * Cas de seguretat: cap posició prou llunyana
        if (candidates.isEmpty()) {
            for (int y = 0; y < HEIGHT; y++) {
                List<Integer> row = cells.get(y);
                for (int x = 0; x < WIDTH; x++) {
                    if (row.get(x) == PATH) {
                        candidates.add(new int[] { x, y });
                    }
                }
            }
        }

        // * Selecció aleatòria final
        int[] chosen = candidates.get(RNG.nextInt(candidates.size()));
        return new PlayerPosition(chosen[0], chosen[1]);
    }
}
