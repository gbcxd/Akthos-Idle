package com.obliviongatestudio.akthosidle.domain.services;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Simple loot table utility for deterministic rolls. */
public class LootService {
    private final Map<String, List<String>> tables = new HashMap<>();

    public void registerTable(String id, List<String> entries) {
        tables.put(id, entries);
    }

    public List<String> rollTable(String tableId, long seed) {
        List<String> table = tables.get(tableId);
        if (table == null || table.isEmpty()) return Collections.emptyList();
        Random rng = new Random(seed);
        String pick = table.get(rng.nextInt(table.size()));
        return Collections.singletonList(pick);
    }
}
