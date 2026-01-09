package com.jairo.app.loop;

/**
 * Adaptive frame pacing aiming for stable frame times.
 *
 * Adds dynamic max-fps capping (foreground/background/afk), by allowing
 * changing the effective max fps at runtime.
 */
public final class AdaptiveFramePacer {

    private final int minFps;
    private final int absoluteMaxFps;

    // Dynamic cap (can be lowered when background/AFK)
    private int maxFpsCap;

    private long targetFrameNs;

    // Stable scheduling timeline (reduces jitter vs now + target)
    private long nextFrameAllowedNs = 0L;

    // EMA of effective frame cost (ns)
    private double avgWorkNs = 0.0;

    // 0..1 (lower = more stable, higher = faster reaction)
    private final double smoothing;

    // Hysteresis band (avoid oscillation)
    private final double downThreshold; // if ratio > X => down
    private final double upThreshold;   // if ratio < Y => up

    // Avoid changing too often
    private final long changeCooldownNs;
    private long lastChangeNs = 0L;

    // Require consecutive evidence before adjusting
    private int downScore = 0;
    private int upScore = 0;
    private final int scoreToChange;

    // Spike handling
    private final double spikeClampMultiplier;

    // Lateness influence: frames finishing past deadline matter for stability
    private final double latenessWeight; // 0..1

    // Step sizes (small = stable)
    private final int stepDownFps;
    private final int stepUpFps;

    // If we fall too far behind, resync timeline
    private final long maxLagFramesToResync;

    public AdaptiveFramePacer(int minFps, int maxFps) {
        this(
                minFps, maxFps,
                0.06,        // smoothing (stable)
                0.92,        // downThreshold
                0.70,        // upThreshold
                350_000_000L,// cooldown (350ms)
                4,           // evidence frames required
                2.5,         // spike clamp multiplier
                0.35,        // lateness weight
                1,           // stepDownFps
                1,           // stepUpFps
                3            // resync if > 3 frames late
        );
    }

    public AdaptiveFramePacer(
            int minFps,
            int maxFps,
            double smoothing,
            double downThreshold,
            double upThreshold,
            long changeCooldownNs,
            int scoreToChange,
            double spikeClampMultiplier,
            double latenessWeight,
            int stepDownFps,
            int stepUpFps,
            long maxLagFramesToResync
    ) {
        if (minFps <= 0 || maxFps < minFps) throw new IllegalArgumentException("Bad fps range");
        if (!(smoothing > 0.0 && smoothing <= 1.0)) throw new IllegalArgumentException("Bad smoothing");
        if (!(downThreshold > 0.0 && downThreshold <= 1.5)) throw new IllegalArgumentException("Bad downThreshold");
        if (!(upThreshold >= 0.0 && upThreshold < downThreshold)) throw new IllegalArgumentException("Bad upThreshold");
        if (changeCooldownNs < 0) throw new IllegalArgumentException("Bad cooldown");
        if (scoreToChange < 1) throw new IllegalArgumentException("Bad scoreToChange");
        if (spikeClampMultiplier < 1.0) throw new IllegalArgumentException("Bad spikeClampMultiplier");
        if (latenessWeight < 0.0 || latenessWeight > 1.0) throw new IllegalArgumentException("Bad latenessWeight");
        if (stepDownFps < 1 || stepUpFps < 1) throw new IllegalArgumentException("Bad step size");
        if (maxLagFramesToResync < 1) throw new IllegalArgumentException("Bad maxLagFramesToResync");

        this.minFps = minFps;
        this.absoluteMaxFps = maxFps;
        this.maxFpsCap = maxFps;

        this.smoothing = smoothing;
        this.downThreshold = downThreshold;
        this.upThreshold = upThreshold;
        this.changeCooldownNs = changeCooldownNs;
        this.scoreToChange = scoreToChange;
        this.spikeClampMultiplier = spikeClampMultiplier;
        this.latenessWeight = latenessWeight;
        this.stepDownFps = stepDownFps;
        this.stepUpFps = stepUpFps;
        this.maxLagFramesToResync = maxLagFramesToResync;

        setTargetFps(maxFps); // start high
    }

    public int getTargetFps() {
        return (int) Math.round(1_000_000_000.0 / targetFrameNs);
    }

    public long getTargetFrameNs() {
        return targetFrameNs;
    }

    public int getMaxFpsCap() {
        return maxFpsCap;
    }

    /**
     * Dynamically changes the effective max fps cap. This is what you use for
     * foreground/background/AFK.
     */
    public void setMaxFpsCap(int cap) {
        cap = Math.clamp(cap, minFps, absoluteMaxFps);

        if (cap == this.maxFpsCap) return;

        this.maxFpsCap = cap;

        // If current target exceeds the new cap, clamp immediately.
        int target = getTargetFps();
        if (target > cap) {
            setTargetFps(cap);
            // resync timeline to avoid long "no render" gaps after a big cap drop
            nextFrameAllowedNs = 0L;
        }
    }

    public void setTargetFps(int fps) {
        fps = Math.clamp(fps, minFps, maxFpsCap);
        long newFrameNs = (long) (1_000_000_000L / (double) fps);
        if (newFrameNs <= 0L) newFrameNs = 1L;
        targetFrameNs = newFrameNs;
    }

    /** Returns true if a render is allowed at 'now' (System.nanoTime()). */
    public boolean shouldRender(long now) {
        return nextFrameAllowedNs == 0L || now >= nextFrameAllowedNs;
    }

    /**
     * Call once per frame at the end.
     * @param now    System.nanoTime() at end of frame
     * @param workNs time spent working this frame (end - start)
     */
    public void onFrameFinished(long now, long workNs) {
        if (workNs < 0L) workNs = 0L;

        if (nextFrameAllowedNs == 0L) {
            nextFrameAllowedNs = now + targetFrameNs;
        }

        long latenessNs = Math.max(0L, now - nextFrameAllowedNs);

        if (avgWorkNs > 0.0) {
            double maxAllowed = avgWorkNs * spikeClampMultiplier;
            if (workNs > maxAllowed) workNs = (long) maxAllowed;
        }

        double effectiveWork = workNs + latenessWeight * latenessNs;

        if (avgWorkNs == 0.0) avgWorkNs = effectiveWork;
        else avgWorkNs = (1.0 - smoothing) * avgWorkNs + smoothing * effectiveWork;

        double ratio = avgWorkNs / targetFrameNs;

        if (ratio > downThreshold) {
            downScore++;
            upScore = Math.max(0, upScore - 1);
        } else if (ratio < upThreshold) {
            upScore++;
            downScore = Math.max(0, downScore - 1);
        } else {
            downScore = Math.max(0, downScore - 1);
            upScore = Math.max(0, upScore - 1);
        }

        boolean canChange = (now - lastChangeNs) >= changeCooldownNs;
        if (canChange) {
            int fps = getTargetFps();
            int cap = maxFpsCap;

            boolean veryLate = latenessNs > (long) (0.75 * targetFrameNs);
            int downNeeded = veryLate ? Math.max(1, scoreToChange - 1) : scoreToChange;

            if (downScore >= downNeeded && fps > minFps) {
                setTargetFps(fps - stepDownFps);
                lastChangeNs = now;
                resetScores();
            } else if (upScore >= scoreToChange && fps < cap) {
                setTargetFps(fps + stepUpFps);
                lastChangeNs = now;
                resetScores();
            }
        }

        nextFrameAllowedNs += targetFrameNs;

        long maxLagNs = maxLagFramesToResync * targetFrameNs;
        if (now - nextFrameAllowedNs > maxLagNs) {
            nextFrameAllowedNs = now + targetFrameNs;
        }
    }

    public void reset(long now) {
        avgWorkNs = 0.0;
        resetScores();
        lastChangeNs = 0L;
        nextFrameAllowedNs = now + targetFrameNs;
    }

    private void resetScores() {
        downScore = 0;
        upScore = 0;
    }
}
