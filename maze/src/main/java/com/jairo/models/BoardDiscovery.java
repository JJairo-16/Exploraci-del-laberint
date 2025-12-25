package com.jairo.models;

import java.util.List;

import static com.jairo.utils.map_generator.Cells.UNKNOWN;

public final class BoardDiscovery {

    private BoardDiscovery() {
    }

    /** Permite que el helper notifique al Board que hubo descubrimiento. */
    public interface DiscoverFlag {
        void markDiscovered();
    }

    public static int discoverAroundPowered(
            List<List<Integer>> cells,
            List<List<Integer>> visibility,
            int boardWidth,
            int boardHeight,
            int px, int py,
            int power,
            DiscoverFlag flag) {
        int minX = Math.max(0, px - power);
        int maxX = Math.min(boardWidth - 1, px + power);
        int minY = Math.max(0, py - power);
        int maxY = Math.min(boardHeight - 1, py + power);

        // Ajustes: menos ruido, más natural
        final double coreRatio = 0.65;
        final double warpAmplitude = power * 0.35;
        final double noiseScale = 0.18;
        final long seed = 1337L;

        final double coreRadius = power * coreRatio;
        final double coreRadius2 = coreRadius * coreRadius;

        int discovered = 0;

        for (int y = minY; y <= maxY; y++) {
            List<Integer> visibilityRow = visibility.get(y);
            List<Integer> cellsRow = cells.get(y);

            double power2 = power * (double) power;

            for (int x = minX; x <= maxX; x++) {
                if (visibilityRow.get(x) != UNKNOWN)
                    continue;

                int dx = x - px;
                int dy = y - py;

                double dist2 = dx * dx + (double) dy * dy;

                // Núcleo siempre
                if (dist2 <= coreRadius2) {
                    discovered++;
                    visibilityRow.set(x, cellsRow.get(x));
                    flag.markDiscovered();
                    continue;
                }

                if (dist2 > power2)
                    continue;

                double n = fbm((x + seed) * noiseScale, (y - seed) * noiseScale);
                double signed = (n * 2.0) - 1.0;

                double threshold = power + signed * warpAmplitude;

                if (dist2 <= threshold * threshold) {
                    // aquí visibilityRow.get(x) todavía es UNKNOWN por el continue de arriba
                    discovered++;
                    visibilityRow.set(x, cellsRow.get(x));
                    flag.markDiscovered();
                }
            }
        }

        return discovered;
    }

    /**
     * Expande aumentando power hasta descubrir al menos minRequired,
     * con límite de intentos.
     */
    public static int discoverUntilMin(
            List<List<Integer>> cells,
            List<List<Integer>> visibility,
            int boardWidth,
            int boardHeight,
            int px, int py,
            int initialPower,
            int minRequired,
            int maxAttempts,
            DiscoverFlag flag) {
        int totalDiscovered = 0;
        int power = Math.max(0, initialPower);

        if (minRequired <= 0 || maxAttempts <= 0)
            return 0;

        for (int attempt = 0; attempt < maxAttempts && totalDiscovered < minRequired; attempt++) {
            int gained = discoverAroundPowered(cells, visibility, boardWidth, boardHeight, px, py, power, flag);
            totalDiscovered += gained;
            power++;
        }

        return totalDiscovered;
    }

    public static int defaultMaxAttempts(int initialPower) {
        return Math.min(6, 2 + initialPower / 2);
    }

    /* ----------------- Ruido coherente (value noise + fbm) ----------------- */

    private static double fbm(double x, double y) {
        double sum = 0.0;
        double amp = 0.55;
        double freq = 1.0;

        for (int i = 0; i < 3; i++) {
            sum += amp * valueNoise(x * freq, y * freq);
            freq *= 2.0;
            amp *= 0.5;
        }

        return Math.max(0.0, Math.min(1.0, sum));
    }

    private static double valueNoise(double x, double y) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double tx = x - x0;
        double ty = y - y0;

        double sx = smoothstep(tx);
        double sy = smoothstep(ty);

        double v00 = hash01(x0, y0);
        double v10 = hash01(x1, y0);
        double v01 = hash01(x0, y1);
        double v11 = hash01(x1, y1);

        double ix0 = lerp(v00, v10, sx);
        double ix1 = lerp(v01, v11, sx);

        return lerp(ix0, ix1, sy);
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return (v < i) ? (i - 1) : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash01(int x, int y) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return ((h & 0xFFFFFFL) / (double) 0x1000000L);
    }
}
