package com.obliviongatestudio.akthosidle.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.obliviongatestudio.akthosidle.data.dtos.InventoryItem;
import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.domain.model.Action;
import com.obliviongatestudio.akthosidle.domain.model.EquipmentSlot;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.Recipe;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;
import com.obliviongatestudio.akthosidle.domain.model.SlayerAssignment;
import com.obliviongatestudio.akthosidle.engine.ActionEngine;
import com.obliviongatestudio.akthosidle.engine.CombatEngine;

import java.util.List;

public class GameViewModel extends AndroidViewModel {

    public final GameRepository repo;              // keep public to avoid breaking your code
    private final CombatEngine combatEngine;
    private final ActionEngine gatherEngine;

    private final MutableLiveData<Boolean> autoRespawn = new MutableLiveData<>(false);

    public GameViewModel(@NonNull Application app) {
        super(app);
        repo = new GameRepository(app.getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();

        combatEngine = new CombatEngine(repo);

        // Shared gather engine + restore if needed
        gatherEngine = new ActionEngine(app.getApplicationContext(), repo);
        boolean restored = gatherEngine.restoreIfRunning();
        if (restored) {
            SkillId s = gatherEngine.getPersistedRunningSkill();
            if (s != null) repo.startGathering(s);
        }
    }

    // ===== Exposed LiveData =====
    public LiveData<CombatEngine.BattleState> battleState() { return combatEngine.state(); }
    public LiveData<List<String>> combatLog() { return combatEngine.log(); }
    public LiveData<List<InventoryItem>> pendingLoot() { return repo.pendingLootLive; }
    public LiveData<Boolean> autoRespawn() { return autoRespawn; }
    public LiveData<SlayerAssignment> slayer() { return repo.slayerLive; }   // NEW

    public void setAutoRespawn(boolean enabled) { autoRespawn.setValue(enabled); }

    // ===== Player accessor =====
    public PlayerCharacter player() { return repo.loadOrCreatePlayer(); }

    // ===== Battle control =====
    public void startFight(String monsterId) { combatEngine.start(monsterId); }
    public void stopFight() { combatEngine.stop(); }
    public void toggleFight(String monsterId) {
        CombatEngine.BattleState s = combatEngine.state().getValue();
        if (s != null && s.running) stopFight(); else startFight(monsterId);
    }

    // ===== Food & Potions =====
    public List<InventoryItem> getFoodItems() { return repo.getFoodItems(); }
    public void consumeFood(String foodId) {
        // Use 'this.player()' to call the GameViewModel's player() method
        PlayerCharacter currentPlayer = this.player(); // Corrected: use local player() method

        // getItem from the repo, which now delegates to ItemRepository
        Item foodItem = repo.getItem(foodId);

        // Check using .heal (singular)
        if (currentPlayer == null || foodItem == null || foodItem.heal == null || foodItem.heal <= 0) {
            // Log or toast if appropriate, e.g.,
            // repo.toast("Cannot use this item or it provides no healing.");
            return;
        }

        Integer currentQuantity = currentPlayer.bag.get(foodId);
        if (currentQuantity == null || currentQuantity <= 0) {
            // repo.toast("Out of " + repo.itemName(foodId));
            return;
        }

        int maxHp = currentPlayer.totalStats(repo.gearStats(currentPlayer)).health;
        int currentHpVal = (currentPlayer.currentHp == null) ? maxHp : currentPlayer.currentHp;

        if (currentHpVal >= maxHp && foodItem.heal > 0) { // Only prevent if it would heal
            repo.toast("Already at full health!");
            // Decide if you still consume the item. For now, let's say no if it's purely for healing.
            return;
        }

        // The actual consumption and HP update is now better handled in GameRepository.consumeFood
        // So, GameViewModel can just delegate the call.
        repo.consumeFood(foodId);

        // The lines below were for direct manipulation in ViewModel,
        // but it's cleaner if GameRepository's consumeFood handles these details
        // and updates its LiveData.

        // // 1. Consume the item (already done in repo.consumeFood)
         currentPlayer.bag.put(foodId, currentQuantity - 1);
         if (currentPlayer.bag.get(foodId) <= 0) {
            currentPlayer.bag.remove(foodId);
         }

        // // 2. Heal the player (already done in repo.consumeFood)
         int newHp = Math.min(maxHp, currentHpVal + foodItem.heal);

         if (newHp != currentHpVal) {
             currentPlayer.currentHp = newHp;
            repo.updatePlayerHp(newHp); // This should be triggered by repo.consumeFood internally
         }
         repo.updatePlayerInventory(currentPlayer.bag); // Also by repo.consumeFood
    }
    public String getQuickFoodId() { return repo.loadOrCreatePlayer().getQuickFoodId(); }
    public void setQuickFoodId(String id) { repo.loadOrCreatePlayer().setQuickFoodId(id); repo.save(); }

    public List<InventoryItem> getPotions(boolean combatOnly, boolean nonCombatOnly) {
        return repo.getPotions(combatOnly, nonCombatOnly);
    }
    public void consumePotion(String potionId) { repo.consumePotion(potionId); }
    public void usePotion(String potionId) { consumePotion(potionId); }
    public void useSyrup() { repo.consumeSyrup(); }

    // ===== Equipment =====
    public boolean equip(String itemId) { return repo.equip(itemId); }
    public boolean unequip(EquipmentSlot slot) { return repo.unequip(slot); }

    // ===== Loot =====
    public void collectLoot() { repo.collectPendingLoot(); }

    // ===== Gathering (shared engine) =====
    public void setGatherListener(ActionEngine.Listener l) { gatherEngine.setListener(l); }
    public void clearGatherListener() { gatherEngine.setListener(null); }
    public boolean isGatherRunning() { return gatherEngine.isRunning(); }
    public void startGather(Action a) {
        if (a == null) return;
        if (a.skill != null) repo.startGathering(a.skill);
        gatherEngine.startLoop(a);
    }

    // ===== Recipe =====
    public java.util.List<Recipe> getRecipesBySkill(SkillId skill) {
        return repo.getRecipesBySkill(skill);
    }

    public boolean canCraft(String recipeId, int times) {
        return repo.canCraft(recipeId, times);
    }

    public boolean craft(String recipeId, int times) {
        return repo.craft(recipeId, times);
    }


    public void stopGather() {
        gatherEngine.stop();
        repo.stopGathering();
    }

    // ===== Slayer helpers (UI-friendly pass-throughs) =====
    public SlayerAssignment rollNewSlayerTaskRandom(String regionId, boolean forceReplace) {
        return repo.rollNewSlayerTask(regionId, forceReplace);
    }
    public SlayerAssignment rollNewSlayerTaskForSelection(String regionId, String monsterId) {
        // requires the overload in GameRepository (see below)
        return repo.rollNewSlayerTask(regionId, monsterId);
    }
    public boolean abandonSlayerTask() { return repo.abandonSlayerTask(); }
    public boolean claimSlayerTaskIfComplete() { return repo.claimSlayerTaskIfComplete(); }

    @Override
    protected void onCleared() {
        try { combatEngine.stop(); } catch (Throwable ignored) {}
        try { gatherEngine.stop(); } catch (Throwable ignored) {}
        super.onCleared();
    }
}
