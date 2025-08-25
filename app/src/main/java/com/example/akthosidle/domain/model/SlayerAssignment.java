package com.example.akthosidle.domain.model;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

/** Persisted Slayer task info. Backward-compatible with older saves that used "progress". */
public class SlayerAssignment {

    // If you ever had "region" in older saves, this will migrate it too.
    @SerializedName(value = "regionId", alternate = { "region" })
    public String regionId;

    public String monsterId;
    public String label;

    /** How many kills are required (clamped to >= 1). */
    public int required = 1;

    /** Current progress; migrated from legacy "progress" if present. */
    @SerializedName(value = "done", alternate = { "progress" })
    public int done = 0;

    /** Extra points granted on completion (optional). */
    public int completionBonus = 0;

    /** When assigned (for future UX/expiry). */
    public long assignedAtMs = System.currentTimeMillis();

    public SlayerAssignment() { }

    public SlayerAssignment(String regionId,
                            String monsterId,
                            @Nullable String label,
                            int required,
                            int completionBonus) {
        this.regionId = regionId;
        this.monsterId = monsterId;
        this.label = (label == null ? monsterId : label);
        this.required = Math.max(1, required);
        this.completionBonus = Math.max(0, completionBonus);
        this.done = 0;
        this.assignedAtMs = System.currentTimeMillis();
    }

    public boolean isComplete() { return getDone() >= Math.max(1, required); }
    public int remaining()      { return Math.max(0, Math.max(1, required) - getDone()); }
    public int getProgress()    { return Math.min(getDone(), Math.max(1, required)); }
    public int getDone()        { return Math.max(0, done); }

    /** Optional convenience: increment by one without exceeding required. */
    public void increment() {
        if (!isComplete()) done = getDone() + 1;
    }

    /** Optional convenience: 0..100 progress percent for UI. */
    public int progressPercent() {
        final int r = Math.max(1, required);
        return (int) Math.round(100.0 * Math.min(getDone(), r) / r);
    }
}
