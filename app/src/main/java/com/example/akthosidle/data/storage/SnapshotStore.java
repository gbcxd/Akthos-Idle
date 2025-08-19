package com.example.akthosidle.data.storage;

import com.example.akthosidle.data.dtos.Snapshot;

public interface SnapshotStore {
    Snapshot load();
    void save(Snapshot s);
}
