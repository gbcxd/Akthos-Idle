package com.obliviongatestudio.akthosidle.domain.model;

import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PlayerCharacter {
    // --- persisted fields ---
    public Map<String, Integer> bag = new HashMap<>();
    public EnumMap<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);

    /** Stores XP per skill (not level). Old saves with small numbers will be migrated. */
    public EnumMap<SkillId, Integer> skills = new EnumMap<>(SkillId.class);

    /** Overall player XP (used by GameRepository.addPlayerExp). */
    public int exp = 0;

    public Map<String, Long> currencies = new HashMap<>();
    public Stats base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);
    public Integer currentHp;
    private String quickFoodId;

    // --- config ---
    private static final int MAX_LEVEL = 99;
    private static final double XP_BASE = 100.0;
    private static final double XP_GROWTH = 1.3;
    private static final int HP_PER_LEVEL = 10; // +10 Max HP per HP level over 1

    // ===== Quick food =====
    public @Nullable String getQuickFoodId() { return quickFoodId; }
    public void setQuickFoodId(@Nullable String id) { quickFoodId = id; }

    // ===== Currency helpers =====
    public void normalizeCurrencies() {
        if (!currencies.containsKey("gold"))   currencies.put("gold",   0L);
        if (!currencies.containsKey("silver")) currencies.put("silver", 0L);
    }
    public long getCurrency(String id) { return currencies.getOrDefault(id, 0L); }
    public void addCurrency(String id, long amount) { currencies.put(id, getCurrency(id) + amount); }
    public boolean spendCurrency(String id, long amount) {
        long have = getCurrency(id);
        if (have < amount) return false;
        currencies.put(id, have - amount);
        return true;
    }

    // ===== Inventory helpers =====
    public void addItem(String id, int qty) {
        if (id == null || qty == 0) return;
        bag.put(id, bag.getOrDefault(id, 0) + qty);
        if (bag.get(id) != null && bag.get(id) <= 0) bag.remove(id);
    }

    // ===== Skill XP system =====
    /** Sum of per-level costs to reach 'level'. level=1 -> 0 XP. */
    public static int xpToReachLevel(int level) {
        if (level <= 1) return 0;
        double sum = 0;
        for (int l = 1; l < level; l++) {
            sum += Math.floor(XP_BASE * Math.pow(XP_GROWTH, l - 1));
        }
        return (int) Math.floor(sum);
    }

    /** Given XP, returns the level (1..MAX_LEVEL). */
    public static int levelForExp(int xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpToReachLevel(level + 1)) level++;
        return level;
    }

    /** XP needed inside the current level (progress numerator). */
    public static int xpIntoLevel(int xp, int level) {
        return Math.max(0, xp - xpToReachLevel(level));
    }

    /** XP size of the current level (progress denominator). */
    public static int xpForNextLevel(int level) {
        return xpToReachLevel(level + 1) - xpToReachLevel(level);
    }

    /** Returns raw XP stored for this skill. */
    public int getSkillExp(SkillId id) {
        return skills.getOrDefault(id, 0);
    }

    /** Returns computed level from XP. */
    public int getSkillLevel(SkillId id) {
        return levelForExp(getSkillExp(id));
    }

    /**
     * Adds XP to a skill and returns true if at least 1 level was gained.
     */
    public boolean addSkillExp(SkillId id, int amount) {
        if (id == null || amount <= 0) return false;
        int oldXp = getSkillExp(id);
        int oldLevel = levelForExp(oldXp);
        int newXp = oldXp + amount;
        skills.put(id, newXp);

        int newLevel = levelForExp(newXp);
        boolean leveled = newLevel > oldLevel;

        // If HP skill leveled, clamping to new max happens in totalStats().
        return leveled;
    }

    // ===== Optional: overall player level helpers (reuse same curve) =====
    public int getPlayerLevel() { return levelForExp(exp); }
    public int getPlayerXpIntoLevel() { return xpIntoLevel(exp, getPlayerLevel()); }
    public int getPlayerXpForNextLevel() { return xpForNextLevel(getPlayerLevel()); }

    // ===== Stats aggregation (include HP per HP level) =====
    public Stats totalStats(Stats gear) {
        Stats out = new Stats(
                base.attack, base.defense, base.speed, base.health, base.critChance, base.critMultiplier
        );
        if (gear != null) {
            out.attack += gear.attack;
            out.defense += gear.defense;
            out.speed += gear.speed;
            out.health += gear.health;
            out.critChance += gear.critChance;
            out.critMultiplier = Math.max(out.critMultiplier, gear.critMultiplier);
        }

        // HP scales with the HP skill level.
        int hpLevel = getSkillLevel(SkillId.HP);
        if (hpLevel > 1) out.health += (hpLevel - 1) * HP_PER_LEVEL;

        // Optional: map combat skills into stats
        // out.attack  += Math.max(0, getSkillLevel(SkillId.ATTACK)  - 1);
        // out.defense += Math.max(0, getSkillLevel(SkillId.DEFENSE) - 1);

        // Clamp current HP to new max
        int maxHp = Math.max(1, out.health);
        if (currentHp == null) currentHp = maxHp;
        if (currentHp > maxHp) currentHp = maxHp;

        return out;
    }

    // ===== Migration: old saves that stored LEVEL instead of XP =====
    /** Call this once after loading from disk to convert level→xp for legacy saves. */
    public void migrateSkillsToXpIfNeeded() {
        if (skills == null) skills = new EnumMap<>(SkillId.class);
        EnumMap<SkillId, Integer> fixed = new EnumMap<>(SkillId.class);
        for (SkillId id : SkillId.values()) {
            int stored = skills.getOrDefault(id, 0);
            // Heuristic: if stored value looks like a level (1..99), convert to XP.
            if (stored > 0 && stored <= MAX_LEVEL && stored <= 99) {
                int asLevel = stored;
                int xp = xpToReachLevel(Math.max(1, asLevel));
                fixed.put(id, xp);
            } else {
                fixed.put(id, Math.max(0, stored)); // already XP
            }
        }
        skills = fixed;
    }

    // Snapshot struct for convenient access (level, xp, etc.)
    public class SkillProgress {
        public final SkillId id;
        public final int xp;
        public final int level;
        public final int xpInto;
        public final int xpForNext;

        SkillProgress(SkillId id, int xp, int level, int xpInto, int xpForNext) {
            this.id = id;
            this.xp = xp;
            this.level = level;
            this.xpInto = xpInto;
            this.xpForNext = xpForNext;
        }

        /** Adds XP to this skill and returns true if at least 1 level was gained. */
        public boolean addXp(int amount) {
            return PlayerCharacter.this.addSkillExp(id, amount);
        }
    }

    /** Convenience accessor used by older code: player.skill(id).level, etc. */
    public SkillProgress skill(SkillId id) {
        int xp = getSkillExp(id);
        int level = getSkillLevel(id);
        return new SkillProgress(
                id,
                xp,
                level,
                xpIntoLevel(xp, level),
                xpForNextLevel(level)
        );
    }
}
