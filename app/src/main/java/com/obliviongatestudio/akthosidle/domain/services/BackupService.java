package com.obliviongatestudio.akthosidle.domain.services;

import java.util.HashSet;
import java.util.Set;

/** Simple include/exclude rules for backups. */
public class BackupService {
    private final Set<String> include = new HashSet<>();
    private final Set<String> exclude = new HashSet<>();

    public Set<String> getInclude() {
        return include;
    }

    public Set<String> getExclude() {
        return exclude;
    }

    public void addInclude(String path) {
        include.add(path);
    }

    public void addExclude(String path) {
        exclude.add(path);
    }
}
