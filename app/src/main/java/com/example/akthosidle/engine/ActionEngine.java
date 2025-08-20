package com.example.akthosidle.engine;

import android.content.Context;
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

    // ---- constructor (public; fixes "has private access") ----
    public ActionEngine(Context context, GameRepository repo) {
        this.app = context.getApplicationContext();
        this.repo = repo;
    }

    // ---- wiring ----
    public void setListener(@Nullable Listener l) { this.listener = l; }

    /** Start infinite loop for the given action (restarts if already running). */
    public synchronized void startLoop(Action action) {
        stop(); // ensure a clean single loop
        this.current = action;
        this.running = true;
        worker = new Thread(this::runLoop, "ActionLoop");
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
        postLoopState(false);
    }

    // ---- core loop ----
    private void runLoop() {
        final Action a = this.current;
        if (a == null) return;

        while (running) {
            runOneTick(a);
        }
    }

    private void runOneTick(Action a) {
        long duration = Math.max(500L, a.durationMs > 0 ? a.durationMs : 3000L);
        long start = SystemClock.uptimeMillis();
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

        if (!running) return;

        // grant rewards + xp after the bar reaches 100%
        boolean leveled = grantRewardsAndXp(a);
        postCompleted(a, leveled);
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
            repo.collectPendingLoot(); // immediately collect; or keep pending if you prefer
        }

        // XP
        PlayerCharacter pc = repo.loadOrCreatePlayer();
        SkillId sid = a.skill;
        boolean leveledUp = pc.addSkillExp(sid, a.exp);
        repo.save();

        return leveledUp;
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
