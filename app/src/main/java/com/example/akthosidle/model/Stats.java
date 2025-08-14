package com.example.akthosidle.model;

public class Stats {
    public int attack;
    public int defense;
    public double speed; // attacks per second contribution (positive speeds up)
    public int health;
    public double critChance;
    public double critMultiplier;

    public Stats() {}
    public Stats(int atk, int def, double spd, int hp, double cc, double cm) {
        this.attack = atk; this.defense = def; this.speed = spd; this.health = hp;
        this.critChance = cc; this.critMultiplier = cm;
    }

    public static Stats add(Stats a, Stats b) {
        return new Stats(a.attack + b.attack, a.defense + b.defense,
                a.speed + b.speed, a.health + b.health,
                a.critChance + b.critChance, Math.max(a.critMultiplier, b.critMultiplier));
    }
}
