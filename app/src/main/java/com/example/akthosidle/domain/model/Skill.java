package com.example.akthosidle.domain.model;

/** Simple skill record: level + experience. */
public class Skill {
    public int level;
    public int exp;
    public int xp;

    public class Skill {
        public int level = 1;
        public int exp = 0;

        public int reqExp(int lvl) { // smooth curve, tweak later
            return 30 + (int)Math.round(25 * Math.pow(lvl, 1.35));
        }
        public boolean addExp(int x) {
            exp += x;
            boolean leveled = false;
            while (exp >= reqExp(level)) { exp -= reqExp(level); level++; leveled = true; }
            return leveled;
        }
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
