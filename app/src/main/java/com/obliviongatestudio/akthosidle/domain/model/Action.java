package com.obliviongatestudio.akthosidle.domain.model;

import java.util.Map;

public class Action {
    public String id;               // "mine_copper"
    public String name;             // "Mine Copper"
    public SkillId skill;           // MINING
    public int durationMs;          // 3000
    public int exp;                 // 8
    public Map<String,Integer> outputs; // {"ore_copper":1}
    public int reqLevel;            // 1
    public String region;           // optional gate
}
