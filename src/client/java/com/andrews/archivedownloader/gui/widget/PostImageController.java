package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.models.ArchiveImageInfo;
import com.andrews.archivedownloader.network.ArchiveNetworkManager;
import com.andrews.archivedownloader.util.ImageCacheUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.render.UiTextureId;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class PostImageController {
    private static final boolean DEBUG_IMAGE_LOADING = false;
    private final UiMinecraftClient client;
    private final LoadingSpinner loadingSpinner;
    private ServerEntry server = ServerDictionary.getDefaultServer();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private List<ArchiveImageInfo> imageInfos = new ArrayList<>();
    private String currentImageDescription = "";
    private boolean isLoadingImage = false;

    private String[] imageUrls = new String[0];
    private int currentImageIndex = 0;
    private UiTextureId currentImageTexture;
    private final Map<String, UiTextureId> imageCache = new ConcurrentHashMap<>();
    private final Map<String, String> imageHashByUrl = new ConcurrentHashMap<>();
    private final Map<String, UiTextureId> imageHashCache = new ConcurrentHashMap<>();
    private final Set<String> failedImageUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> preloadingImages = ConcurrentHashMap.newKeySet();
    private String loadingImageUrl = null;
    private int originalImageWidth = 0;
    private int originalImageHeight = 0;
    private final Map<String, int[]> imageDimensionsCache = new ConcurrentHashMap<>();
    private final Map<String, int[]> imageHashDimensionsCache = new ConcurrentHashMap<>();

    private ImageViewerWidget imageViewer;

    public PostImageController(UiMinecraftClient client) {
        this.client = client;
        this.loadingSpinner = new LoadingSpinner(0, 0);
    }

    public void clear() {
        imageInfos = new ArrayList<>();
        currentImageDescription = "";
        isLoadingImage = false;
        imageUrls = new String[0];
        currentImageIndex = 0;
        currentImageTexture = null;
        originalImageWidth = 0;
        originalImageHeight = 0;
        loadingImageUrl = null;
        preloadingImages.clear();
        imageCache.clear();
        imageHashByUrl.clear();
        imageHashCache.clear();
        failedImageUrls.clear();
        imageDimensionsCache.clear();
        imageHashDimensionsCache.clear();
        imageViewer = null;
    }

    public void setImageInfos(List<ArchiveImageInfo> infos) {
        imageInfos = infos != null ? new ArrayList<>(infos) : new ArrayList<>();
        imageHashByUrl.clear();
        for (ArchiveImageInfo info : imageInfos) {
            if (info == null || info.url() == null) {
                continue;
            }
            String hash = ImageCacheUtil.normalizeSha256(info.hash());
            if (hash != null) {
                imageHashByUrl.put(info.url(), hash);
            }
        }
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
    }

    public void setImages(List<String> images) {
        imageUrls = images != null ? images.toArray(new String[0]) : new String[0];
        currentImageIndex = 0;
        currentImageTexture = null;
        originalImageWidth = 0;
        originalImageHeight = 0;
        loadingImageUrl = null;
        isLoadingImage = false;
        failedImageUrls.clear();
        if (imageUrls.length > 0) {
            updateCurrentImageDescription(imageUrls[currentImageIndex]);
        } else {
            currentImageDescription = "";
        }
    }

    public void loadCurrentImageIfNeeded() {
        if (imageUrls.length == 0) {
            return;
        }
        if (currentImageTexture == null) {
            loadImage(imageUrls[currentImageIndex]);
        }
        preloadNextImage(false);
    }

    public boolean hasMultipleImages() {
        return imageUrls.length > 1;
    }

    public int getImageCount() {
        return imageUrls.length;
    }

    public int getCurrentImageIndex() {
        return currentImageIndex;
    }

    public UiTextureId getCurrentImageTexture() {
        return currentImageTexture;
    }

    public boolean isLoadingImage() {
        return isLoadingImage;
    }

    public String getCurrentImageDescription() {
        return currentImageDescription != null ? currentImageDescription : "";
    }

    public int getOriginalImageWidth() {
        return originalImageWidth;
    }

    public int getOriginalImageHeight() {
        return originalImageHeight;
    }

    public LoadingSpinner getLoadingSpinner() {
        return loadingSpinner;
    }

    public void previousImage() {
        if (!hasMultipleImages()) {
            return;
        }
        currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
        updateCurrentImageDescription(imageUrls[currentImageIndex]);
        loadImage(imageUrls[currentImageIndex]);
        preloadNextImage(true);
    }

    public void nextImage() {
        if (!hasMultipleImages()) {
            return;
        }
        currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
        updateCurrentImageDescription(imageUrls[currentImageIndex]);
        loadImage(imageUrls[currentImageIndex]);
        preloadNextImage(false);
    }

    public boolean hasImageViewerOpen() {
        return imageViewer != null;
    }

    public void openImageViewer(int screenWidth, int screenHeight) {
        if (currentImageTexture == null) {
            return;
        }

        int totalImages = Math.max(1, imageUrls.length);
        imageViewer = new ImageViewerWidget(
                client,
                currentImageTexture,
                originalImageWidth,
                originalImageHeight,
                currentImageIndex,
                totalImages,
                this::previousImageFromViewer,
                this::nextImageFromViewer,
                this::closeImageViewer);
        imageViewer.updateLayout(screenWidth, screenHeight);
    }

    public void closeImageViewer() {
        imageViewer = null;
    }

    public void renderImageViewer(UiRenderContext context, int mouseX, int mouseY, float delta) {
        if (imageViewer != null) {
            imageViewer.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        return imageViewer != null && imageViewer.mouseClicked(click, doubled);
    }

    public boolean mouseReleased(UiMouseEvent click) {
        return imageViewer != null && imageViewer.mouseReleased(click);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return imageViewer != null && imageViewer.keyPressed(keyCode, scanCode, modifiers);
    }

    private void previousImageFromViewer() {
        if (!hasMultipleImages()) {
            return;
        }
        currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
        loadImage(imageUrls[currentImageIndex]);
        reopenViewer();
    }

    private void nextImageFromViewer() {
        if (!hasMultipleImages()) {
            return;
        }
        currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
        loadImage(imageUrls[currentImageIndex]);
        reopenViewer();
    }

    private void reopenViewer() {
        if (client.windowHandle() == 0L) {
            return;
        }
        closeImageViewer();
        openImageViewer(client.guiScaledWidth(), client.guiScaledHeight());
    }

    private void loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        if (isLoadingImage && imageUrl.equals(loadingImageUrl)) {
            return;
        }
        if (failedImageUrls.contains(imageUrl)) {
            return;
        }

        if (imageCache.containsKey(imageUrl)) {
            debug("memory-hit url=" + imageUrl);
            currentImageTexture = imageCache.get(imageUrl);
            int[] dims = imageDimensionsCache.get(imageUrl);
            if (dims != null) {
                originalImageWidth = dims[0];
                originalImageHeight = dims[1];
            }
            updateCurrentImageDescription(imageUrl);
            isLoadingImage = false;
            return;
        }
        String imageHash = imageHashByUrl.get(imageUrl);
        if (imageHash != null && imageHashCache.containsKey(imageHash)) {
            debug("hash-memory-hit url=" + imageUrl + " hash=" + imageHash);
            UiTextureId cachedTexture = imageHashCache.get(imageHash);
            currentImageTexture = cachedTexture;
            imageCache.put(imageUrl, cachedTexture);
            int[] dims = imageHashDimensionsCache.get(imageHash);
            if (dims != null) {
                imageDimensionsCache.put(imageUrl, dims);
                originalImageWidth = dims[0];
                originalImageHeight = dims[1];
            }
            updateCurrentImageDescription(imageUrl);
            isLoadingImage = false;
            return;
        }

        isLoadingImage = true;
        loadingImageUrl = imageUrl;
        debug("load-start url=" + imageUrl + " hash=" + (imageHash != null ? imageHash : "none"));

        loadImageAsync(imageUrl).thenAccept(texId -> {
            client.execute(() -> {
                if (imageUrl.equals(loadingImageUrl)) {
                    currentImageTexture = texId;
                    int[] dims = imageDimensionsCache.get(imageUrl);
                    if (dims != null) {
                        originalImageWidth = dims[0];
                        originalImageHeight = dims[1];
                    }
                    updateCurrentImageDescription(imageUrl);
                    isLoadingImage = false;
                    loadingImageUrl = null;
                    debug("load-success url=" + imageUrl);
                }
            });
        }).exceptionally(ex -> {
            client.execute(() -> {
                isLoadingImage = false;
                loadingImageUrl = null;
                failedImageUrls.add(imageUrl);
                System.err.println("[AD-IMG-DETAIL] load-fail url=" + imageUrl + " err=" + ex.getMessage());
            });
            return null;
        });
    }

    private void preloadNextImage(boolean reversed) {
        if (!hasMultipleImages()) {
            return;
        }

        int nextIndex = (currentImageIndex + (reversed ? -1 : 1) + imageUrls.length) % imageUrls.length;
        String nextUrl = imageUrls[nextIndex];
        if (nextUrl == null || nextUrl.isEmpty()) {
            return;
        }
        if (imageCache.containsKey(nextUrl) || nextUrl.equals(loadingImageUrl) || preloadingImages.contains(nextUrl)) {
            return;
        }

        preloadingImages.add(nextUrl);
        loadImageAsync(nextUrl).handle((tex, ex) -> {
            preloadingImages.remove(nextUrl);
            if (ex != null) {
                client.execute(() -> System.err.println("Failed to preload image: " + ex.getMessage()));
            }
            return null;
        });
    }

    private CompletableFuture<UiTextureId> loadImageAsync(String imageUrl) {
        if (imageCache.containsKey(imageUrl)) {
            return CompletableFuture.completedFuture(imageCache.get(imageUrl));
        }
        String expectedHash = imageHashByUrl.get(imageUrl);
        debug("disk-check-start url=" + imageUrl + " hash=" + (expectedHash != null ? expectedHash : "none"));
        return CompletableFuture
                .supplyAsync(() -> ImageCacheUtil.readCachedImageBytesByHash(server, imageUrl, expectedHash))
                .thenCompose(cachedBytes -> {
                    if (cachedBytes != null && cachedBytes.length > 0) {
                        debug("disk-hit url=" + imageUrl + " hash=" + (expectedHash != null ? expectedHash : "none"));
                        return CompletableFuture.completedFuture(createTextureFromBytes(imageUrl, expectedHash, cachedBytes, true));
                    }
                    debug("disk-miss url=" + imageUrl);
                    return fetchImageFromNetwork(imageUrl, expectedHash);
                });
    }

    private CompletableFuture<UiTextureId> fetchImageFromNetwork(String imageUrl, String expectedHash) {
        String requestUrl = imageUrl.replace(" ", "%20");
        debug("network-fetch url=" + requestUrl + " hash=" + (expectedHash != null ? expectedHash : "none"));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .header("User-Agent", ArchiveNetworkManager.USER_AGENT);
        ArchiveNetworkManager.applyApiAuthorization(builder, server, imageUrl);
        HttpRequest request = builder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode()));
                    }
                    return createTextureFromBytes(imageUrl, expectedHash, response.body(), false);
                });
    }

    private UiTextureId createTextureFromBytes(String imageUrl, String expectedHash, byte[] imageData, boolean fromDisk) {
        String actualHash = ImageCacheUtil.computeSha256(imageData);
        boolean hashMatches = expectedHash == null || expectedHash.equals(actualHash);
        if (!hashMatches) {
            debug("hash-mismatch url=" + imageUrl + " expected=" + expectedHash + " actual=" + actualHash);
        }
        NativeImage nativeImage;
        try {
            nativeImage = NativeImage.read(new ByteArrayInputStream(imageData));
        } catch (Exception e) {
            byte[] pngBytes;
            try {
                pngBytes = convertImageToPng(imageData);
                nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
            } catch (Exception conversionError) {
                throw new CompletionException(conversionError);
            }
        }

        int imgWidth = nativeImage.getWidth();
        int imgHeight = nativeImage.getHeight();
        if (imgWidth <= 0 || imgHeight <= 0) {
            nativeImage.close();
            throw new CompletionException(new RuntimeException("Invalid image dimensions"));
        }

        imageDimensionsCache.put(imageUrl, new int[] { imgWidth, imgHeight });

        UiTextureId texId = client.registerDynamicTexture("post", nativeImage);
        imageCache.put(imageUrl, texId);
        if (hashMatches && expectedHash != null) {
            imageHashCache.put(expectedHash, texId);
            imageHashDimensionsCache.put(expectedHash, new int[] { imgWidth, imgHeight });
            if (!fromDisk) {
                ImageCacheUtil.writeCachedImageBytesByHash(server, imageUrl, expectedHash, imageData);
                debug("disk-write url=" + imageUrl + " hash=" + expectedHash);
            }
        }
        return texId;
    }

    private void updateCurrentImageDescription(String imageUrl) {
        currentImageDescription = "";
        if (imageUrl == null) {
            return;
        }
        for (ArchiveImageInfo info : imageInfos) {
            if (info != null && imageUrl.equals(info.url())) {
                currentImageDescription = info.description() != null ? info.description() : "";
                break;
            }
        }
    }

    private byte[] convertImageToPng(byte[] imageData) throws Exception {
        BufferedImage bufferedImage = null;

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    bufferedImage = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            // fallback below
        }

        if (bufferedImage == null) {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
        }
        if (bufferedImage == null) {
            throw new Exception("Failed to decode image");
        }

        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB &&
                bufferedImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = converted.createGraphics();
            g2d.drawImage(bufferedImage, 0, 0, null);
            g2d.dispose();
            bufferedImage = converted;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", baos);
        return baos.toByteArray();
    }

    private void debug(String message) {
        if (!DEBUG_IMAGE_LOADING) {
            return;
        }
        System.out.println("[AD-IMG-DETAIL] " + message);
    }
}
