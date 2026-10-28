package com.uykutakip.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/** An immutable record revision. Parents are the versions the author actually observed. */
public final class Revision {
    public final String id;
    public final String recordId;
    public final String deviceId;
    public final String kind;
    public final List<String> parents;
    public final String payload;
    public final boolean deleted;

    public Revision(String id, String recordId, String deviceId, String kind,
                    List<String> parents, String payload, boolean deleted) {
        this.id = requireUuid(id);
        this.recordId = requireUuid(recordId);
        this.deviceId = requireUuid(deviceId);
        if (kind == null || !kind.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid record kind");
        }
        this.kind = kind;
        Objects.requireNonNull(parents, "parents");
        TreeSet<String> sorted = new TreeSet<>();
        for (String parent : parents) {
            if (!sorted.add(requireUuid(parent)) || id.equals(parent)) {
                throw new IllegalArgumentException("Duplicate or self parent");
            }
        }
        this.parents = Collections.unmodifiableList(new ArrayList<>(sorted));
        this.payload = Objects.requireNonNull(payload, "payload");
        if (deleted && !payload.isEmpty()) throw new IllegalArgumentException("Deletion must not contain a payload");
        this.deleted = deleted;
    }

    private static String requireUuid(String value) {
        Objects.requireNonNull(value, "UUID");
        if (!UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException("Expected canonical lowercase UUID");
        }
        return value;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Revision)) return false;
        Revision r = (Revision) other;
        return id.equals(r.id) && recordId.equals(r.recordId) && deviceId.equals(r.deviceId)
                && kind.equals(r.kind) && parents.equals(r.parents)
                && payload.equals(r.payload) && deleted == r.deleted;
    }

    @Override public int hashCode() {
        return Objects.hash(id, recordId, deviceId, kind, parents, payload, deleted);
    }
}
