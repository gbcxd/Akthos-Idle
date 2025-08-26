package com.obliviongatestudio.akthosidle.domain.rules;

/**
 * RuneScape-like exponential curve (fast early, ramps later).
 * Good enough for v1; replace with your final curve anytime without changing call sites.
 */
public final class LevelCurve {
    private LevelCurve() {}

    /** Total XP required to REACH a given level (level >= 1). */
    public static long expForLevel(int level) {
        if (level <= 1) return 0L;
        double sum = 0.0;
        for (int i = 1; i < level; i++) {
            sum += Math.floor(i + 300 * Math.pow(2, i / 7.0));
        }
        return (long) Math.floor(sum / 4.0);
    }

    /** Level for a given total XP (caps at 120 by default). */
    public static int levelForExp(long exp) {
        if (exp <= 0) return 1;
        int lvl = 1;
        while (lvl < 120 && expForLevel(lvl + 1) <= exp) {
            lvl++;
        }
        return lvl;
    }

    /** XP needed from current level to next level. */
    public static long expToNextLevel(int currentLevel, long currentExp) {
        long next = expForLevel(currentLevel + 1);
        return Math.max(0, next - currentExp);
    }
}
