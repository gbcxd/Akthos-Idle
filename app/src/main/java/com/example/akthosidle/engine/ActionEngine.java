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
import com.example.akthosidle.domain.model.SkillId;

import java.util.Locale;
import java.util.Map;

/**
 * Runs a chosen non-combat Action in a loop and reports progress back to the UI.
 * - Directly grants outputs (no pending buffer).
 * - Adds Skill XP via GameRepository using Action.exp.
 * - Toasts are rate-limited to avoid "already queued 5 toasts" errors.
 */
public class ActionEngine {

    public interface Listener {
        @MainThread void onTick(Action action, int progressPercent, long elapsedMs, long remainingMs);
        @MainThread void onActionComplete(Action action, boolean leveledUp);
        @MainThread void onLoopStateChanged(boolean running);
    }

    private final Context app;
    private final GameRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    @Nullable private Thread worker;
    @Nullable private Action current;
    @Nullable private Listener listener;

    private static final String SP_NAME = "akthos_idle_engine";
    private static final String KEY_RUN_ACTION_ID = "run_action_id";
    private static final String KEY_RUN_STARTED_AT = "run_started_at";
    private static final String KEY_RUN_SKILL = "run_skill";

    // Cap offline catch-up to 2h
    private static final long MAX_OFFLINE_MS = 2L * 60L * 60L * 1000L;

    // Avoid toast spam: at most 1 toast every 1200ms.
    private static final long MIN_TOAST_INTERVAL_MS = 1200L;
    private long lastToastAt = 0L;

    private final SharedPreferences engineSp;

    public ActionEngine(Context context, GameRepository repo) {
        this.app = context.getApplicationContext();
        this.repo = repo;
        this.engineSp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }
    public boolean isRunning() { return running; }

    @Nullable
    public SkillId getPersistedRunningSkill() {
        String s = engineSp.getString(KEY_RUN_SKILL, null);
        if (s == null) return null;
        try { return SkillId.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    /** Start infinite loop for the given action (restarts if already running). */
    public synchronized void startLoop(Action action) {
        stop(); // ensure clean
        this.current = action;
        this.running = true;

        long startedAt = SystemClock.uptimeMillis();
        persistStart(action, startedAt);

        worker = new Thread(this::runLoopFromNow, "ActionLoop");
        worker.start();
        postLoopState(true);
    }

    /** Stop the loop gracefully. */
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

        long cycles = cappedElapsed / duration;
        for (long i = 0; running && i < cycles; i++) {
            boolean leveled = grantRewardsAndXp(a, false); // no toasts during catch-up
            postCompleted(a, leveled);
        }

        long remainderStart = now - (cappedElapsed % duration);
        loopFromStartTime(a, remainderStart);
    }

    private void loopFromStartTime(Action a, long startTimeMs) {
        long start = startTimeMs;
        while (running) {
            long duration = Math.max(500L, a.durationMs > 0 ? a.durationMs : 3000L);
            long end   = start + duration;

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

            boolean leveled = grantRewardsAndXp(a, true); // real-time cycle: allow toast (rate-limited)
            postCompleted(a, leveled);

            start = SystemClock.uptimeMillis();
            persistStart(a, start);
        }
    }

    @WorkerThread
    private boolean grantRewardsAndXp(Action a, boolean allowToast) {
        StringBuilder toastMsg = new StringBuilder();

        // Direct outputs (items or currency:xxx)
        if (a.outputs != null && !a.outputs.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Integer> e : a.outputs.entrySet()) {
                String idOrCurrency = e.getKey();
                int qty = Math.max(1, e.getValue());

                repo.grantGathered(idOrCurrency, qty, null); // saves & publishes

                String pretty;
                if (idOrCurrency != null && idOrCurrency.startsWith("currency:")) {
                    String code = idOrCurrency.substring("currency:".length());
                    pretty = capitalize(code);
                } else {
                    pretty = repo.itemName(idOrCurrency);
                }

                if (!first) toastMsg.append(", ");
                toastMsg.append("+").append(qty).append("× ").append(pretty);
                first = false;
            }
        }

        // Skill XP (use Action.exp, fallback to 5)
        int xp = (a.exp > 0) ? a.exp : 5;
        boolean leveledUp = false;
        if (a.skill != null && xp > 0) {
            leveledUp = repo.addSkillExp(a.skill, xp);
        }

        // Optional: also add player XP (commented out)
        // repo.addPlayerExp(xp);

        // Rate-limited toast to avoid system drops
        if (allowToast) {
            final String msg = (toastMsg.length() > 0)
                    ? (toastMsg + " (+" + xp + " XP)")
                    : ("+" + xp + " XP");
            maybeToast(msg);
        }

        return leveledUp;
    }

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

    /** Only show a toast if enough time passed since the last one. */
    private void maybeToast(String message) {
        long now = SystemClock.uptimeMillis();
        if (now - lastToastAt >= MIN_TOAST_INTERVAL_MS) {
            lastToastAt = now;
            main.post(() -> repo.toast(message));
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.US);
    }
}
