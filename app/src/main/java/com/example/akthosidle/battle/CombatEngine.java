package com.example.akthosidle.battle;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.Drop;
import com.example.akthosidle.model.Monster;
import com.example.akthosidle.model.PlayerCharacter;
import com.example.akthosidle.model.Stats;

import java.util.Random;

public class CombatEngine {
    public static class BattleState {
        public String monsterId;
        public String monsterName;

        public int playerHp;
        public int playerMaxHp;
        public int monsterHp;
        public int monsterMaxHp;

        /** 0..1 progress toward next attack */
        public float playerAttackProgress;
        public float monsterAttackProgress;

        /** Current attack intervals (seconds) so the UI can label/sync */
        public float playerAttackInterval;
        public float monsterAttackInterval;

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

    // cache current intervals for progress calculation
    private double pAtkItv = 2.5, mAtkItv = 2.5;

    public CombatEngine(GameRepository repo) { this.repo = repo; }

    public LiveData<BattleState> state() { return state; }

    public void start(String monsterId) {
        this.pc = repo.loadOrCreatePlayer();
        this.monster = repo.getMonster(monsterId);
        if (monster == null) return;

        this.pStats = pc.totalStats(repo.gearStats(pc));
        this.mStats = monster.stats;

        BattleState s = new BattleState();
        s.monsterId = monsterId;
        s.monsterName = monster.name;

        s.playerMaxHp = Math.max(1, pStats.health);
        s.monsterMaxHp = Math.max(1, mStats.health);
        s.playerHp = Math.min(pc.currentHp != null ? pc.currentHp : s.playerMaxHp, s.playerMaxHp);
        s.monsterHp = s.monsterMaxHp;

        s.playerAttackProgress = 0f;
        s.monsterAttackProgress = 0f;
        s.playerAttackInterval = 0f;
        s.monsterAttackInterval = 0f;

        s.running = true;
        state.setValue(s);

        pTimer = mTimer = 0;
        loop();
    }

    public void stop() {
        BattleState s = state.getValue();
        if (s != null) {
            s.running = false;
            state.setValue(s);
            // persist player HP
            if (pc != null) {
                pc.currentHp = s.playerHp;
                repo.save();
            }
        }
    }

    private void loop() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;
        handler.postDelayed(this::tick, 16); // ~60 Hz
    }

    private void tick() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;

        double dt = 0.016; // 16 ms

        // attack intervals (base 2.5s, minus speed bonuses)
        pAtkItv = Math.max(0.6, 2.5 - pStats.speed * 2.5);
        mAtkItv = Math.max(0.6, 2.5 - mStats.speed * 2.5);

        pTimer += dt;
        mTimer += dt;

        // progress (0..1)
        s.playerAttackProgress = (float) Math.min(1.0, pTimer / pAtkItv);
        s.monsterAttackProgress = (float) Math.min(1.0, mTimer / mAtkItv);
        s.playerAttackInterval = (float) pAtkItv;
        s.monsterAttackInterval = (float) mAtkItv;

        if (pTimer >= pAtkItv && s.monsterHp > 0) {
            pTimer = 0;
            int dmg = damageRoll(pStats.attack, mStats.defense, pStats.critChance, pStats.critMultiplier);
            s.monsterHp = Math.max(0, s.monsterHp - dmg);
            s.playerAttackProgress = 0f;
        }
        if (mTimer >= mAtkItv && s.playerHp > 0) {
            mTimer = 0;
            int dmg = damageRoll(mStats.attack, pStats.defense, mStats.critChance, mStats.critMultiplier);
            s.playerHp = Math.max(0, s.playerHp - dmg);
            s.monsterAttackProgress = 0f;
        }

        // defeat/victory
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            if (s.monsterHp == 0) grantRewards(monster);
            if (pc != null) pc.currentHp = s.playerHp;
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

    private void grantRewards(PlayerCharacter pc, Monster m) {
        // XP / gold
        pc.exp += m.expReward;
        pc.addItem("gold", m.goldReward);

        // --- DROPS ---
        for (Drop d : m.drops) {
            if (rng.nextDouble() <= d.chance) {
                int qty = d.min + rng.nextInt(Math.max(1, d.max - d.min + 1));
                Item def = repo.getItem(d.itemId);
                String name = def != null ? def.name : d.itemId;
                repo.addPendingLoot(d.itemId, name, qty);
            }
        }

        repo.save();
    }


    private int reqExp(int lvl) {
        return 50 + (lvl * 25);
    }
}
