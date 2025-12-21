package com.jairo.models;

import java.util.List;
import com.jairo.utils.map_generator.BoardGenerator;

/**
 * Representa el jugador dins del joc.
 *
 * <p>Gestiona la posició del jugador i la seva interacció amb el tauler.
 * En el moment de la creació, el jugador es col·loca automàticament en
 * una posició vàlida del mapa, seleccionada de manera aleatòria i a una
 * distància mínima de la sortida.</p>
 *
 * <p>El moviment del jugador es realitza sempre a través del tauler,
 * que valida els desplaçaments i actualitza l'estat del mapa.</p>
 */
public class Player {

    // * Regles
    private static final int MIN_DISTANCE_FROM_EXIT = 20;

    // * Propietats
    int x;
    int y;

    // * Referència al tauler
    Board board;

    /**
     * Crea una nova instància del jugador i el posiciona inicialment al mapa.
     *
     * <p>La posició inicial es determina utilitzant el generador del tauler,
     * assegurant que el jugador aparegui en una cel·la transitable i prou
     * allunyada de la sortida.</p>
     *
     * @param board
     *     Referència al tauler de joc on es crearà el jugador.
     */
    public Player(Board board) {
        // * Obtenir mapa
        this.board = board;
        List<List<Integer>> cells = board.getCells();

        // * Posicionar jugador
        BoardGenerator.PlayerPosition pos =
                BoardGenerator.placePlayer(cells, MIN_DISTANCE_FROM_EXIT);

        this.x = pos.x();
        this.y = pos.y();

        board.movePlayer(x, y);
    }

    /**
     * Desplaça el jugador segons un moviment relatiu.
     *
     * <p>El moviment només es realitza si la direcció indicada és diferent
     * de zero i si la nova posició és vàlida segons les regles del tauler.</p>
     *
     * @param dx
     *     Desplaçament horitzontal del jugador.
     *
     * @param dy
     *     Desplaçament vertical del jugador.
     *
     * @return
     *     {@code true} si el moviment s'ha realitzat correctament;
     *     {@code false} si el moviment no és vàlid o no es pot efectuar.
     */
    public boolean move(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return false;
        }

        if (board.canMovePlayer(dx, dy)) {
            x += dx;
            y += dy;
            return board.movePlayer(x, y);
        }

        return false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
