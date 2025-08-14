package com.example.akthosidle.battle;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.*;

import java.util.Random;

public class CombatEngine {
    public static class BattleState {
        public String monsterId;
        public int playerHp;
        public int monsterHp;
        public boolean running;
    }

    private final GameRepository repo;
    private final MutableLiveData<BattleState> state = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();

    // internal
    private double pTimer = 0, mTimer = 0; // seconds
    private Stats pStats, mStats;
    private Monster monster;
    private PlayerCharacter pc;

    public CombatEngine(GameRepository repo) { this.repo = repo; }

    public LiveData<BattleState> state() { return state; }

    public void start(String monsterId) {
        this.pc = repo.loadOrCreatePlayer();
        this.monster = repo.getMonster(monsterId);
        this.pStats = pc.totalStats(repo.gearStats(pc));
        this.mStats = monster.stats;

        BattleState s = new BattleState();
        s.monsterId = monsterId;
        s.playerHp = Math.max(1, pStats.health);
        s.monsterHp = Math.max(1, mStats.health);
        s.running = true;
        state.setValue(s);

        pTimer = mTimer = 0;
        loop();
    }

    public void stop() {
        BattleState s = state.getValue();
        if (s != null) { s.running = false; state.setValue(s); }
    }

    private void loop() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;

        // 60 Hz tick
        handler.postDelayed(this::tick, 16);
    }

    private void tick() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;

        double dt = 0.016; // 16 ms

        // attack intervals (base 2.5s, minus speed bonuses)
        double pAttackInterval = Math.max(0.6, 2.5 - pStats.speed * 2.5);
        double mAttackInterval = Math.max(0.6, 2.5 - mStats.speed * 2.5);

        pTimer += dt;
        mTimer += dt;

        if (pTimer >= pAttackInterval && s.monsterHp > 0) {
            pTimer = 0;
            int dmg = damageRoll(pStats.attack, mStats.defense, pStats.critChance, pStats.critMultiplier);
            s.monsterHp = Math.max(0, s.monsterHp - dmg);
        }
        if (mTimer >= mAttackInterval && s.playerHp > 0) {
            mTimer = 0;
            int dmg = damageRoll(mStats.attack, pStats.defense, mStats.critChance, mStats.critMultiplier);
            s.playerHp = Math.max(0, s.playerHp - dmg);
        }

        // victory/defeat handling
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            // quick rewards if victory
            if (s.monsterHp == 0) grantRewards(monster);
            repo.save();
            state.setValue(s);
            return;
        }

        state.setValue(s);
        loop();
    }

    private int damageRoll(int atk, int def, double critC, double critM) {
        int base = Math.max(1, atk - (int)(def * 0.6));
        int roll = (int)Math.round(base * (0.85 + rng.nextDouble()*0.3)); // ±15%
        boolean crit = rng.nextDouble() < critC;
        if (crit) roll = (int)Math.round(roll * Math.max(1.25, critM));
        return Math.max(1, roll);
    }

    private void grantRewards(Monster m) {
        // exp
        pc.exp += m.exp;
        // simple level up
        while (pc.exp >= reqExp(pc.level)) {
            pc.exp -= reqExp(pc.level);
            pc.level += 1;
            pc.base.health += 5;
            pc.base.attack += 2;
            pc.base.defense += 1;
        }
        // drops
        for (Drop d : m.drops) {
            if (rng.nextDouble() <= d.chance) {
                int qty = d.min + rng.nextInt(Math.max(1, d.max - d.min + 1));
                pc.addItem(d.itemId, qty);
            }
        }
    }

    private int reqExp(int lvl) {
        return 50 + (lvl * 25); // tweak later
    }
}
