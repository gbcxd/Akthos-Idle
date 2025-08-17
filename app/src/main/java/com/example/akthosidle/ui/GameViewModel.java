package com.example.akthosidle.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.akthosidle.battle.CombatEngine;
import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.EquipmentSlot;
import com.example.akthosidle.model.InventoryItem;
import com.example.akthosidle.model.Item;
import com.example.akthosidle.model.PlayerCharacter;

import java.util.List;

/**
 * Central app state for Character/Inventory/Battle.
 * Delegates persistence to GameRepository and the combat loop to CombatEngine.
 */
public class GameViewModel extends AndroidViewModel {
    public final GameRepository repo;
    private final CombatEngine engine;

    public GameViewModel(@NonNull Application app) {
        super(app);
        repo = new GameRepository(app.getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();
        engine = new CombatEngine(repo);
    }

    // ---------- Character / Inventory ----------
    public PlayerCharacter player() { return repo.loadOrCreatePlayer(); }

    public void equip(String itemId) {
        PlayerCharacter pc = player();
        Item it = repo.getItem(itemId);
        if (it == null) return;
        EquipmentSlot slot = repo.slotOf(it);
        if (slot == null) return;

        // Unequip previous (return to bag)
        String prev = pc.equipment.get(slot);
        if (prev != null) pc.addItem(prev, 1);

        // Equip new, remove from bag
        pc.equipment.put(slot, itemId);
        Integer have = pc.bag.get(itemId);
        if (have != null && have > 0) pc.bag.put(itemId, have - 1);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) pc.bag.remove(itemId);

        repo.save();
    }

    // ---------- Food Picker ----------
    public List<InventoryItem> getFoodItems() { return repo.getFoodItems(); }

    public void setQuickFood(String itemId) {
        PlayerCharacter pc = player();
        pc.setQuickFoodId(itemId);   // use accessor
        repo.save();
    }

    // ---------- Potions Picker ----------
    public List<InventoryItem> getPotions(boolean combatOnly, boolean nonCombatOnly) {
        return repo.getPotions(combatOnly, nonCombatOnly);
    }

    public void usePotion(String potionId) {
        repo.consumePotion(potionId);
        repo.save();
    }

    public void useSyrup() {
        repo.consumeSyrup();
        repo.save();
    }

    // ---------- Battle ----------
    public LiveData<CombatEngine.BattleState> battleState() { return engine.state(); }
    public void startFight(String monsterId) { engine.start(monsterId); }
    public void stopFight() { engine.stop(); }

    /** Convenience for a single Start/Stop button. */
    public void toggleFight(String monsterId) {
        CombatEngine.BattleState s = engine.state().getValue();
        if (s != null && s.running) engine.stop();
        else engine.start(monsterId);
    }
}
