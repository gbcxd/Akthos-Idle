package com.example.akthosidle.domain.model;

import androidx.annotation.Nullable;

/** Persisted Slayer task info. Backward-compatible with older saves that used "progress". */
public class SlayerAssignment {
    /** Monster you must kill (simple v1: single id). */
    public String monsterId;
    /** Display label (monster name / task name). */
    public String label;
    /** How many kills are required. */
    public int required;
    /** Canonical completed kills count (use this going forward). */
    public int done;

    /** Legacy alias kept for backward compatibility with older saves. */
    @Deprecated
    public int progress;

    /** Extra points granted on completion (optional). */
    public int completionBonus;
    /** When assigned (for future UX/expiry). */
    public long assignedAtMs;

    public SlayerAssignment() {
        // For deserialization. Call normalize() afterward if you need to be extra safe.
    }

    public SlayerAssignment(String monsterId, @Nullable String label, int required, int completionBonus) {
        this.monsterId = monsterId;
        this.label = (label == null ? monsterId : label);
        this.required = Math.max(1, required);
        this.completionBonus = Math.max(0, completionBonus);
        this.done = 0;
        this.progress = 0; // keep legacy in sync
        this.assignedAtMs = System.currentTimeMillis();
    }

    /** Ensure fields are sane and keep legacy progress in sync with done. */
    public void normalize() {
        if (this.required < 1) this.required = 1;
        if (this.completionBonus < 0) this.completionBonus = 0;
        if (this.label == null) this.label = this.monsterId;
        // If loaded from an old save that only had progress, adopt it.
        if (this.done <= 0 && this.progress > 0) {
            this.done = Math.max(0, this.progress);
        }
        // Keep legacy field mirrored so older UI (if any) still reads correctly.
        this.progress = this.done;
    }

    /** Current completed kills (canonical). */
    public int getDone() {
        return (done > 0) ? done : Math.max(0, progress);
    }

    /** Increment kills done by delta (>=1). */
    public void increment(int delta) {
        if (delta <= 0) return;
        this.done = Math.max(0, getDone() + delta);
        this.progress = this.done; // keep legacy in sync
    }

    /** Convenience: +1 kill. */
    public void increment() { increment(1); }

    /** True if required kills reached. */
    public boolean isComplete() { return getDone() >= required; }

    /** Remaining kills to finish the task. */
    public int remaining() { return Math.max(0, required - getDone()); }
}
