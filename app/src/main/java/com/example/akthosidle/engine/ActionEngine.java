package com.example.akthosidle.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.SkillId;

import java.util.Map;

/**
 * Runs a chosen non-combat Action in a loop and reports progress back to the UI.
 * Threading: a single background worker thread, callbacks posted to main.
 */
public class ActionEngine {

    // ---- Listener API for the fragment ----
    public interface Listener {
        /** Called many times while an action is in progress. */
        @MainThread
        void onTick(Action action, int progressPercent, long elapsedMs, long remainingMs);

        /** Called when one action iteration completes (after rewards/xp are granted). */
        @MainThread
        void onActionComplete(Action action, boolean leveledUp);

        /** Called when the loop starts/stops. */
        @MainThread
        void onLoopStateChanged(boolean running);
    }

    /** Simple value object if you want to surface item grants to the UI. */
    public static class ItemGrant {
        public final String itemId;
        public final int quantity;
        public ItemGrant(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    private final Context app;
    private final GameRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    @Nullable private Thread worker;
    @Nullable private Action current;
    @Nullable private Listener listener;

    // ---- persistence keys (to restore after relaunch) ----
    private static final String SP_NAME = "akthos_idle_engine";
    private static final String KEY_RUN_ACTION_ID = "run_action_id";
    private static final String KEY_RUN_STARTED_AT = "run_started_at"; // SystemClock.uptimeMillis()
    private static final String KEY_RUN_SKILL = "run_skill";
    // Cap the offline catch-up window (e.g., 2 hours)
    private static final long MAX_OFFLINE_MS = 2L * 60L * 60L * 1000L;

    private final SharedPreferences engineSp;

    // ---- constructor (public) ----
    public ActionEngine(Context context, GameRepository repo) {
        this.app = context.getApplicationContext();
        this.repo = repo;
        this.engineSp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ---- wiring ----
    public void setListener(@Nullable Listener l) { this.listener = l; }

    /** Start infinite loop for the given action (restarts if already running). */
    public synchronized void startLoop(Action action) {
        stop(); // ensure a clean single loop
        this.current = action;
        this.running = true;

        long startedAt = SystemClock.uptimeMillis();
        persistStart(action, startedAt);

        worker = new Thread(this::runLoopFromNow, "ActionLoop");
        worker.start();
        postLoopState(true);
    }

    /** Stop the loop gracefully. Safe to call multiple times. */
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        clearPersisted();
        postLoopState(false);
    }

    /** Try to restore a previously running loop. Returns true if resumed. */
    public synchronized boolean restoreIfRunning() {
        String id = engineSp.getString(KEY_RUN_ACTION_ID, null);
        long startedAt = engineSp.getLong(KEY_RUN_STARTED_AT, 0L);
        if (id == null || startedAt <= 0) return false;

        Action a = repo.getAction(id);
        if (a == null) {
            clearPersisted();
            return false;
        }

        this.current = a;
        this.running = true;
        worker = new Thread(() -> runLoopRestored(a, startedAt), "ActionLoop-Restore");
        worker.start();
        postLoopState(true);
        return true;
    }

    // ---- core loops ----
    private void runLoopFromNow() {
        final Action a = this.current;
        if (a == null) return;
        loopFromStartTime(a, SystemClock.uptimeMillis());
    }

    private void runLoopRestored(Action a, long startedAt) {
        long duration = Math.max(500L, a.durationMs > 0 ? a.durationMs : 3000L);
        long now = SystemClock.uptimeMillis();
        long elapsed = Math.max(0, now - startedAt);
        long cappedElapsed = Math.min(elapsed, MAX_OFFLINE_MS);

        // Grant complete cycles from the offline window
        long cycles = cappedElapsed / duration;
        for (long i = 0; running && i < cycles; i++) {
            boolean leveled = grantRewardsAndXp(a);
            postCompleted(a, leveled);
        }

        // Continue from any partial remainder as if started 'remainderStart'
        long remainderStart = now - (cappedElapsed % duration);
        loopFromStartTime(a, remainderStart);
    }

    /** Loop forever, starting a cycle at the provided start time. */
    private void loopFromStartTime(Action a, long startTimeMs) {
        while (running) {
            long duration = Math.max(500L, a.durationMs > 0 ? a.durationMs : 3000L);
            long start = startTimeMs;
            long end   = start + duration;

            // progress updates
            while (running) {
                long now = SystemClock.uptimeMillis();
                long elapsed = now - start;
                int percent = (int) Math.min(100, (elapsed * 100) / duration);
                long remaining = Math.max(0, end - now);

                postTick(a, percent, elapsed, remaining);

                if (now >= end) break;
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }

            if (!running) break;

            // grant rewards + xp after the bar reaches 100%
            boolean leveled = grantRewardsAndXp(a);
            postCompleted(a, leveled);

            // next cycle starts now
            startTimeMs = SystemClock.uptimeMillis();

            // Update persisted start to keep offline resume accurate
            persistStart(a, startTimeMs);
        }
    }

    // ---- reward/xp ----
    @WorkerThread
    private boolean grantRewardsAndXp(Action a) {
        // Items
        if (a.outputs != null && !a.outputs.isEmpty()) {
            for (Map.Entry<String, Integer> e : a.outputs.entrySet()) {
                String itemId = e.getKey();
                int qty = Math.max(1, e.getValue());
                repo.addPendingLoot(itemId, repo.itemName(itemId), qty);
            }
            // Immediately collect into player; or keep pending if you want a "claim" UI
            repo.collectPendingLoot();
        }

        // XP
        PlayerCharacter pc = repo.loadOrCreatePlayer();
        SkillId sid = a.skill;
        boolean leveledUp = pc.addSkillExp(sid, a.exp);
        repo.save();

        return leveledUp;
    }

    // ---- persistence helpers ----
    private void persistStart(Action a, long startedAt) {
        engineSp.edit()
                .putString(KEY_RUN_ACTION_ID, a.id)
                .putString(KEY_RUN_SKILL, a.skill != null ? a.skill.name() : null)
                .putLong(KEY_RUN_STARTED_AT, startedAt)
                .apply();
    }

    private void clearPersisted() {
        engineSp.edit()
                .remove(KEY_RUN_ACTION_ID)
                .remove(KEY_RUN_STARTED_AT)
                .remove(KEY_RUN_SKILL)
                .apply();
    }

    // ---- UI posting helpers ----
    private void postTick(Action a, int percent, long elapsed, long remaining) {
        Listener l = listener;
        if (l == null) return;
        main.post(() -> l.onTick(a, percent, elapsed, remaining));
    }

    private void postCompleted(Action a, boolean leveled) {
        Listener l = listener;
        if (l == null) return;
        main.post(() -> l.onActionComplete(a, leveled));
    }

    private void postLoopState(boolean state) {
        Listener l = listener;
        if (l == null) return;
        main.post(() -> l.onLoopStateChanged(state));
    }
}
