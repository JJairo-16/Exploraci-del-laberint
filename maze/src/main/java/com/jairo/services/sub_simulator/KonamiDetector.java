package com.jairo.services.sub_simulator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import com.jairo.utils.KeyBind.Action;

public class KonamiDetector {
    private static final List<Action> KONAMI = List.of(
        Action.UP, Action.UP,
        Action.DOWN, Action.DOWN,
        Action.LEFT, Action.RIGHT,
        Action.LEFT, Action.RIGHT,
        Action.ZOOM_IN, Action.ZOOM_OUT,
        Action.ACTIVE_KONAMI
    );

    private final Deque<Action> buffer = new ArrayDeque<>(KONAMI.size());

    /** Devuelve true justo cuando se completa la secuencia */
    public boolean push(Action a) {
        if (!KONAMI.contains(a)) return false;

        if (buffer.size() == KONAMI.size()) buffer.removeFirst();
        buffer.addLast(a);

        return matches();
    }

    public void reset() {
        buffer.clear();
    }

    private boolean matches() {
        if (buffer.size() != KONAMI.size()) return false;

        int i = 0;
        for (Action x : buffer) {
            if (x != KONAMI.get(i++)) return false;
        }
        return true;
    }
}
