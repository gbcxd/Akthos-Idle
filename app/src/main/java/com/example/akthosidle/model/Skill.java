package com.example.akthosidle.model;

/** Simple skill record: level + experience. */
public class Skill {
    public int level;
    public int exp;
    public int xp;

    public Skill() {
        this(1, 0);
    }

    public Skill(int level, int exp) {
        this.level = level;
        this.exp = exp;
    }

    /** Optional helper to add XP and handle simple level-ups. */
    public void addXp(int amount) {
        if (amount <= 0) return;
        this.exp += amount;
        while (this.exp >= xpToLevel(this.level)) {
            this.exp -= xpToLevel(this.level);
            this.level++;
        }
    }

    private int xpToLevel(int lvl) {
        // Simple curve; tweak as desired
        return 50 + (lvl * 25);
    }
}
