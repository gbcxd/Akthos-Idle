package com.example.akthosidle.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PlayerCharacter {
    public String name = "Hero";
    public int level = 1;
    public int exp = 0;
    public Stats base = new Stats(10, 5, 0.25, 100, 0.05, 1.5);

    // Equipment by slot: item id
    public Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);

    // Simple inventory: itemId -> qty
    public Map<String, Integer> bag = new HashMap<>();

    // Derived at runtime
    public Stats totalStats(Stats gearSum) {
        return Stats.add(base, gearSum);
    }

    public void addItem(String itemId, int qty) {
        bag.put(itemId, bag.getOrDefault(itemId, 0) + Math.max(qty, 0));
        if (bag.get(itemId) <= 0) bag.remove(itemId);
    }
}
