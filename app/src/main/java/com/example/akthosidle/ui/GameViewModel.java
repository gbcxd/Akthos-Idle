package com.example.akthosidle.ui;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.idlerpg.battle.CombatEngine;
import com.example.idlerpg.data.GameRepository;
import com.example.idlerpg.model.*;

public class GameViewModel extends AndroidViewModel {
    public final GameRepository repo;
    public final CombatEngine combat;

    public GameViewModel(@NonNull Application app) {
        super(app);
        repo = new GameRepository(app);
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();
        combat = new CombatEngine(repo);
    }

    public PlayerCharacter player() { return repo.loadOrCreatePlayer(); }
    public LiveData<CombatEngine.BattleState> battleState() { return combat.state(); }

    public void equip(String itemId) {
        Item it = repo.getItem(itemId);
        EquipmentSlot slot = repo.slotOf(it);
        if (it != null && slot != null) {
            PlayerCharacter p = repo.loadOrCreatePlayer();
            // move currently equipped item back to bag
            String prev = p.equipment.put(slot, it.id);
            if (prev != null) p.addItem(prev, 1);
            // consume one copy
            p.addItem(it.id, -1);
            repo.save();
        }
    }

    public void startFight(String monsterId) { combat.start(monsterId); }
    public void stopFight() { combat.stop(); }
}
