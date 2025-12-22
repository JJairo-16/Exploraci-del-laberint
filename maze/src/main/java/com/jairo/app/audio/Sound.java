package com.jairo.app.audio;

import static com.jairo.app.audio.SoundsRoot.*;

public enum Sound {

    THEME("/music/theme.mp3"),
    VICTORY(GEN.get("victory.mp3")),

    STEP1(STEPS.get("step1.mp3")),
    STEP2(STEPS.get("step2.mp3")),
    STEP3(STEPS.get("step3.mp3")),
    STEP4(STEPS.get("step4.mp3")),
    STEP5(STEPS.get("step5.mp3")),
    STEP6(STEPS.get("step6.mp3")),
    STEP7(STEPS.get("step7.mp3")),

    TOCTOC(DOORS.get("toc_toc.wav")),
    LOCKED_DOOR(DOORS.get("doorLocked.mp3")),
    OPEN_DOOR(DOORS.get("openDoor.mp3")),
    
    JIJI_CA(DOORS.get("jiji_ca.mp3")),
    JIJI_ES(DOORS.get("jiji_es.mp3")),
    JIJI_EN(DOORS.get("jiji_en.mp3"));

    private final String resourcePath;

    Sound(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }
}
