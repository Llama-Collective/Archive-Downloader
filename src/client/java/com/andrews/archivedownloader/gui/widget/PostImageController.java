package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.models.ArchiveImageInfo;
import com.andrews.archivedownloader.network.ArchiveNetworkManager;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Handles image state, loading, and viewer interactions for the post detail view.
 */
public class PostImageController {

    private final Minecraft client;
    private final LoadingSpinner loadingSpinner;
    private ServerEntry server = ServerDictionary.getDefaultServer();

    private List<ArchiveImageInfo> imageInfos = new ArrayList<>();
    private String currentImageDescription = "";
    private boolean isLoadingImage = false;

    private String[] imageUrls = new String[0];
    private int currentImageIndex = 0;
    private Identifier currentImageTexture;
    private final Map<String, Identifier> imageCache = new ConcurrentHashMap<>();
    private final Set<String> preloadingImages = ConcurrentHashMap.newKeySet();
    private String loadingImageUrl = null;
    private int originalImageWidth = 0;
    private int originalImageHeight = 0;
    private final Map<String, int[]> imageDimensionsCache = new ConcurrentHashMap<>();

    private ImageViewerWidget imageViewer;

    public PostImageController(Minecraft client) {
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
        imageDimensionsCache.clear();
        imageViewer = null;
    }

    public void setImageInfos(List<ArchiveImageInfo> infos) {
        imageInfos = infos != null ? new ArrayList<>(infos) : new ArrayList<>();
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

    public boolean hasImages() {
        return imageUrls.length > 0;
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

    public Identifier getCurrentImageTexture() {
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
        if (currentImageTexture == null || client == null) {
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

    public void renderImageViewer(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (imageViewer != null) {
            imageViewer.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return imageViewer != null && imageViewer.mouseClicked(click, doubled);
    }

    public boolean mouseReleased(MouseButtonEvent click) {
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
        if (client == null || client.getWindow() == null) {
            return;
        }
        closeImageViewer();
        openImageViewer(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }

    private void loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (imageCache.containsKey(imageUrl)) {
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

        isLoadingImage = true;
        loadingImageUrl = imageUrl;

        loadImageAsync(imageUrl).thenAccept(texId -> {
            if (client != null) {
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
                    }
                });
            }
        }).exceptionally(ex -> {
            if (client != null) {
                client.execute(() -> {
                    isLoadingImage = false;
                    System.err.println("Failed to load image: " + ex.getMessage());
                });
            }
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
            if (ex != null && client != null) {
                client.execute(() -> System.err.println("Failed to preload image: " + ex.getMessage()));
            }
            return null;
        });
    }

    private CompletableFuture<Identifier> loadImageAsync(String imageUrl) {
        if (imageCache.containsKey(imageUrl)) {
            return CompletableFuture.completedFuture(imageCache.get(imageUrl));
        }

        String encodedUrl = encodeImageUrl(imageUrl);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(encodedUrl))
                .GET()
                .header("User-Agent", ArchiveNetworkManager.USER_AGENT);
        ArchiveNetworkManager.applyApiAuthorization(builder, server, encodedUrl);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode()));
                    }

                    byte[] imageData = response.body();
                    byte[] pngBytes;
                    try {
                        pngBytes = convertImageToPng(imageData);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }

                    NativeImage nativeImage;
                    try {
                        nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }

                    int imgWidth = nativeImage.getWidth();
                    int imgHeight = nativeImage.getHeight();

                    if (imgWidth <= 0 || imgHeight <= 0 || imgWidth > 4096 || imgHeight > 4096) {
                        nativeImage.close();
                        throw new CompletionException(new RuntimeException("Invalid image dimensions"));
                    }

                    imageDimensionsCache.put(imageUrl, new int[] { imgWidth, imgHeight });

                    final String uniqueId = UUID.randomUUID().toString().replace("-", "");
                    final Identifier texId = Identifier.fromNamespaceAndPath("litematicdownloader",
                            "textures/dynamic/" + uniqueId);

                    if (client != null) {
                        client.execute(() -> {
                            client.getTextureManager().register(
                                    texId,
                                    new DynamicTexture(() -> "post_image", nativeImage));
                            imageCache.put(imageUrl, texId);
                        });
                    }
                    return texId;
                });
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

    private String encodeImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String encodedPath = path.replace(" ", "%20");
            return uri.getScheme() + "://" + uri.getHost() +
                    (uri.getPort() != -1 ? ":" + uri.getPort() : "") +
                    encodedPath +
                    (uri.getQuery() != null ? "?" + uri.getQuery() : "");
        } catch (Exception e) {
            return url.replace(" ", "%20");
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
}
