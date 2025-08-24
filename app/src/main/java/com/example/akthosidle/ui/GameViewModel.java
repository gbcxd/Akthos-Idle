package com.example.akthosidle.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.EquipmentSlot;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.engine.CombatEngine;
import com.example.akthosidle.engine.ActionEngine;
import com.example.akthosidle.domain.model.Action;

import java.util.List;

public class GameViewModel extends AndroidViewModel {

    public final GameRepository repo;
    private final CombatEngine combatEngine;

    // NEW: single shared gather engine
    private final ActionEngine gatherEngine;

    private final MutableLiveData<Boolean> autoRespawn = new MutableLiveData<>(false);

    public GameViewModel(@NonNull Application app) {
        super(app);
        repo = new GameRepository(app.getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();

        combatEngine = new CombatEngine(repo);

        // Gather engine (shared) + try to restore ongoing loop
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
    public void consumeFood(String foodId) { repo.consumeFood(foodId); }
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

    public void stopGather() {
        gatherEngine.stop();
        repo.stopGathering();
    }

    @Override
    protected void onCleared() {
        try { combatEngine.stop(); } catch (Throwable ignored) {}
        try { gatherEngine.stop(); } catch (Throwable ignored) {}
        super.onCleared();
    }
}
