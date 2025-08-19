package com.example.akthosidle.domain.rules;

public final class DropMath {
    private DropMath() {}

    /** P(at least one success) across n independent trials with probability p each */
    public static double atLeastOne(double p, int n) {
        if (p <= 0.0 || n <= 0) return 0.0;
        if (p >= 1.0) return 1.0;
        return 1.0 - Math.pow(1.0 - p, n);
    }

    /** Expected number of successes across n trials with probability p each */
    public static double expectedSuccesses(double p, int n) {
        if (p <= 0.0 || n <= 0) return 0.0;
        if (p >= 1.0) return n;
        return p * n;
    }

    /** Convert "1 in X" odds (e.g., X=128) to probability p */
    public static double oneInToProb(int x) {
        if (x <= 1) return 1.0;
        return 1.0 / (double) x;
    }
}
