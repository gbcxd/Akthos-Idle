package com.example.akthosidle.domain.model;

/**
 * Combat stats.
 * Constructor order: (atk, def, spd, hp, critChance, critMultiplier)
 */
public class Stats {
    public int attack;
    public int defense;
    public double speed;        // attacks-per-second contribution (positive speeds up)
    public int health;
    public double critChance;
    public double critMultiplier;

    public Stats() {}

    public Stats(int atk, int def, double spd, int hp, double cc, double cm) {
        this.attack = atk;
        this.defense = def;
        this.speed = spd;
        this.health = hp;
        this.critChance = cc;
        this.critMultiplier = cm;
    }

    /** Pure sum; critMultiplier uses the larger (common RPG approach). */
    public static Stats add(Stats a, Stats b) {
        if (a == null && b == null) return new Stats();
        if (a == null) return new Stats(b.attack, b.defense, b.speed, b.health, b.critChance, b.critMultiplier);
        if (b == null) return new Stats(a.attack, a.defense, a.speed, a.health, a.critChance, a.critMultiplier);
        return new Stats(
                a.attack + b.attack,
                a.defense + b.defense,
                a.speed + b.speed,
                a.health + b.health,
                a.critChance + b.critChance,
                Math.max(a.critMultiplier, b.critMultiplier)
        );
    }
}
