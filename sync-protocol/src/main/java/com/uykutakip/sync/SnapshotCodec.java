package com.uykutakip.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotCodec {
    private static final int MAGIC = 0x47544B50;
    private static final int MAX_RECORDS = 1_000_000;
    private static final int MAX_TEXT_BYTES = 1_000_000;

    private SnapshotCodec() {}

    public static byte[] encode(SyncSnapshot value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(SyncSnapshot.PROTOCOL_VERSION);
            out.writeLong(value.generatedAtMillis);
            writeNullableLong(out, value.activeSleepStartMillis);
            out.writeLong(value.activeSleepUpdatedAtMillis);
            writeList(out, value.sleeps, (stream, item) -> {
                stream.writeLong(item.id); stream.writeLong(item.startMillis); stream.writeLong(item.endMillis); stream.writeLong(item.updatedAtMillis);
            });
            out.writeBoolean(value.activeMeal != null);
            if (value.activeMeal != null) writeMeal(out, value.activeMeal);
            writeList(out, value.meals, SnapshotCodec::writeMeal);
            writeList(out, value.water, (stream, item) -> {
                stream.writeLong(item.id); stream.writeInt(item.amountMl); stream.writeLong(item.timestampMillis); stream.writeLong(item.updatedAtMillis);
            });
            out.writeInt(value.waterGoalMl);
            out.writeLong(value.waterGoalUpdatedAtMillis);
            writeList(out, value.agenda, (stream, item) -> {
                stream.writeLong(item.id); writeString(stream, item.day); writeString(stream, item.text);
                stream.writeBoolean(item.task); stream.writeBoolean(item.completed); stream.writeBoolean(item.notDone);
                stream.writeBoolean(item.repeatDaily); stream.writeBoolean(item.generalGoal);
                stream.writeInt(item.streak); stream.writeBoolean(item.notifyEnabled);
                stream.writeInt(item.notifyHour); stream.writeInt(item.notifyMinute);
                writeString(stream, item.category); writeString(stream, item.subcategory);
                writeNullableString(stream, item.lastCompletedDay);
                stream.writeLong(item.updatedAtMillis);
                writeString(stream, item.projectPartsJson);
                writeString(stream, item.dailyCountsJson);
            });
            writeList(out, value.agendaTemplates, (stream, item) -> {
                writeString(stream, item.id); writeString(stream, item.text); writeString(stream, item.category); writeString(stream, item.subcategory);
                stream.writeBoolean(item.favorite); stream.writeBoolean(item.builtIn); stream.writeBoolean(item.hidden); stream.writeLong(item.updatedAtMillis);
            });
            writeList(out, value.agendaCategories, (stream, item) -> {
                stream.writeLong(item.id); writeString(stream, item.category); writeString(stream, item.subcategory);
                stream.writeBoolean(item.hidden); stream.writeLong(item.updatedAtMillis);
            });
            writeList(out, value.hiddenAgendaSuggestions, (stream, item) -> {
                writeString(stream, item.key); stream.writeLong(item.hiddenAtMillis);
            });
            writeList(out, value.privateActivities, (stream, item) -> {
                stream.writeLong(item.id); stream.writeLong(item.startMillis); writeNullableLong(stream, item.endMillis); stream.writeLong(item.updatedAtMillis);
            });
            writeList(out, value.deletions, (stream, item) -> {
                writeString(stream, item.kind); stream.writeLong(item.id); stream.writeLong(item.updatedAtMillis);
            });
        }
        return bytes.toByteArray();
    }

    public static SyncSnapshot decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Gecersiz senkron dosyasi");
            int version = in.readInt();
            if (version < 2 || version > SyncSnapshot.PROTOCOL_VERSION) throw new IOException("Desteklenmeyen protokol: " + version);
            long generated = in.readLong();
            Long activeSleep = readNullableLong(in);
            long activeSleepUpdated = version >= 3 ? in.readLong() : (activeSleep == null ? 0L : activeSleep);
            var sleeps = readList(in, stream -> new SyncSnapshot.Sleep(stream.readLong(), stream.readLong(), stream.readLong(), stream.readLong()));
            SyncSnapshot.Meal activeMeal = in.readBoolean() ? readMeal(in) : null;
            var meals = readList(in, SnapshotCodec::readMeal);
            var water = readList(in, stream -> {
                long id = stream.readLong(); int amount = stream.readInt(); long timestamp = stream.readLong();
                return new SyncSnapshot.Water(id, amount, timestamp, version >= 3 ? stream.readLong() : timestamp);
            });
            int goal = in.readInt();
            long goalUpdated = version >= 3 ? in.readLong() : generated;
            var agenda = readList(in, stream -> new SyncSnapshot.Agenda(
                    stream.readLong(), readString(stream), readString(stream), stream.readBoolean(), stream.readBoolean(),
                    stream.readBoolean(), stream.readBoolean(), stream.readBoolean(), stream.readInt(), stream.readBoolean(), stream.readInt(), stream.readInt(),
                    version >= 4 ? readString(stream) : "", version >= 4 ? readString(stream) : "",
                    version >= 5 ? readNullableString(stream) : null,
                    version >= 3 ? stream.readLong() : generated, version >= 6 ? readString(stream) : "[]",
                    version >= 7 ? readString(stream) : "{}"));
            var agendaTemplates = version >= 5 ? readList(in, stream -> new SyncSnapshot.AgendaTemplate(
                    readString(stream), readString(stream), readString(stream), readString(stream), stream.readBoolean(),
                    stream.readBoolean(), stream.readBoolean(), stream.readLong())) : List.<SyncSnapshot.AgendaTemplate>of();
            var agendaCategories = version >= 5 ? readList(in, stream -> new SyncSnapshot.AgendaCategory(
                    stream.readLong(), readString(stream), readString(stream), stream.readBoolean(), stream.readLong())) : List.<SyncSnapshot.AgendaCategory>of();
            var hiddenSuggestions = version >= 5 ? readList(in, stream -> new SyncSnapshot.HiddenAgendaSuggestion(
                    readString(stream), stream.readLong())) : List.<SyncSnapshot.HiddenAgendaSuggestion>of();
            var privateItems = readList(in, stream -> {
                long id = stream.readLong(); long start = stream.readLong(); Long end = readNullableLong(stream);
                return new SyncSnapshot.PrivateActivity(id, start, end, version >= 3 ? stream.readLong() : (end == null ? start : end));
            });
            var deletions = version >= 3
                    ? readList(in, stream -> new SyncSnapshot.Deletion(readString(stream), stream.readLong(), stream.readLong()))
                    : List.<SyncSnapshot.Deletion>of();
            if (in.available() != 0) throw new IOException("Senkron paketinde beklenmeyen veri var");
            return new SyncSnapshot(generated, activeSleep, activeSleepUpdated, sleeps, activeMeal, meals, water, goal,
                    goalUpdated, agenda, agendaTemplates, agendaCategories, hiddenSuggestions, privateItems, deletions);
        }
    }

    private static void writeMeal(DataOutputStream out, SyncSnapshot.Meal item) throws IOException {
        out.writeLong(item.id); writeString(out, item.type); out.writeLong(item.startMillis); out.writeLong(item.endMillis);
        writeString(out, item.itemsJson); out.writeBoolean(item.quick); out.writeLong(item.updatedAtMillis);
    }

    private static SyncSnapshot.Meal readMeal(DataInputStream in) throws IOException {
        return new SyncSnapshot.Meal(in.readLong(), readString(in), in.readLong(), in.readLong(), readString(in), in.readBoolean(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IOException("Metin cok uzun");
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_TEXT_BYTES) throw new IOException("Gecersiz metin boyutu");
        return new String(readExactly(in, size), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeNullableLong(DataOutputStream out, Long value) throws IOException {
        out.writeBoolean(value != null); if (value != null) out.writeLong(value);
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null); if (value != null) writeString(out, value);
    }

    private static Long readNullableLong(DataInputStream in) throws IOException { return in.readBoolean() ? in.readLong() : null; }
    private static String readNullableString(DataInputStream in) throws IOException { return in.readBoolean() ? readString(in) : null; }

    private static byte[] readExactly(DataInputStream in, int size) throws IOException {
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return bytes;
    }

    private static <T> void writeList(DataOutputStream out, List<T> items, Writer<T> writer) throws IOException {
        out.writeInt(items.size()); for (T item : items) writer.write(out, item);
    }

    private static <T> List<T> readList(DataInputStream in, Reader<T> reader) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_RECORDS) throw new IOException("Gecersiz kayit sayisi");
        var result = new ArrayList<T>(size); for (int i = 0; i < size; i++) result.add(reader.read(in));
        return result;
    }

    private interface Writer<T> { void write(DataOutputStream out, T item) throws IOException; }
    private interface Reader<T> { T read(DataInputStream in) throws IOException; }
}
