package com.jairo.app.loop;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import javafx.animation.AnimationTimer;

/**
 * Encapsula el "game loop" en dos timers:
 *  - Render loop con throttle por frameNs.
 *  - Repeat loop para acciones mantenibles (controlado, no repeat del SO).
 *
 * El Controller le inyecta callbacks para ejecutar la lógica concreta.
 */
public final class GameLoop {

    private final long frameNs;

    private final BooleanSupplier isRunning;
    private final LongConsumer onRenderTick;
    private final LongConsumer onRepeatTick;

    private AnimationTimer renderTimer;
    private AnimationTimer repeatTimer;

    private long lastRenderNs = 0L;

    public GameLoop(
            long frameNs,
            BooleanSupplier isRunning,
            LongConsumer onRenderTick,
            LongConsumer onRepeatTick
    ) {
        this.frameNs = frameNs;
        this.isRunning = Objects.requireNonNull(isRunning, "isRunning");
        this.onRenderTick = Objects.requireNonNull(onRenderTick, "onRenderTick");
        this.onRepeatTick = Objects.requireNonNull(onRepeatTick, "onRepeatTick");
    }

    public void start() {
        if (renderTimer != null || repeatTimer != null) {
            // Ya está arrancado (o parcialmente)
            return;
        }

        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isRunning.getAsBoolean()) return;

                // Throttle (≈ FPS objetivo)
                if (now - lastRenderNs < frameNs) return;

                onRenderTick.accept(now);
                lastRenderNs = now;
            }
        };
        renderTimer.start();

        repeatTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isRunning.getAsBoolean()) return;

                onRepeatTick.accept(now);
            }
        };
        repeatTimer.start();
    }

    public void stop() {
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
        if (repeatTimer != null) {
            repeatTimer.stop();
            repeatTimer = null;
        }
    }

    public boolean isStarted() {
        return renderTimer != null && repeatTimer != null;
    }

    public long getLastRenderNs() {
        return lastRenderNs;
    }

    public void resetLastRenderNs() {
        lastRenderNs = 0L;
    }
}
