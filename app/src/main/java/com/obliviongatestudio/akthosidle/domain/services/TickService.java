package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.data.dtos.Snapshot;
import com.obliviongatestudio.akthosidle.domain.model.Job;

public class TickService {
    public Snapshot applyCatchUp(Snapshot s, long nowMs) {
        long delta = Math.max(0, nowMs - s.timestampMs);
        if (s.jobs != null) {
            for (Job j : s.jobs) {
                long total = j.progressMs + delta;
                if (j.intervalMs > 0) {
                    long ticks = total / j.intervalMs;
                    j.progressMs = total % j.intervalMs;
                    j.accumulatedXp += j.xpPerTick * ticks;
                    j.accumulatedCurrency += j.currencyPerTick * ticks;
                }
            }
        }
        s.timestampMs = nowMs;
        return s;
    }
}
