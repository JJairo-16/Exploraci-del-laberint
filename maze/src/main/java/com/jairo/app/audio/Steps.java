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
            Sound.STEP7);

    private static final List<Sound> ICE_STEP_SOUNDS = List.of(
        Sound.ICE_STEP1,
        Sound.ICE_STEP2,
        Sound.ICE_STEP3,
        Sound.ICE_STEP4,
        Sound.ICE_STEP5,
        Sound.ICE_STEP6
    );

    private static final Random random = new Random();

    public static void playRandomStep() {
        String soundPath = getRandomSound(STEP_SOUNDS);
        if (soundPath == null) return;
        SoundManager.get().playSfx(soundPath, 0.9);
    }

    public static void playRandomIceStep() {
        String soundPath = getRandomSound(ICE_STEP_SOUNDS);
        if (soundPath == null) return;
        SoundManager.get().playSfx(soundPath, 0.7);
    }

    private static String getRandomSound(List<Sound> sounds) {
        if (sounds.isEmpty()) {
            return null;
        }

        int i = random.nextInt(sounds.size());
        Sound sound = sounds.get(i);
        if (sound == null) return null;

        return sound.path();
    }
}
