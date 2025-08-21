package com.example.akthosidle.engine;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Random;

public class CombatMathTest {
    @Test public void cooldown_ready_when_elapsed_meets_threshold() {
        assertTrue(CombatMath.cooldownReady(1000, 1500, 500));
        assertFalse(CombatMath.cooldownReady(1000, 1499, 500));
    }

    @Test public void crit_is_deterministic_with_seed() {
        Random seeded = new Random(42L);
        boolean c1 = CombatMath.isCrit(seeded, 0.50); // with seed, this is stable
        boolean c2 = CombatMath.isCrit(seeded, 0.50);
        // Assert exact pattern you expect for that seed:
        // (You can print once to see sequence locally, then lock assertions.)
        assertFalse(c1);
        assertTrue(c2);
    }

    @Test public void damage_pipeline_is_pure() {
        int dmg = CombatMath.baseDamage(20, 10); // 25
        dmg = CombatMath.applyElementMod(dmg, 1.2); // 30
        dmg = CombatMath.applyCrit(dmg, /*crit*/true, 1.5); // 45
        assertEquals(45, dmg);
    }
}
