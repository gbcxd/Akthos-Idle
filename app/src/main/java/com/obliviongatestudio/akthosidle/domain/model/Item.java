package com.obliviongatestudio.akthosidle.domain.model;

import java.util.Map;

public class Item {
    public String id;
    public String name;
    public String type; // e.g. "WEAPON", "CONSUMABLE", "RESOURCE"
    public String icon; // drawable name
    public String slot; // e.g. "WEAPON", "HEAD", "CHEST" (for EquipmentSlot)
    public String rarity;

    // This is the critical field for healing. Ensure it's singular and public.
    public Integer heal; // <-- MAKE SURE THIS IS `heal` (singular) and public

    public Stats stats;
    public Map<String, Integer> skillBuffs; // e.g. {"MINING": 5, "ATTACK": 2}

    // Default constructor is fine for Gson
    public Item() {}

    // You can add getters if you prefer, but public fields work with Gson and for direct access
    // public Integer getHeal() { return heal; }
}
