package com.andrews.archivedownloader.network;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;

final class PersistentIndexParser {
    private static final int SUPPORTED_VERSION = 1;

    private PersistentIndexParser() {
    }

    static PersistentIndexData parse(byte[] buffer) {
        ByteBuffer data = ByteBuffer.wrap(buffer);
        data.order(ByteOrder.BIG_ENDIAN);

        int version = Short.toUnsignedInt(data.getShort());
        if (version != SUPPORTED_VERSION) {
            throw new CompletionException(new IllegalArgumentException("Unsupported persistent index version: " + version));
        }
        long updatedAt = data.getLong();

        data.order(ByteOrder.LITTLE_ENDIAN);
        List<String> allTags = readStringList(data);
        List<String> allAuthors = readStringList(data);
        List<String> allCategories = readStringList(data);

        int schemaStylesLength = data.getInt();
        if (schemaStylesLength < 0) {
            long unsigned = Integer.toUnsignedLong(schemaStylesLength);
            throw new CompletionException(new IllegalArgumentException("Invalid schema styles length: " + unsigned));
        }
        byte[] schemaStylesBytes = new byte[schemaStylesLength];
        data.get(schemaStylesBytes);

        List<PersistentChannel> channels = new ArrayList<>();
        while (data.hasRemaining()) {
            channels.add(readChannel(data));
        }

        return new PersistentIndexData(updatedAt, allTags, allAuthors, allCategories, schemaStylesBytes, channels);
    }

    private static List<String> readStringList(ByteBuffer buffer) {
        int count = Short.toUnsignedInt(buffer.getShort());
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readString(buffer));
        }
        return values;
    }

    private static PersistentChannel readChannel(ByteBuffer buffer) {
        String code = readString(buffer);
        String name = readString(buffer);
        String description = readString(buffer);
        int category = Short.toUnsignedInt(buffer.getShort());

        int tagCount = Short.toUnsignedInt(buffer.getShort());
        List<Integer> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            tags.add(Short.toUnsignedInt(buffer.getShort()));
        }

        String path = readString(buffer);

        long entriesCountUnsigned = Integer.toUnsignedLong(buffer.getInt());
        if (entriesCountUnsigned > Integer.MAX_VALUE) {
            throw new CompletionException(new IllegalArgumentException("Channel entries exceed max int: " + entriesCountUnsigned));
        }
        int entriesCount = (int) entriesCountUnsigned;

        List<PersistentEntry> entries = new ArrayList<>(entriesCount);
        for (int i = 0; i < entriesCount; i++) {
            entries.add(readEntry(buffer));
        }

        return new PersistentChannel(code, name, description, category, tags, path, entries);
    }

    private static PersistentEntry readEntry(ByteBuffer buffer) {
        String id = readString(buffer);
        List<String> codes = parseCodes(readString(buffer));
        String name = readString(buffer);

        int authorCount = Short.toUnsignedInt(buffer.getShort());
        List<Integer> authors = new ArrayList<>(authorCount);
        for (int i = 0; i < authorCount; i++) {
            authors.add(Short.toUnsignedInt(buffer.getShort()));
        }

        int tagCount = Short.toUnsignedInt(buffer.getShort());
        List<Integer> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            tags.add(Short.toUnsignedInt(buffer.getShort()));
        }

        buffer.order(ByteOrder.BIG_ENDIAN);
        long updatedAt = buffer.getLong();
        long archivedAt = buffer.getLong();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        String path = readString(buffer);

        int mainImageLength = Short.toUnsignedInt(buffer.getShort());
        String mainImagePath = null;
        if (mainImageLength > 0) {
            byte[] bytes = new byte[mainImageLength];
            buffer.get(bytes);
            mainImagePath = new String(bytes, StandardCharsets.UTF_8);
        }

        return new PersistentEntry(id, codes, name, authors, tags, updatedAt, archivedAt, path, mainImagePath);
    }

    private static String readString(ByteBuffer buffer) {
        int length = Short.toUnsignedInt(buffer.getShort());
        if (length == 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> parseCodes(String codesString) {
        if (codesString == null || codesString.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codesString.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}

record PersistentIndexData(
    long updatedAt,
    List<String> allTags,
    List<String> allAuthors,
    List<String> allCategories,
    byte[] schemaStylesBytes,
    List<PersistentChannel> channels
) {
}

record PersistentChannel(
    String code,
    String name,
    String description,
    int category,
    List<Integer> tags,
    String path,
    List<PersistentEntry> entries
) {
}

record PersistentEntry(
    String id,
    List<String> codes,
    String name,
    List<Integer> authors,
    List<Integer> tags,
    long updatedAt,
    long archivedAt,
    String path,
    String mainImagePath
) {
}
