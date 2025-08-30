package com.obliviongatestudio.akthosidle.domain.services;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.obliviongatestudio.akthosidle.data.dtos.Snapshot;
import com.obliviongatestudio.akthosidle.domain.model.Job;

public class TickServiceTest {
    @Test public void awards_jobs_on_catchup() {
        Snapshot s = new Snapshot();
        Job j = new Job();
        j.intervalMs = 1000;
        j.xpPerTick = 5;
        j.currencyPerTick = 2;
        s.jobs = List.of(j);
        s.timestampMs = 0;
        TickService svc = new TickService();
        svc.applyCatchUp(s, 3000);
        assertEquals(15, j.accumulatedXp);
        assertEquals(6, j.accumulatedCurrency);
        assertEquals(0, j.progressMs);
    }
}
