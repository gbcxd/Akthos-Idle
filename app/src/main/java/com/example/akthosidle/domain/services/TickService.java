package com.example.akthosidle.domain.services;

import com.example.akthosidle.data.dtos.Snapshot;

public class TickService {
    public Snapshot applyCatchUp(Snapshot s, long nowMs) {
        long delta = Math.max(0, nowMs - s.timestampMs);
        // TODO: iterate jobs and award xp/loot
        s.timestampMs = nowMs;
        return s;
    }
}
