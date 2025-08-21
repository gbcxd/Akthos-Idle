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
import com.example.akthosidle.engine.CombatEngine;

import java.util.List;

/**
 * App-scoped gameplay view model:
 * - Owns GameRepository and CombatEngine (single instance per activity scope).
 * - Exposes battle state, combat log, and pending loot to the UI.
 * - Offers simple actions: start/stop/toggle fight, equip/unequip, collect loot, etc.
 */
public class GameViewModel extends AndroidViewModel {

    public final GameRepository repo;      // public so adapters can read defs (e.g., items map)
    private final CombatEngine engine;

    // Optional UI toggle (wire up later if desired)
    private final MutableLiveData<Boolean> autoRespawn = new MutableLiveData<>(false);

    public GameViewModel(@NonNull Application app) {
        super(app);

        // Repository
        repo = new GameRepository(app.getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();

        // Combat engine
        engine = new CombatEngine(repo);
        // engine.setRandomSeed(1234); // deterministic damage while debugging (optional)
        // engine.setDebugToasts(true); // toast combat log lines (optional)
    }

    // ===== Exposed LiveData =====
    public LiveData<CombatEngine.BattleState> battleState() { return engine.state(); }
    public LiveData<List<String>> combatLog() { return engine.log(); }
    public LiveData<List<InventoryItem>> pendingLoot() { return repo.pendingLootLive; }

    public LiveData<Boolean> autoRespawn() { return autoRespawn; }
    public void setAutoRespawn(boolean enabled) { autoRespawn.setValue(enabled); }

    // ===== Player accessor =====
    public PlayerCharacter player() { return repo.loadOrCreatePlayer(); }

    // ===== Battle control =====
    public void startFight(String monsterId) { engine.start(monsterId); }
    public void stopFight() { engine.stop(); }
    public void toggleFight(String monsterId) {
        CombatEngine.BattleState s = engine.state().getValue();
        if (s != null && s.running) stopFight(); else startFight(monsterId);
    }

    // ===== Food helpers (FoodPickerDialog) =====
    public List<InventoryItem> getFoodItems() { return repo.getFoodItems(); }
    public void consumeFood(String foodId) { repo.consumeFood(foodId); }
    public String getQuickFoodId() { return repo.loadOrCreatePlayer().getQuickFoodId(); }
    public void setQuickFoodId(String id) { repo.loadOrCreatePlayer().setQuickFoodId(id); repo.save(); }

    // ===== Potions / Syrup helpers =====
    /** Returns potions filtered by type: set one or both flags. */
    public List<InventoryItem> getPotions(boolean combatOnly, boolean nonCombatOnly) {
        return repo.getPotions(combatOnly, nonCombatOnly);
    }

    /** Consume a specific potion by id. */
    public void consumePotion(String potionId) { repo.consumePotion(potionId); }

    /** Back-compat alias for UIs that call usePotion(). */
    public void usePotion(String potionId) { consumePotion(potionId); }

    /** Use one “syrup” item (heal if it has heal, else apply the small speed buff). */
    public void useSyrup() { repo.consumeSyrup(); }

    // ===== Equipment helpers (InventoryFragment) =====
    /** Equip the given itemId if it has a valid slot and exists in the bag. */
    public boolean equip(String itemId) { return repo.equip(itemId); }

    /** Unequip whatever is in the given slot (returns to bag). */
    public boolean unequip(EquipmentSlot slot) { return repo.unequip(slot); }

    // ===== Loot =====
    public void collectLoot() { repo.collectPendingLoot(); }

    @Override
    protected void onCleared() {
        try { engine.stop(); } catch (Throwable ignored) {}
        super.onCleared();
    }
}
