package com.example.akthosidle.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.akthosidle.domain.CombatEngine;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.EquipmentSlot;
import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.domain.model.Item;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.Skill;
import com.example.akthosidle.domain.model.SkillId;

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

    // ---------- Skills ----------
    /** XP needed for the next level. Keep this in sync with your game design. */
    public int skillReqXp(int level) {
        // Simple baseline curve; adjust as desired
        return 50 + (level * 25);
    }

    /** Award XP to a skill and handle level-ups, then persist. */
    public void addSkillXp(SkillId id, int amount) {
        if (amount <= 0) return;
        PlayerCharacter pc = player();
        Skill s = pc.skill(id);
        s.xp += amount;
        while (s.xp >= skillReqXp(s.level)) {
            s.xp -= skillReqXp(s.level);
            s.level += 1;
        }
        repo.save();
    }

    // ---------- Loot (pending) ----------
    public LiveData<List<InventoryItem>> pendingLoot() { return repo.pendingLootLive; }

    public void collectLoot() {
        repo.collectPendingLoot(player());
    }
}
