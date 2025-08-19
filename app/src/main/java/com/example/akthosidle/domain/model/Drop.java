package com.example.akthosidle.domain.model;

/** Loot drop entry for monsters. */
public class Drop {
    public String itemId;
    public int min;
    public int max;
    /** 0.0..1.0 chance per kill. */
    public double chance;

    public Drop() {} // For Gson

    public Drop(String itemId, int min, int max, double chance) {
        this.itemId = itemId;
        this.min = min;
        this.max = max;
        this.chance = chance;
    }
}
