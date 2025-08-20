package com.example.akthosidle.domain.services;

import androidx.annotation.Nullable;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.PlayerCharacter;

import java.util.Map;

public class SkillService {
    private final GameRepository repo;
    private Action running;          // current action def
    private long startMs;            // when job started
    private long doneMs;             // next completion time (start + duration)

    public SkillService(GameRepository r) { this.repo = r; }

    public @Nullable Action getRunning() { return running; }

    public boolean canStart(Action a) {
        return repo.loadOrCreatePlayer().skill(a.skill).level >= a.reqLevel;
    }

    public boolean start(Action a) {
        if (!canStart(a)) return false;
        running = a;
        long now = System.currentTimeMillis();
        startMs = now;
        doneMs = now + a.durationMs;
        repo.setLastSeen(now); // persist for offline calc
        repo.save();
        return true;
    }

    public void stop() { running = null; repo.save(); }

    /** Call this from a 250–500ms handler or your TickService. */
    public void tick() {
        if (running == null) return;
        long now = System.currentTimeMillis();
        while (running != null && now >= doneMs) {
            grant(running);
            startMs = doneMs;
            doneMs += running.durationMs; // auto-repeat for idle loop
        }
    }

    /** When app opens, award offline completions. */
    public void applyOfflineProgress() {
        if (running == null) return;
        long last = Math.max(repo.getLastSeen(), startMs);
        long now = System.currentTimeMillis();
        if (now <= last) return;
        long cycles = (now - last) / running.durationMs;
        for (long i=0;i<cycles;i++) grant(running);
        // advance next done
        long remainder = (now - last) % running.durationMs;
        startMs = now - remainder;
        doneMs = startMs + running.durationMs;
    }

    private void grant(Action a) {
        // rewards
        for (Map.Entry<String,Integer> e : a.outputs.entrySet())
            repo.addPendingLoot(e.getKey(), repo.itemName(e.getKey()), e.getValue());

        // exp
        PlayerCharacter pc = repo.loadOrCreatePlayer();
        boolean leveled = pc.skill(a.skill).addXp(a.exp);
        if (leveled) repo.toast("Level up: " + a.skill.name() + " " + pc.skill(a.skill).level); // implement UI hook as needed

        repo.save();
    }
}

