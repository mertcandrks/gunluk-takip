package com.uykutakip.sync;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RevisionCodecTest {
    private Revision sample(String payload) {
        return new Revision("00000000-0000-4000-8000-000000000001",
                "00000000-0000-4000-8000-000000000100", "00000000-0000-4000-8000-000000000200",
                "agenda", List.of(), payload, false);
    }

    @Test public void matchesSharedGoFixture() throws Exception {
        byte[] expected;
        try (var stream = getClass().getResourceAsStream("/revision-v1.hex")) {
            assertNotNull(stream);
            String hex = new String(stream.readAllBytes(), StandardCharsets.US_ASCII).trim();
            expected = new byte[hex.length() / 2];
            for (int i = 0; i < expected.length; i++) expected[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
        }
        assertArrayEquals(expected, RevisionCodec.encode(List.of(sample("note"))));
        assertEquals(List.of(sample("note")), RevisionCodec.decode(expected));
    }

    @Test public void unicodeRoundTripAndTruncatedPackets() throws Exception {
        List<Revision> records = List.of(sample("\u00d6\u011f\u00fcn, \u0131\u015f\u0131k, \ud83c\udf19"));
        byte[] encoded = RevisionCodec.encode(records);
        assertEquals(records, RevisionCodec.decode(encoded));
        for (int n = 0; n < encoded.length; n++) {
            byte[] cut = Arrays.copyOf(encoded, n);
            assertThrows(IOException.class, () -> RevisionCodec.decode(cut));
        }
        assertThrows(IOException.class, () -> RevisionCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
    }

    @Test public void rejectsUnknownVersionsAndInvalidText() throws Exception {
        byte[] encoded = RevisionCodec.encode(List.of(sample("note")));
        encoded[7] = 2;
        assertThrows(IOException.class, () -> RevisionCodec.decode(encoded));
        assertThrows(IOException.class, () -> RevisionCodec.encode(List.of(sample("\ud800"))));
        byte[] malformed = RevisionCodec.encode(List.of(sample("note")));
        malformed[malformed.length - 1] = (byte) 0xff;
        assertThrows(IOException.class, () -> RevisionCodec.decode(malformed));
    }
}
