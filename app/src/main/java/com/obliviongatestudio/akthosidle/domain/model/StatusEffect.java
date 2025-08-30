package com.obliviongatestudio.akthosidle.domain.model;

/**
 * Basic runtime status effect used by the {@link com.obliviongatestudio.akthosidle.engine.CombatEngine}.
 * The goal of this lightweight class is to provide just enough structure to
 * model common RPG effects (damage over time, heal over time, stun and slow)
 * without pulling in a full component system.
 */
public class StatusEffect {

    // 1. Made the enum public static and named it 'Type' (uppercase T - standard convention)
    public static enum Type {
        DOT,
        HOT,
        STUN,
        SLOW
    }

    public final Type type;     // 2. Field type is 'Type'
    public double remaining;
    public double value;
    public double tickAcc;
    public String name;         // 3. Added name field

    // 4. Constructor updated to accept a name and use 'Type'
    public StatusEffect(String name, Type type, double durationSec, double value) {
        this.name = name;
        this.type = type;
        this.remaining = durationSec;
        this.value = value;
        this.tickAcc = 0.0;
    }

    // 5. Copy constructor updated to also copy the name
    public StatusEffect copy() {
        StatusEffect c = new StatusEffect(this.name, this.type, this.remaining, this.value);
        c.tickAcc = this.tickAcc;
        return c;
    }

    // 6. Getter for the name
    public String getName() {
        return this.name;
    }

    // Optional: Getter for the enum type name if needed directly
    public String getTypeName() {
        return this.type.name();
    }
}
