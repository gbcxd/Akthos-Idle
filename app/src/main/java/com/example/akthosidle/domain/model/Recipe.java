package com.example.akthosidle.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Recipe {
    public String id;
    public String name;
    public SkillId skill;
    public int timeSec;
    public int reqLevel;
    public int xp;
    public String station;

    // Use RecipeIO here (not Entry / Map.Entry)
    public List<RecipeIO> inputs  = new ArrayList<>();
    public List<RecipeIO> outputs = new ArrayList<>();
}
