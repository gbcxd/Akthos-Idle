package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.Quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal quest manager placeholder. Real implementation will handle
 * progression, rewards, and persistence.
 */
public class QuestService {
    private final List<Quest> active = new ArrayList<>();

    public List<Quest> getActive() {
        return active;
    }

    public void addQuest(Quest q) {
        if (q != null) active.add(q);
    }

    public void clearCompleted() {
        // TODO: evaluate quest completion and remove.
    }
}
