package com.jairo.app.loop;

/**
 * Adaptive frame pacing aiming for stable frame times.
 *
 * Usage pattern (typical):
 *   long frameStart = System.nanoTime();
 *   if (pacer.shouldRender(frameStart)) {
 *       // update + render
 *   } else {
 *       // skip render (optional: still update fixed-step / input, depending on your engine)
 *   }
 *   long frameEnd = System.nanoTime();
 *   pacer.onFrameFinished(frameEnd, frameEnd - frameStart);
 *
 * Notes:
 * - This class only decides *when* you should render next; it does not sleep/yield for you.
 * - If your loop busy-waits while shouldRender() == false, you will burn CPU and increase jitter.
 */
public final class AdaptiveFramePacer {

    private final int minFps;
    private final int maxFps;

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
        this.maxFps = maxFps;
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

        setTargetFps(maxFps); // start high (you can change this to 60 if you prefer)
    }

    public int getTargetFps() {
        return (int) Math.round(1_000_000_000.0 / targetFrameNs);
    }

    public long getTargetFrameNs() {
        return targetFrameNs;
    }

    public void setTargetFps(int fps) {
        fps = clampInt(fps, minFps, maxFps);
        long newFrameNs = (long) (1_000_000_000L / (double) fps);
        // Avoid 0 due to weird inputs (should not happen with clamp, but safe)
        if (newFrameNs <= 0L) newFrameNs = 1L;
        targetFrameNs = newFrameNs;
    }

    /** Returns true if a render is allowed at 'now' (System.nanoTime()). */
    public boolean shouldRender(long now) {
        // If not yet initialized, allow first render immediately
        return nextFrameAllowedNs == 0L || now >= nextFrameAllowedNs;
    }

    /**
     * Call once per frame at the end.
     * @param now    System.nanoTime() at end of frame
     * @param workNs time spent working this frame (end - start)
     */
    public void onFrameFinished(long now, long workNs) {
        if (workNs < 0L) workNs = 0L;

        // Initialize timeline on first call
        if (nextFrameAllowedNs == 0L) {
            nextFrameAllowedNs = now + targetFrameNs;
        }

        // How late are we relative to when the next frame should have been allowed?
        long latenessNs = Math.max(0L, now - nextFrameAllowedNs);

        // Clamp extreme spikes so one bad frame doesn't whiplash the controller
        if (avgWorkNs > 0.0) {
            double maxAllowed = avgWorkNs * spikeClampMultiplier;
            if (workNs > maxAllowed) workNs = (long) maxAllowed;
        }

        // Effective work accounts for lateness (stability signal)
        double effectiveWork = workNs + latenessWeight * latenessNs;

        // EMA
        if (avgWorkNs == 0.0) avgWorkNs = effectiveWork;
        else avgWorkNs = (1.0 - smoothing) * avgWorkNs + smoothing * effectiveWork;

        // Ratio: how much of the budget we're consuming
        double ratio = avgWorkNs / targetFrameNs;

        // Accumulate evidence (consecutive-ish)
        if (ratio > downThreshold) {
            downScore++;
            upScore = Math.max(0, upScore - 1);
        } else if (ratio < upThreshold) {
            upScore++;
            downScore = Math.max(0, downScore - 1);
        } else {
            // In dead-zone: decay both slowly
            downScore = Math.max(0, downScore - 1);
            upScore = Math.max(0, upScore - 1);
        }

        boolean canChange = (now - lastChangeNs) >= changeCooldownNs;
        if (canChange) {
            int fps = getTargetFps();

            // If we're very late, treat as strong "down" signal (without tiers)
            boolean veryLate = latenessNs > (long) (0.75 * targetFrameNs);
            int downNeeded = veryLate ? Math.max(1, scoreToChange - 1) : scoreToChange;

            if (downScore >= downNeeded && fps > minFps) {
                setTargetFps(fps - stepDownFps);
                lastChangeNs = now;
                resetScores();
            } else if (upScore >= scoreToChange && fps < maxFps) {
                setTargetFps(fps + stepUpFps);
                lastChangeNs = now;
                resetScores();
            }
        }

        // Advance stable timeline (prevents drift/jitter bursts)
        nextFrameAllowedNs += targetFrameNs;

        // If we fell too far behind, resync
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

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
