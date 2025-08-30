package com.obliviongatestudio.akthosidle.engine;

import com.obliviongatestudio.akthosidle.domain.model.Element;

import java.util.EnumMap;
import java.util.Map;

/**
 * Defines elemental strengths and weaknesses.
 */
public final class ElementalSystem {
    private static final Map<Element, Map<Element, Double>> MATRIX = new EnumMap<>(Element.class);
    static {
        for (Element e : Element.values()) {
            MATRIX.put(e, new EnumMap<>(Element.class));
        }
        // Strength/weakness cycle
        set(Element.FIRE,  Element.EARTH, 1.2);
        set(Element.FIRE,  Element.WATER, 0.8);

        set(Element.WATER, Element.FIRE,  1.2);
        set(Element.WATER, Element.AIR,   0.8);

        set(Element.AIR,   Element.WATER, 1.2);
        set(Element.AIR,   Element.EARTH, 0.8);

        set(Element.EARTH, Element.AIR,   1.2);
        set(Element.EARTH, Element.FIRE,  0.8);
    }

    private static void set(Element atk, Element def, double mod) {
        MATRIX.get(atk).put(def, mod);
    }

    private ElementalSystem() {}

    /** Returns elemental modifier (1.0 if no relation). */
    public static double modifier(Element atk, Element def) {
        if (atk == null || def == null) return 1.0;
        Map<Element, Double> row = MATRIX.get(atk);
        if (row == null) return 1.0;
        Double v = row.get(def);
        return v != null ? v : 1.0;
    }
}
