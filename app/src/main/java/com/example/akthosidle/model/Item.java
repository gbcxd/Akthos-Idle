package com.example.akthosidle.model;

import java.util.Map;

public class Item {
    public String id;
    public String name;
    public String icon;
    public String type;          // EQUIPMENT | CONSUMABLE | MATERIAL
    public String slot;          // now matches EquipmentSlot names above (or legacy; see repo)
    public String rarity;
    public Stats stats;          // combat stats

    public Integer heal;         // if CONSUMABLE food

    // NEW: optional skill buffs from gear (percent or flat, your choice)
    // Example JSON:
    // "skillBuffs": { "ATTACK": 3, "MINING": 5 }
    public Map<String, Integer> skillBuffs;
}
