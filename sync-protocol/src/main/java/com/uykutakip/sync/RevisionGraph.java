package com.uykutakip.sync;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Pure merge engine. Callers persist the accepted graph atomically before acknowledging it. */
public final class RevisionGraph {
    private final Map<String, Revision> revisions;

    public RevisionGraph() { revisions = Collections.emptyMap(); }

    private RevisionGraph(Map<String, Revision> revisions) {
        this.revisions = Collections.unmodifiableMap(new TreeMap<>(revisions));
    }

    public RevisionGraph merge(Collection<Revision> incoming) {
        Map<String, Revision> combined = new TreeMap<>(revisions);
        for (Revision revision : incoming) {
            Revision previous = combined.putIfAbsent(revision.id, revision);
            if (previous != null && !previous.equals(revision)) {
                throw new IllegalArgumentException("Operation ID reused with different content");
            }
        }
        validate(combined);
        return new RevisionGraph(combined);
    }

    public List<Revision> all() {
        return Collections.unmodifiableList(new ArrayList<>(revisions.values()));
    }

    /** Multiple heads mean a conflict, including concurrent deletion and editing. */
    public List<Revision> heads(String recordId) {
        Set<String> superseded = new HashSet<>();
        for (Revision revision : revisions.values()) {
            if (revision.recordId.equals(recordId)) superseded.addAll(revision.parents);
        }
        List<Revision> heads = new ArrayList<>();
        for (Revision revision : revisions.values()) {
            if (revision.recordId.equals(recordId) && !superseded.contains(revision.id)) heads.add(revision);
        }
        return Collections.unmodifiableList(heads);
    }

    private static void validate(Map<String, Revision> revisions) {
        Map<String, String> kinds = new HashMap<>();
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (Revision revision : revisions.values()) {
            String kind = kinds.putIfAbsent(revision.recordId, revision.kind);
            if (kind != null && !kind.equals(revision.kind)) throw new IllegalArgumentException("Record kind changed");
            remaining.put(revision.id, revision.parents.size());
            if (revision.parents.isEmpty()) ready.add(revision.id);
            for (String id : revision.parents) {
                Revision parent = revisions.get(id);
                if (parent == null || !parent.recordId.equals(revision.recordId)) {
                    throw new IllegalArgumentException("Missing or unrelated parent");
                }
                children.computeIfAbsent(id, key -> new ArrayList<>()).add(revision.id);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String child : children.getOrDefault(id, Collections.emptyList())) {
                int count = remaining.get(child) - 1;
                remaining.put(child, count);
                if (count == 0) ready.add(child);
            }
        }
        if (visited != revisions.size()) throw new IllegalArgumentException("Cyclic revision history");
    }
}
