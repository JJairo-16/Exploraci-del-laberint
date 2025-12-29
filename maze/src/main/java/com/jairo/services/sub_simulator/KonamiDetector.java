package com.jairo.services.sub_simulator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import com.jairo.utils.KeyBind.Action;

public class KonamiDetector {

    private static final List<Action> KONAMI = List.of(
            Action.UP, Action.UP,
            Action.DOWN, Action.DOWN,
            Action.LEFT, Action.RIGHT,
            Action.LEFT, Action.RIGHT,
            Action.ZOOM_IN, Action.ZOOM_OUT,
            Action.ACTIVE_KONAMI);
    
    private static final Set<Action> BLACKLIST = Set.of(Action.NONE);

    private final List<Action> keyCombination;
    private final Deque<Action> buffer;

    public KonamiDetector() {
        this.keyCombination = KONAMI;
        this.buffer = new ArrayDeque<>(this.keyCombination.size());
    }

    public KonamiDetector(Action... keyCombination) {
        if (keyCombination == null || keyCombination.length == 0) {
            this.keyCombination = KONAMI;
        } else {
            this.keyCombination = List.of(keyCombination);
        }
        this.buffer = new ArrayDeque<>(this.keyCombination.size());
    }

    /** Devuelve true justo cuando se completa la secuencia */
    public boolean push(Action a) {
        if (a == null)
            return false;
    
        if (BLACKLIST.contains(a))
            return false;

        if (!keyCombination.contains(a))
            return false;

        if (buffer.size() == keyCombination.size())
            buffer.removeFirst();
        buffer.addLast(a);

        return matches();
    }

    public void reset() {
        buffer.clear();
    }

    private boolean matches() {
        if (buffer.size() != keyCombination.size())
            return false;

        int i = 0;
        for (Action x : buffer) {
            if (x != keyCombination.get(i++))
                return false;
        }
        return true;
    }
}
