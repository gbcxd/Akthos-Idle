package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.StatusEffect;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds resistance values against various status effects.
 */
public class StatusResistanceService {
    private final Map<StatusEffect.Type, Double> resistances = new EnumMap<>(StatusEffect.Type.class);

    /** Assign a resistance value in the range 0-1. */
    public void setResistance(StatusEffect.Type type, double value) {
        resistances.put(type, value);
    }

    /**
     * Apply resistance to a base chance, clamping the result to [0, 1].
     */
    public double applyResistance(StatusEffect.Type type, double baseChance) {
        double resist = resistances.getOrDefault(type, 0.0);
        double chance = baseChance - resist;
        if (chance < 0.0) return 0.0;
        if (chance > 1.0) return 1.0;
        return chance;
    }
}
