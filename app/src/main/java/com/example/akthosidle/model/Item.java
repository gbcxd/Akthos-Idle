package com.example.akthosidle.model;

public class Item {
    public String id;
    public String name;
    public String icon;         // drawable name
    public String type;         // EQUIPMENT | CONSUMABLE | MATERIAL
    public String slot;         // null unless EQUIPMENT
    public String rarity;       // COMMON/RARE/EPIC/etc
    public Stats stats;         // may be null
    public Integer heal;        // for consumables
}
