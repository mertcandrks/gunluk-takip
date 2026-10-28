package com.uykutakip.sync;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class RevisionGraphTest {
    private static String id(int n) { return String.format("00000000-0000-4000-8000-%012d", n); }
    private Revision revision(int n, String payload, boolean deleted, String... parents) {
        return new Revision(id(n), id(100), id(200), "agenda", List.of(parents), payload, deleted);
    }

    @Test public void concurrentEditAndDeleteRequireResolution() {
        Revision base = revision(1, "original", false);
        Revision edit = revision(2, "edited", false, base.id);
        Revision deletion = revision(3, "", true, base.id);
        RevisionGraph graph = new RevisionGraph().merge(List.of(base, deletion, edit));
        assertEquals(List.of(edit, deletion), graph.heads(base.recordId));
        Revision resolve = revision(4, "keep edit", false, edit.id, deletion.id);
        assertEquals(List.of(resolve), graph.merge(List.of(resolve)).heads(base.recordId));
        assertEquals(2, graph.heads(base.recordId).size());
    }

    @Test public void staleReplayCannotResurrectDeletedRecord() {
        Revision base = revision(1, "original", false);
        Revision deletion = revision(2, "", true, base.id);
        RevisionGraph graph = new RevisionGraph().merge(List.of(base, deletion)).merge(List.of(base));
        assertEquals(List.of(deletion), graph.heads(base.recordId));
    }

    @Test public void duplicateIdCannotOverwriteContent() {
        RevisionGraph graph = new RevisionGraph().merge(List.of(revision(1, "original", false)));
        assertThrows(IllegalArgumentException.class, () -> graph.merge(List.of(revision(1, "changed", false))));
        assertEquals("original", graph.all().get(0).payload);
    }

    @Test public void rejectsMissingParentsCyclesAndCrossRecordParents() {
        assertThrows(IllegalArgumentException.class, () -> new RevisionGraph().merge(List.of(revision(1, "", false, id(2)))));
        assertThrows(IllegalArgumentException.class, () -> new RevisionGraph().merge(List.of(
                revision(1, "", false, id(2)), revision(2, "", false, id(1)))));
        Revision unrelated = new Revision(id(2), id(101), id(200), "agenda", List.of(id(1)), "", false);
        assertThrows(IllegalArgumentException.class, () -> new RevisionGraph().merge(List.of(revision(1, "", false), unrelated)));
    }

    @Test public void rejectsKindChangesAndMalformedRevisions() {
        Revision other = new Revision(id(2), id(100), id(200), "sleep", List.of(), "", false);
        assertThrows(IllegalArgumentException.class, () -> new RevisionGraph().merge(List.of(revision(1, "", false), other)));
        assertThrows(IllegalArgumentException.class, () -> revision(1, "data", true));
        assertThrows(IllegalArgumentException.class, () -> revision(1, "", false, id(1)));
        assertThrows(IllegalArgumentException.class, () -> revision(1, "", false, id(2), id(2)));
    }

    @Test public void replayOrderAndBatchGroupingDoNotChangeMerge() {
        Revision base = revision(1, "initial", false);
        List<Revision> left = List.of(base, revision(2, "phone", false, base.id));
        List<Revision> right = List.of(base, revision(3, "desktop", false, base.id));
        RevisionGraph a = new RevisionGraph().merge(left).merge(right);
        RevisionGraph b = new RevisionGraph().merge(right).merge(left);
        assertEquals(a.all(), b.all());
        assertEquals(a.all(), a.merge(a.all()).all());
        Random random = new Random(749);
        for (int n = 0; n < 100; n++) {
            List<Revision> shuffled = new ArrayList<>(a.all());
            Collections.shuffle(shuffled, random);
            assertEquals(a.all(), new RevisionGraph().merge(shuffled).all());
        }
    }
}
