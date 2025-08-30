package com.obliviongatestudio.akthosidle.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.domain.model.AiBehavior;
import com.obliviongatestudio.akthosidle.domain.model.Drop;
import com.obliviongatestudio.akthosidle.domain.model.Element;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.Monster;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;
import com.obliviongatestudio.akthosidle.domain.model.Stats;
import com.obliviongatestudio.akthosidle.domain.model.StatusEffect;


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

    /**
     * Calculates raw damage and critical results for an attack.
     */
    private DamageResult damageRollEx(int atk, int def, double critC, double critM) {
        int base = Math.max(1, atk - (int) (def * 0.6));
        int roll = (int) Math.round(base * (0.85 + rng.nextDouble() * 0.3));
        boolean crit = rng.nextDouble() < critC;
        if (crit) roll = (int) Math.round(roll * Math.max(1.25, critM));
        return new DamageResult(Math.max(1, roll), crit);
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
        s.playerEffects = new ArrayList<>(); // Initialize BattleState effects
        s.monsterEffects = new ArrayList<>(); // Initialize BattleState effects

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
        handler.postDelayed(this::tick, 16); // Aim for roughly 60 FPS
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

        boolean monsterCanAttack = monster != null && monster.behavior != AiBehavior.PASSIVE;

        pAtkItv = Math.max(0.6, 2.5 - clamp01(pStats.speed) * 2.5) * pSlowMult;
        mAtkItv = Math.max(0.6, 2.5 - clamp01(mStats.speed) * 2.5) * mSlowMult;

        if (!pStunned) pTimer += deltaSec; else pTimer = 0;
        if (!mStunned && monsterCanAttack) mTimer += deltaSec; else mTimer = 0;


        // Player's turn
        while (pTimer >= pAtkItv && s.monsterHp > 0 && s.playerHp > 0) { // also check player HP
            pTimer -= pAtkItv;
            DamageResult res = damageRollEx(pStats.attack, mStats.defense,
                    clamp01(pStats.critChance), Math.max(1.0, pStats.critMultiplier));

            // Apply elemental modifier
            int finalDmg = CombatMath.applyElementMod(res.dmg, ElementalSystem.modifier(pElement, mElement));
            s.monsterHp = Math.max(0, s.monsterHp - finalDmg);
            logLine("You hit " + s.monsterName + " for " + finalDmg + (res.crit ? " (CRIT!)" : ""));

            if (rng.nextDouble() < BURN_APPLY_CHANCE) {
                monsterEffects.add(new StatusEffect("Burn", StatusEffect.Type.DOT, BURN_DURATION_SEC, (double)BURN_DMG_PER_TICK));
                logLine("Burn applied to " + s.monsterName);
            }
            if (s.monsterHp == 0) break; // Monster defeated, stop player attacks this tick
        }

        // Update ongoing effects (player and monster)
        updateEffects(monsterEffects, false, deltaSec, s);
        if (s.monsterHp == 0) { // check if monster died from DOT
            // proceed to victory handling below
        }
        updateEffects(playerEffects, true, deltaSec, s);
        if (s.playerHp == 0) { // check if player died from DOT
            // proceed to defeat handling below
        }


        // Monster's turn
        if (monsterCanAttack && s.monsterHp > 0 && s.playerHp > 0) { // check monster and player HP
            while (mTimer >= mAtkItv && s.playerHp > 0 && s.monsterHp > 0) { // also check monster HP
                mTimer -= mAtkItv;
                DamageResult res = damageRollEx(mStats.attack, pStats.defense,
                        clamp01(mStats.critChance), Math.max(1.0, mStats.critMultiplier));

                // Apply elemental modifier
                int finalDmg = CombatMath.applyElementMod(res.dmg, ElementalSystem.modifier(mElement, pElement));
                s.playerHp = Math.max(0, s.playerHp - finalDmg);
                logLine(s.monsterName + " hits you for " + finalDmg + (res.crit ? " (CRIT!)" : ""));
                if (s.playerHp == 0) break; // Player defeated, stop monster attacks this tick
            }
        }

        // Check for defeat/victory
        if (s.monsterHp == 0 || s.playerHp == 0) {
            s.running = false;
            if (s.monsterHp == 0) {
                grantRewards(pc, monster);
                logLine("You defeated " + s.monsterName + "!");
                if (s.monsterId != null) {
                    repo.onMonsterKilled(s.monsterId);
                }
            } else {
                logLine("You were defeated by " + s.monsterName + "...");
            }
            if (pc != null) pc.currentHp = s.playerHp; // Save current HP (even if 0)
            repo.save();
            repo.stopBattle();

            // Update final state for UI
            s.playerAttackProgress = (float) Math.min(1.0, pTimer / Math.max(0.1, pAtkItv)); // avoid div by zero
            s.monsterAttackProgress = (float) Math.min(1.0, mTimer / Math.max(0.1, mAtkItv)); // avoid div by zero
            s.playerAttackInterval = (float) pAtkItv;
            s.monsterAttackInterval = (float) mAtkItv;
            s.playerEffects = copyEffects(playerEffects);
            s.monsterEffects = copyEffects(monsterEffects);
            state.setValue(s);
            return; // Combat ended, stop tick loop
        }

        // Update progress for UI and continue loop
        s.playerAttackProgress = (float) Math.min(1.0, pTimer / Math.max(0.1, pAtkItv));
        s.monsterAttackProgress = (float) Math.min(1.0, mTimer / Math.max(0.1, mAtkItv));
        s.playerAttackInterval = (float) pAtkItv;
        s.monsterAttackInterval = (float) mAtkItv;

        s.playerEffects = copyEffects(playerEffects);
        s.monsterEffects = copyEffects(monsterEffects);

        state.setValue(s);
        loop(); // Schedule next tick
    }


    private void grantRewards(PlayerCharacter pc, Monster m) {
        if (pc == null || m == null) return;

        // === XP → selected training skill ===
        int xp = (m.getExpPerKill());
        if (xp > 0) {
            SkillId train = repo.getCombatTrainingSkill();
            if (train == null) train = SkillId.ATTACK; // Default to Attack if not set
            repo.addSkillExp(train, xp);
            // repo.addPlayerExp(xp); // Optional generic player XP
        }

        // === Currency / drops ===
        if (m.silverReward > 0) {
            repo.addPendingCurrency("silver", "Silver", m.silverReward);
        }

        List<Drop> drops = m.drops;
        if (drops != null) {
            for (Drop d : drops) {
                if (d == null || d.chance <= 0) continue;
                if (rng.nextDouble() <= d.chance) { // Use rng from CombatEngine
                    int max = Math.max(d.min, d.max);
                    int min = Math.min(d.min, d.max);
                    int qty = Math.max(1, min + rng.nextInt(Math.max(1, max - min + 1)));
                    Item def = repo.getItem(d.itemId);
                    String name = def != null ? def.name : (d.itemId != null ? d.itemId : "unknown item");
                    repo.addPendingLoot(d.itemId, name, qty);
                }
            }
        }
        repo.save(); // Save after granting all rewards
    }

    private void updateEffects(List<StatusEffect> list, boolean onPlayer, double deltaSec, BattleState s) {
        for (int i = list.size() - 1; i >= 0; i--) {
            StatusEffect e = list.get(i);
            e.remaining -= deltaSec;

            // Get a descriptive name for the effect (using its Type for now)
            String effectName = e.type.toString().toLowerCase(); // Or a more sophisticated way to get a display name

            if (e.type == StatusEffect.Type.DOT || e.type == StatusEffect.Type.HOT) {
                e.tickAcc += deltaSec;
                while (e.tickAcc >= 1.0 && e.remaining > 0) { // Process ticks only if effect is active
                    e.tickAcc -= 1.0; // Consume one second of accumulated time for a tick
                    int amt = (int) Math.round(e.value); // Value is per tick
                    if (amt == 0) continue; // No damage/healing this tick

                    if (e.type == StatusEffect.Type.DOT) {
                        if (onPlayer) {
                            if(s.playerHp == 0) continue; // Already defeated
                            s.playerHp = Math.max(0, s.playerHp - amt);
                            logLine("You take " + amt + " " + effectName + " damage."); // Use effectName
                        } else {
                            if(s.monsterHp == 0) continue; // Already defeated
                            s.monsterHp = Math.max(0, s.monsterHp - amt);
                            logLine(s.monsterName + " takes " + amt + " " + effectName + " damage."); // Use effectName
                        }
                    } else { // HOT
                        if (onPlayer) {
                            s.playerHp = Math.min(s.playerMaxHp, s.playerHp + amt);
                            logLine("You heal " + amt + " from " + effectName + "."); // Use effectName
                        } else {
                            s.monsterHp = Math.min(s.monsterMaxHp, s.monsterHp + amt);
                            logLine(s.monsterName + " heals " + amt + " from " + effectName + "."); // Use effectName
                        }
                    }
                }
            }
            if (e.remaining <= 0) {
                logLine((onPlayer ? "Your" : s.monsterName + "'s") + " " + effectName + " effect wore off."); // Use effectName
                list.remove(i);
            }
        }
    }

    private double totalSlow(List<StatusEffect> list) {
        double t = 0.0;
        for (StatusEffect e : list) if (e.type == StatusEffect.Type.SLOW && e.remaining > 0) t += e.value;
        return t;
    }

    private boolean hasStun(List<StatusEffect> list) {
        for (StatusEffect e : list) if (e.type == StatusEffect.Type.STUN && e.remaining > 0) return true;
        return false;
    }

    private List<StatusEffect> copyEffects(List<StatusEffect> src) {
        List<StatusEffect> out = new ArrayList<>();
        for (StatusEffect e : src) out.add(e.copy()); // Assuming StatusEffect has a copy() method
        return out;
    }

    public void addPlayerEffect(StatusEffect effect) {
        if (effect != null) {
            StatusEffect effectCopy = effect.copy(); // Add a copy to prevent external modification
            playerEffects.add(effectCopy);
            // Assuming StatusEffect has a public 'name' field or a public 'getName()' method
            // If 'name' is a public field:
            logLine("You are affected by " + effectCopy.getName() + ".");
            // If 'name' is private and you have a public getName() method:
            // logLine("You are affected by " + effectCopy.getName() + ".");
        }
    }

    public void addMonsterEffect(StatusEffect effect) {
        BattleState s = state.getValue();
        if (effect != null && s != null) {
            StatusEffect effectCopy = effect.copy();
            monsterEffects.add(effectCopy);
            // Assuming StatusEffect has a public 'name' field or a public 'getName()' method
            // If 'name' is a public field:
            logLine(s.monsterName + " is affected by " + effectCopy.name + ".");
            // If 'name' is private and you have a public getName() method:
            // logLine(s.monsterName + " is affected by " + effectCopy.getName() + ".");
        }
    }
    public List<StatusEffect> getPlayerEffects() {
        return copyEffects(playerEffects);
    }

    public List<StatusEffect> getMonsterEffects() {
        return copyEffects(monsterEffects);
    }

    public void clearEffects() {
        playerEffects.clear();
        monsterEffects.clear();
        logLine("All status effects cleared.");
    }


    // Legacy shim methods (Consider removing or refactoring these if no longer used)
    public void updateBar() { /* no-op legacy stub */ }
    public void tickLagCheck() { /* no-op legacy stub */ }
    public void onPotion() { /* no-op legacy stub */ }
    public void runTickTimers() { /* no-op legacy stub */ }
    public void restart() {
        pTimer = 0;
        mTimer = 0;
    }
    public void shiftTime(long ms) { /* no-op legacy stub */ }
    public void onTick() { tick(); } // This might still be useful for manual stepping in debug
    public void updateTickProgress() { /* no-op legacy stub */ }

    public void onStun(boolean player, double durationSec) {
        BattleState s = state.getValue();
        if (s == null) return;
        StatusEffect e = new StatusEffect("Stun", StatusEffect.Type.STUN, durationSec, 0); // Assuming a constructor
        if (player) {
            playerEffects.add(e);
            logLine("You are stunned!");
        } else {
            monsterEffects.add(e);
            logLine(s.monsterName + " is stunned!");
        }
    }
    public double getTickItv(boolean player) {
        return player ? pAtkItv : mAtkItv;
    }
    public void shiftTimer(boolean player, double sec) {
        if (player) pTimer += sec; else mTimer += sec;
    }
    public void addMonsterEffect() { /* no-op legacy stub for old save migrations */ }

    public void applyEffects() { // Renamed from updateEffects() to avoid conflict
        BattleState s = state.getValue();
        if (s != null) {
            // Apply a very small delta to process one tick of effects if needed,
            // or 0 if you just want to check expirations.
            // Using 0 here means only expirations are handled unless tickAcc is already >= 1.0
            updateEffects(playerEffects, true, 0.001, s); // Minimal delta to trigger one tick if ready
            updateEffects(monsterEffects, false, 0.001, s);
            state.setValue(s); // Ensure UI updates if HPs changed
        }
    }

    public void onSlow(boolean player, double durationSec, double amount) {
        BattleState s = state.getValue();
        if (s == null) return;
        StatusEffect e = new StatusEffect("Slow", StatusEffect.Type.SLOW, durationSec, amount); // Assuming a constructor
        if (player) {
            playerEffects.add(e);
            logLine("You are slowed!");
        } else {
            monsterEffects.add(e);
            logLine(s.monsterName + " is slowed!");
        }
    }

    // This was a duplicate of applyEffects essentially.
    // public void updateEffects() { ... }


    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    // ----- tiny log helpers -----
    public void logClear() {
        // Ensure this is called on the main thread if logLive is observed by UI
        if (Looper.myLooper() == Looper.getMainLooper()) {
            logLive.setValue(new ArrayList<>());
        } else {
            logLive.postValue(new ArrayList<>());
        }
    }
    private void logLine(String msg) {
        List<String> cur = logLive.getValue();
        // Initialize if null, important if postValue is used and then getValue before main thread processes
        if (cur == null) cur = new ArrayList<>();

        ArrayList<String> newList = new ArrayList<>(cur); // Create a new list for modification
        newList.add(0, msg);
        if (newList.size() > 50) { // Trim if over limit
            newList = new ArrayList<>(newList.subList(0, 50));
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            logLive.setValue(newList);
        } else {
            logLive.postValue(newList);
        }

        if (debugToasts) repo.toast(msg); // Assuming repo.toast is thread-safe or posts to main
    }
}
