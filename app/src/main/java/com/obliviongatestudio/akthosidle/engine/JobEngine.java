package com.obliviongatestudio.akthosidle.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.obliviongatestudio.akthosidle.domain.model.Job;

/**
 * Lightweight loop that advances a Job in real-time and accumulates its rewards.
 */
public class JobEngine {
    @Nullable private Job current;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTick;
    private boolean running;

    /** Begin looping the given job. */
    public void start(Job job) {
        current = job;
        lastTick = SystemClock.uptimeMillis();
        running = true;
        loop();
    }

    /** Stop the loop. */
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        current = null;
    }

    private void loop() {
        if (!running) return;
        handler.postDelayed(this::tick, 50);
    }

    private void tick() {
        if (!running || current == null) return;
        long now = SystemClock.uptimeMillis();
        long delta = now - lastTick;
        lastTick = now;
        applyProgress(current, delta);
        loop();
    }

    /** Core progress logic reused by TickService and tests. */
    public static void applyProgress(Job j, long deltaMs) {
        long total = j.progressMs + deltaMs;
        if (j.intervalMs > 0) {
            long ticks = total / j.intervalMs;
            j.progressMs = total % j.intervalMs;
            j.accumulatedXp += j.xpPerTick * ticks;
            j.accumulatedCurrency += j.currencyPerTick * ticks;
        }
    }
}
