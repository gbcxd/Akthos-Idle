package com.obliviongatestudio.akthosidle.domain.model;

import java.util.List;

import com.obliviongatestudio.akthosidle.domain.model.Element;
import com.obliviongatestudio.akthosidle.domain.model.AiBehavior;

/** Game monster definition parsed from JSON. */
public class Monster {
    public String id;
    public String name;
    public Stats stats;          // Repo ensures default if null.
    /** Elemental affinity used for weakness/strength calculations. */
    public Element element = Element.NEUTRAL;
    /** Basic AI behavior mode. */
    public AiBehavior behavior = AiBehavior.AGGRESSIVE;

    public List<Drop> drops;     // Optional loot table

    // --- Rewards (ints; missing values in JSON default to 0) ---

    /** Preferred XP per kill. */
    public int exp;

    /** Legacy alias for XP per kill; used only if exp == 0. */
    public int expReward;

    /** Soft currency reward (optional in your data). */
    public int silverReward;

    /** Slayer “points” or similar (only grant if a Slayer task is active). */
    public int slayerReward;

    /** @deprecated Gold is premium; never awarded from monsters. Kept for legacy JSON only. */
    @Deprecated
    public int goldReward;

    public Monster() {
        // Needed for Gson
    }

    public Monster(String id, String name, Stats stats, List<Drop> drops,
                   int exp, int silverReward, int slayerReward) {
        this.id = id;
        this.name = name;
        this.stats = stats;
        this.drops = drops;
        this.exp = exp;
        this.silverReward = silverReward;
        this.slayerReward = slayerReward;
        normalize();
    }

    public Monster(String id, String name, Stats stats, List<Drop> drops,
                   int exp, int silverReward, int slayerReward, Element element) {
        this(id, name, stats, drops, exp, silverReward, slayerReward);
        this.element = element != null ? element : Element.NEUTRAL;
    }

    /** Back-compat ctor that used to pass expReward & goldReward. */
    public Monster(String id, String name, Stats stats, List<Drop> drops,
                   int expRewardLegacy, int goldRewardLegacy) {
        this(id, name, stats, drops, /*exp*/expRewardLegacy, /*silver*/0, /*slayer*/0);
        // keep legacy fields for completeness
        this.expReward = expRewardLegacy;
        this.goldReward = goldRewardLegacy;
        normalize();
    }

    /** Ensure sane defaults after construction/deserialization. */
    public void normalize() {
        if (name == null) name = id;
        if (stats == null) stats = new Stats();
        if (element == null) element = Element.NEUTRAL;
        if (behavior == null) behavior = AiBehavior.AGGRESSIVE;

        // If legacy field has value but new field doesn't, adopt it.
        if (exp <= 0 && expReward > 0) exp = expReward;

        // Clamp negatives (defensive)
        if (exp < 0) exp = 0;
        if (silverReward < 0) silverReward = 0;
        if (slayerReward < 0) slayerReward = 0;

        // Gold is premium → ignore any parsed value.
        goldReward = 0;
    }

    /** XP per kill (handles legacy expReward). */
    public int getExpPerKill() {
        return (exp > 0) ? exp : Math.max(0, expReward);
    }

    /** Silver per kill (never negative). */
    public int getSilverReward() {
        return Math.max(0, silverReward);
    }

    /**
     * Gold is never awarded from monsters.
     * Kept to avoid breaking old code; always returns 0.
     */
    public int getGoldReward() {
        return 0;
    }

    /** Slayer reward value (grant only if player has an active Slayer task). */
    public int getSlayerReward() {
        return Math.max(0, slayerReward);
    }

    public boolean hasDrops() {
        return drops != null && !drops.isEmpty();
    }

    /** Returns the monster's elemental affinity. */
    public Element getElement() {
        return element;
    }

    /** Sets the monster's elemental affinity. */
    public void setElement(Element element) {
        this.element = element != null ? element : Element.NEUTRAL;
    }

    /** Returns the monster's AI behavior mode. */
    public AiBehavior getBehavior() {
        return behavior;
    }

    /** Sets the monster's AI behavior mode. */
    public void setBehavior(AiBehavior behavior) {
        this.behavior = behavior != null ? behavior : AiBehavior.AGGRESSIVE;
    }
}
