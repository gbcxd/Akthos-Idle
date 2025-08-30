package com.obliviongatestudio.akthosidle.engine;

import static org.junit.Assert.*;

import org.junit.Test;

import com.obliviongatestudio.akthosidle.domain.model.Job;

public class JobEngineTest {
    @Test public void apply_progress_awards_ticks() {
        Job j = new Job();
        j.intervalMs = 1000;
        j.xpPerTick = 5;
        j.currencyPerTick = 2;
        JobEngine.applyProgress(j, 2500);
        assertEquals(0, j.progressMs);
        assertEquals(10, j.accumulatedXp);
        assertEquals(4, j.accumulatedCurrency);
    }
}
