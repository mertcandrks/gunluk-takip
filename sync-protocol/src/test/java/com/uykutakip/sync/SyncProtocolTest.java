package com.uykutakip.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.GeneralSecurityException;
import java.util.List;
import org.junit.Test;

public class SyncProtocolTest {
    private SyncSnapshot sample() {
        return new SyncSnapshot(1234L, 100L, 1234L,
                List.of(new SyncSnapshot.Sleep(1, 10, 20, 21)), null, List.of(),
                List.of(new SyncSnapshot.Water(2, 125, 30, 31)), 2500, 1234L,
                List.of(new SyncSnapshot.Agenda(3, "2026-09-11", "Toplantı notu", true, true, false, false, false, 2, true, 20, 15, "İş", "Toplantı", "2026-09-11", 32, "[{\"id\":\"front\",\"title\":\"Frontend\",\"progress\":50}]", "{\"2026-10-17\":2}")),
                List.of(new SyncSnapshot.AgendaTemplate("template-1", "Toplantı hazırla", "İş", "Toplantı", true, false, false, 33)),
                List.of(new SyncSnapshot.AgendaCategory(-1, "İş", "Toplantı", false, 34)),
                List.of(new SyncSnapshot.HiddenAgendaSuggestion("eski\u001fİş\u001f", 35)),
                List.of(), List.of());
    }

    @Test public void snapshotRoundTripPreservesData() throws Exception {
        SyncSnapshot decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample()));
        assertEquals(1234L, decoded.generatedAtMillis);
        assertEquals(1, decoded.sleeps.size());
        assertEquals("Toplantı notu", decoded.agenda.get(0).text);
        assertEquals("2026-09-11", decoded.agenda.get(0).lastCompletedDay);
        assertEquals(sample().agenda.get(0).projectPartsJson, decoded.agenda.get(0).projectPartsJson);
        assertEquals(sample().agenda.get(0).dailyCountsJson, decoded.agenda.get(0).dailyCountsJson);
        assertEquals("Toplantı hazırla", decoded.agendaTemplates.get(0).text);
        assertEquals("İş", decoded.agendaCategories.get(0).category);
        assertEquals("eski\u001fİş\u001f", decoded.hiddenAgendaSuggestions.get(0).key);
        assertEquals(125, decoded.water.get(0).amountMl);
    }

    @Test public void securePacketRejectsWrongCode() throws Exception {
        var wire = new ByteArrayOutputStream();
        SecureSyncPacket.write(new DataOutputStream(wire), SnapshotCodec.encode(sample()), "12345678".toCharArray());
        assertThrows(GeneralSecurityException.class, () -> SecureSyncPacket.read(
                new DataInputStream(new ByteArrayInputStream(wire.toByteArray())), "87654321".toCharArray()));
    }

    @Test public void securePacketRoundTripPreservesSnapshot() throws Exception {
        var wire = new ByteArrayOutputStream();
        byte[] plain = SnapshotCodec.encode(sample());
        SecureSyncPacket.write(new DataOutputStream(wire), plain, "12345678".toCharArray());
        byte[] decodedPacket = SecureSyncPacket.read(new DataInputStream(new ByteArrayInputStream(wire.toByteArray())), "12345678".toCharArray());
        assertEquals("Toplantı notu", SnapshotCodec.decode(decodedPacket).agenda.get(0).text);
    }
}
