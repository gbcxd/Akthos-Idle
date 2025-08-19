package com.example.akthosidle.domain.model;

import java.util.List;

public class Monster {
    public String id;
    public String name;
    public Stats stats;

    public List<Drop> drops;

    // NEW: rewards used by CombatEngine.grantRewards(...)
    public int expReward;
    public int silverReward;
    public int goldReward;    // keep for IAP-less test drops if you want
    public int slayerReward;  // amount of "gold" item to add
    public int exp;

    public Monster() {}

    public Monster(String id, String name, Stats stats, List<Drop> drops, int expReward, int goldReward) {
        this.id = id;
        this.name = name;
        this.stats = stats;
        this.drops = drops;
        this.expReward = expReward;
        this.goldReward = goldReward;
    }
}
