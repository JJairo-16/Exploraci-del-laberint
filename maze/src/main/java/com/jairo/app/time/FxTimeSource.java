package com.jairo.app.time;

/**
 * Fuente de tiempo unificada basada en el "now" que entrega AnimationTimer.
 * El render loop llama update(now) y el resto del sistema lee now().
 *
 * Fallback: si aún no hay now válido, usa System.nanoTime().
 */
public final class FxTimeSource {
    private volatile long now;

    public void update(long now) {
        this.now = now;
    }

    public long now() {
        long n = this.now;
        return (n != 0L) ? n : System.nanoTime();
    }
}
