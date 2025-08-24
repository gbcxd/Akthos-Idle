package com.example.akthosidle.domain.model;

import androidx.annotation.Nullable;

/** Persisted Slayer task info. Backward-compatible with older saves that used "progress". */
public class SlayerAssignment {
    public String regionId;   // NEW
    public String monsterId;
    public String label;
    public int required;
    public int done;          // renamed from progress
    public int completionBonus;
    public long assignedAtMs;

    public SlayerAssignment() {}

    public SlayerAssignment(String regionId, String monsterId, @Nullable String label, int required, int completionBonus) {
        this.regionId = regionId;
        this.monsterId = monsterId;
        this.label = (label == null ? monsterId : label);
        this.required = Math.max(1, required);
        this.completionBonus = Math.max(0, completionBonus);
        this.done = 0;
        this.assignedAtMs = System.currentTimeMillis();
    }

    public boolean isComplete() { return done >= required; }
    public int remaining() { return Math.max(0, required - done); }
    public int getProgress() { return Math.min(done, required); }

    public int getDone() {
        return Math.max(0, done);
    }
}

