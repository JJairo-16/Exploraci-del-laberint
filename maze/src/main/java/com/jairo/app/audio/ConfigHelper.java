package com.jairo.app.audio;

public class ConfigHelper {
    private ConfigHelper() {}

    private static final double ROUND_STEP = 0.01;

    public static double getVolum(double input) {
        return Math.round(input / ROUND_STEP) * ROUND_STEP;
    }
}
