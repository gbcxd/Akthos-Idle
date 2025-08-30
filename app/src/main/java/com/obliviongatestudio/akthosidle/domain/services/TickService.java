package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.data.dtos.Snapshot;
import com.obliviongatestudio.akthosidle.domain.model.Job;
import com.obliviongatestudio.akthosidle.engine.JobEngine;

public class TickService {
    public Snapshot applyCatchUp(Snapshot s, long nowMs) {
        long delta = Math.max(0, nowMs - s.timestampMs);
        if (s.jobs != null) {
            for (Job j : s.jobs) {
                JobEngine.applyProgress(j, delta);
            }
        }
        s.timestampMs = nowMs;
        return s;
    }
}
