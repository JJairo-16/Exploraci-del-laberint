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
    JIJI_EN(DOORS.get("jiji_en.mp3")),
    
    COIN(ITEMS.get("coin.mp3")),
    COINS_POWER(ITEMS.get("coins-power.mp3")),
    POWERUP(ITEMS.get("power-pick-up.mp3")),

    CHEATED_BUTTON(ITEMS.get("cheated-button.mp3")),
    CHEATED_WALL1(ITEMS.get("cheated-wall-1.mp3")),
    CHEATED_WALL2(ITEMS.get("cheated-wall-2.mp3")),
    BOOTS(ITEMS.get("boots.mp3")),
    
    PICKAXE_DOOR(ITEMS.get("pickaxe-door.mp3")),
    PICKAXE_WALL(ITEMS.get("pickaxe-wall.mp3")),

    BLAI_GLASSES_POWER(ITEMS.get("blai-glasses-power.wav")),

    OPEN_LOCK(ITEMS.get("open-lock.mp3")),
    EXIT_LOCK(ITEMS.get("no-open-lock.mp3"));

    private final String resourcePath;

    Sound(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }
}
