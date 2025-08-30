package com.obliviongatestudio.akthosidle.domain.model;

/** Simple job state for catch-up calculations. */
public class Job {
    public long intervalMs;
    public long progressMs;
    public int xpPerTick;
    public int currencyPerTick;
    public int accumulatedXp;
    public int accumulatedCurrency;
}
