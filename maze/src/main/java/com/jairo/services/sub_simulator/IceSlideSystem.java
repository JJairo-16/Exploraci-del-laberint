package com.jairo.services.sub_simulator;

import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.utils.KeyBind.Action;
import com.jairo.utils.map_generator.Cells;

import static com.jairo.utils.map_generator.Cells.ICE;

/**
 * Encapsula toda la lógica de deslizamiento sobre hielo (estado + ticks + animación forzada).
 */
public class IceSlideSystem {

    /** Ajusta para más/menos suavidad */
    private static final long ICE_STEP_NS = 120_000_000L; // 120ms por tile (ojo: comentario original decía 45ms)

    private final Board board;
    private final Player player;

    // Dependencias externas (opcionales)
    private Drawer drawer;

    // Estado
    private boolean iceActivated = true;

    private boolean iceSliding = false;
    private int iceDx = 0;
    private int iceDy = 0;

    private long nextIceStepAtNs = 0L;

    /** Evita reentradas (iceTick llama a move) */
    private boolean inAutoIceStep = false;

    public interface MoveExecutor {
        boolean move(int dx, int dy);
    }

    public IceSlideSystem(Board board, Player player) {
        this.board = board;
        this.player = player;
    }

    public void setDrawer(Drawer drawer) {
        this.drawer = drawer;
    }

    public boolean isIceActivated() {
        return iceActivated;
    }

    public void setIceActivated(boolean iceActivated) {
        this.iceActivated = iceActivated;
        if (!iceActivated) {
            stopIceSlide();
        }
    }

    public boolean isSliding() {
        return iceSliding;
    }

    public boolean isInAutoIceStep() {
        return inAutoIceStep;
    }

    private boolean isIceTile(int tile) {
        return tile == ICE;
    }

    /**
     * Se llama justo después de un movimiento manual (si se ha movido).
     * Inicia el slide si el jugador queda sobre hielo.
     */
    public void afterManualMove(long now, Action lastMovement) {
        if (!iceActivated) return;
        if (inAutoIceStep) return;

        int under = board.getTile(player.getX(), player.getY());
        if (!isIceTile(under)) return;

        int dx = switch (lastMovement) {
            case LEFT -> -1;
            case RIGHT -> 1;
            default -> 0;
        };
        int dy = switch (lastMovement) {
            case UP -> -1;
            case DOWN -> 1;
            default -> 0;
        };

        if (dx == 0 && dy == 0) return;

        iceSliding = true;
        iceDx = dx;
        iceDy = dy;

        // Empieza ya en el próximo tick
        nextIceStepAtNs = now + ICE_STEP_NS;
    }

    /**
     * Avanza el slide en ticks temporales (1 tile por tick).
     *
     * @param now            tiempo actual en ns/ms (debe ser consistente con el que uses en Simulator)
     * @param moveExecutor   callback para mover (normalmente Simulator::simulatePlayerMovement)
     * @param actionSetter   callback para forzar currentAction + lastMovement mientras se desliza
     */
    public void tick(long now, MoveExecutor moveExecutor, java.util.function.Consumer<Action> actionSetter) {
        if (!iceSliding) return;
        if (now < nextIceStepAtNs) return;

        // Mantener animación activa en la dirección del slide
        Action forced = switch (iceDx) {
            case -1 -> Action.LEFT;
            case 1 -> Action.RIGHT;
            default -> (iceDy == -1 ? Action.UP : Action.DOWN);
        };
        actionSetter.accept(forced);

        int nx = player.getX() + iceDx;
        int ny = player.getY() + iceDy;

        // Fuera de bounds => parar
        if (nx < 0 || ny < 0 || nx >= Board.BOARD_WIDTH || ny >= Board.BOARD_HEIGHT) {
            stopIceSlide();
            return;
        }

        int nextTile = board.getTile(nx, ny);

        // Colisión => parar (paredes, puertas cerradas, LOCKED_EXIT, etc.)
        if (Cells.hasCollision(nextTile)) {
            stopIceSlide();
            return;
        }

        // Mover 1 tile por tick (suavidad temporal)
        inAutoIceStep = true;
        boolean moved = moveExecutor.move(iceDx, iceDy);

        board.discoverAroundPlayer();
        if (drawer != null) drawer.update();

        inAutoIceStep = false;

        if (!moved) {
            stopIceSlide();
            return;
        }

        // Steps.playRandomStep();

        // Si ya no está en hielo => parar
        int under = board.getTile(player.getX(), player.getY());
        if (!isIceTile(under)) {
            stopIceSlide();
            return;
        }

        nextIceStepAtNs = now + ICE_STEP_NS;
    }

    private void stopIceSlide() {
        iceSliding = false;
        iceDx = 0;
        iceDy = 0;

        board.discoverAroundPlayer(player.getX(), player.getY());
    }
}
