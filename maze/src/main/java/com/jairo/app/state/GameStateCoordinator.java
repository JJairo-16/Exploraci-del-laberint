package com.jairo.app.state;

import java.util.Objects;
import java.util.function.LongConsumer;

import com.jairo.services.Simulator;

/**
 * Coordina la integración UI <-> Simulator a nivel de "estado de juego":
 *  - update del Simulator por frame
 *  - detección de fin de partida
 *  - gestión del poder Blai Glasses (tiempo restante + on/off)
 *
 * No dibuja nada. Informa al Controller vía callbacks.
 */
public final class GameStateCoordinator {

    private final Simulator simulator;

    /**
     * Callback para avisar de fin de juego (por ejemplo: apagar audio, cambiar vista, etc.)
     */
    private final Runnable onGameEnded;

    /**
     * Callback para actualizar UI del poder (recibe remainingNs).
     * Si el poder no está activo, se llamará onBlaiUiDeactivate.
     */
    private final LongConsumer onBlaiUiActive;
    private final Runnable onBlaiUiDeactivate;

    /**
     * Controla si la lógica debe ejecutarse o no (readKeys del Controller).
     */
    private final java.util.function.BooleanSupplier isRunning;

    public GameStateCoordinator(
            Simulator simulator,
            java.util.function.BooleanSupplier isRunning,
            Runnable onGameEnded,
            LongConsumer onBlaiUiActive,
            Runnable onBlaiUiDeactivate
    ) {
        this.simulator = Objects.requireNonNull(simulator, "simulator");
        this.isRunning = Objects.requireNonNull(isRunning, "isRunning");
        this.onGameEnded = Objects.requireNonNull(onGameEnded, "onGameEnded");
        this.onBlaiUiActive = Objects.requireNonNull(onBlaiUiActive, "onBlaiUiActive");
        this.onBlaiUiDeactivate = Objects.requireNonNull(onBlaiUiDeactivate, "onBlaiUiDeactivate");
    }

    /**
     * Ejecuta la lógica de juego asociada al Simulator por frame.
     * @param now tiempo actual (ns) desde AnimationTimer
     * @param dt  delta desde el último frame (ns). Si es 0, no se descuenta tiempo.
     */
    public void tick(long now, long dt) {
        if (!isRunning.getAsBoolean()) return;

        simulator.updateCheatedSystem(now);

        // Fin de partida: el InputHandler original consultaba getContinue().
        // Aquí lo centralizamos:
        if (!simulator.getContinue()) {
            onGameEnded.run();
            return;
        }

        // Poder Blai glasses (misma lógica que tenías, pero movida aquí):
        if (simulator.isBlaiGlassesPowerActive()) {
            long remaining = simulator.getRemainingBlaiGlassesPower();
            long nextRemaining = Math.max(0L, remaining - Math.max(0L, dt));
            simulator.updateRemainingBlaiGlassesPower(nextRemaining);

            onBlaiUiActive.accept(nextRemaining);

            if (nextRemaining == 0L) {
                simulator.offBlaiGlasses();
                onBlaiUiDeactivate.run();
            }
        } else {
            onBlaiUiDeactivate.run();
        }
    }
}
