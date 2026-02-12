package com.andrews.archivedownloader.util;

import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.network.ArchiveNetworkManager;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

public final class ImageCacheUtil {
    private static final String OFFLINE_CACHE_DIR = "archivedownloader/offlinecache";
    private static final String DEFAULT_BRANCH = "main";

    private ImageCacheUtil() {
    }

    public static String normalizeSha256(String hash) {
        if (hash == null) {
            return null;
        }
        String normalized = hash.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) {
                return null;
            }
        }
        return normalized;
    }

    public static String computeSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] readCachedImageBytesByHash(ServerEntry server, String imageUrl, String expectedHash) {
        if (imageUrl == null || imageUrl.isBlank() || expectedHash == null) {
            return null;
        }
        Path cachePath = getOfflineImagePath(server, imageUrl);
        if (!Files.exists(cachePath) || !Files.isRegularFile(cachePath)) {
            return null;
        }
        try {
            byte[] imageBytes = Files.readAllBytes(cachePath);
            String cachedHash = computeSha256(imageBytes);
            if (!expectedHash.equals(cachedHash)) {
                return null;
            }
            return imageBytes;
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeCachedImageBytesByHash(ServerEntry server, String imageUrl, String expectedHash, byte[] imageBytes) {
        if (imageUrl == null || imageUrl.isBlank() || expectedHash == null || imageBytes == null) {
            return;
        }
        try {
            String actualHash = computeSha256(imageBytes);
            if (!expectedHash.equals(actualHash)) {
                return;
            }
            Path target = getOfflineImagePath(server, imageUrl);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(target)) {
                Files.write(target, imageBytes);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path getOfflineImagePath(ServerEntry server, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return getOfflineCacheServerDir(server).resolve("images").resolve("unknown");
        }
        try {
            URI uri = URI.create(imageUrl);
            String rawPath = uri.getPath() != null ? uri.getPath() : "";
            String normalizedPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";

            String owner = server != null && server.owner() != null && !server.owner().isBlank()
                    ? server.owner()
                    : "Storage-Tech-2";
            String repo = server != null && server.repo() != null && !server.repo().isBlank()
                    ? server.repo()
                    : "Archive";
            String branch = server != null && server.branch() != null && !server.branch().isBlank()
                    ? server.branch()
                    : DEFAULT_BRANCH;

            if ("raw.githubusercontent.com".equals(host)) {
                String prefix = owner + "/" + repo + "/" + branch + "/";
                String rel = normalizedPath.startsWith(prefix) ? normalizedPath.substring(prefix.length()) : normalizedPath;
                return resolveOfflinePath(getOfflineCacheServerDir(server).resolve("repo"), rel);
            }
            if ("media.githubusercontent.com".equals(host)) {
                String prefix = "media/" + owner + "/" + repo + "/refs/heads/" + branch + "/";
                String rel = normalizedPath.startsWith(prefix) ? normalizedPath.substring(prefix.length()) : normalizedPath;
                return resolveOfflinePath(getOfflineCacheServerDir(server).resolve("repo"), rel);
            }
            if (ArchiveNetworkManager.isApiUrlForServer(server, imageUrl)) {
                String apiRel = normalizedPath.isBlank() ? "root" : normalizedPath;
                if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                    apiRel += "__q_" + sanitizePathComponent(uri.getQuery());
                }
                return resolveOfflinePath(getOfflineCacheServerDir(server).resolve("api"), apiRel);
            }
            return resolveOfflinePath(getOfflineCacheServerDir(server).resolve("images"), sanitizePathComponent(normalizedPath));
        } catch (Exception e) {
            return resolveOfflinePath(getOfflineCacheServerDir(server).resolve("images"), sanitizePathComponent(imageUrl));
        }
    }

    private static Path getOfflineCacheServerDir(ServerEntry server) {
        String serverId = server != null && server.id() != null && !server.id().isBlank()
                ? server.id().toLowerCase(Locale.ROOT)
                : (server != null && server.name() != null && !server.name().isBlank()
                        ? server.name().toLowerCase(Locale.ROOT)
                        : "default");
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(OFFLINE_CACHE_DIR)
                .resolve(serverId);
    }

    private static Path resolveOfflinePath(Path baseDir, String relativePath) {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir.normalize())) {
            throw new IllegalArgumentException("Invalid offline cache path");
        }
        return resolved;
    }

    private static String sanitizePathComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[^a-zA-Z0-9._/\\-]", "_");
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }
}
