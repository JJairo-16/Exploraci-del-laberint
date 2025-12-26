package com.jairo.services.sub_simulator;

import com.jairo.app.gfx.Drawer;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.utils.KeyBind.Action;
import com.jairo.utils.map_generator.Cells;

import static com.jairo.utils.map_generator.Cells.ICE;

/**
 * Encapsula toda la lógica de deslizamiento sobre hielo (estado + ticks + animación forzada).
 *
 * Ahora expone eventos mediante SlideSfx (capa de dominio -> capa de app/infrastructure).
 */
public class IceSlideSystem {

    /** Ajusta para más/menos suavidad */
    private static final long ICE_STEP_NS = 110_000_000L;

    private final Board board;
    private final Player player;

    // Dependencias externas (opcionales)
    private Drawer drawer;

    // Eventos (opcional)
    private SlideSfx slideSfx;

    // Estado
    private boolean iceActivated = true;

    private boolean iceSliding = false;
    private int iceDx = 0;
    private int iceDy = 0;

    private long nextIceStepAtNs = 0L;

    /** Evita reentradas (iceTick llama a move) */
    private boolean inAutoIceStep = false;

    /** Cuenta los tiles deslizados en esta "sesión" */
    private int slideTiles = 0;

    public interface MoveExecutor {
        boolean move(int dx, int dy);
    }

    /**
     * Eventos semánticos del deslizamiento (puerto / port).
     * Implementación típica: capa de aplicación -> llama a SoundManager/FX/etc.
     */
    public interface SlideSfx {
        /** Se dispara cuando empieza el deslizamiento. */
        void onSlideStart();

        /** Se dispara tras mover 1 tile durante el deslizamiento (tileIndex empieza en 1). */
        void onSlideTile(int tileIndex);

        /**
         * Se dispara al finalizar el deslizamiento.
         * @param collided true si se ha parado por colisión/bounds/move fallido.
         */
        void onSlideEnd(boolean collided);
    }

    public IceSlideSystem(Board board, Player player) {
        this.board = board;
        this.player = player;
    }

    public void setDrawer(Drawer drawer) {
        this.drawer = drawer;
    }

    public void setSlideSfx(SlideSfx slideSfx) {
        this.slideSfx = slideSfx;
    }

    public boolean isIceActivated() {
        return iceActivated;
    }

    public void setIceActivated(boolean iceActivated) {
        this.iceActivated = iceActivated;
        if (!iceActivated) {
            stopIceSlide(false);
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

        // Si ya estaba deslizando, no reiniciar
        if (!iceSliding) {
            iceSliding = true;
            iceDx = dx;
            iceDy = dy;
            slideTiles = 0;

            if (slideSfx != null) slideSfx.onSlideStart();
        } else {
            // Si estabas deslizando y el jugador cambia dirección manualmente en hielo,
            // actualiza dirección (opcional). Si no quieres esto, elimina este bloque.
            iceDx = dx;
            iceDy = dy;
        }

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

        // Fuera de bounds => parar (como colisión)
        if (nx < 0 || ny < 0 || nx >= Board.BOARD_WIDTH || ny >= Board.BOARD_HEIGHT) {
            stopIceSlide(true);
            return;
        }

        int nextTile = board.getTile(nx, ny);

        // Colisión => parar (paredes, puertas cerradas, LOCKED_EXIT, etc.)
        if (Cells.hasCollision(nextTile)) {
            stopIceSlide(true);
            return;
        }

        // Mover 1 tile por tick (suavidad temporal)
        inAutoIceStep = true;
        boolean moved = moveExecutor.move(iceDx, iceDy);

        board.discoverAroundPlayer();
        if (drawer != null) drawer.update();

        inAutoIceStep = false;

        if (!moved) {
            stopIceSlide(true);
            return;
        }

        // Evento: 1 tile deslizado
        slideTiles++;
        if (slideSfx != null) slideSfx.onSlideTile(slideTiles);

        // Si ya no está en hielo => parar (fin normal)
        int under = board.getTile(player.getX(), player.getY());
        if (!isIceTile(under)) {
            stopIceSlide(false);
            return;
        }

        nextIceStepAtNs = now + ICE_STEP_NS;
    }

    private void stopIceSlide(boolean collided) {
        if (iceSliding) {
            // Solo emitir fin si realmente estaba deslizando
            if (slideSfx != null) slideSfx.onSlideEnd(collided);
        }

        iceSliding = false;
        iceDx = 0;
        iceDy = 0;
        slideTiles = 0;

        board.discoverAroundPlayer(player.getX(), player.getY());
    }
}
