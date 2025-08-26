// app/src/main/java/com/example/akthosidle/engine/CombatMath.java
package com.obliviongatestudio.akthosidle.engine;

import java.util.Map;
import java.util.Random;

public final class CombatMath {
    private CombatMath() {}

    // --- Timing ---
    public static boolean cooldownReady(long lastAttackAtMs, long nowMs, long cooldownMs) {
        return (nowMs - lastAttackAtMs) >= cooldownMs;
    }

    // --- Damage pipeline ---
    public static int baseDamage(int attack, int strength) {
        // Simple starter formula – tweak as you like
        return Math.max(0, attack + (int)Math.floor(strength * 0.5));
    }

    public static boolean isCrit(Random rng, double critChance) {
        return rng.nextDouble() < critChance; // pass a seeded Random in tests
    }

    public static int applyCrit(int dmg, boolean crit, double critMult) {
        return crit ? (int)Math.round(dmg * critMult) : dmg;
    }

    /** Element multiplier from a matrix (defaults to 1.0 if missing). */
    public static double elementMod(String atk, String def, Map<String, Map<String, Double>> matrix) {
        if (atk == null || def == null || matrix == null) return 1.0;
        Map<String, Double> row = matrix.get(atk);
        if (row == null) return 1.0;
        Double v = row.get(def);
        return v != null ? v : 1.0;
    }

}
