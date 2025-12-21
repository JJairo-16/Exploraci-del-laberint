package com.jairo.app.audio;

public enum Sound {

    THEME("/music/theme.mp3"),
    VICTORY("/sfx/victory.mp3"),
    STEP1("/sfx/step1.mp3"),
    STEP2("/sfx/step2.mp3"),
    STEP3("/sfx/step3.mp3"),
    STEP4("/sfx/step4.mp3"),
    STEP5("/sfx/step5.mp3"),
    STEP6("/sfx/step6.mp3"),
    STEP7("/sfx/step7.mp3");

    private final String resourcePath;

    Sound(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }
}
