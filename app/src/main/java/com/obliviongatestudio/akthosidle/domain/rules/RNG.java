package com.obliviongatestudio.akthosidle.domain.rules;

import java.util.Random;

public final class RNG {
    private static final Random rnd = new Random();

    private RNG() {} // prevent instantiation

    /** Returns true with probability p (0.0 - 1.0) */
    public static boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    /** Returns random int between min and max (inclusive) */
    public static int range(int min, int max) {
        if (max < min) return min; // safety
        return min + rnd.nextInt(max - min + 1);
    }

    /** Returns a random double [0.0, 1.0) */
    public static double next() {
        return rnd.nextDouble();
    }

    /** Optionally reseed (useful for tests or deterministic runs) */
    public static void reseed(long seed) {
        rnd.setSeed(seed);
    }
}
