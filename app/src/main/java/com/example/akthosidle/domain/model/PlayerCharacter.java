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

    // ---- Currency ----
    /** Main currency. Keep as a simple counter, not an inventory item. */
    public long gold = 0;

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

    public Skill skill(SkillId id) {
        Skill s = skills.get(id);
        if (s == null) { s = new Skill(1, 0); skills.put(id, s); }
        return s;
    }

    // ---------- Inventory helpers ----------
    public void addItem(String itemId, int qty) {
        if (qty == 0) return;
        int now = bag.getOrDefault(itemId, 0) + qty;
        if (now <= 0) bag.remove(itemId); else bag.put(itemId, now);
    }

    // ---------- Quick Food ----------
    public String getQuickFoodId() { return quickFoodId; }
    public void setQuickFoodId(String quickFoodId) { this.quickFoodId = quickFoodId; }
}
