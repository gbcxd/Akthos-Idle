package com.example.akthosidle.model;

import java.util.List;

/** Monster definition used by the CombatEngine. */
public class Monster {
    public String id;
    public String name;
    public Stats stats;
    public int exp;
    public List<Drop> drops;
}
