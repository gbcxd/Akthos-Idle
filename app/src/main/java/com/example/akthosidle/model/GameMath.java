package com.example.akthosidle.model;

public final class GameMath {
    private GameMath() {}

    // Base 4.0s per attack; faster with ATTACK level and gear speed
    // ATTACK level: -1% per level beyond 1 (i.e., Lv1 = 0%)
    // Gear speed stat (Stats.speed): interpret as 0..+? additive bonus (e.g., 0.20 = 20% faster)
    public static double attackIntervalSeconds(PlayerCharacter pc, Stats gearSum) {
        double base = 4.0;
        int atkLv = pc.skill(SkillId.ATTACK).level;
        double levelBonus = Math.max(0.0, (atkLv - 1) * 0.01);       // 1% per level
        double gearBonus = Math.max(0.0, Math.min(0.6, gearSum.speed)); // cap 60% from gear
        double mult = (1.0 - levelBonus) * (1.0 - gearBonus);
        double seconds = base * mult;
        return Math.max(0.6, seconds); // floor
    }

    // Max HP scales with HP skill
    public static int maxHp(PlayerCharacter pc, Stats gearSum) {
        int hpLv = pc.skill(SkillId.HP).level;
        int baseMax = pc.totalStats(gearSum).health;
        return baseMax + (hpLv - 1) * 5; // +5 per HP level above 1
    }

    // Character level XP requirement (simple curve)
    public static int reqExpChar(int level) {
        return 50 + level * 25;
    }

    // Skill XP requirement (slightly steeper)
    public static int reqExpSkill(int level) {
        return 30 + (int)Math.round(level * 20 * 1.10);
    }
}
