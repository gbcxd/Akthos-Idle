package com.obliviongatestudio.akthosidle.domain.model;

/**
 * Basic runtime status effect used by the {@link com.obliviongatestudio.akthosidle.engine.CombatEngine}.
 * The goal of this lightweight class is to provide just enough structure to
 * model common RPG effects (damage over time, heal over time, stun and slow)
 * without pulling in a full component system.
 */
public class StatusEffect {
    public enum Type { DOT, HOT, STUN, SLOW }

    public final Type type;
    /** Remaining duration in seconds. */
    public double remaining;
    /**
     * Generic magnitude value. For DoT/HoT this is applied per tick (1s).
     * For SLOW this represents a fractional slowdown (0.25 = +25% interval).
     */
    public double value;
    /** Internal accumulator for 1s ticks on DoT/HoT. */
    public double tickAcc;

    public StatusEffect(Type type, double durationSec, double value) {
        this.type = type;
        this.remaining = durationSec;
        this.value = value;
        this.tickAcc = 0.0;
    }

    public StatusEffect copy() {
        StatusEffect c = new StatusEffect(this.type, this.remaining, this.value);
        c.tickAcc = this.tickAcc;
        return c;
    }
}
