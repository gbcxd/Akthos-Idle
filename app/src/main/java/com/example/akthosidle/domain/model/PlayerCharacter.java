package com.example.akthosidle.domain.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;


/** Player save model: stats, skills, equipment, inventory, and quick selections. */
public class PlayerCharacter {

    // ---- Identity & progression ----
    public String name = "Hero";
    public int level = 1;
    public int exp = 0;

    // ---- Currency (legacy + new) ----
    /** Legacy main currency (kept for compatibility). */
    public long gold = 0;

    /** New: flexible currency balances (e.g., silver, gold, slayer, etc.) */
    public Map<String, Long> currencies = new HashMap<>(); // id -> balance

    // ---- Base stats (augmented by gear & buffs) ----
    /** attack, defense, speed(+), health, critChance, critMultiplier */
    public Stats base = new Stats(10, 5, 0.25, 100, 0.05, 1.5);

    // ---- Health (persisted) ----
    public Integer currentHp; // initialized on first load to max HP

    // ---- Equipment & inventory ----
    /** Equipped item IDs by slot. */
    public Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
    /** Inventory: itemId -> quantity. */
    public Map<String, Integer> bag = new HashMap<>();

    // ---- Skills ----
    /** Initialized by repository if empty. */
    public Map<SkillId, Skill> skills = new EnumMap<>(SkillId.class);

    // ---- Quick selections ----
    private String quickFoodId;

    public PlayerCharacter() {}

    // ---------- Computed helpers ----------
    public Stats totalStats(Stats gearSum) { return Stats.add(base, gearSum); }

    // ---------- Inventory helpers ----------
    public void addItem(String itemId, int qty) {
        if (qty == 0) return;
        int now = bag.getOrDefault(itemId, 0) + qty;
        if (now <= 0) bag.remove(itemId); else bag.put(itemId, now);
    }

    // ---------- Currency helpers (new) ----------
    /** Get balance for a currency id (e.g., "silver", "gold", "slayer"). */
    public long getCurrency(String id) {
        if (id == null) return 0L;
        if ("gold".equals(id)) return Math.max(gold, currencies.getOrDefault("gold", 0L)); // legacy compat
        return currencies.getOrDefault(id, 0L);
    }

    /** Add (or subtract) an amount to a currency (amount can be negative). */
    public void addCurrency(String id, long amount) {
        if (id == null || amount == 0) return;
        if ("gold".equals(id)) {
            gold = Math.max(0L, gold + amount); // keep legacy in sync
            currencies.put("gold", gold);
            return;
        }
        long cur = currencies.getOrDefault(id, 0L);
        long next = cur + amount;
        currencies.put(id, Math.max(0L, next));
    }

    /** Spend returns true if sufficient balance; does nothing and returns false otherwise. */
    public boolean spendCurrency(String id, long amount) {
        if (id == null || amount <= 0) return true;
        long cur = getCurrency(id);
        if (cur < amount) return false;
        addCurrency(id, -amount);
        return true;
    }

    /** Optional: one-time call after load to mirror legacy gold into the map. */
    public void normalizeCurrencies() {
        long mappedGold = currencies.getOrDefault("gold", 0L);
        if (mappedGold != gold) {
            currencies.put("gold", gold);
        }
    }

    // ---------- Quick Food ----------
    public String getQuickFoodId() { return quickFoodId; }
    public void setQuickFoodId(String quickFoodId) { this.quickFoodId = quickFoodId; }

    public int heal(int amount, int maxHp) {
        if (amount <= 0) return 0;
        int cur = currentHp == null ? maxHp : currentHp;
        int newHp = Math.min(maxHp, Math.max(0, cur) + amount);
        int healed = newHp - cur;
        currentHp = newHp;
        return healed;
    }

    /** Get-or-create the Skill object for a given id. */
    public Skill skill(SkillId id) {
        Skill s = skills.get(id);
        if (s == null) {
            s = new Skill(); // level 1, 0 xp
            skills.put(id, s);
        }
        return s;
    }

    /** Convenience: current level for a skill (always >=1). */
    public int getSkillLevel(SkillId id) {
        return skill(id).level;
    }

    /** Add XP to a skill and return whether it leveled up. */
    public boolean addSkillExp(SkillId id, int amount) {
        return skill(id).addXp(amount);
    }
}
