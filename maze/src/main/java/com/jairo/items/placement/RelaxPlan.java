package com.jairo.items.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.IntToDoubleFunction;

import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.items.SpecialType;

/**
 * <p>
 * Plan de relajación configurable para el sistema de colocación.
 * </p>
 *
 * <ul>
 * <li><b>order</b>: orden en el que se intenta relajar cada restricción.</li>
 * <li><b>step</b>: cuánto se reduce una restricción por paso
 * (<code>&gt;= 1</code>).</li>
 * <li><b>floor</b>: valor mínimo permitido para una restricción
 * (<code>&gt;= 0</code>).</li>
 * <li><b>cooldown</b>: cada cuántas rondas se permite relajar una restricción
 * (<code>&gt;= 1</code>).</li>
 * <li><b>maxRounds</b>: número máximo de rondas de relajación.</li>
 * <li><b>maxStallRounds</b>: máximo de rondas consecutivas sin cambios antes de
 * abandonar.</li>
 * <li><b>mode</b>: estrategia de relajación:
 * <ul>
 * <li><code>ONE_PER_ROUND</code>: relaja una sola restricción por ronda
 * (cíclico).</li>
 * <li><code>ALL_EACH_ROUND</code>: en cada ronda relaja todas las posibles
 * siguiendo <b>order</b>.</li>
 * </ul>
 * </li>
 * <li><b>scanMode</b>: política de escaneo local (3x3) alrededor de una celda
 * candidata para evitar conflictos con items ya colocados:
 * <ul>
 * <li><code>NONE</code>: no se realiza ningún escaneo.</li>
 * <li><code>SAME_TYPE_EXACT</code>: evita items con el mismo identificador
 * exacto.</li>
 * <li><code>SAME_TYPE_GENERAL</code>: evita items del mismo tipo general
 * (<code>BasicItemType</code>, <code>PowerType</code> o
 * <code>SpecialType</code>).</li>
 * <li><code>ANY_TYPE</code>: evita cualquier item adyacente.</li>
 * </ul>
 * </li>
 * <li><b>weightDecay</b>: peso de cada ronda.</li>
 * <li><b>weightFn</b>: formula para calcular el peso.</li>
 * </ul>
 */
public final class RelaxPlan {

    public enum Mode {
        ONE_PER_ROUND,
        ALL_PER_ROUND
    }

    public enum ScanMode {
        NONE,
        SAME_TYPE_EXACT,
        SAME_TYPE_GENERAL,
        ANY_TYPE
    }

    private final List<Constraint> order;
    private final EnumMap<Constraint, Integer> step;
    private final EnumMap<Constraint, Integer> floor;
    private final EnumMap<Constraint, Integer> cooldown;

    private final int maxRounds;
    private final int maxStallRounds;
    private final Mode mode;
    private final ScanMode scanMode;

    private final double weightDecay;
    private final IntToDoubleFunction weightFn;

    private RelaxPlan(
            List<Constraint> order,
            EnumMap<Constraint, Integer> step,
            EnumMap<Constraint, Integer> floor,
            EnumMap<Constraint, Integer> cooldown,
            int maxRounds,
            int maxStallRounds,
            Mode mode,
            ScanMode scan,
            double weightDecay,
            IntToDoubleFunction weightFn) {

        this.order = Collections.unmodifiableList(new ArrayList<>(order));
        this.step = new EnumMap<>(step);
        this.floor = new EnumMap<>(floor);
        this.cooldown = new EnumMap<>(cooldown);

        this.maxRounds = maxRounds;
        this.maxStallRounds = maxStallRounds;
        this.mode = mode;
        this.scanMode = scan;

        this.weightDecay = weightDecay;
        this.weightFn = Objects.requireNonNull(weightFn, "weightFn");
    }

    public List<Constraint> order() {
        return order;
    }

    public int step(Constraint c) {
        return step.getOrDefault(c, 0);
    }

    public int floor(Constraint c) {
        return floor.getOrDefault(c, 0);
    }

    public int cooldown(Constraint c) {
        return cooldown.getOrDefault(c, 0);
    }

    public int maxRounds() {
        return maxRounds;
    }

    public int maxStallRounds() {
        return maxStallRounds;
    }

    public Mode mode() {
        return mode;
    }

    public ScanMode scanMode() {
        return scanMode;
    }

    public double weightDecay() {
        return weightDecay;
    }

    public IntToDoubleFunction weightFunction() {
        return weightFn;
    }

    public boolean conflicts(ItemType self, ItemType neighbor) {
        if (scanMode == ScanMode.NONE || neighbor == null) {
            return false;
        }

        return switch (scanMode) {
            case NONE -> false;
            case ANY_TYPE -> true;

            case SAME_TYPE_EXACT ->
                self.getId().equals(neighbor.getId());

            case SAME_TYPE_GENERAL ->
                sameGeneralType(self, neighbor);
        };
    }

    private boolean sameGeneralType(ItemType a, ItemType b) {
        return (a instanceof BasicItemType && b instanceof BasicItemType)
                || (a instanceof PowerType && b instanceof PowerType)
                || (a instanceof SpecialType && b instanceof SpecialType);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Constraint> order = new ArrayList<>();
        private final EnumMap<Constraint, Integer> step = new EnumMap<>(Constraint.class);
        private final EnumMap<Constraint, Integer> floor = new EnumMap<>(Constraint.class);
        private final EnumMap<Constraint, Integer> cooldown = new EnumMap<>(Constraint.class);

        private int maxRounds = 8;
        private int maxStallRounds = 2;
        private Mode mode = Mode.ONE_PER_ROUND;
        private ScanMode scanMode = ScanMode.NONE;

        private double weightDecay = 0.90;

        // NUNCA null. Default “neutral”: pow(decay, round)
        private IntToDoubleFunction weightFn = r -> Math.pow(weightDecay, Math.max(0, r));

        private Builder() {
            // Default order si no se añade nada
            // (puedes cambiarlo a tu preferencia)
            order.add(Constraint.BETWEEN);
            order.add(Constraint.PLAYER);
            order.add(Constraint.EXIT);
            order.add(Constraint.BORDER);
        }

        public Builder order(List<Constraint> constraints) {
            order.clear();
            order.addAll(Objects.requireNonNull(constraints, "order"));
            return this;
        }

        public Builder addToOrder(Constraint c) {
            order.add(Objects.requireNonNull(c, "constraint"));
            return this;
        }

        public Builder step(Constraint c, int v) {
            step.put(Objects.requireNonNull(c, "constraint"), Math.max(0, v));
            return this;
        }

        public Builder floor(Constraint c, int v) {
            floor.put(Objects.requireNonNull(c, "constraint"), Math.max(0, v));
            return this;
        }

        public Builder cooldown(Constraint c, int v) {
            cooldown.put(Objects.requireNonNull(c, "constraint"), Math.max(0, v));
            return this;
        }

        public Builder maxRounds(int v) {
            maxRounds = Math.max(0, v);
            return this;
        }

        public Builder maxStallRounds(int v) {
            maxStallRounds = Math.max(0, v);
            return this;
        }

        public Builder mode(Mode m) {
            mode = Objects.requireNonNull(m, "mode");
            return this;
        }

        public Builder scanMode(ScanMode s) {
            scanMode = s;
            return this;
        }

        public Builder weightDecay(double decay) {
            if (!(decay > 0.0 && decay < 1.0)) {
                throw new IllegalArgumentException("weightDecay must be in (0, 1)");
            }
            this.weightDecay = decay;

            this.weightFn = r -> Math.pow(this.weightDecay, Math.max(0, r));
            return this;
        }

        public Builder weightFunction(IntToDoubleFunction fn) {
            this.weightFn = Objects.requireNonNull(fn, "weightFunction");
            return this;
        }

        public RelaxPlan build() {
            if (order.isEmpty()) {
                // fallback seguro
                order.add(Constraint.BETWEEN);
                order.add(Constraint.PLAYER);
                order.add(Constraint.EXIT);
                order.add(Constraint.BORDER);
            }

            // Defaults razonables si no configuraron algo:
            for (Constraint c : Constraint.values()) {
                step.putIfAbsent(c, 1);
                floor.putIfAbsent(c, 0);
                cooldown.putIfAbsent(c, 0);
            }

            if (this.weightFn == null) {
                this.weightFn = r -> Math.pow(this.weightDecay, Math.max(0, r));
            }

            return new RelaxPlan(
                    order,
                    step,
                    floor,
                    cooldown,
                    maxRounds,
                    maxStallRounds,
                    mode,
                    scanMode,
                    weightDecay,
                    weightFn);
        }
    }
}
