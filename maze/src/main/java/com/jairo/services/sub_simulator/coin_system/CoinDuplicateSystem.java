package com.jairo.services.sub_simulator.coin_system;

public final class CoinDuplicateSystem {
    private CoinDuplicateSystem() {}

    // Se activa a partir de x% de monedas
    private static int minPercentage = 25;

    // A partir de y% de monedas, ya no mejora más
    private static int maxPercentage = minPercentage + 10;

    // Probabilidad máxima de duplicar
    private static final double MAX_DUPLICATE_CHANCE = 0.40; // 40%

    // Curva: más alto => sube más rápido al principio sin descontrolarse
    private static final double K = 2.0;

    public static int getMinPercentage() {
        return minPercentage;
    }

    /**
     * @return probabilidad [0.0..MAX_DUPLICATE_CHANCE]
     */
    public static double duplicateChance(int currentCoins, int maxCoins) {
        double p = coinPercent(currentCoins, maxCoins); // 0..1
        double t = activationT(p);                      // 0..1

        if (t <= 0.0) return 0.0;

        // Curva "natural" con rendimientos decrecientes
        double eased = 1.0 - Math.exp(-K * t);

        return lerp(0.0, MAX_DUPLICATE_CHANCE, eased);
    }

    public static boolean canActivated(int currentCoins, int maxCoins) {
        if (maxCoins <= 0) return false;

        double p = coinPercent(currentCoins, maxCoins);
        double min = minPercentage / 100.0;

        return p >= min;
    }

    private static double coinPercent(int coins, int maxCoins) {
        if (maxCoins <= 0) return 0.0;
        return clamp01(coins / (double) maxCoins);
    }

    private static double activationT(double p01) {
        double min = minPercentage / 100.0;
        double max = maxPercentage / 100.0;

        if (max <= min) {
            return (p01 >= min) ? 1.0 : 0.0;
        }
        return clamp01((p01 - min) / (max - min));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public static int setMinPercentage(int min) {
        minPercentage = min;
        maxPercentage = min + 10;
        return min;
    }
}
