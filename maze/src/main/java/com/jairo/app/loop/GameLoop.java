package com.jairo.app.loop;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import javafx.animation.AnimationTimer;

public final class GameLoop {

    private final BooleanSupplier isRunning;
    private final LongConsumer onRenderTick;
    private final LongConsumer onRepeatTick;

    private final AdaptiveFramePacer pacer;

    private AnimationTimer renderTimer;
    private AnimationTimer repeatTimer;

    private long lastRenderNs = 0L;

    public GameLoop(
            AdaptiveFramePacer pacer,
            BooleanSupplier isRunning,
            LongConsumer onRenderTick,
            LongConsumer onRepeatTick
    ) {
        this.pacer = Objects.requireNonNull(pacer, "pacer");
        this.isRunning = Objects.requireNonNull(isRunning, "isRunning");
        this.onRenderTick = Objects.requireNonNull(onRenderTick, "onRenderTick");
        this.onRepeatTick = Objects.requireNonNull(onRepeatTick, "onRepeatTick");
    }

    public void start() {
        if (renderTimer != null || repeatTimer != null) return;

        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isRunning.getAsBoolean()) return;

                if (!pacer.shouldRender(now)) return;

                long start = now;
                onRenderTick.accept(now);
                long end = System.nanoTime(); // medir coste real

                long workNs = Math.max(0L, end - start);
                pacer.onFrameFinished(now, workNs);

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
        if (renderTimer != null) { renderTimer.stop(); renderTimer = null; }
        if (repeatTimer != null) { repeatTimer.stop(); repeatTimer = null; }
    }

    public long getLastRenderNs() {
        return lastRenderNs;
    }
}
