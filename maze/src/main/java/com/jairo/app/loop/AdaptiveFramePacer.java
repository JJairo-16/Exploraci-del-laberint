package com.jairo.app.loop;

public final class AdaptiveFramePacer {

    private final int minFps;
    private final int maxFps;

    private long targetFrameNs;
    private long nextFrameAllowedNs = 0L;

    // Media móvil del coste real del frame
    private double avgWorkNs = 0.0;

    // Ajuste suave (0..1). Más alto = responde más rápido, pero más inestable.
    private final double smoothing;

    // Márgenes para evitar oscilar
    private final double downThreshold; // si avgWork supera X% del frame => baja FPS
    private final double upThreshold;   // si avgWork baja de Y% del frame => sube FPS

    public AdaptiveFramePacer(int minFps, int maxFps) {
        this(minFps, maxFps, 0.10, 0.90, 0.55);
    }

    public AdaptiveFramePacer(int minFps, int maxFps, double smoothing, double downThreshold, double upThreshold) {
        if (minFps <= 0 || maxFps < minFps) throw new IllegalArgumentException("Bad fps range");
        this.minFps = minFps;
        this.maxFps = maxFps;
        this.smoothing = smoothing;
        this.downThreshold = downThreshold;
        this.upThreshold = upThreshold;

        setTargetFps(maxFps); // empieza alto
    }

    public int getTargetFps() {
        return (int) Math.round(1_000_000_000.0 / targetFrameNs);
    }

    public long getTargetFrameNs() {
        return targetFrameNs;
    }

    public void setTargetFps(int fps) {
        fps = Math.max(minFps, Math.min(maxFps, fps));
        targetFrameNs = (long) (1_000_000_000L / (double) fps);
    }

    /**
     * Devuelve true si se permite renderizar este frame.
     */
    public boolean shouldRender(long now) {
        return now >= nextFrameAllowedNs;
    }

    /**
     * Llamar al final del frame, pasando cuánto ha costado (ns).
     * Ajusta targetFrameNs dinámicamente.
     */
    public void onFrameFinished(long now, long workNs) {
        // media móvil exponencial
        if (avgWorkNs == 0.0) avgWorkNs = workNs;
        else avgWorkNs = (1.0 - smoothing) * avgWorkNs + smoothing * workNs;

        // ratio = cuánto del presupuesto consume el frame
        double ratio = avgWorkNs / targetFrameNs;

        int fps = getTargetFps();

        // Si vamos pasados => bajar fps (subir frameNs)
        if (ratio > downThreshold && fps > minFps) {
            setTargetFps(fps - 5); // step. Puedes afinarlo
        }
        // Si vamos sobrados => subir fps
        else if (ratio < upThreshold && fps < maxFps) {
            setTargetFps(fps + 5);
        }

        nextFrameAllowedNs = now + targetFrameNs;
    }
}
