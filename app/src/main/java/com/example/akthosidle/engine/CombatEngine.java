package com.example.akthosidle.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Drop;
import com.example.akthosidle.domain.model.Item;
import com.example.akthosidle.domain.model.Monster;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.SkillId;   // ← import SkillId
import com.example.akthosidle.domain.model.Stats;

import java.util.ArrayList;
import java.util.List;
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

        // 🔥 Burn status (on monster)
        public int burnStacksOnMonster;
        public float burnRemainingSec;

        public boolean running;
    }

    private static class DamageResult {
        final int dmg; final boolean crit;
        DamageResult(int d, boolean c){ this.dmg=d; this.crit=c; }
    }

    private final GameRepository repo;
    private final MutableLiveData<BattleState> state = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();

    // Simple observable combat log
    private final MutableLiveData<List<String>> logLive = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<String>> log() { return logLive; }
    private boolean debugToasts = false;
    public void setDebugToasts(boolean enabled) { this.debugToasts = enabled; }

    // internal timers (seconds)
    private double pTimer = 0, mTimer = 0;
    private long lastTickMs = 0L;

    private Stats pStats, mStats;
    private Monster monster;
    private PlayerCharacter pc;

    // cache current intervals (seconds) for progress calculation
    private double pAtkItv = 2.5, mAtkItv = 2.5;

    // 🔥 Burn constants (tune as desired)
    private static final double BURN_APPLY_CHANCE = 0.25; // 25% per player hit
    private static final int    BURN_MAX_STACKS   = 5;
    private static final double BURN_DURATION_SEC = 5.0;
    private static final int    BURN_DMG_PER_TICK_PER_STACK = 2;

    // Burn runtime (monster)
    private int burnStacks = 0;
    private double burnRemainSec = 0.0;
    private double burnTickAcc = 0.0; // accumulates toward 1s ticks

    public CombatEngine(GameRepository repo) {
        this.repo = repo;
    }

    public LiveData<BattleState> state() { return state; }

    /** Optional helper to make combat deterministic while debugging. */
    public void setRandomSeed(long seed) { rng.setSeed(seed); }

    public void start(String monsterId) {
        this.pc = repo.loadOrCreatePlayer();
        this.monster = repo.getMonster(monsterId);
        if (monster == null) return;

        // Guard stats in case of nulls
        this.pStats = (pc != null) ? pc.totalStats(repo.gearStats(pc)) : new Stats();
        this.mStats = (monster.stats != null) ? monster.stats : new Stats();

        BattleState s = new BattleState();
        s.monsterId = monsterId;
        s.monsterName = monster.name;

        s.playerMaxHp = Math.max(1, pStats.health);
        s.monsterMaxHp = Math.max(1, mStats.health);
        s.playerHp = Math.min(pc != null && pc.currentHp != null ? pc.currentHp : s.playerMaxHp, s.playerMaxHp);
        if (s.playerHp <= 0) s.playerHp = s.playerMaxHp; // safety: never start at 0 HP
        s.monsterHp = s.monsterMaxHp;

        s.playerAttackProgress = 0f;
        s.monsterAttackProgress = 0f;
        s.playerAttackInterval = 0f;
        s.monsterAttackInterval = 0f;

        // Reset burn status
        burnStacks = 0; burnRemainSec = 0.0; burnTickAcc = 0.0;
        s.burnStacksOnMonster = 0; s.burnRemainingSec = 0f;

        s.running = true;
        state.setValue(s);

        // Clear log at start
        logClear();
        logLine("Encounter started: " + s.monsterName);

        // ✅ Authoritative flag: battle is active
        repo.startBattle();

        pTimer = mTimer = 0;
        lastTickMs = SystemClock.uptimeMillis();
        loop();
    }

    public void stop() {
        BattleState s = state.getValue();
        if (s != null) {
            s.running = false;
            state.setValue(s);
        }
        handler.removeCallbacksAndMessages(null); // ensure loop stops

        // persist player HP
        if (pc != null && s != null) {
            pc.currentHp = s.playerHp;
            repo.save();
        }

        // ✅ Authoritative flag: battle ended
        repo.stopBattle();
    }

    private void loop() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;
        handler.postDelayed(this::tick, 16); // schedule next tick (~60Hz)
    }

    private void tick() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;

        // Real elapsed time since last frame (seconds)
        long now = SystemClock.uptimeMillis();
        double deltaSec = Math.max(0, (now - lastTickMs) / 1000.0);
        lastTickMs = now;

        // attack intervals (base 2.5s, minus speed bonuses)
        pAtkItv = Math.max(0.6, 2.5 - clamp01(pStats.speed) * 2.5);
        mAtkItv = Math.max(0.6, 2.5 - clamp01(mStats.speed) * 2.5);

        // advance timers by real elapsed time
        pTimer += deltaSec;
        mTimer += deltaSec;

        // Player attacks (preserve overrun for consistent timing)
        while (pTimer >= pAtkItv && s.monsterHp > 0) {
            pTimer -= pAtkItv;
            DamageResult res = damageRollEx(pStats.attack, mStats.defense,
                    clamp01(pStats.critChance), Math.max(1.0, pStats.critMultiplier));
            s.monsterHp = Math.max(0, s.monsterHp - res.dmg);
            logLine("You hit " + s.monsterName + " for " + res.dmg + (res.crit ? " (CRIT!)" : ""));

            // ❌ Removed per-swing skill XP (was giving 1 XP per second)
            // repo.addSkillExp(SkillId.ATTACK, 1);
        }

        // 🔥 Burn ticking (on monster) — occurs after hits, before victory check
        if (burnStacks > 0 && s.monsterHp > 0) {
            burnRemainSec = Math.max(0.0, burnRemainSec - deltaSec);
            burnTickAcc += deltaSec;

            // Deal damage every full 1s
            while (burnTickAcc >= 1.0 && burnRemainSec > 0.0 && s.monsterHp > 0) {
                int burnDmg = burnStacks * BURN_DMG_PER_TICK_PER_STACK;
                s.monsterHp = Math.max(0, s.monsterHp - burnDmg);
                burnTickAcc -= 1.0;
                logLine("Burn tick: -" + burnDmg + " HP (" + burnStacks + " stacks)");
            }

            if (burnRemainSec == 0.0) {
                burnStacks = 0;
                burnTickAcc = 0.0;
                logLine("Burn expired");
            }
        }

        // Monster attacks
        while (mTimer >= mAtkItv && s.playerHp > 0) {
            mTimer -= mAtkItv;
            DamageResult res = damageRollEx(mStats.attack, pStats.defense,
                    clamp01(mStats.critChance), Math.max(1.0, mStats.critMultiplier));
            s.playerHp = Math.max(0, s.playerHp - res.dmg);
            logLine(s.monsterName + " hits you for " + res.dmg + (res.crit ? " (CRIT!)" : ""));

            // ❌ Removed per-hit Defense trickle XP
            // repo.addSkillExp(SkillId.DEFENSE, 1);
        }

        // defeat/victory
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            if (s.monsterHp == 0) {
                grantRewards(pc, monster); // ✅ All XP goes to selected training skill here
                logLine("You defeated " + s.monsterName + "!");
            } else {
                logLine("You were defeated by " + s.monsterName + "...");
            }
            if (pc != null) pc.currentHp = s.playerHp;
            repo.save();

            // ✅ Authoritative flag: end battle immediately for UI (FAB visibility, etc.)
            repo.stopBattle();

            // progress/intervals for final frame
            s.playerAttackProgress = (float) Math.min(1.0, pTimer / pAtkItv);
            s.monsterAttackProgress = (float) Math.min(1.0, mTimer / mAtkItv);
            s.playerAttackInterval = (float) pAtkItv;
            s.monsterAttackInterval = (float) mAtkItv;
            // expose burn to UI on the last frame
            s.burnStacksOnMonster = burnStacks;
            s.burnRemainingSec = (float) burnRemainSec;
            state.setValue(s);
            return;
        }

        // Update progress & intervals for UI this frame
        s.playerAttackProgress = (float) Math.min(1.0, pTimer / pAtkItv);
        s.monsterAttackProgress = (float) Math.min(1.0, mTimer / mAtkItv);
        s.playerAttackInterval = (float) pAtkItv;
        s.monsterAttackInterval = (float) mAtkItv;

        // Update burn UI fields
        s.burnStacksOnMonster = burnStacks;
        s.burnRemainingSec = (float) burnRemainSec;

        state.setValue(s);
        loop();
    }

    // Returns both damage and crit flag for UI/logging
    private DamageResult damageRollEx(int atk, int def, double critC, double critM) {
        int base = Math.max(1, atk - (int) (def * 0.6));
        // ±15% variation
        int roll = (int) Math.round(base * (0.85 + rng.nextDouble() * 0.3));
        boolean crit = rng.nextDouble() < critC;
        if (crit) roll = (int) Math.round(roll * Math.max(1.25, critM));
        return new DamageResult(Math.max(1, roll), crit);
    }

    /** Overload so old one-arg calls won’t break if they resurface. */
    private void grantRewards(Monster m) { grantRewards(this.pc, m); }

    private void grantRewards(PlayerCharacter pc, Monster m) {
        if (pc == null || m == null) return;

        // === XP → selected training skill ===
        int xp = (m.getExpPerKill());           // Uses Monster.exp or legacy expReward
        if (xp > 0) {
            SkillId train = repo.getCombatTrainingSkill();
            if (train == null) train = SkillId.ATTACK; // sensible default
            repo.addSkillExp(train, xp);               // <- the actual XP grant
            // Optional: also show a generic XP toast (comment out if you don’t want it)
            // repo.addPlayerExp(xp);
        }

        // === Currency / drops ===
        // Silver (allowed). No gold here per your rule.
        if (m.silverReward > 0) {
            repo.addPendingCurrency("silver", "Silver", m.silverReward);
        }

        // Item DROPS
        List<Drop> drops = m.drops;
        if (drops != null) {
            for (Drop d : drops) {
                if (d == null || d.chance <= 0) continue;
                if (rng.nextDouble() <= d.chance) {
                    int max = Math.max(d.min, d.max);
                    int min = Math.min(d.min, d.max);
                    int qty = Math.max(1, min + rng.nextInt(Math.max(1, max - min + 1)));
                    Item def = repo.getItem(d.itemId);
                    String name = def != null ? def.name : (d.itemId != null ? d.itemId : "unknown");
                    repo.addPendingLoot(d.itemId, name, qty);
                }
            }
        }

        // Slayer points are **not** granted here unless you wire an active-task check:
        // if (slayerTaskManager.isActiveFor(monster.id) && m.slayerReward > 0) { ... }

        repo.save();
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ----- tiny log helpers -----
    private void logClear() { logLive.postValue(new ArrayList<>()); }
    private void logLine(String msg) {
        List<String> cur = logLive.getValue();
        if (cur == null) cur = new ArrayList<>();
        cur.add(0, msg);
        if (cur.size() > 50) cur = new ArrayList<>(cur.subList(0, 50));
        logLive.postValue(cur);
        if (debugToasts) repo.toast(msg);
    }
}
