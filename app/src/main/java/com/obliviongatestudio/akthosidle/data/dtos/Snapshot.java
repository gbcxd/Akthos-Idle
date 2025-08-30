package com.obliviongatestudio.akthosidle.data.dtos;

import java.util.Collections;
import java.util.List;

import com.obliviongatestudio.akthosidle.domain.model.Job;

public class Snapshot {
    public long timestampMs;
    // keep these broad for now; tighten later
    public List<Object> skills;
    public List<Object> inventory;
    public List<Job> jobs;

    public static Snapshot empty() {
        Snapshot s = new Snapshot();
        s.timestampMs = System.currentTimeMillis();
        s.skills = Collections.emptyList();
        s.inventory = Collections.emptyList();
        s.jobs = Collections.emptyList();
        return s;
    }
}
