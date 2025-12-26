package com.jairo.items.placement;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * <p>Plan de relajación configurable para el sistema de colocación.</p>
 *
 * <ul>
 *   <li><b>order</b>: orden en el que se intenta relajar cada restricción.</li>
 *   <li><b>step</b>: cuánto se reduce una restricción por paso (<code>&gt;= 1</code>).</li>
 *   <li><b>floor</b>: valor mínimo permitido para una restricción (<code>&gt;= 0</code>).</li>
 *   <li><b>cooldown</b>: cada cuántas rondas se permite relajar una restricción (<code>&gt;= 1</code>).</li>
 *   <li><b>maxRounds</b>: número máximo de rondas de relajación.</li>
 *   <li><b>mode</b>: estrategia de relajación:
 *     <ul>
 *       <li><code>ONE_PER_ROUND</code>: relaja una sola restricción por ronda (cíclico).</li>
 *       <li><code>ALL_EACH_ROUND</code>: en cada ronda relaja todas las posibles siguiendo <b>order</b>.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class RelaxPlan {

    public enum Mode {
        ONE_PER_ROUND,
        ALL_EACH_ROUND
    }

    private final List<Constraint> order;
    private final EnumMap<Constraint, Integer> step;
    private final EnumMap<Constraint, Integer> floor;
    private final EnumMap<Constraint, Integer> cooldown;

    private final int maxRounds;
    private final Mode mode;

    private RelaxPlan(
            List<Constraint> order,
            EnumMap<Constraint, Integer> step,
            EnumMap<Constraint, Integer> floor,
            EnumMap<Constraint, Integer> cooldown,
            int maxRounds,
            Mode mode
    ) {
        this.order = Objects.requireNonNull(order, "order");
        if (order.isEmpty()) throw new IllegalArgumentException("order cannot be empty");

        this.step = Objects.requireNonNull(step, "step");
        this.floor = Objects.requireNonNull(floor, "floor");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");

        this.maxRounds = Math.max(0, maxRounds);
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public List<Constraint> order() {
        return order;
    }

    public int step(Constraint c) {
        Integer v = step.get(c);
        return (v == null) ? 1 : Math.max(1, v);
    }

    public int floor(Constraint c) {
        Integer v = floor.get(c);
        return (v == null) ? 0 : Math.max(0, v);
    }

    /**
     * Cada cuántas rondas se permite relajar esa restricción.
     * - 1 => puede relajarse cada ronda
     * - 2 => una vez cada 2 rondas
     * - 10 => muy poco frecuente
     */
    public int cooldown(Constraint c) {
        Integer v = cooldown.get(c);
        return (v == null) ? 1 : Math.max(1, v);
    }

    public int maxRounds() {
        return maxRounds;
    }

    public Mode mode() {
        return mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Constraint> order = List.of(
                Constraint.PLAYER,
                Constraint.BETWEEN,
                Constraint.EXIT,
                Constraint.BORDER
        );

        private final EnumMap<Constraint, Integer> step = new EnumMap<>(Constraint.class);
        private final EnumMap<Constraint, Integer> floor = new EnumMap<>(Constraint.class);
        private final EnumMap<Constraint, Integer> cooldown = new EnumMap<>(Constraint.class);

        private int maxRounds = 64;
        private Mode mode = Mode.ONE_PER_ROUND;

        public Builder order(List<Constraint> order) {
            this.order = Objects.requireNonNull(order, "order");
            return this;
        }

        public Builder step(Constraint c, int v) {
            step.put(Objects.requireNonNull(c, "constraint"), Math.max(1, v));
            return this;
        }

        public Builder floor(Constraint c, int v) {
            floor.put(Objects.requireNonNull(c, "constraint"), Math.max(0, v));
            return this;
        }

        public Builder cooldown(Constraint c, int rounds) {
            cooldown.put(Objects.requireNonNull(c, "constraint"), Math.max(1, rounds));
            return this;
        }

        public Builder maxRounds(int v) {
            this.maxRounds = Math.max(0, v);
            return this;
        }

        public Builder mode(Mode m) {
            this.mode = Objects.requireNonNull(m, "mode");
            return this;
        }

        public RelaxPlan build() {
            if (order == null || order.isEmpty()) {
                throw new IllegalArgumentException("RelaxPlan.order cannot be null/empty");
            }
            return new RelaxPlan(order, step, floor, cooldown, maxRounds, mode);
        }
    }
}
