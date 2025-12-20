// KeyBind.java
package com.jairo.utils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javafx.scene.input.KeyCode;

public final class KeyBind {

    private KeyBind() {}

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

        KEY_BINDS.put(KeyCode.R, Action.SWITCH_CONFIRM);

        KEY_BINDS.put(KeyCode.PLUS, Action.ZOOM_IN);
        KEY_BINDS.put(KeyCode.MINUS, Action.ZOOM_OUT);
    }

    public static Action getAction(KeyCode key) {
        return KEY_BINDS.getOrDefault(key, Action.NONE);
    }

    public enum Action {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        USE,
        SWITCH_CONFIRM,
        ZOOM_IN,
        ZOOM_OUT,
        NONE
    }

    private static final List<Action> canMaintain = List.of(
            Action.RIGHT,
            Action.LEFT,
            Action.UP,
            Action.DOWN
    );

    public static boolean actionCanMaintains(Action action) {
        return canMaintain.contains(action);
    }
}
