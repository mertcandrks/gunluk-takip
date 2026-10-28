package com.uykutakip.sync;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Payload for the future bidirectional transport, distinct from legacy snapshots. */
public final class RevisionCodec {
    private static final int MAGIC = 0x47545233;
    private static final int MAX_PACKET = 32 * 1024 * 1024;
    private static final int MAX_RECORDS = 100_000;
    private static final int MAX_PARENTS = 1024;
    private static final int MAX_TEXT = 1024 * 1024;

    private RevisionCodec() {}

    public static byte[] encode(List<Revision> revisions) throws IOException {
        if (revisions.size() > MAX_RECORDS) throw new IOException("Too many revisions");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(1);
        out.writeInt(revisions.size());
        for (Revision r : revisions) {
            text(out, r.id); text(out, r.recordId); text(out, r.deviceId); text(out, r.kind);
            if (r.parents.size() > MAX_PARENTS) throw new IOException("Too many parents");
            out.writeInt(r.parents.size());
            for (String parent : r.parents) text(out, parent);
            out.writeBoolean(r.deleted);
            text(out, r.payload);
            if (bytes.size() > MAX_PACKET) throw new IOException("Revision packet too large");
        }
        return bytes.toByteArray();
    }

    public static List<Revision> decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_PACKET) throw new IOException("Revision packet too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Unsupported revision protocol");
        int count = bounded(in.readInt(), MAX_RECORDS);
        List<Revision> revisions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = text(in), record = text(in), device = text(in), kind = text(in);
            int parentCount = bounded(in.readInt(), MAX_PARENTS);
            List<String> parents = new ArrayList<>();
            for (int p = 0; p < parentCount; p++) parents.add(text(in));
            int deleted = in.readUnsignedByte();
            if (deleted > 1) throw new IOException("Invalid deletion marker");
            String payload = text(in);
            try {
                revisions.add(new Revision(id, record, device, kind, parents, payload, deleted == 1));
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid revision", failure);
            }
        }
        if (in.available() != 0) throw new IOException("Unexpected trailing data");
        return revisions;
    }

    private static int bounded(int value, int max) throws IOException {
        if (value < 0 || value > max) throw new IOException("Invalid field size");
        return value;
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
        } catch (CharacterCodingException failure) { throw new IOException("Invalid Unicode", failure); }
        bounded(bytes.length, MAX_TEXT);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInputStream in) throws IOException {
        int length = bounded(in.readInt(), MAX_TEXT);
        if (length > in.available()) throw new EOFException("Truncated text");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
}
