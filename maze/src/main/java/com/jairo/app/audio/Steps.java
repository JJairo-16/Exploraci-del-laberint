package com.jairo.app.audio;

import java.util.List;
import java.util.Random;

public class Steps {
    private Steps() {
    }

    private static final List<Sound> STEP_SOUNDS = List.of(
            Sound.STEP1,
            Sound.STEP2,
            Sound.STEP3,
            Sound.STEP4,
            Sound.STEP5,
            Sound.STEP6,
            Sound.STEP7
        );

    private static final Random random = new Random();

    public static void playRandomStep() {
        int i = random.nextInt(STEP_SOUNDS.size());
        String soundPath = STEP_SOUNDS.get(i).path();
        SoundManager.get().playSfx(soundPath, 0.7);
    }
}
