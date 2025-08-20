package com.example.akthosidle.domain.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.Skill;
import com.example.akthosidle.domain.model.SkillId;

import java.util.Map;

/**
 * Runs a single timed Action at a time. Ticks progress, validates requirements,
 * awards outputs, grants XP, and persists via GameRepository.
 */
public class ActionEngine {

    // Singleton (per process)
    private static ActionEngine INSTANCE;

    public static synchronized ActionEngine get(Context ctx, GameRepository repo) {
        if (INSTANCE == null) {
            INSTANCE = new ActionEngine(ctx.getApplicationContext(), repo);
        }
        return INSTANCE;
    }

    // --- State ---
    private final Context app;
    private final GameRepository repo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private @Nullable String activeActionId = null;
    private long startAtMs = 0L;
    private long endAtMs   = 0L;
    private long durationMs = 0L;

    // Live outputs for UI
    private final MutableLiveData<Boolean> runningLive = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> progressLive = new MutableLiveData<>(0);
    private final MutableLiveData<Long> remainingLive = new MutableLiveData<>(0L);
    private final MutableLiveData<String> activeActionLive = new MutableLiveData<>(null);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (activeActionId == null) return;
            long now = System.currentTimeMillis();
            long remaining = Math.max(0, endAtMs - now);
            long elapsed = Math.max(0, now - startAtMs);
            int pct = durationMs <= 0 ? 100 : (int)Math.min(100, Math.round(100.0 * elapsed / durationMs));

            progressLive.setValue(pct);
            remainingLive.setValue(remaining);

            if (remaining <= 0) {
                finishAction();
            } else {
                handler.postDelayed(this, 50L);
            }
        }
    };

    private ActionEngine(Context app, GameRepository repo) {
        this.app = app;
        this.repo = repo;
    }

    // --- Public LiveData for UI binding ---
    public LiveData<Boolean> isRunning() { return runningLive; }
    public LiveData<Integer> progressPercent() { return progressLive; }
    public LiveData<Long> millisRemaining() { return remainingLive; }
    public LiveData<String> activeActionId() { return activeActionLive; }

    /** Attempts to start the action by id; returns false if requirements fail or already running. */
    @MainThread
    public boolean start(String actionId) {
        if (runningLive.getValue() != null && runningLive.getValue()) {
            Toast.makeText(app, "An action is already running.", Toast.LENGTH_SHORT).show();
            return false;
        }
        final Action a = repo.getAction(actionId);
        if (a == null) {
            Toast.makeText(app, "Action not found.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validate requirements (level)
        PlayerCharacter pc = repo.loadOrCreatePlayer();
        Skill s = pc.skill(a.skill != null ? a.skill : SkillId.MINING); // default to any skill to avoid NPE
        if (a.reqLevel > 0 && (s == null || s.level < a.reqLevel)) {
            Toast.makeText(app, "Requires " + safeName(a.skill) + " " + a.reqLevel, Toast.LENGTH_SHORT).show();
            return false;
        }

        // (Optional) Validate inputs if you add them later

        // Start timers
        this.activeActionId = a.id;
        this.durationMs = Math.max(1L, a.durationMs);
        this.startAtMs = System.currentTimeMillis();
        this.endAtMs   = startAtMs + durationMs;

        runningLive.setValue(true);
        activeActionLive.setValue(a.id);
        progressLive.setValue(0);
        remainingLive.setValue(durationMs);

        handler.removeCallbacks(ticker);
        handler.post(ticker);

        return true;
    }

    /** Cancels the current action without rewards. */
    @MainThread
    public void cancel() {
        if (activeActionId == null) return;
        handler.removeCallbacks(ticker);
        resetState();
        Toast.makeText(app, "Action cancelled.", Toast.LENGTH_SHORT).show();
    }

    /** Finishes, grants rewards and XP, and persists. */
    private void finishAction() {
        final String finishedId = activeActionId;
        resetState();

        final Action a = repo.getAction(finishedId);
        if (a == null) return;

        // Award outputs
        if (a.outputs != null) {
            for (Map.Entry<String, Integer> e : a.outputs.entrySet()) {
                String outId = e.getKey();
                int qty = Math.max(0, e.getValue() == null ? 0 : e.getValue());
                if (qty <= 0) continue;

                if (outId.startsWith("currency:")) {
                    String code = outId.substring("currency:".length());
                    repo.addCurrency(code, qty);
                } else {
                    // queue item loot, then we'll collect
                    repo.addPendingLoot(outId, repo.itemName(outId), qty);
                }
            }
        }
        repo.collectPendingLoot(); // apply any pending items

        // Grant XP (+ level-up toasts)
        PlayerCharacter pc = repo.loadOrCreatePlayer();
        Skill skill = pc.skill(a.skill);
        int before = skill.level;
        skill.addXp(a.exp);
        int after = skill.level;

        repo.save();
        if (after > before) {
            // Simple multi-level message if big XP
            String msg = safeName(a.skill) + " " + before + " → " + after + "!";
            Toast.makeText(app, "Level up! " + msg, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(app, "+" + a.exp + " " + safeName(a.skill) + " XP", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetState() {
        handler.removeCallbacks(ticker);
        activeActionId = null;
        startAtMs = 0L;
        endAtMs = 0L;
        durationMs = 0L;

        runningLive.setValue(false);
        activeActionLive.setValue(null);
        progressLive.setValue(0);
        remainingLive.setValue(0L);
    }

    private static String safeName(@Nullable SkillId id) {
        if (id == null) return "Skill";
        String n = id.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
