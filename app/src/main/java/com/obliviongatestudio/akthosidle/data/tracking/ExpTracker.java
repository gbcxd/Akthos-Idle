package com.obliviongatestudio.akthosidle.data.tracking;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Sliding-window XP tracker to compute XP/hour for selected sources. */
public class ExpTracker {
    public static final long DEFAULT_WINDOW_MS = 5 * 60 * 1000; // 5 minutes
    private static final int MAX_EVENTS_PER_KEY = 2000;

    private static final class Event { long t; int amt; Event(long t,int a){this.t=t;this.amt=a;} }
    private final Map<String, ArrayDeque<Event>> events = new HashMap<>();

    public synchronized void note(String key, int amount) {
        if (key == null || amount <= 0) return;
        long now = System.currentTimeMillis();
        ArrayDeque<Event> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(new Event(now, amount));
        prune(q, now, DEFAULT_WINDOW_MS);
        while (q.size() > MAX_EVENTS_PER_KEY) q.pollFirst();
    }

    public synchronized double ratePerHour(Set<String> keys, long windowMs) {
        if (keys == null || keys.isEmpty()) return 0.0;
        long now = System.currentTimeMillis();
        long win = Math.max(1_000L, windowMs);
        long start = now - win;
        int sum = 0;
        for (String k : keys) {
            ArrayDeque<Event> q = events.get(k);
            if (q == null) continue;
            prune(q, now, win);
            for (Event e : q) if (e.t >= start) sum += e.amt;
        }
        double hours = win / 3600000.0;
        return sum / hours; // xp/h
    }

    private void prune(ArrayDeque<Event> q, long now, long windowMs) {
        long minT = now - windowMs;
        while (!q.isEmpty() && q.peekFirst().t < minT) q.pollFirst();
    }

    public synchronized Set<String> keys() { return new HashSet<>(events.keySet()); }
}
