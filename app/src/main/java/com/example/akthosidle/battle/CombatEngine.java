package com.example.akthosidle.battle;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.Drop;
import com.example.akthosidle.model.GameMath;
import com.example.akthosidle.model.Monster;
import com.example.akthosidle.model.PlayerCharacter;
import com.example.akthosidle.model.Stats;

import java.util.Random;

public class CombatEngine {
    public static class BattleState {
        public String monsterId;
        public int playerHp;
        public int monsterHp;
        public boolean running;

        // NEW: 0..1 fill for the player's attack progress bar
        public double playerAttackProgress;
    }

    private final GameRepository repo;
    private final MutableLiveData<BattleState> state = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();

    // internal
    private double pTimer = 0, mTimer = 0; // seconds since last attack
    private double pAttackInterval = 4.0, mAttackInterval = 2.5;
    private Stats pStats, mStats;
    private Monster monster;
    private PlayerCharacter pc;

    public CombatEngine(GameRepository repo) { this.repo = repo; }

    public LiveData<BattleState> state() { return state; }

    public void start(String monsterId) {
        this.pc = repo.loadOrCreatePlayer();
        this.monster = repo.getMonster(monsterId);

        // compute stats
        Stats gear = repo.gearStats(pc);
        this.pStats = pc.totalStats(gear);
        this.mStats = monster.stats;

        // intervals (player uses skill+gear formula; monster still uses old speed rule)
        this.pAttackInterval = GameMath.attackIntervalSeconds(pc, gear); // base 4.0s, faster with ATTACK level + gear speed
        this.mAttackInterval = Math.max(0.6, 2.5 - mStats.speed * 2.5);

        // HP init
        int maxPlayerHp = GameMath.maxHp(pc, gear);
        if (pc.currentHp == null) pc.currentHp = maxPlayerHp;

        BattleState s = new BattleState();
        s.monsterId = monsterId;
        s.playerHp = clamp(pc.currentHp, 1, maxPlayerHp);
        s.monsterHp = Math.max(1, mStats.health);
        s.running = true;
        s.playerAttackProgress = 0.0;
        state.setValue(s);

        pTimer = mTimer = 0;
        loop();
    }

    public void stop() {
        BattleState s = state.getValue();
        if (s != null) {
            s.running = false;
            // persist current HP on stop
            pc.currentHp = s.playerHp;
            repo.save();
            state.setValue(s);
        }
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

        double dt = 0.016; // 16 ms @ ~60Hz

        pTimer += dt;
        mTimer += dt;

        // Player attacks
        if (pTimer >= pAttackInterval && s.monsterHp > 0) {
            pTimer = 0;
            int dmg = damageRoll(pStats.attack, mStats.defense, pStats.critChance, pStats.critMultiplier);
            s.monsterHp = Math.max(0, s.monsterHp - dmg);
        }

        // Monster attacks
        if (mTimer >= mAttackInterval && s.playerHp > 0) {
            mTimer = 0;
            int dmg = damageRoll(mStats.attack, pStats.defense, mStats.critChance, mStats.critMultiplier);
            s.playerHp = Math.max(0, s.playerHp - dmg);
        }

        // publish player attack progress (0..1)
        s.playerAttackProgress = Math.max(0.0, Math.min(1.0, pTimer / pAttackInterval));

        // victory/defeat handling
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            if (s.monsterHp == 0) grantRewards(monster);
            // persist current HP
            pc.currentHp = s.playerHp;
            repo.save();
            state.setValue(s);
            return;
        }

        // keep current HP persisted during battle so process death doesn’t lose state
        pc.currentHp = s.playerHp;
        repo.save();

        state.setValue(s);
        loop();
    }

    private int damageRoll(int atk, int def, double critC, double critM) {
        int base = Math.max(1, atk - (int) (def * 0.6));
        int roll = (int) Math.round(base * (0.85 + rng.nextDouble() * 0.3)); // ±15%
        boolean crit = rng.nextDouble() < critC;
        if (crit) roll = (int) Math.round(roll * Math.max(1.25, critM));
        return Math.max(1, roll);
    }

    private void grantRewards(Monster m) {
        // Character XP (you can swap to a skill XP system later)
        pc.exp += m.exp;
        while (pc.exp >= GameMath.reqExpChar(pc.level)) {
            pc.exp -= GameMath.reqExpChar(pc.level);
            pc.level += 1;
            // small base stat bumps on character level
            pc.base.health += 5;
            pc.base.attack += 2;
            pc.base.defense += 1;
        }

        // Drops
        for (Drop d : m.drops) {
            if (rng.nextDouble() <= d.chance) {
                int qty = d.min + rng.nextInt(Math.max(1, d.max - d.min + 1));
                pc.addItem(d.itemId, qty);
            }
        }
    }

    private int clamp(Integer v, int min, int max) {
        if (v == null) return min;
        return Math.max(min, Math.min(max, v));
    }
}
