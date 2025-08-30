package com.obliviongatestudio.akthosidle.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.domain.model.Drop;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.Monster;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;
import com.obliviongatestudio.akthosidle.domain.model.Stats;
import com.obliviongatestudio.akthosidle.domain.model.StatusEffect;

import com.obliviongatestudio.akthosidle.domain.model.Element;

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

        /** Active status effects for UI display */
        public List<StatusEffect> playerEffects = new ArrayList<>();
        public List<StatusEffect> monsterEffects = new ArrayList<>();

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
    private Element pElement = Element.NEUTRAL, mElement = Element.NEUTRAL;

    // cache current intervals (seconds) for progress calculation
    private double pAtkItv = 2.5, mAtkItv = 2.5;

    // Example burn effect constants (player applies to monster)
    private static final double BURN_APPLY_CHANCE = 0.25;
    private static final double BURN_DURATION_SEC = 5.0;
    private static final int    BURN_DMG_PER_TICK = 2;

    private final List<StatusEffect> playerEffects = new ArrayList<>();
    private final List<StatusEffect> monsterEffects = new ArrayList<>();

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

        this.pStats = (pc != null) ? pc.totalStats(repo.gearStats(pc)) : new Stats();
        this.mStats = (monster.stats != null) ? monster.stats : new Stats();
        this.pElement = (pc != null && pc.element != null) ? pc.element : Element.NEUTRAL;
        this.mElement = (monster.element != null) ? monster.element : Element.NEUTRAL;

        BattleState s = new BattleState();
        s.monsterId = monsterId;
        s.monsterName = monster.name;

        s.playerMaxHp = Math.max(1, pStats.health);
        s.monsterMaxHp = Math.max(1, mStats.health);
        s.playerHp = Math.min(pc != null && pc.currentHp != null ? pc.currentHp : s.playerMaxHp, s.playerMaxHp);
        if (s.playerHp <= 0) s.playerHp = s.playerMaxHp;
        s.monsterHp = s.monsterMaxHp;

        s.playerAttackProgress = 0f;
        s.monsterAttackProgress = 0f;
        s.playerAttackInterval = 0f;
        s.monsterAttackInterval = 0f;

        playerEffects.clear();
        monsterEffects.clear();
        s.playerEffects = new ArrayList<>();
        s.monsterEffects = new ArrayList<>();

        s.running = true;
        state.setValue(s);

        logClear();
        logLine("Encounter started: " + s.monsterName);

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
        handler.removeCallbacksAndMessages(null);

        if (pc != null && s != null) {
            pc.currentHp = s.playerHp;
            repo.save();
        }

        repo.stopBattle();
    }

    private void loop() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;
        handler.postDelayed(this::tick, 16);
    }

    private void tick() {
        BattleState s = state.getValue();
        if (s == null || !s.running) return;

        long now = SystemClock.uptimeMillis();
        double deltaSec = Math.max(0, (now - lastTickMs) / 1000.0);
        lastTickMs = now;

        double pSlowMult = 1.0 + totalSlow(playerEffects);
        double mSlowMult = 1.0 + totalSlow(monsterEffects);
        boolean pStunned = hasStun(playerEffects);
        boolean mStunned = hasStun(monsterEffects);

        pAtkItv = Math.max(0.6, 2.5 - clamp01(pStats.speed) * 2.5) * pSlowMult;
        mAtkItv = Math.max(0.6, 2.5 - clamp01(mStats.speed) * 2.5) * mSlowMult;

        if (!pStunned) pTimer += deltaSec; else pTimer = 0;
        if (!mStunned) mTimer += deltaSec; else mTimer = 0;

        while (pTimer >= pAtkItv && s.monsterHp > 0) {
            pTimer -= pAtkItv;
            DamageResult res = damageRollEx(pStats.attack, mStats.defense,
                    clamp01(pStats.critChance), Math.max(1.0, pStats.critMultiplier));

            int finalDmg = CombatMath.applyElementMod(res.dmg, ElementalSystem.modifier(pElement, mElement));
            s.monsterHp = Math.max(0, s.monsterHp - finalDmg);
            logLine("You hit " + s.monsterName + " for " + finalDmg + (res.crit ? " (CRIT!)" : ""));
            s.monsterHp = Math.max(0, s.monsterHp - res.dmg);
            logLine("You hit " + s.monsterName + " for " + res.dmg + (res.crit ? " (CRIT!)" : ""));

            if (rng.nextDouble() < BURN_APPLY_CHANCE) {
                monsterEffects.add(new StatusEffect(StatusEffect.Type.DOT, BURN_DURATION_SEC, BURN_DMG_PER_TICK));
                logLine("Burn applied");
            }
        }

        // update ongoing effects
        updateEffects(monsterEffects, false, deltaSec, s);
        updateEffects(playerEffects, true, deltaSec, s);

        while (mTimer >= mAtkItv && s.playerHp > 0) {
            mTimer -= mAtkItv;
            DamageResult res = damageRollEx(mStats.attack, pStats.defense,
                    clamp01(mStats.critChance), Math.max(1.0, mStats.critMultiplier));
            int finalDmg = CombatMath.applyElementMod(res.dmg, ElementalSystem.modifier(mElement, pElement));
            s.playerHp = Math.max(0, s.playerHp - finalDmg);
            logLine(s.monsterName + " hits you for " + finalDmg + (res.crit ? " (CRIT!)" : ""));
        }

        // defeat/victory
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            if (s.monsterHp == 0) {
                grantRewards(pc, monster);
                logLine("You defeated " + s.monsterName + "!");
                // ✅ Count Slayer task progress & per-kill coins if on-task
                if (s.monsterId != null) {
                    repo.onMonsterKilled(s.monsterId); // ← NEW: count Slayer kill
                }
            } else {
                logLine("You were defeated by " + s.monsterName + "...");
            }
            if (pc != null) pc.currentHp = s.playerHp;
            repo.save();

            repo.stopBattle();

            s.playerAttackProgress = (float) Math.min(1.0, pTimer / pAtkItv);
            s.monsterAttackProgress = (float) Math.min(1.0, mTimer / mAtkItv);
            s.playerAttackInterval = (float) pAtkItv;
            s.monsterAttackInterval = (float) mAtkItv;
            s.playerEffects = copyEffects(playerEffects);
            s.monsterEffects = copyEffects(monsterEffects);
            state.setValue(s);
            return;
        }

        s.playerAttackProgress = (float) Math.min(1.0, pTimer / pAtkItv);
        s.monsterAttackProgress = (float) Math.min(1.0, mTimer / mAtkItv);
        s.playerAttackInterval = (float) pAtkItv;
        s.monsterAttackInterval = (float) mAtkItv;

        s.playerEffects = copyEffects(playerEffects);
        s.monsterEffects = copyEffects(monsterEffects);

        state.setValue(s);
        loop();
    }

    private DamageResult damageRollEx(int atk, int def, double critC, double critM) {
        int base = Math.max(1, atk - (int) (def * 0.6));
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
        int xp = (m.getExpPerKill());
        if (xp > 0) {
            SkillId train = repo.getCombatTrainingSkill();
            if (train == null) train = SkillId.ATTACK;
            repo.addSkillExp(train, xp);
            // repo.addPlayerExp(xp); // optional generic toast
        }

        // === Currency / drops ===
        if (m.silverReward > 0) {
            repo.addPendingCurrency("silver", "Silver", m.silverReward);
        }

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

        repo.save();
    }

    private void updateEffects(List<StatusEffect> list, boolean onPlayer, double deltaSec, BattleState s) {
        for (int i = list.size() - 1; i >= 0; i--) {
            StatusEffect e = list.get(i);
            e.remaining -= deltaSec;
            if (e.type == StatusEffect.Type.DOT || e.type == StatusEffect.Type.HOT) {
                e.tickAcc += deltaSec;
                while (e.tickAcc >= 1.0 && e.remaining > 0) {
                    int amt = (int) Math.round(e.value);
                    if (e.type == StatusEffect.Type.DOT) {
                        if (onPlayer) {
                            s.playerHp = Math.max(0, s.playerHp - amt);
                        } else {
                            s.monsterHp = Math.max(0, s.monsterHp - amt);
                        }
                        logLine((onPlayer ? "You" : s.monsterName) + " suffer " + amt + " damage");
                    } else {
                        if (onPlayer) {
                            s.playerHp = Math.min(s.playerMaxHp, s.playerHp + amt);
                        } else {
                            s.monsterHp = Math.min(s.monsterMaxHp, s.monsterHp + amt);
                        }
                        logLine((onPlayer ? "You" : s.monsterName) + " heal " + amt);
                    }
                    e.tickAcc -= 1.0;
                }
            }
            if (e.remaining <= 0) {
                list.remove(i);
            }
        }
    }

    private double totalSlow(List<StatusEffect> list) {
        double t = 0.0;
        for (StatusEffect e : list) if (e.type == StatusEffect.Type.SLOW) t += e.value;
        return t;
    }

    private boolean hasStun(List<StatusEffect> list) {
        for (StatusEffect e : list) if (e.type == StatusEffect.Type.STUN) return true;
        return false;
    }

    private List<StatusEffect> copyEffects(List<StatusEffect> src) {
        List<StatusEffect> out = new ArrayList<>();
        for (StatusEffect e : src) out.add(e.copy());
        return out;
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
