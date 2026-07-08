package com.andrews.archivedownloader.config;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Runtime catalog of supported servers and update metadata.
 */
public final class ServerDictionary {
    private static final String MOD_DATA_URL = "https://raw.githubusercontent.com/Llama-Collective/llama-archives/refs/heads/main/mod_data.json";
    private static final String OFFLINE_CACHE_DIR = "archivedownloader/offlinecache/global";
    private static final String MOD_DATA_CACHE_FILE = "mod_data.json";
    private static final int TIMEOUT_SECONDS = 10;
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build();

    public record ServerEntry(
        String id,
        String name,
        String owner,
        String repo,
        String branch,
        String description,
        String discordInviteUrl,
        String submissionsUrl,
        String downloadFolder,
        String websiteBase,
        String apiBase
    ) {}

    private static final List<String> DEFAULT_LFS_EXTENSIONS = List.of("mp4", "bin", "zip");
    private static volatile List<ServerEntry> servers = List.of();
    private static volatile String latestVersion = "";
    private static volatile String modPageUrl = "";
    private static volatile List<String> lfsExtensions = DEFAULT_LFS_EXTENSIONS;
    private static volatile CompletableFuture<Void> metadataLoadFuture;

    private ServerDictionary() {}

    public static CompletableFuture<Void> ensureLoaded() {
        CompletableFuture<Void> existing = metadataLoadFuture;
        if (existing != null) {
            return existing;
        }

        synchronized (ServerDictionary.class) {
            if (metadataLoadFuture != null) {
                return metadataLoadFuture;
            }
            metadataLoadFuture = fetchModData()
                .thenAccept(ServerDictionary::applyRemoteData)
                .exceptionally(throwable -> {
                    System.err.println("Failed to load server metadata: " + throwable.getMessage());
                    return null;
                });
            return metadataLoadFuture;
        }
    }

    public static List<ServerEntry> getServers() {
        return servers;
    }

    public static ServerEntry getDefaultServer() {
        List<ServerEntry> current = servers;
        if (current != null && !current.isEmpty()) {
            return current.get(0);
        }
        return defaultPlaceholderServer();
    }

    public static Optional<ServerEntry> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return servers.stream()
            .filter(s -> id.equalsIgnoreCase(s.id()))
            .findFirst();
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getModPageUrl() {
        return modPageUrl;
    }

    public static List<String> getLfsExtensions() {
        return lfsExtensions;
    }

    public static boolean isUpdateAvailable(String currentVersion) {
        String latest = latestVersion;
        if (latest == null || latest.isBlank() || currentVersion == null || currentVersion.isBlank()) {
            return false;
        }
        return compareVersions(latest, currentVersion) > 0;
    }

    private static CompletableFuture<RemoteModData> fetchModData() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MOD_DATA_URL))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Accept", "application/json")
            .header("User-Agent", "ArchiveDownloader/1.0 (+https://github.com/Llama-Collective/Archive-Downloader)")
            .GET()
            .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new CompletionException(new RuntimeException("HTTP " + response.statusCode() + " for " + MOD_DATA_URL));
                }
                String body = response.body();
                if (body == null || body.isBlank()) {
                    throw new CompletionException(new RuntimeException("Empty metadata response"));
                }
                writeModDataCache(body);
                RemoteModData parsed = GSON.fromJson(body, RemoteModData.class);
                if (parsed == null) {
                    throw new CompletionException(new RuntimeException("Empty metadata response"));
                }
                return parsed;
            })
            .handle((data, throwable) -> {
                if (throwable == null) {
                    return data;
                }
                String cached = readModDataCache();
                if (cached != null && !cached.isBlank()) {
                    RemoteModData parsed = GSON.fromJson(cached, RemoteModData.class);
                    if (parsed != null) {
                        System.out.println("[OfflineCache] Using cached mod_data.json");
                        return parsed;
                    }
                }
                throw unwrapCompletionException(throwable);
            });
    }

    private static CompletionException unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completion && completion.getCause() != null) {
            return new CompletionException(completion.getCause());
        }
        return new CompletionException(throwable);
    }

    private static Path getModDataCachePath() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve(OFFLINE_CACHE_DIR)
            .resolve(MOD_DATA_CACHE_FILE);
    }

    private static void writeModDataCache(String content) {
        try {
            Path cachePath = getModDataCachePath();
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(cachePath, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Failed to write mod_data cache: " + e.getMessage());
        }
    }

    private static String readModDataCache() {
        try {
            Path cachePath = getModDataCachePath();
            if (!Files.exists(cachePath) || !Files.isRegularFile(cachePath)) {
                return null;
            }
            return Files.readString(cachePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyRemoteData(RemoteModData data) {
        if (data == null) {
            return;
        }
        List<ServerEntry> parsedServers = sanitizeServers(data.server_list);
        if (!parsedServers.isEmpty()) {
            servers = List.copyOf(parsedServers);
        }
        latestVersion = safeTrim(data.latest_version);
        modPageUrl = safeTrim(data.mod_page);
        List<String> parsedLfsExtensions = sanitizeExtensions(data.lfs_extensions);
        if (!parsedLfsExtensions.isEmpty()) {
            lfsExtensions = List.copyOf(parsedLfsExtensions);
        }
    }

    private static List<ServerEntry> sanitizeServers(List<ServerEntry> rawServers) {
        if (rawServers == null || rawServers.isEmpty()) {
            return List.of();
        }
        List<ServerEntry> sanitized = new ArrayList<>();
        int index = 0;
        for (ServerEntry entry : rawServers) {
            if (entry == null) {
                continue;
            }
            String fallbackId = "server-" + index++;
            // id and downloadFolder are used to build on-disk paths (caches, download folders), so they
            // must be reduced to a single safe path segment before use — a remote mod_data.json must
            // not be able to steer writes outside the mod's directories via traversal/absolute paths.
            String id = sanitizePathToken(entry.id(), fallbackId);
            String name = sanitizeNonBlank(entry.name(), id.toUpperCase(Locale.ROOT));
            String owner = safeTrim(entry.owner());
            String repo = safeTrim(entry.repo());
            String branch = safeTrim(entry.branch());
            String description = safeTrim(entry.description());
            String discordInviteUrl = safeTrim(entry.discordInviteUrl());
            String submissionsUrl = safeTrim(entry.submissionsUrl());
            String downloadFolder = sanitizePathToken(entry.downloadFolder(), id);
            String websiteBase = safeTrim(entry.websiteBase());
            String apiBase = safeTrim(entry.apiBase());
            sanitized.add(new ServerEntry(
                id,
                name,
                owner,
                repo,
                branch,
                description,
                discordInviteUrl,
                submissionsUrl,
                downloadFolder,
                websiteBase,
                apiBase
            ));
        }
        return sanitized;
    }

    private static String sanitizeNonBlank(String value, String fallback) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * Collapses a remote value to a single safe path segment: every character outside
     * {@code [A-Za-z0-9._-]} (including all path separators, drive letters and UNC markers) is
     * replaced, and the pure traversal tokens "." / ".." are rejected. The result can never escape
     * the directory it is appended to via {@code Path.resolve}.
     */
    private static String sanitizePathToken(String value, String fallback) {
        String slug = safeTrim(value).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (slug.isEmpty() || slug.equals(".") || slug.equals("..")) {
            return fallback;
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
        }
        return slug;
    }

    private static String safeTrim(String value) {
        return value != null ? value.trim() : "";
    }

    private static ServerEntry defaultPlaceholderServer() {
        return new ServerEntry(
            "default",
            "Default Server",
            "",
            "",
            "",
            "",
            "",
            "",
            "downloads",
            "",
            ""
        );
    }

    private static List<String> sanitizeExtensions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            String normalized = safeTrim(value).toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && !sanitized.contains(normalized)) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private static int compareVersions(String left, String right) {
        List<Integer> leftParts = parseVersionParts(left);
        List<Integer> rightParts = parseVersionParts(right);
        int max = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.size() ? leftParts.get(i) : 0;
            int r = i < rightParts.size() ? rightParts.get(i) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String version) {
        if (version == null || version.isBlank()) {
            return List.of();
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int metadataIndex = normalized.indexOf('+');
        if (metadataIndex >= 0) {
            normalized = normalized.substring(0, metadataIndex);
        }
        String[] rawParts = normalized.split("\\.");
        List<Integer> parts = new ArrayList<>(rawParts.length);
        for (String part : rawParts) {
            parts.add(parseLeadingNumber(part));
        }
        return parts;
    }

    private static int parseLeadingNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static class RemoteModData {
        String latest_version;
        String mod_page;
        List<String> lfs_extensions;
        List<ServerEntry> server_list;
    }
}
