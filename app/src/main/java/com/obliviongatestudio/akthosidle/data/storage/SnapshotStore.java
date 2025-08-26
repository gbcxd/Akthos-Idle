package com.obliviongatestudio.akthosidle.data.storage;

import com.obliviongatestudio.akthosidle.data.dtos.Snapshot;

public interface SnapshotStore {
    Snapshot load();
    void save(Snapshot s);
}
