package com.andrews.st2downloader.gui.widget;

import com.andrews.st2downloader.config.ServerDictionary;
import com.andrews.st2downloader.config.ServerDictionary.ServerEntry;
import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.models.ArchivePostSummary;
import com.andrews.st2downloader.network.ArchiveNetworkManager;
import com.andrews.st2downloader.util.RenderUtil;
import com.andrews.st2downloader.util.TagUtil;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class PostGridWidget implements Drawable, Element {
    private static final int CARD_HEIGHT = 135;
    private static final int CARD_MIN_WIDTH = 120;
    private static final int CARD_MAX_WIDTH = 160;
    private static final int GAP = 6;
    private static final int IMAGE_HEIGHT = 60;
    private static final float TEXT_SCALE = 0.7f;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private int height;
    private double scrollOffset = 0;
    private ScrollBar scrollBar;
    private List<ArchivePostSummary> posts = new ArrayList<>();
    private final OnPostClickListener onPostClick;
    private final Map<String, Identifier> imageTextures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> imageLoading = new ConcurrentHashMap<>();
    private final Map<String, int[]> imageSizes = new ConcurrentHashMap<>();
    private final Set<String> noImagePosts = ConcurrentHashMap.newKeySet();
    private final Set<String> failedImagePosts = ConcurrentHashMap.newKeySet();
    private boolean blocked = false;
    private int expectedTotalPosts = 0;
    private Runnable onEndReached;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private ServerEntry server = ServerDictionary.getDefaultServer();

    public interface OnPostClickListener {
        void onPostClick(ArchivePostSummary post);
    }

    public PostGridWidget(int x, int y, int width, int height, OnPostClickListener onPostClick) {
        this.client = MinecraftClient.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onPostClick = onPostClick;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
    }

    public void setPosts(List<ArchivePostSummary> posts) {
        resetPosts(posts);
    }

    public void resetPosts(List<ArchivePostSummary> posts) {
        this.posts = posts != null ? new ArrayList<>(posts) : new ArrayList<>();
        this.scrollOffset = 0;

        if (this.posts.isEmpty()) {
            imageTextures.clear();
            imageLoading.clear();
            imageSizes.clear();
            noImagePosts.clear();
            failedImagePosts.clear();
            return;
        }

        Set<String> idsToKeep = new HashSet<>();
        for (ArchivePostSummary post : this.posts) {
            if (post != null && post.id() != null) {
                idsToKeep.add(post.id());
            }
        }

        imageTextures.keySet().retainAll(idsToKeep);
        imageSizes.keySet().retainAll(idsToKeep);
        imageLoading.keySet().removeIf(id -> !idsToKeep.contains(id));
        noImagePosts.retainAll(idsToKeep);
        failedImagePosts.retainAll(idsToKeep);
    }

    public void appendPosts(List<ArchivePostSummary> posts) {
        if (posts == null || posts.isEmpty()) return;
        if (this.posts == null) {
            this.posts = new ArrayList<>();
        }
        this.posts.addAll(posts);
    }

    public void setExpectedTotalPosts(int total) {
        this.expectedTotalPosts = Math.max(total, 0);
    }

    public void setOnEndReached(Runnable onEndReached) {
        this.onEndReached = onEndReached;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = computeLayout();
        int columns = layout.columns();
        int cardWidth = layout.cardWidth();

        int contentHeight = computeContentHeight(columns);
        scrollBar.setScrollData(contentHeight, height);
        double maxScroll = Math.max(0, contentHeight - height);

        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        if (!scrollBar.isDragging()) {
            scrollBar.setScrollPercentage(maxScroll > 0 ? scrollOffset / maxScroll : 0);
        }
        int effectiveMouseX = blocked ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = blocked ? Integer.MIN_VALUE : mouseY;

        RenderUtil.enableScissor(context, x, y, x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y + height);

        int offsetY = (int) scrollOffset;
        int firstRow = Math.max(0, offsetY / (CARD_HEIGHT + GAP) - 1);
        int startIndex = Math.min(posts.size(), firstRow * columns);

        for (int i = startIndex; i < posts.size(); i++) {
            ArchivePostSummary post = posts.get(i);
            int row = i / columns;
            int col = i % columns;

            int cardX = layout.startX() + col * (cardWidth + GAP);
            int cardY = y + GAP + row * (CARD_HEIGHT + GAP) - offsetY;

            if (cardY > y + height) {
                break;
            }
            if (cardY + CARD_HEIGHT < y) {
                continue;
            }

            renderCard(context, post, cardX, cardY, cardWidth, CARD_HEIGHT, effectiveMouseX, effectiveMouseY);
        }

        RenderUtil.disableScissor(context);
        if (client != null && client.getWindow() != null) {
            if (blocked) {
                scrollBar.render(context, mouseX, mouseY, delta);
            } else {
                boolean changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());
                if (changed || scrollBar.isDragging()) {
                    scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - height);
                }
            }
        } else {
            scrollBar.render(context, mouseX, mouseY, delta);
        }

        int loadedContentHeight = computeLoadedContentHeight(columns);
        if (onEndReached != null && posts.size() < expectedTotalPosts &&
            scrollOffset + height >= loadedContentHeight - CARD_HEIGHT) {
            onEndReached.run();
        }
    }

    private void renderCard(DrawContext context, ArchivePostSummary post, int cardX, int cardY, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        int bgColor = UITheme.Colors.PANEL_BG;
        boolean hovered = mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY && mouseY < cardY + cardHeight;
        if (hovered) {
            bgColor = UITheme.Colors.BUTTON_BG_HOVER;
        }
        RenderUtil.fillRect(context, cardX, cardY, cardX + cardWidth, cardY + cardHeight, bgColor);
        RenderUtil.fillRect(context, cardX, cardY, cardX + cardWidth, cardY + 1, UITheme.Colors.BUTTON_BORDER);

        int imgPadding = 5;
        int imgX = cardX + imgPadding;
        int imgY = cardY + imgPadding;
        int imgW = cardWidth - imgPadding * 2;

        Identifier tex = imageTextures.get(post.id());
        if (tex != null) {
            int[] dims = imageSizes.get(post.id());
            int drawW = imgW;
            int drawH = IMAGE_HEIGHT;
            if (dims != null && dims[0] > 0 && dims[1] > 0) {
                double scale = Math.min((double) imgW / dims[0], (double) IMAGE_HEIGHT / dims[1]);
                drawW = Math.max(1, (int) (dims[0] * scale));
                drawH = Math.max(1, (int) (dims[1] * scale));
            }
            int drawX = imgX + (imgW - drawW) / 2;
            int drawY = imgY + (IMAGE_HEIGHT - drawH) / 2;
            RenderUtil.fillRect(context, imgX, imgY, imgX + imgW, imgY + IMAGE_HEIGHT, UITheme.Colors.CONTAINER_BG);
            RenderUtil.blit(
                context,
                RenderLayer::getGuiTextured,
                tex,
                drawX,
                drawY,
                0,
                0,
                drawW,
                drawH,
                drawW,
                drawH
            );
        } else {
            RenderUtil.fillRect(context, imgX, imgY, imgX + imgW, imgY + IMAGE_HEIGHT, UITheme.Colors.CONTAINER_BG);
            ensureImageLoading(post);
            boolean hasId = post != null && post.id() != null;
            boolean failed = hasId && failedImagePosts.contains(post.id());
            boolean noImage = hasId && noImagePosts.contains(post.id());
            boolean loading = hasId && imageLoading.containsKey(post.id());
            String status;
            if (failed) {
                status = "Failed to load";
            } else if (noImage) {
                status = "No image";
            } else if (loading) {
                status = "Loading...";
            } else {
                status = "Loading...";
            }
            int textWidth = (int) (client.textRenderer.getWidth(status) * 0.8f);
            int textHeight = (int) (client.textRenderer.fontHeight * 0.8f);
            int textX = imgX + Math.max(0, (imgW - textWidth) / 2);
            int textY = imgY + Math.max(0, (IMAGE_HEIGHT - textHeight) / 2);
            RenderUtil.drawScaledString(context, status, textX, textY, UITheme.Colors.TEXT_SUBTITLE, 0.8f);
        }

        String title = post.title() != null ? post.title() : "Untitled";
        int textY = imgY + IMAGE_HEIGHT + 3;
        int titleWidth = cardWidth - imgPadding * 2;
        int titleHeight = drawScaledWrappedTextLimited(context, title, cardX + imgPadding, textY, titleWidth, UITheme.Colors.TEXT_PRIMARY, TEXT_SCALE, 2);
        textY += titleHeight + 4;

        long timestamp = post.updatedAt() > 0 ? post.updatedAt() : post.archivedAt();
        String dateText = timeAgo(timestamp);
        String code = post.code() != null ? post.code() : "";
        String metaLine = code.isEmpty() ? dateText : code + " • " + dateText;
        if (!metaLine.isEmpty()) {
            RenderUtil.drawScaledString(context, metaLine, cardX + imgPadding, textY, UITheme.Colors.TEXT_SUBTITLE, 0.7f);
            textY += (int) (client.textRenderer.fontHeight * 0.7f) + 4;
        }

        String authorLine = (post.authors() != null && post.authors().length > 0) ? String.join(", ", post.authors()) : "Unknown Author";
        int authorHeight = drawScaledWrappedTextLimited(context, authorLine, cardX + imgPadding, textY, cardWidth - imgPadding * 2, UITheme.Colors.TEXT_SUBTITLE, TEXT_SCALE * 0.9f, 2);
        textY += authorHeight + 4;

        String[] tags = post.tags();
        if (tags != null && tags.length > 0) {
            int tagX = cardX + imgPadding;
            int maxWidth = cardWidth - imgPadding * 2;
            int tagHeight = 10;
            float tagScale = 0.7f;
            int tagY = textY;
            int rows = 1;
            int maxRows = 2;
            for (String tag : TagUtil.orderTags(tags)) {
                if (tag == null) continue;
                int tw = (int) (client.textRenderer.getWidth(tag) * tagScale) + 8;
                if (tagX + tw > cardX + imgPadding + maxWidth) {
                    rows++;
                if (rows > maxRows) {
                    break;
                }
                tagX = cardX + imgPadding;
                tagY += tagHeight + 2;
            }
            RenderUtil.fillRect(context, tagX, tagY, tagX + tw, tagY + tagHeight, TagUtil.getTagColor(tag));
            RenderUtil.drawScaledString(context, tag, tagX + 4, tagY + 2, UITheme.Colors.TEXT_TAG, tagScale);
            tagX += tw + 4;
        }
            textY = tagY + tagHeight + 2;
        }
    }

    private int computeContentHeight(int columns) {
        int total = Math.max(expectedTotalPosts, posts != null ? posts.size() : 0);
        int rows = (int) Math.ceil(total / (double) Math.max(columns, 1));
        return rows * (CARD_HEIGHT + GAP) + GAP;
    }

    private int computeLoadedContentHeight(int columns) {
        int loaded = posts != null ? posts.size() : 0;
        int rows = (int) Math.ceil(loaded / (double) Math.max(columns, 1));
        return rows * (CARD_HEIGHT + GAP) + GAP;
    }

    private int drawScaledWrappedTextLimited(DrawContext context, String text, int textX, int textY, int maxWidth, int color, float scale, int maxLines) {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = textY;
        int linesDrawn = 0;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = (int) (client.textRenderer.getWidth(testLine) * scale);

            if (testWidth > maxWidth && !line.isEmpty()) {
                RenderUtil.drawScaledString(context, line.toString(), textX, lineY, color, scale);
                linesDrawn++;
                if (linesDrawn >= maxLines) {
                    return linesDrawn * (int) (client.textRenderer.fontHeight * scale);
                }
                line = new StringBuilder(word);
                lineY += (int) (client.textRenderer.fontHeight * scale);
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (!line.isEmpty() && linesDrawn < maxLines) {
            RenderUtil.drawScaledString(context, line.toString(), textX, lineY, color, scale);
            linesDrawn++;
        }

        return linesDrawn * (int) (client.textRenderer.fontHeight * scale);
    }

    private String timeAgo(long ts) {
        if (ts <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - ts);
        long sec = Math.round(diff / 1000.0);
        long min = Math.round(sec / 60.0);
        long hr = Math.round(min / 60.0);
        long day = Math.round(hr / 24.0);
        long yr = Math.round(day / 365.0);
        if (sec < 60) return sec + "s ago";
        if (min < 60) return min + "m ago";
        if (hr < 48) return hr + "h ago";
        if (day < 365) return day + "d ago";
        return yr + "y ago";
    }

    private Layout computeLayout() {
        int availableWidth = width - UITheme.Dimensions.SCROLLBAR_WIDTH - GAP;
        int maxColumns = Math.max(1, (availableWidth + GAP) / (CARD_MIN_WIDTH + GAP));
        int columns = maxColumns;
        int cardWidth = CARD_MIN_WIDTH;

        while (columns > 0) {
            int candidate = (availableWidth - GAP * (columns - 1)) / columns;
            if (candidate < CARD_MIN_WIDTH) {
                columns--;
                continue;
            }
            cardWidth = Math.min(CARD_MAX_WIDTH, candidate);
            break;
        }

        if (columns <= 0) {
            columns = 1;
            cardWidth = Math.min(CARD_MAX_WIDTH, Math.max(CARD_MIN_WIDTH, availableWidth - GAP));
        }

        int usedWidth = columns * cardWidth + GAP * (columns - 1);
        int startX = x + Math.max(GAP, (width - UITheme.Dimensions.SCROLLBAR_WIDTH - usedWidth) / 2);
        return new Layout(columns, cardWidth, startX);
    }

    private record Layout(int columns, int cardWidth, int startX) {}

    private void ensureImageLoading(ArchivePostSummary post) {
        if (post == null || post.id() == null) return;
        if (noImagePosts.contains(post.id())) return;
        if (failedImagePosts.contains(post.id())) return;
        if (imageTextures.containsKey(post.id()) || imageLoading.containsKey(post.id())) return;

        CompletableFuture<Void> future = ArchiveNetworkManager.getPostDetails(server, post)
            .thenApply(detail -> {
                if (detail == null || detail.images().isEmpty()) {
                    noImagePosts.add(post.id());
                    return null;
                }
                return detail.images().get(0);
            })
            .thenCompose(url -> {
                if (url == null || url.isEmpty()) {
                    noImagePosts.add(post.id());
                    return CompletableFuture.completedFuture(null);
                }
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.replace(" ", "%20")))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", ArchiveNetworkManager.USER_AGENT)
                    .GET()
                    .build();
                return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0) {
                            throw new RuntimeException("Image request failed with status " + resp.statusCode());
                        }
                        return resp.body();
                    });
            })
            .thenAccept(bytes -> {
                if (bytes == null) {
                    noImagePosts.add(post.id());
                    return;
                }
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                    if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
                        failedImagePosts.add(post.id());
                        return;
                    }
                    String id = UUID.randomUUID().toString().replace("-", "");
                    Identifier texId = Identifier.of("litematicdownloader", "grid/" + id);
                    if (client != null) {
                        client.execute(() -> {
                            client.getTextureManager().registerTexture(texId, new NativeImageBackedTexture(img));
                            imageTextures.put(post.id(), texId);
                            imageSizes.put(post.id(), new int[]{img.getWidth(), img.getHeight()});
                        });
                    }
                } catch (Exception e) {
                    failedImagePosts.add(post.id());
                    System.err.println("Failed to load grid image: " + e.getMessage());
                }
            })
            .exceptionally(ex -> {
                failedImagePosts.add(post.id());
                System.err.println("Image load failed for " + post.id() + ": " + ex.getMessage());
                return null;
            })
            .whenComplete((r, t) -> imageLoading.remove(post.id()));

        imageLoading.put(post.id(), future);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (blocked) return false;
        if (button != 0) return false;

        Layout layout = computeLayout();
        int columns = layout.columns();
        int cardWidth = layout.cardWidth();
        int offsetY = (int) scrollOffset;

        for (int i = 0; i < posts.size(); i++) {
            int row = i / columns;
            int col = i % columns;

            int cardX = layout.startX() + col * (cardWidth + GAP);
            int cardY = y + GAP + row * (CARD_HEIGHT + GAP) - offsetY;

            if (mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT) {
                if (onPostClick != null) {
                    onPostClick.onPostClick(posts.get(i));
                }
                return true;
            }
        }

        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (blocked) return false;
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            Layout layout = computeLayout();
            double contentHeight = computeContentHeight(layout.columns());
            double maxScroll = Math.max(0, contentHeight - height);
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 18, maxScroll));
            scrollBar.setScrollPercentage((float) (maxScroll > 0 ? scrollOffset / maxScroll : 0));
            int loadedContentHeight = computeLoadedContentHeight(layout.columns());
            if (onEndReached != null && posts.size() < expectedTotalPosts &&
                scrollOffset + height >= loadedContentHeight - CARD_HEIGHT) {
                onEndReached.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (blocked) return false;
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            Layout layout = computeLayout();
            double contentHeight = computeContentHeight(layout.columns());
            scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - height);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}
