package com.jairo.app.audio;

public enum SoundsRoot {
    GEN("/sfx/"),
    STEPS("/sfx/steps/"),
    DOORS("/sfx/doors/");

    private final String baseRoot;

    private SoundsRoot(String baseRoot) {
        this.baseRoot = baseRoot;
    }

    public String get(String path) {
        return baseRoot + path;
    }
}
