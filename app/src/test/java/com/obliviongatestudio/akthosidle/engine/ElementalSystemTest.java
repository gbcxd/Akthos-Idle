package com.obliviongatestudio.akthosidle.engine;

import static org.junit.Assert.*;

import org.junit.Test;

import com.obliviongatestudio.akthosidle.domain.model.Element;

public class ElementalSystemTest {
    @Test public void fire_beats_earth() {
        assertEquals(1.2, ElementalSystem.modifier(Element.FIRE, Element.EARTH), 0.0001);
    }

    @Test public void water_loses_to_air() {
        assertEquals(0.8, ElementalSystem.modifier(Element.WATER, Element.AIR), 0.0001);
    }
}
