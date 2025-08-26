package com.obliviongatestudio.akthosidle.domain.model;

import java.util.Map;

/** Item definition usable for equipment, consumables, or materials. */
public class Item {
    public String id;
    public String name;
    public String icon;
    /** "EQUIPMENT" | "CONSUMABLE" | "MATERIAL" */
    public String type;
    /** For equipment: must match EquipmentSlot enum name (e.g., "WEAPON"). */
    public String slot;
    public String rarity;
    /** Combat stats (used by equipment and certain consumables). */
    public Stats stats;

    /** Food heal amount if this is a consumable food. */
    public Integer heal;

    /** Optional skill buffs (e.g., {"ATTACK":3,"MINING":5}). */
    public Map<String, Integer> skillBuffs;
}
