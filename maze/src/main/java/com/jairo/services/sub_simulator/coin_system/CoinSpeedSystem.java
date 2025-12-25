package com.jairo.services.sub_simulator.coin_system;

public class CoinSpeedSystem {
    private CoinSpeedSystem() {
    }

    // Se activa a partir de x% de monedas
    private static int minPercentage = 10;

    // A partir de y% de monedas, ya no mejora más
    private static int maxPercentage = minPercentage + 10;

    // Multiplicador mínimo del cooldown (límite inferior).
    // Ej: 0.60 => como mucho reduces el cooldown a un 60% del original (40% menos).
    private static final double MIN_COOLDOWN_MULTIPLIER = 0.75;

    // Curva: más alto => baja más rápido al principio pero con rendimientos
    // decrecientes
    private static final double K = 2.0;

    /**
     * @return multiplicador del cooldown (entre MIN_COOLDOWN_MULTIPLIER y 1.0)
     */
    public static double cooldownMultiplier(int currentCoins, int maxCoins) {
        double p = coinPercent(currentCoins, maxCoins); // 0..1
        double t = activationT(p); // 0..1 (aplicando min/max)

        if (t <= 0.0)
            return 1.0; // no activo

        // Curva "natural" con rendimientos decrecientes:
        // eased va de 0 a 1 y se aplana arriba.
        double eased = 1.0 - Math.exp(-K * t);

        // Convertimos eased en multiplicador:
        // eased=0 -> 1.0
        // eased=1 -> MIN_COOLDOWN_MULTIPLIER
        return lerp(1.0, MIN_COOLDOWN_MULTIPLIER, eased);
    }

    public static boolean canActivated(int currentCoins, int maxCoins) {
        if (maxCoins <= 0)
            return false;

        double p = coinPercent(currentCoins, maxCoins);
        double min = minPercentage / 100.0;

        return p >= min;
    }

    private static double coinPercent(int coins, int maxCoins) {
        if (maxCoins <= 0)
            return 0.0;
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

    public static int getMinPercentage() {
        return minPercentage;
    }
    

    public static int setMinPercentage(int min) {
        minPercentage = min;
        maxPercentage = min + 10;
        return min;
    }
}
