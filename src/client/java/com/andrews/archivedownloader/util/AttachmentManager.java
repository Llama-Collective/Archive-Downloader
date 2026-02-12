package com.andrews.archivedownloader.util;

import com.andrews.archivedownloader.config.DownloadSettings;
import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.models.ArchiveAttachment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.platform.UiPlatform;

/**
 * Manages attachment data and download operations for the post detail view.
 */
public class AttachmentManager {

    public record SaveResult(String fileName, Path path, boolean isWorldDownload, List<String> worldNames) {}

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ArchiveDownloader-IO");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_TOTAL_UNZIPPED_BYTES = 1_000_000_000L; // ~1GB
    private static final long MAX_SINGLE_ENTRY_BYTES = 512L * 1024 * 1024; // 512MB per entry
    private static final int MAX_ENTRY_COUNT = 20000;
    private static final String FASTSTREAM_PREFIX = "https://faststream.online/player/#";

    private final UiMinecraftClient client;
    private List<ArchiveAttachment> availableFiles = new ArrayList<>();
    private String downloadStatus = "";
    private ServerEntry server = ServerDictionary.getDefaultServer();

    public AttachmentManager(UiMinecraftClient client) {
        this.client = client;
    }

    public void clear() {
        availableFiles = new ArrayList<>();
        downloadStatus = "";
    }

    public void setAvailableFiles(List<ArchiveAttachment> attachments) {
        availableFiles = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
    }

    public List<ArchiveAttachment> getAvailableFiles() {
        return availableFiles;
    }

    public String getDownloadStatus() {
        return downloadStatus;
    }

    public boolean hasAttachments() {
        return availableFiles != null && !availableFiles.isEmpty();
    }

    public void handleAttachmentClick(ArchiveAttachment attachment) {
        if (attachment == null) {
            return;
        }
        if (isMp4Attachment(attachment)) {
            openAttachmentUrl(attachment);
            return;
        }

        if (attachment.isDownloadable()) {
            downloadSchematic(attachment);
        } else {
            openAttachmentUrl(attachment);
        }
    }

    public String buildAttachmentMeta(ArchiveAttachment attachment) {
        if (attachment == null) {
            return "";
        }
        if (attachment.youtube() != null) {
            ArchiveAttachment.YoutubeInfo yt = attachment.youtube();
            List<String> parts = new ArrayList<>();
            if (yt.title() != null && !yt.title().isEmpty())
                parts.add(yt.title());
            if (yt.authorName() != null && !yt.authorName().isEmpty())
                parts.add("by " + yt.authorName());
            return parts.isEmpty() ? "YouTube" : "YouTube • " + String.join(" • ", parts);
        }
        if (attachment.litematic() != null) {
            ArchiveAttachment.LitematicInfo info = attachment.litematic();
            List<String> parts = new ArrayList<>();
            if (info.version() != null && !info.version().isEmpty())
                parts.add("Version " + info.version());
            if (info.size() != null && !info.size().isEmpty())
                parts.add(info.size());
            if (info.error() != null && !info.error().isEmpty())
                parts.add("Error: " + info.error());
            return parts.isEmpty() ? "Litematic" : "Litematic • " + String.join(" • ", parts);
        }
        if (attachment.wdl() != null) {
            ArchiveAttachment.WdlInfo info = attachment.wdl();
            List<String> parts = new ArrayList<>();
            if (info.version() != null && !info.version().isEmpty())
                parts.add("Version " + info.version());
            if (info.error() != null && !info.error().isEmpty())
                parts.add("Error: " + info.error());
            return parts.isEmpty() ? "WorldDL" : "WorldDL • " + String.join(" • ", parts);
        }
        return attachment.contentType() != null ? attachment.contentType() : "";
    }

    private void downloadSchematic(ArchiveAttachment file) {
        downloadStatus = "Checking existing downloads...";

        CompletableFuture.supplyAsync(() -> findExistingDownloadedAttachment(file), IO_EXECUTOR)
                .thenAccept(existingPath -> {
                    if (existingPath != null) {
                        handleExistingAttachment(file, existingPath);
                        return;
                    }
                    client.execute(() -> downloadStatus = "Downloading...");
                    startSchematicDownload(file);
                })
                .exceptionally(e -> {
                    System.err.println("[Download] Failed to check existing files: " + e.getMessage());
                    client.execute(() -> downloadStatus = "Downloading...");
                    startSchematicDownload(file);
                    return null;
                });
    }

    private void startSchematicDownload(ArchiveAttachment file) {
        CompletableFuture.runAsync(() -> {
            String downloadUrl = file.downloadUrl();
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                client.execute(() -> downloadStatus = "Invalid download URL");
                System.err.println("[Download] Invalid download URL");
                return;
            }

            System.out.println("[Download] Starting download from: " + downloadUrl);
            System.out.println("[Download] File: " + file.name());

            String encodedUrl = downloadUrl.replace(" ", "%20");

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(encodedUrl))
                    .GET()
                    .header("User-Agent", com.andrews.archivedownloader.network.ArchiveNetworkManager.USER_AGENT);
            com.andrews.archivedownloader.network.ArchiveNetworkManager.applyApiAuthorization(requestBuilder, server, encodedUrl);
            HttpRequest request = requestBuilder.build();

            System.out.println("[Download] Sending request...");
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> handleDownloadResponse(file, response))
                    .exceptionally(e -> {
                        handleDownloadError(e);
                        return null;
                    });
        });
    }

    private void handleDownloadResponse(ArchiveAttachment file, HttpResponse<byte[]> response) {
        System.out.println("[Download] Response status: " + response.statusCode());
        System.out.println("[Download] Content length: " + response.body().length);

        if (response.statusCode() != 200) {
            String errorMsg;
            switch (response.statusCode()) {
                case 404:
                    errorMsg = "✗ Error: File not found on server";
                    break;
                case 403:
                    errorMsg = "✗ Error: Access denied";
                    break;
                case 500:
                case 502:
                case 503:
                    errorMsg = "✗ Error: Server error (" + response.statusCode() + ")";
                    break;
                case 429:
                    errorMsg = "✗ Error: Too many requests, try again later";
                    break;
                default:
                    errorMsg = "✗ Download failed: HTTP " + response.statusCode();
            }
            System.err.println("[Download] " + errorMsg);
            client.execute(() -> downloadStatus = errorMsg);
            return;
        }

        String expectedHash = normalizeSha256(file != null ? file.hash() : null);
        String actualHash = expectedHash != null ? computeSha256(response.body()) : null;
        boolean hashMismatch = expectedHash != null && actualHash != null && !expectedHash.equals(actualHash);

        processAttachmentBytes(file, response.body(), false)
                .thenAccept(result -> {
                    final String finalFileName = result.fileName();
                    final Path finalPath = result.path();
                    final List<String> worldNames = result.worldNames();
                    final boolean finalHashMismatch = hashMismatch;
                    client.execute(() -> {
                        boolean attemptedAutoLoad = shouldAutoLoadSchematic(file, finalPath);
                        boolean autoLoaded = attemptedAutoLoad && LitematicaAutoLoader.loadIntoWorld(finalPath);
                        downloadStatus = buildDownloadStatus(result, finalFileName, attemptedAutoLoad, autoLoaded,
                                finalHashMismatch);
                        showDownloadToast(result, finalFileName, finalPath, worldNames, autoLoaded, attemptedAutoLoad);
                        if (finalHashMismatch) {
                            showToast("Hash mismatch warning", finalFileName + " did not match expected SHA-256");
                        }
                        System.out.println("Downloaded to: " + finalPath.toAbsolutePath());
                    });
                })
                .exceptionally(ex -> {
                    handleDownloadError(ex);
                    return null;
                });
    }

    private void handleDownloadError(Throwable throwable) {
        Throwable e = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause()
                : throwable;
        client.execute(() -> {
            String errorMsg;
            String exceptionMessage = e != null ? e.getMessage() : null;
            if (e instanceof java.net.UnknownHostException) {
                errorMsg = "✗ Error: No internet connection";
            } else if (e instanceof java.net.SocketTimeoutException) {
                errorMsg = "✗ Error: Connection timeout";
            } else if (e instanceof java.io.FileNotFoundException) {
                errorMsg = "✗ Error: File not found";
            } else if (e instanceof java.io.IOException
                    && exceptionMessage != null
                    && exceptionMessage.contains("Permission denied")) {
                errorMsg = "✗ Error: Cannot write to disk (permission denied)";
            } else if (e instanceof java.io.IOException
                    && exceptionMessage != null
                    && exceptionMessage.contains("No space")) {
                errorMsg = "✗ Error: Not enough disk space";
            } else {
                String msg = exceptionMessage;
                if (msg != null && msg.length() > 40) {
                    msg = msg.substring(0, 37) + "...";
                }
                errorMsg = "✗ Error: " + (msg != null ? msg : "Unknown error");
            }

            downloadStatus = errorMsg;
            System.err.println("Failed to download schematic: " + exceptionMessage);
            if (e != null) {
                e.printStackTrace();
            }
            showToast("Download failed", errorMsg);
        });
    }

    private void handleExistingSchematic(ArchiveAttachment attachment, Path existingPath) {
        String fileName = existingPath.getFileName() != null
                ? existingPath.getFileName().toString()
                : existingPath.toString();
        boolean attemptedAutoLoad = shouldAutoLoadSchematic(attachment, existingPath);
        boolean autoLoaded = attemptedAutoLoad && LitematicaAutoLoader.loadIntoWorld(existingPath);

        if (!attemptedAutoLoad) {
            downloadStatus = "✓ Reused existing file: " + fileName;
            showToast("Already downloaded", fileName);
            return;
        }

        if (autoLoaded) {
            downloadStatus = "✓ Loaded existing file: " + fileName;
            showToast("Loaded existing schematic", fileName);
        } else {
            downloadStatus = "✓ Reused existing file (auto-load failed): " + fileName;
            showToast("Already downloaded", fileName + " (auto-load failed)");
        }
    }

    private void handleExistingAttachment(ArchiveAttachment attachment, Path existingPath) {
        if (attachment != null && attachment.wdl() != null) {
            client.execute(() -> downloadStatus = "Using cached WDL archive...");
            CompletableFuture.supplyAsync(() -> {
                        try {
                            return Files.readAllBytes(existingPath);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, IO_EXECUTOR)
                    .thenCompose(bytes -> processAttachmentBytes(attachment, bytes, true))
                    .thenAccept(result -> client.execute(() -> {
                        String fileName = result.fileName();
                        String base = result.isWorldDownload()
                                ? "✓ World saved from cache: saves/" + fileName
                                : "✓ Reused existing file: " + fileName;
                        downloadStatus = base;
                        showDownloadToast(result, fileName, result.path(), result.worldNames(), false, false);
                    }))
                    .exceptionally(ex -> {
                        handleDownloadError(ex);
                        return null;
                    });
            return;
        }
        client.execute(() -> handleExistingSchematic(attachment, existingPath));
    }

    private CompletableFuture<SaveResult> processAttachmentBytes(ArchiveAttachment attachment, byte[] data, boolean fromCache) {
        CompletableFuture<Path> rawSaveFuture;
        if (!fromCache && attachment != null && attachment.wdl() != null) {
            rawSaveFuture = saveRawWdlZipAsync(attachment, data);
        } else {
            rawSaveFuture = CompletableFuture.completedFuture(null);
        }
        return rawSaveFuture.thenCompose(path -> saveAsync(attachment, data));
    }

    private Path findExistingDownloadedAttachment(ArchiveAttachment attachment) {
        if (attachment == null) {
            return null;
        }

        String expectedHash = normalizeSha256(attachment.hash());
        if (expectedHash == null) {
            return null;
        }

        if (attachment.wdl() != null) {
            String rawBaseName = resolveAttachmentBaseName(attachment, ".zip", "world");
            return findByHashInDirectory(getRawWdlDirectory(), rawBaseName, expectedHash);
        }

        String baseName = resolveAttachmentBaseName(attachment, ".litematic", "download");

        ServerEntry targetServer = server != null ? server : ServerDictionary.getDefaultServer();
        Path baseDir = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath(targetServer));

        List<Path> candidateDirs = List.of(baseDir, baseDir.resolve("submissions"));
        for (Path dir : candidateDirs) {
            Path match = findByHashInDirectory(dir, baseName, expectedHash);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private CompletableFuture<Path> saveRawWdlZipAsync(ArchiveAttachment attachment, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (attachment == null || attachment.wdl() == null) {
                    return null;
                }
                Path rawDir = getRawWdlDirectory();
                Files.createDirectories(rawDir);

                String baseName = resolveAttachmentBaseName(attachment, ".zip", "world");
                String expectedHash = normalizeSha256(attachment.hash());
                if (expectedHash != null) {
                    Path existingByHash = findByHashInDirectory(rawDir, baseName, expectedHash);
                    if (existingByHash != null) {
                        return existingByHash;
                    }
                }

                Path existingIdentical = findIdenticalFile(rawDir, baseName, data);
                if (existingIdentical != null) {
                    return existingIdentical;
                }

                Path outputFile = ensureUniqueName(rawDir, baseName);
                Files.write(outputFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return outputFile;
            } catch (Exception e) {
                System.err.println("[Download] Failed to cache raw WDL zip: " + e.getMessage());
                return null;
            }
        }, IO_EXECUTOR);
    }

    private Path getRawWdlDirectory() {
        ServerEntry targetServer = server != null ? server : ServerDictionary.getDefaultServer();
        Path baseDir = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath(targetServer));
        return baseDir.resolve("rawWDLs");
    }

    private String resolveAttachmentBaseName(ArchiveAttachment attachment, String defaultExtension, String fallbackName) {
        String baseName = attachment != null && attachment.name() != null ? attachment.name() : fallbackName;
        if (!baseName.contains(".")) {
            baseName += defaultExtension;
        }
        return baseName;
    }

    private Path findByHashInDirectory(Path dir, String baseName, String expectedHash) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        int dot = baseName.lastIndexOf('.');
        String nameOnly = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";
        Path candidate = dir.resolve(baseName);
        int counter = 1;
        while (Files.exists(candidate)) {
            if (Files.isRegularFile(candidate)) {
                try {
                    String fileHash = computeSha256(candidate);
                    if (expectedHash.equals(fileHash)) {
                        return candidate;
                    }
                } catch (Exception ignored) {
                }
            }
            candidate = dir.resolve(nameOnly + "_" + counter + ext);
            counter++;
        }
        return null;
    }

    private String computeSha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (var input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private String computeSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            byte[] hash = digest.digest();
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

    private String normalizeSha256(String hash) {
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

    private boolean shouldAutoLoadSchematic(ArchiveAttachment attachment, Path savedPath) {
        if (attachment != null && attachment.wdl() != null) {
            return false;
        }
        if (savedPath == null || savedPath.getFileName() == null) {
            return false;
        }
        String fileName = savedPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return LitematicaAutoLoader.isAvailable() && fileName.endsWith(".litematic");
    }

    private String buildDownloadStatus(SaveResult result, String fileName, boolean attemptedAutoLoad,
            boolean autoLoaded, boolean hashMismatch) {
        String base = result.isWorldDownload()
                ? "✓ World saved: saves/" + fileName
                : "✓ Downloaded: " + fileName;
        if (result.isWorldDownload() || !attemptedAutoLoad) {
            return hashMismatch ? (base + " [WARNING: hash mismatch]") : base;
        }
        String status = autoLoaded ? ("✓ Loaded " + fileName) : "- Downloaded (but failed to load): " + fileName;
        return hashMismatch ? (status + " [WARNING: hash mismatch]") : status;
    }

    private CompletableFuture<SaveResult> saveAsync(ArchiveAttachment attachment, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isWorldDownload = attachment != null && attachment.wdl() != null;
                ServerEntry targetServer = server != null ? server : ServerDictionary.getDefaultServer();
                Path baseTargetDir;
                if (isWorldDownload) {
                    baseTargetDir = Paths.get(DownloadSettings.getInstance().getGameDirectory(), "saves");
                } else {
                    baseTargetDir = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath(targetServer));
                    if (isSubmissionSchematicAttachment(attachment)) {
                        baseTargetDir = baseTargetDir.resolve("submissions");
                    }
                }
                Files.createDirectories(baseTargetDir);

                if (isWorldDownload) {
                    String worldName = (attachment != null && attachment.name() != null ? attachment.name() : "world")
                            .replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (worldName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        worldName = worldName.substring(0, worldName.length() - 4);
                    }
                    ExtractionOutcome outcome = extractWorldsFromZip(data, baseTargetDir, worldName);
                    Path primaryWorld = outcome.primaryWorldDir() != null ? outcome.primaryWorldDir() : baseTargetDir;
                    String primaryName = primaryWorld.getFileName() != null ? primaryWorld.getFileName().toString() : worldName;
                    return new SaveResult(primaryName, primaryWorld, true, outcome.worldNames());
                }

                String baseName = attachment != null && attachment.name() != null ? attachment.name() : "download";
                if (!baseName.contains(".")) {
                    baseName += ".litematic";
                }

                Path existingIdentical = findIdenticalFile(baseTargetDir, baseName, data);
                if (existingIdentical != null) {
                    return new SaveResult(existingIdentical.getFileName().toString(), existingIdentical, false, List.of());
                }

                Path outputFile = ensureUniqueName(baseTargetDir, baseName);
                Files.write(outputFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new SaveResult(outputFile.getFileName().toString(), outputFile, false, List.of());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, IO_EXECUTOR);
    }

    private boolean isSubmissionSchematicAttachment(ArchiveAttachment attachment) {
        if (attachment == null || attachment.wdl() != null) {
            return false;
        }
        String downloadUrl = attachment.downloadUrl();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return false;
        }
        ServerEntry targetServer = server != null ? server : ServerDictionary.getDefaultServer();
        if (!com.andrews.archivedownloader.network.ArchiveNetworkManager.isApiUrlForServer(targetServer, downloadUrl)) {
            return false;
        }
        String lowered = downloadUrl.toLowerCase(Locale.ROOT);
        return lowered.contains("/submission/") && lowered.contains("/attachments/");
    }

    private Path ensureUniqueName(Path dir, String fileName) throws Exception {
        Path candidate = dir.resolve(fileName);
        String baseName = fileName;
        int counter = 1;
        int dot = baseName.lastIndexOf('.');
        String nameOnly = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";

        while (Files.exists(candidate)) {
            candidate = dir.resolve(nameOnly + "_" + counter + ext);
            counter++;
        }
        return candidate;
    }

    private Path ensureUniqueDirectory(Path dir, String name) throws Exception {
        Path candidate = dir.resolve(name);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(name + "_" + counter);
            counter++;
        }
        return candidate;
    }

    private ExtractionOutcome extractWorldsFromZip(byte[] zipBytes, Path baseTargetDir, String defaultWorldName) throws Exception {
        ExtractionStats stats = new ExtractionStats();
        extractWorldsFromZipInternal(zipBytes, baseTargetDir, defaultWorldName, stats, false);
        List<String> worldNames = stats.worldDirs.stream()
            .map(path -> path.getFileName() != null ? path.getFileName().toString() : "world")
            .toList();
        return new ExtractionOutcome(stats.primaryWorldDir != null ? stats.primaryWorldDir : baseTargetDir, worldNames);
    }

    private void extractWorldsFromZipInternal(byte[] zipBytes, Path baseTargetDir, String defaultWorldName, ExtractionStats stats, boolean isNested) throws Exception {
        Path tempZip = Files.createTempFile("ldl_wdl_", ".zip");
        try {
            Files.write(tempZip, zipBytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (ZipFile zipFile = new ZipFile(tempZip.toFile())) {
                ArrayList<String> roots = new ArrayList<>();
                Enumeration<? extends ZipEntry> scan = zipFile.entries();
                while (scan.hasMoreElements()) {
                    ZipEntry entry = scan.nextElement();
                    if (entry.isDirectory())
                        continue;
                    String name = entry.getName().replace("\\", "/");
                    if (shouldIgnoreZipEntry(name))
                        continue;
                    if (name.endsWith("level.dat")) {
                        String parent = name.contains("/") ? name.substring(0, name.lastIndexOf('/')) : "";
                        if (!roots.contains(parent)) {
                            roots.add(parent);
                        }
                    }
                }

                if (roots.isEmpty()) {
                    if (isNested) {
                        return;
                    } else {
                        throw new IllegalStateException("No world (level.dat) found in archive");
                    }
                }

                ArrayList<WorldTarget> worldTargets = new ArrayList<>();
                for (String root : roots) {
                    String worldName = root.isEmpty()
                            ? defaultWorldName
                            : root.substring(root.lastIndexOf('/') >= 0 ? root.lastIndexOf('/') + 1 : 0);
                    Path worldDir = ensureUniqueDirectory(baseTargetDir, worldName);
                    Files.createDirectories(worldDir);
                    worldTargets.add(new WorldTarget(root, worldDir));
                    if (stats.primaryWorldDir == null) {
                        stats.primaryWorldDir = worldDir;
                    }
                    stats.worldDirs.add(worldDir);
                }

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory())
                        continue;
                    String entryName = entry.getName().replace("\\", "/");
                    if (shouldIgnoreZipEntry(entryName))
                        continue;
                    stats.totalEntries++;
                    if (stats.totalEntries > MAX_ENTRY_COUNT) {
                        throw new IllegalStateException("Archive has too many files to extract");
                    }

                    if (entryName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        byte[] nestedBytes = readEntryBytesWithLimit(zipFile, entry, stats);
                        String nestedDefaultName = entryName.contains("/") ? entryName.substring(entryName.lastIndexOf('/') + 1) : entryName;
                        if (nestedDefaultName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                            nestedDefaultName = nestedDefaultName.substring(0, nestedDefaultName.length() - 4);
                        }
                        extractWorldsFromZipInternal(nestedBytes, baseTargetDir, nestedDefaultName, stats, true);
                        continue;
                    }

                    WorldTarget target = matchWorld(entryName, worldTargets);
                    if (target == null)
                        continue;

                    String relative = target.root().isEmpty() ? entryName : entryName.substring(target.root().length());
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    Path resolved = target.dir().resolve(relative).normalize();
                    if (!resolved.startsWith(target.dir())) {
                        continue;
                    }
                    Files.createDirectories(resolved.getParent());
                    copyEntryWithLimit(zipFile, entry, resolved, stats);
                }
            }
        } finally {
            try {
                Files.deleteIfExists(tempZip);
            } catch (Exception ignored) {
            }
        }
    }

    private WorldTarget matchWorld(String entryName, List<WorldTarget> targets) {
        if (targets.isEmpty())
            return null;
        if (targets.size() == 1)
            return targets.get(0);

        for (WorldTarget target : targets) {
            String root = target.root();
            if (root.isEmpty()) {
                continue;
            } else if (entryName.equals(root) || entryName.startsWith(root + "/")) {
                return target;
            }
        }
        return targets.get(0);
    }

    private record WorldTarget(String root, Path dir) {}

    private boolean shouldIgnoreZipEntry(String name) {
        String normalized = name.replace("\\", "/");
        return normalized.startsWith("__MACOSX/") || normalized.equals("__MACOSX") || normalized.contains("/__MACOSX/");
    }

    private long copyEntryWithLimit(ZipFile zipFile, ZipEntry entry, Path destination, ExtractionStats stats) throws Exception {
        long declaredSize = entry.getSize();
        if (declaredSize > MAX_SINGLE_ENTRY_BYTES) {
            throw new IllegalStateException("Archive entry exceeds allowed size");
        }

        byte[] buffer = new byte[8192];
        long written = 0;
        try (var is = zipFile.getInputStream(entry);
                var os = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (written + read > MAX_SINGLE_ENTRY_BYTES) {
                    throw new IllegalStateException("Archive entry exceeds allowed size");
                }
                if (stats.totalBytes + written + read > MAX_TOTAL_UNZIPPED_BYTES) {
                    throw new IllegalStateException("Archive is too large to extract");
                }
                os.write(buffer, 0, read);
                written += read;
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(destination);
            } catch (Exception ignored) {
            }
            throw e;
        }
        stats.totalBytes += written;
        return written;
    }

    private byte[] readEntryBytesWithLimit(ZipFile zipFile, ZipEntry entry, ExtractionStats stats) throws Exception {
        long declaredSize = entry.getSize();
        if (declaredSize > MAX_SINGLE_ENTRY_BYTES) {
            throw new IllegalStateException("Archive entry exceeds allowed size");
        }
        if (declaredSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("Archive entry size invalid");
        }
        int initial = declaredSize > 0 ? (int) declaredSize : 8192;
        byte[] buffer = new byte[initial];
        int offset = 0;
        try (var is = zipFile.getInputStream(entry)) {
            int read;
            while ((read = is.read(buffer, offset, buffer.length - offset)) != -1) {
                offset += read;
                if (offset > MAX_SINGLE_ENTRY_BYTES || stats.totalBytes + offset > MAX_TOTAL_UNZIPPED_BYTES) {
                    throw new IllegalStateException("Archive entry exceeds allowed size");
                }
                if (offset == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
        }
        return Arrays.copyOf(buffer, offset);
    }

    private void showDownloadToast(SaveResult result, String fileName, Path path, List<String> worldNames, boolean autoLoaded, boolean attemptedAutoLoad) {
        if (result == null) return;
        if (result.isWorldDownload()) {
            List<String> names = (worldNames != null && !worldNames.isEmpty()) ? worldNames : List.of(fileName);
            String title = "World download saved";
            String body = "Worlds: " + String.join(", ", names);
            showToast(title, body);
        } else if (attemptedAutoLoad) {
            if (autoLoaded) {
                showToast("Loaded into Litematica", fileName);
            } else {
                showToast("Downloaded", fileName + " (auto-load failed)");
            }
        } else {
            showToast("Downloaded", fileName);
        }
    }

    private void showToast(String title, String body) {
        client.showBasicToast(title, body);
    }

    private static class ExtractionStats {
        long totalBytes = 0;
        int totalEntries = 0;
        Path primaryWorldDir;
        List<Path> worldDirs = new ArrayList<>();
    }

    private record ExtractionOutcome(Path primaryWorldDir, List<String> worldNames) {}

    private Path findIdenticalFile(Path dir, String fileName, byte[] data) {
        int dot = fileName.lastIndexOf('.');
        String nameOnly = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        Path candidate = dir.resolve(fileName);
        int counter = 1;
        while (Files.exists(candidate)) {
            if (Files.isRegularFile(candidate)) {
                try {
                    if (Files.size(candidate) == data.length && Arrays.equals(Files.readAllBytes(candidate), data)) {
                        return candidate;
                    }
                } catch (Exception ignored) {
                }
            }
            candidate = dir.resolve(nameOnly + "_" + counter + ext);
            counter++;
        }
        return null;
    }

    private void openAttachmentUrl(ArchiveAttachment attachment) {
        if (attachment == null) {
            return;
        }
        String url = attachment.downloadUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        String openUrl = isMp4Attachment(attachment) && !url.startsWith(FASTSTREAM_PREFIX)
                ? FASTSTREAM_PREFIX + url
                : url;
        try {
            UiPlatform.openUri(openUrl);
        } catch (Exception e) {
            System.err.println("Failed to open attachment URL: " + e.getMessage());
        }
    }

    private boolean isMp4Attachment(ArchiveAttachment attachment) {
        if (attachment == null) {
            return false;
        }
        String name = attachment.name() != null ? attachment.name().toLowerCase(Locale.ROOT) : "";
        String contentType = attachment.contentType() != null ? attachment.contentType().toLowerCase(Locale.ROOT) : "";
        String dlUrl = attachment.downloadUrl() != null ? attachment.downloadUrl().toLowerCase(Locale.ROOT) : "";
        return contentType.contains("mp4") || name.endsWith(".mp4") || dlUrl.endsWith(".mp4");
    }
}
