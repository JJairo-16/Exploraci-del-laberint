// KeyBind.java
package com.jairo.utils;

import java.util.EnumMap;
import java.util.Map;

import javafx.scene.input.KeyCode;

public final class KeyBind {

    private KeyBind() {
    }

    private static final Map<KeyCode, Action> KEY_BINDS = new EnumMap<>(KeyCode.class);

    static {
        KEY_BINDS.put(KeyCode.A, Action.LEFT);
        KEY_BINDS.put(KeyCode.LEFT, Action.LEFT);

        KEY_BINDS.put(KeyCode.W, Action.UP);
        KEY_BINDS.put(KeyCode.UP, Action.UP);

        KEY_BINDS.put(KeyCode.S, Action.DOWN);
        KEY_BINDS.put(KeyCode.DOWN, Action.DOWN);

        KEY_BINDS.put(KeyCode.D, Action.RIGHT);
        KEY_BINDS.put(KeyCode.RIGHT, Action.RIGHT);

        KEY_BINDS.put(KeyCode.E, Action.USE);
        KEY_BINDS.put(KeyCode.ENTER, Action.USE);

        KEY_BINDS.put(KeyCode.SHIFT, Action.SPRINT);

        KEY_BINDS.put(KeyCode.PLUS, Action.ZOOM_IN);
        KEY_BINDS.put(KeyCode.MINUS, Action.ZOOM_OUT);

        KEY_BINDS.put(KeyCode.Z, Action.PREVIOUS_SKIN);
        KEY_BINDS.put(KeyCode.X, Action.NEXT_SKIN);

        KEY_BINDS.put(KeyCode.DIGIT1, Action.PREVIOUS_ITEM);
        KEY_BINDS.put(KeyCode.DIGIT2, Action.NEXT_ITEM);
        KEY_BINDS.put(KeyCode.Q, Action.NEXT_ITEM);

        KEY_BINDS.put(KeyCode.F1, Action.SWITCH_SHOW_FPS);
        KEY_BINDS.put(KeyCode.F2, Action.SWITCH_COINS_POWER);
    }

    public static Action getAction(KeyCode key) {
        return KEY_BINDS.getOrDefault(key, Action.NONE);
    }

    public enum Action {
        LEFT(true, true, 1.0),
        RIGHT(true, true, 1.0),
        UP(true, true, 1.0),
        DOWN(true, true, 1.0),
        USE(),
        SPRINT(),
        ZOOM_IN(),
        ZOOM_OUT(),
        NEXT_SKIN(true, false, 1.5),
        PREVIOUS_SKIN(true, false, 1.5),
        PREVIOUS_ITEM(),
        NEXT_ITEM(),
        SWITCH_SHOW_FPS(),
        SWITCH_COINS_POWER(),
        NONE();

        public final boolean canMaintain;
        public final boolean isAMovement;
        public final double cooldownMultiplier;

        Action(boolean canMaintain, boolean isAMovement, double cooldownMultiplier) {
            this.canMaintain = canMaintain;
            this.isAMovement = isAMovement;
            this.cooldownMultiplier = cooldownMultiplier;
        }

        Action() {
            this.canMaintain = false;
            this.isAMovement = false;
            this.cooldownMultiplier = 1.0;
        }
    }
}
