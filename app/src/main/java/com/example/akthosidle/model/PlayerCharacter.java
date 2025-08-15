package com.example.akthosidle.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Player save model: stats, skills, equipment, inventory, and quick selections.
 * Gson-friendly (no special adapters needed).
 */
public class PlayerCharacter {

    // ---- Identity & progression ----
    public String name = "Hero";
    /** Overall character level & XP (separate from skill levels). */
    public int level = 1;
    public int exp = 0;

    // ---- Base stats (augmented by gear & buffs) ----
    /** attack, defense, speed (as +% attack speed), health, critChance, critMultiplier */
    public Stats base = new Stats(10, 5, 0.25, 100, 0.05, 1.5);

    // ---- Health (persistent current HP; max is derived from base+gear+skills) ----
    public Integer currentHp; // initialized on first load to max HP

    // ---- Equipment & inventory ----
    /** Equipped item IDs by slot. */
    public Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
    /** Inventory: itemId -> quantity. */
    public Map<String, Integer> bag = new HashMap<>();

    // ---- Skills ----
    /** All skills start at level 1 with 0 XP (initialized in repository if empty). */
    public Map<SkillId, Skill> skills = new EnumMap<>(SkillId.class);

    // ---- Quick selections (UI convenience) ----
    /** Selected food itemId for quick-consume (nullable). */
    private String quickFoodId;

    public PlayerCharacter() {
        // Fields with defaults are already initialized above.
        // currentHp and quickFoodId remain null until first setup/selection.
    }

    // ---------- Computed helpers ----------

    /** Total stats = base + gear stats (pass repo.gearStats(player) here). */
    public Stats totalStats(Stats gearSum) {
        return Stats.add(base, gearSum);
    }

    /** Ensure a skill exists and return it. */
    public Skill skill(SkillId id) {
        Skill s = skills.get(id);
        if (s == null) {
            s = new Skill(1, 0);
            skills.put(id, s);
        }
        return s;
    }

    // ---------- Inventory helpers ----------

    /** Add/remove items to bag. Negative qty removes; removes entry if qty <= 0. */
    public void addItem(String itemId, int qty) {
        if (qty == 0) return;
        int now = bag.getOrDefault(itemId, 0) + qty;
        if (now <= 0) bag.remove(itemId); else bag.put(itemId, now);
    }

    // ---------- Quick Food getters/setters ----------

    public String getQuickFoodId() {
        return quickFoodId;
    }

    public void setQuickFoodId(String quickFoodId) {
        this.quickFoodId = quickFoodId;
    }
}
