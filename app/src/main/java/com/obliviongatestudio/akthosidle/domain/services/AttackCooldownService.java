package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.Stats;

/**
 * Calculates attack cooldown factoring equipment stats, potions, and buffs.
 * This is a placeholder; real implementation will consider more data sources.
 */
public class AttackCooldownService {
    /**
     * @param baseSeconds Base attack time in seconds.
     * @param gearStats   Combined stats from equipped items (may be null).
     * @param potionBonus Flat speed bonus from potions.
     * @param buffBonus   Flat speed bonus from temporary buffs.
     * @return Cooldown in seconds after all modifiers.
     */
    public double computeCooldown(double baseSeconds, Stats gearStats, double potionBonus, double buffBonus) {
        double modifier = 1.0;
        if (gearStats != null) modifier -= gearStats.speed;
        modifier -= potionBonus;
        modifier -= buffBonus;
        return Math.max(0.1, baseSeconds * modifier);
    }
}
