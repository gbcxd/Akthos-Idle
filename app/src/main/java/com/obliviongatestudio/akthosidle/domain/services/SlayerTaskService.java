package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.SlayerAssignment;

/** Assigns and tracks slayer tasks. */
public class SlayerTaskService {
    private SlayerAssignment current;

    public void assign(SlayerAssignment assignment) {
        this.current = assignment;
    }

    public SlayerAssignment getCurrent() {
        return current;
    }
}
