package com.obliviongatestudio.akthosidle.domain.services;

import static org.junit.Assert.*;

import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.Recipe;
import com.obliviongatestudio.akthosidle.domain.model.RecipeIO;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;

import org.junit.Test;

public class CraftingServiceTest {
    @Test
    public void craftConsumesInputsAndGrantsOutput() {
        PlayerCharacter pc = new PlayerCharacter();
        pc.addItem("log", 2);

        Recipe r = new Recipe();
        r.inputs.add(new RecipeIO("log", 2));
        r.outputs.add(new RecipeIO("plank", 1));
        r.skill = SkillId.CRAFTING;
        r.xp = 5;

        CraftingService svc = new CraftingService();
        assertTrue(svc.craft(pc, r));
        assertEquals(0, pc.bag.getOrDefault("log", 0).intValue());
        assertEquals(1, pc.bag.getOrDefault("plank", 0).intValue());
        assertEquals(5, pc.getSkillExp(SkillId.CRAFTING));
    }

    @Test
    public void craftFailsWithoutMaterials() {
        PlayerCharacter pc = new PlayerCharacter();
        Recipe r = new Recipe();
        r.inputs.add(new RecipeIO("log", 1));
        CraftingService svc = new CraftingService();
        assertFalse(svc.craft(pc, r));
    }
}
