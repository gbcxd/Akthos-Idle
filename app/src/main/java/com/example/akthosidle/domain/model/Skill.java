package com.example.akthosidle.domain.model;

public class Skill {
    public int level;
    public int exp;
    public int xp;

    public Skill() {
        this.level = 1;
        this.exp = 0;
    }

    public Skill(int level, int exp) {
        this.level = level;
        this.exp = exp;
    }

    /**
     * Adds exp and returns true if a level-up happened.
     */
    public boolean addExp(int amount) {
        boolean leveledUp = false;
        this.exp += amount;

        // Keep checking in case multiple levels are gained at once
        while (this.exp >= requiredXpForLevel(this.level)) {
            this.exp -= requiredXpForLevel(this.level);
            this.level++;
            leveledUp = true;
        }

        return leveledUp;
    }

    /**
     * Exponential XP curve (like RuneScape-style).
     * Example: base 100, grows ~1.2x each level.
     */
    private int requiredXpForLevel(int lvl) {
        // Formula: base * (growth ^ (lvl-1))
        return (int) Math.floor(100 * Math.pow(1.2, lvl - 1));
    }
}
