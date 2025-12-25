package com.jairo.app.input;

import java.util.EnumSet;
import java.util.Set;

import com.jairo.services.Simulator;
import com.jairo.utils.KeyBind;

import javafx.scene.input.KeyCode;

/**
 * Controla input mantenido sin depender del auto-repeat del SO.
 * Usa una única fuente de tiempo (now del AnimationTimer).
 */
public final class InputRepeatController {

    private final Simulator simulator;
    private final InputHandler input;

    private final Set<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);

    private KeyBind.Action activeMoveAction = null;
    private boolean sprinting = false;

    private long nextRepeatNs = 0L;

    private final long initialDelayNs;
    private final long repeatEveryNs;
    private final double sprintSpeedMultiplier;

    public InputRepeatController(
            Simulator simulator,
            InputHandler input,
            long initialDelayNs,
            long repeatEveryNs,
            double sprintSpeedMultiplier) {
        this.simulator = simulator;
        this.input = input;
        this.initialDelayNs = initialDelayNs;
        this.repeatEveryNs = repeatEveryNs;
        this.sprintSpeedMultiplier = sprintSpeedMultiplier;
    }

    private boolean isActionHeld(KeyBind.Action action) {
        if (action == null)
            return false;
        for (KeyCode k : pressed) {
            if (KeyBind.getAction(k) == action)
                return true;
        }
        return false;
    }

    private KeyBind.Action findAnyHeldMaintainableAction() {
        for (KeyCode k : pressed) {
            KeyBind.Action a = KeyBind.getAction(k);
            if (a != null && a.canMaintain)
                return a;
        }
        return null;
    }

    private double tapFactor() {
        // Solo afecta al "tap" (la primera pulsación)
        return (sprinting && simulator.getCurrentAction().isAMovement)
                ? sprintSpeedMultiplier
                : 1.0;
    }

    private double holdFactor() {
        // Mantener pulsado: sprint + monedas
        return tapFactor() * simulator.getSprintingCooldownMultiplier();
    }

    /**
     * @return true si ejecutó acción (marca drawerDirty)
     */
    public boolean onKeyPressed(KeyCode key, long now) {
        // 🔒 Bloqueo REAL del auto-repeat del SO
        if (pressed.contains(key)) {
            return false;
        }
        pressed.add(key);

        if (key == KeyCode.SHIFT) {
            sprinting = true;
            return false;
        }

        KeyBind.Action action = KeyBind.getAction(key);
        if (action == null)
            return false;

        if (action.canMaintain) {
            activeMoveAction = action;
            input.runAction(action);

            long base = (long) (initialDelayNs * action.cooldownMultiplier);
            long delay = (long) (base * tapFactor()); // 👈 SIN monedas

            boolean use = action == KeyBind.Action.USE;
            boolean power = simulator.getInventory().containsPower(simulator.getLastPower());
            if (use && power)
                delay *= 1.5;

            nextRepeatNs = now + delay;
            return true;
        }

        return false;
    }

    public void onKeyReleased(KeyCode key, long now) {
        pressed.remove(key);

        if (key == KeyCode.SHIFT) {
            sprinting = false;
            return;
        }

        KeyBind.Action action = KeyBind.getAction(key);
        if (action == null)
            return;

        if (action == activeMoveAction && !isActionHeld(activeMoveAction)) {
            activeMoveAction = findAnyHeldMaintainableAction();

            long base = (long) (initialDelayNs * action.cooldownMultiplier);
            long delay = (long) (base * holdFactor()); // 👈 CON monedas

            boolean use = action == KeyBind.Action.USE;
            boolean power = simulator.getInventory().containsPower(simulator.getLastPower());
            if (use && power)
                delay *= 1.5;

            nextRepeatNs = now + delay;
        }
    }

    /**
     * @return true si ejecutó acción (marca drawerDirty)
     */
    public boolean handleRepeatTick(long now) {
        if (activeMoveAction == null)
            return false;
        if (!activeMoveAction.canMaintain)
            return false;
        if (!isActionHeld(activeMoveAction))
            return false;
        if (now < nextRepeatNs)
            return false;

        input.runAction(activeMoveAction);

        long baseInterval = (long) (repeatEveryNs * activeMoveAction.cooldownMultiplier);
        long interval = (long) (baseInterval * holdFactor()); // 👈 CON monedas

        boolean use = activeMoveAction == KeyBind.Action.USE;
        boolean power = simulator.getInventory().containsPower(simulator.getLastPower());
        if (use && power)
            interval *= 1.5;

        nextRepeatNs = now + interval;
        return true;

    }

    /**
     * 🔧 CRÍTICO:
     * Llamar cuando se pierde foco o escena para evitar teclas "atascadas".
     */
    public void resetHeldKeys() {
        pressed.clear();
        activeMoveAction = null;
        sprinting = false;
        nextRepeatNs = 0L;
    }
}
