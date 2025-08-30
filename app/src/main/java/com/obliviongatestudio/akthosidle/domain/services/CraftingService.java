package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.Recipe;
import com.obliviongatestudio.akthosidle.domain.model.RecipeIO;

/** Simple crafting service that consumes inputs and yields outputs. */
public class CraftingService {

    public boolean canCraft(PlayerCharacter pc, Recipe recipe) {
        if (pc == null || recipe == null) return false;
        for (RecipeIO in : recipe.inputs) {
            int have = pc.bag.getOrDefault(in.id, 0);
            if (have < in.qty) return false;
        }
        return true;
    }

    public boolean craft(PlayerCharacter pc, Recipe recipe) {
        if (!canCraft(pc, recipe)) return false;
        for (RecipeIO in : recipe.inputs) {
            pc.addItem(in.id, -in.qty);
        }
        for (RecipeIO out : recipe.outputs) {
            pc.addItem(out.id, out.qty);
        }
        if (recipe.skill != null) {
            pc.addSkillExp(recipe.skill, recipe.xp);
        }
        return true;
    }
}
