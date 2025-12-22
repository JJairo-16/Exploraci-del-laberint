package com.jairo.models;

import java.util.List;
import java.util.ArrayList;

// * Generadors
import com.jairo.utils.map_generator.MapGenerator;
import com.jairo.utils.map_generator.map_modifier.MapModifier;
import com.jairo.utils.map_generator.BoardGenerator;
import com.jairo.utils.map_generator.Cells;

import static com.jairo.utils.map_generator.Cells.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representa el tauler de joc.
 *
 * <p>
 * Gestiona l'estat intern del mapa, la visibilitat de les cel·les
 * i la posició del jugador. El tauler es genera de manera aleatòria
 * mitjançant utilitats de generació de mapes i no permet la càrrega
 * de mapes externs.
 * </p>
 *
 * <p>
 * També proporciona funcionalitats per al moviment del jugador,
 * el descobriment progressiu del mapa i la renderització en format
 * textual.
 * </p>
 */
public class Board {
    private static final Logger log = LoggerFactory.getLogger(Board.class);

    // #region Regles
    public static final int BOARD_WIDTH = MapGenerator.BOARD_WIDTH;
    public static final int BOARD_HEIGHT = MapGenerator.BOARD_HEIGHT;

    private static final int X_POWER = 1;
    private static final int Y_POWER = 1;

    // #endregion

    // * Propietats
    // Mapa
    private final List<List<Integer>> cells;
    private final List<List<Integer>> visibility;

    // Jugador
    private int playerX;
    private int playerY;

    /**
     * Crea un nou tauler de joc generat de manera aleatòria.
     *
     * <p>
     * Inicialitza el mapa de cel·les i la seva corresponent matriu
     * de visibilitat utilitzant els generadors de mapes disponibles.
     * </p>
     */

    public Board() {
        try {
            // * Genera mapa
            String base = MapGenerator.generateMap(); // ? Genera mapa pla
            String flat = MapModifier.modify(base, BOARD_WIDTH, BOARD_HEIGHT);
            BoardGenerator.Maps maps = BoardGenerator.generateEmptyBoard(flat, BOARD_WIDTH, BOARD_HEIGHT); // ? Genera
                                                                                                           // mapa 2D i
                                                                                                           // visibilitat
                                                                                                           // inicial
            this.cells = maps.cells();
            this.visibility = maps.visibility();
        } catch (Exception e) {
            log.error("Failed to generate board", e);
            throw e;
        }
    }

    /**
     * Retorna una còpia immutable del mapa de cel·les.
     *
     * <p>
     * La còpia és profunda a nivell de files, de manera que no es pot
     * modificar l'estat intern del tauler des de l'exterior.
     * </p>
     *
     * @return Còpia immutable del mapa de cel·les.
     */
    public List<List<Integer>> getCells(boolean playerPOV) {
        List<List<Integer>> cellsToRender = playerPOV ? visibility : cells;

        List<List<Integer>> copy = new ArrayList<>(cellsToRender.size());
        for (List<Integer> row : cellsToRender) {
            copy.add(List.copyOf(row));
        }
        return List.copyOf(copy);
    }

    /**
     * Retorna una còpia immutable del mapa de cel·les.
     *
     * <p>
     * La còpia és profunda a nivell de files, de manera que no es pot
     * modificar l'estat intern del tauler des de l'exterior.
     * </p>
     *
     * @return Còpia immutable del mapa de cel·les.
     */
    public List<List<Integer>> getCells() {
        return getCells(false);
    }

    // #region toString
    /**
     * Retorna una representació textual del tauler.
     *
     * <p>
     * Permet escollir entre mostrar el mapa complet o només les
     * cel·les descobertes segons l'estat de visibilitat.
     * </p>
     *
     * @param visibilityMode
     *                       Si és {@code true}, es mostren totes les cel·les del
     *                       mapa.
     *                       Si és {@code false}, només es mostren les cel·les
     *                       visibles.
     *
     * @return Representació en cadena del tauler.
     */
    public String toString(boolean visibilityMode) {
        // * Prepara StringBuilder amb capacitat adequada
        int cap = (BOARD_WIDTH * 2 + 1) * BOARD_HEIGHT;
        StringBuilder sb = new StringBuilder(cap);

        // * Escull quines cel·les renderitzar
        List<List<Integer>> renderCells = visibilityMode ? cells : visibility;

        // * Construeix la representació en cadena
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            List<Integer> row = renderCells.get(y);

            for (int x = 0; x < BOARD_WIDTH; x++) {
                int cell = needToLoadPlayer(x, y) ? PLAYER : row.get(x); // ? Marca posició del jugador
                sb.append(SYMBOLS.get(cell));
            }

            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Retorna la representació textual completa del tauler.
     *
     * <p>
     * Equivalent a cridar {@link #toString(boolean)} amb el paràmetre
     * {@code true}.
     * </p>
     *
     * @return Representació en cadena del tauler.
     */
    @Override
    public String toString() {
        return toString(true);
    }

    private boolean needToLoadPlayer(int x, int y) {
        if (y == 0 || y == BOARD_HEIGHT - 1) {
            return false;
        }

        if (x == 0 || x == BOARD_WIDTH - 1) {
            return false;
        }

        return (playerX == x && playerY == y);
    }

    // #endregion

    // #endregion

    // #region Player Movement
    /**
     * Mou el jugador a una nova posició absoluta dins del tauler.
     *
     * <p>
     * El moviment només es realitza si la posició indicada es troba
     * dins dels límits del mapa i correspon a una cel·la transitable.
     * </p>
     *
     * <p>
     * En cas d'èxit, s'actualitza també la visibilitat de les cel·les
     * al voltant del jugador.
     * </p>
     *
     * @param x
     *          Nova coordenada X del jugador.
     *
     * @param y
     *          Nova coordenada Y del jugador.
     *
     * @return
     *         {@code true} si el moviment s'ha realitzat correctament;
     *         {@code false} en cas contrari.
     */
    public boolean movePlayer(int x, int y) {
        // * Validacions bàsiques
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT) {
            log.trace("Move rejected: out of bounds (x={}, y={})", x, y);
            return false;
        }

        // if (Cells.hasCollision(cells.get(y).get(x))) {
        //     log.trace("Move rejected: hit wall (x={}, y={})", x, y);
        //     return false;
        // }

        if (Cells.hasCollision(getTile(x, y))) {
            log.trace("Move rejected: hit wall (x={}, y={})", x, y);
            return false;
        }

        // * Mou el jugador
        this.playerX = x;
        this.playerY = y;
        log.trace("Player moved to (x={}, y={})", x, y);

        discoverAroundPlayer();
        return true;
    }

    /**
     * Comprova si el jugador pot moure's en una direcció determinada.
     *
     * <p>
     * La direcció es defineix mitjançant un desplaçament relatiu respecte
     * a la posició actual del jugador.
     * </p>
     *
     * @param dx
     *           Desplaçament horitzontal.
     *
     * @param dy
     *           Desplaçament vertical.
     *
     * @return
     *         {@code true} si el moviment és possible;
     *         {@code false} si surt dels límits o col·lideix amb un mur.
     */
    public boolean canMovePlayer(int dx, int dy) {
        // * Validacions bàsiques
        if (dx == 0 && dy == 0) {
            return false;
        }

        // * Calcula nova posició
        int newX = playerX + dx;
        int newY = playerY + dy;

        // * Comprova límits i obstacles
        if (newX < 0 || newX >= BOARD_WIDTH || newY < 0 || newY >= BOARD_HEIGHT) {
            return false;
        }

        // * Comprova si la cel·la és transitable
        int cell = getTile(newX, newY);
        // int cell = cells.get(newY).get(newX);
        return !Cells.hasCollision(cell);
    }

    /**
     * Actualitza la visibilitat de les cel·les al voltant del jugador.
     *
     * <p>
     * Descobreix la cel·la actual del jugador i totes les cel·les
     * adjacents (fins a un radi d'1), respectant els límits del tauler.
     * </p>
     */
    private void discoverAroundPlayer() {
        // * Descobreix cel·les al voltant del jugador
        for (int dy = -Y_POWER; dy <= Y_POWER; dy++) {
            int ny = playerY + dy;

            List<Integer> visibilityRow = visibility.get(ny);
            List<Integer> cellsRow = cells.get(ny);

            for (int dx = -X_POWER; dx <= X_POWER; dx++) {
                int nx = playerX + dx;

                if (visibilityRow.get(nx) == UNKNOWN) {
                    visibilityRow.set(nx, cellsRow.get(nx));
                }
            }
        }
    }

    // #endregion

    public void updateTile(int x, int y, int tile) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT) {
            return;
        }

        cells.get(y).set(x, tile);
        visibility.get(y).set(x, tile);
    }

    public int getTile(int x, int y) {
        if (x < 0 || y < 0 || y >= BOARD_HEIGHT || x >= BOARD_WIDTH) {
            return Cells.WALL;
        }
        return cells.get(y).get(x);
    }

}
