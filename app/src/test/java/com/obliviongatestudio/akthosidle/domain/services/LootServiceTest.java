package com.obliviongatestudio.akthosidle.domain.services;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class LootServiceTest {
    @Test public void roll_is_deterministic_with_seed() {
        LootService svc = new LootService();
        svc.registerTable("basic", List.of("a", "b", "c"));
        List<String> out1 = svc.rollTable("basic", 123L);
        List<String> out2 = svc.rollTable("basic", 123L);
        assertEquals(out1, out2);
        assertEquals(1, out1.size());
    }
}
