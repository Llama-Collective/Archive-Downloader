package com.andrews.st2downloader.gui.widget;

import com.andrews.st2downloader.config.ServerDictionary;
import com.andrews.st2downloader.config.ServerDictionary.ServerEntry;
import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.models.ArchiveAttachment;
import com.andrews.st2downloader.models.ArchivePostDetail;
import com.andrews.st2downloader.models.ArchivePostSummary;
import com.andrews.st2downloader.models.ArchiveRecordSection;
import com.andrews.st2downloader.network.ArchiveNetworkManager;
import com.andrews.st2downloader.util.AttachmentManager;
import com.andrews.st2downloader.util.RenderUtil;
import com.andrews.st2downloader.util.TagUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class PostDetailPanel implements Drawable, Element {
    private static final int MAX_IMAGE_SIZE = 120;

    private int x;
    private int y;
    private int width;
    private int height;

    private ArchivePostSummary postInfo;
    private ArchivePostDetail postDetail;
    private boolean isLoadingDetails = false;

    private final MinecraftClient client;
    private final PostImageController imageController;
    private final AttachmentManager attachmentManager;

    private double scrollOffset = 0;
    private int contentHeight = 0;

    private CustomButton prevImageButton;
    private CustomButton nextImageButton;
    private CustomButton discordThreadButton;
    private CustomButton websiteButton;

    private ScrollBar scrollBar;
    private final List<AttachmentHitbox> attachmentHitboxes = new ArrayList<>();

    private Consumer<String> discordLinkOpener;
    private ServerEntry server = ServerDictionary.getDefaultServer();

    public PostDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.imageController = new PostImageController(this.client);
        this.attachmentManager = new AttachmentManager(this.client);
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
    }

    public void setDiscordLinkOpener(Consumer<String> opener) {
        this.discordLinkOpener = opener;
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
        this.attachmentManager.setServer(this.server);
    }

    private int getDisplayImageWidth() {
        return width - 10;
    }

    private int getDisplayImageHeight() {
        return Math.min(MAX_IMAGE_SIZE, height / 2);
    }

    private int getActualImageWidth() {
        int originalImageWidth = imageController.getOriginalImageWidth();
        int originalImageHeight = imageController.getOriginalImageHeight();

        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageWidth();
        }

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int widthAtContainerHeight = (int) ((float) originalImageWidth / originalImageHeight * containerHeight);

        if (widthAtContainerHeight <= containerWidth) {
            return Math.min(originalImageWidth, widthAtContainerHeight);
        } else {
            return Math.min(originalImageWidth, containerWidth);
        }
    }

    private int getActualImageHeight() {
        int originalImageWidth = imageController.getOriginalImageWidth();
        int originalImageHeight = imageController.getOriginalImageHeight();
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageHeight();
        }

        int containerHeight = getDisplayImageHeight();

        int actualWidth = getActualImageWidth();
        int calculatedHeight = (int) ((float) originalImageHeight / originalImageWidth * actualWidth);

        return Math.min(originalImageHeight, Math.min(containerHeight, calculatedHeight));
    }

    private boolean isCompactMode() {
        return width < 200;
    }

    private void updateCarouselButtons(int imageNavY) {
        int imageCount = imageController.getImageCount();
        if (imageCount <= 1) {
            prevImageButton = null;
            nextImageButton = null;
            return;
        }

        boolean compact = isCompactMode();
        int btnWidth = compact ? 18 : 25;
        int btnHeight = compact ? 14 : 16;
        int btnSpacing = compact ? 5 : 10;

        String indicator = String.format("%d / %d", imageController.getCurrentImageIndex() + 1, imageCount);
        int indicatorWidth = client.textRenderer.getWidth(indicator);
        int indicatorX = x + (width - indicatorWidth) / 2;

        int prevBtnX = indicatorX - btnWidth - btnSpacing;
        int nextBtnX = indicatorX + indicatorWidth + btnSpacing;

        if (prevImageButton == null) {
            prevImageButton = new CustomButton(prevBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of("<"), btn -> imageController.previousImage());
        } else {
            prevImageButton.setX(prevBtnX);
            prevImageButton.setY(imageNavY);
            prevImageButton.setWidth(btnWidth);
        }

        if (nextImageButton == null) {
            nextImageButton = new CustomButton(nextBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of(">"), btn -> imageController.nextImage());
        } else {
            nextImageButton.setX(nextBtnX);
            nextImageButton.setY(imageNavY);
            nextImageButton.setWidth(btnWidth);
        }
    }

    public void setPost(ArchivePostSummary post) {
        if (post == null) {
            clear();
            return;
        }

        if (this.postInfo != null && post.id() != null && post.id().equals(this.postInfo.id())) {
            return;
        }

        clearDownloadState();
        imageController.clear();

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.scrollOffset = 0;
        this.attachmentHitboxes.clear();

        ArchiveNetworkManager.getPostDetails(server, post)
                .thenAccept(this::handlePostDetailLoaded)
                .exceptionally(throwable -> {
                    if (client != null) {
                        client.execute(() -> {
                            isLoadingDetails = false;
                            System.err.println(
                                    "[PostDetailPanel] Failed to load post details: " + throwable.getMessage());
                        });
                    }
                    return null;
                });

        imageController.setImages(List.of());
    }

    private void handlePostDetailLoaded(ArchivePostDetail detail) {
        if (client != null) {
            client.execute(() -> {
                this.postDetail = detail;
                this.isLoadingDetails = false;
                attachmentManager.setAvailableFiles(detail.attachments());
                imageController.setImageInfos(detail.imageInfos());

                List<String> detailImages = detail.images();
                if (detailImages != null && !detailImages.isEmpty()) {
                    imageController.setImages(detailImages);
                    imageController.loadCurrentImageIfNeeded();
                } else {
                    imageController.setImages(List.of());
                }
            });
        }
    }


    public void clear() {
        this.postInfo = null;
        this.postDetail = null;
        this.isLoadingDetails = false;
        this.scrollOffset = 0;
        imageController.clear();
        clearDownloadState();
    }

    private void clearDownloadState() {
        attachmentManager.clear();
        this.discordThreadButton = null;
        this.websiteButton = null;
    }

    private boolean hasDiscordThread() {
        String url = getDiscordThreadUrl();
        return url != null && !url.isBlank();
    }

    private boolean hasWebsiteLink() {
        if (!server.id().equals("st2")) {
            return false;
        }
        String id = getCurrentPostId();
        return id != null && !id.isBlank();
    }

    public String getCurrentPostId() {
        return postInfo != null ? postInfo.id() : null;
    }

    private String getDiscordThreadUrl() {
        if (postDetail != null && postDetail.discordPost() != null) {
            return postDetail.discordPost().threadURL();
        }
        return null;
    }

    private void ensureDiscordButton(int width, int xPos, int yPos) {
        if (discordThreadButton == null) {
            discordThreadButton = new CustomButton(
                    xPos,
                    yPos,
                    width,
                    UITheme.Dimensions.BUTTON_HEIGHT,
                    Text.of("Open Discord Thread"),
                    button -> openDiscordThread());
        }
        discordThreadButton.active = hasDiscordThread();
        discordThreadButton.setWidth(width);
        discordThreadButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
        discordThreadButton.setX(xPos);
        discordThreadButton.setY(yPos);
    }

    private void ensureWebsiteButton(int width, int xPos, int yPos) {
        if (websiteButton == null) {
            websiteButton = new CustomButton(
                    xPos,
                    yPos,
                    width,
                    UITheme.Dimensions.BUTTON_HEIGHT,
                    Text.of("Open On Website"),
                    button -> openWebsiteLink());
        }
        websiteButton.active = hasWebsiteLink();
        websiteButton.setWidth(width);
        websiteButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
        websiteButton.setX(xPos);
        websiteButton.setY(yPos);
    }

    private void openDiscordThread() {
        String url = getDiscordThreadUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        if (discordLinkOpener != null) {
            discordLinkOpener.accept(url);
            return;
        }
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            System.err.println("Failed to open Discord thread: " + e.getMessage());
        }
    }

    private void openWebsiteLink() {
        if (postInfo == null) {
            return;
        }
        String slug = buildEntrySlug(postInfo.code(), postInfo.title());
        String url;
        if (slug == null || slug.isBlank()) {
            String id = getCurrentPostId();
            if (id == null || id.isBlank()) {
                return;
            }
            url = "http://storagetech2.org/?id=" + id;
        } else {
            url = "http://storagetech2.org/archives/" + slug;
        }
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            System.err.println("Failed to open website: " + e.getMessage());
        }
    }

    private static String slugifyName(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        String withoutDiacritics = normalized.replaceAll("\\p{M}", "");
        String replaced = withoutDiacritics.replaceAll("[^a-zA-Z0-9]+", "-");
        return replaced.replaceAll("^-+|-+$", "");
    }

    private static String buildEntrySlug(String code, String entryName) {
        String base = code != null ? code : "";
        String name = slugifyName(entryName);
        if (name.isEmpty()) {
            if (base.isEmpty()) {
                return "";
            }
            return base;
        }
        if (base.isEmpty()) {
            return name;
        }
        return base + "-" + name;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int renderMouseX = mouseX;
        int renderMouseY = mouseY;

        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);

        RenderUtil.fillRect(context, x, y, x + 1, y + height, UITheme.Colors.BUTTON_BORDER);

        if (postInfo == null) {
            String text = "Select a schematic to view details";
            int textWidth = client.textRenderer.getWidth(text);
            RenderUtil.drawString(context, client.textRenderer, text,
                    x + (width - textWidth) / 2, y + height / 2 - 4, UITheme.Colors.TEXT_SUBTITLE);
            return;
        }

        int contentStartY = y + UITheme.Dimensions.PADDING;
        RenderUtil.enableScissor(context, x + 1, contentStartY, x + width, y + height);

        int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;
        contentHeight = 0;
        attachmentHitboxes.clear();

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int actualImageWidth = getActualImageWidth();
        int actualImageHeight = getActualImageHeight();

        int containerX = x + 1;
        int containerY = currentY;

        int imageX = containerX + (containerWidth - actualImageWidth) / 2;
        int imageY = containerY + (containerHeight - actualImageHeight) / 2;

        Identifier currentImageTexture = imageController.getCurrentImageTexture();
        boolean isLoadingImage = imageController.isLoadingImage();

        if (isLoadingImage) {
            RenderUtil.fillRect(context, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.CONTAINER_BG);
            LoadingSpinner spinner = imageController.getLoadingSpinner();
            spinner.setPosition(
                    containerX + containerWidth / 2 - spinner.getWidth() / 2,
                    containerY + containerHeight / 2 - spinner.getHeight() / 2);
            spinner.render(context, mouseX, mouseY, delta);
        } else if (currentImageTexture != null) {
            RenderUtil.fillRect(context, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.PANEL_BG);
            RenderUtil.blit(
                    context,
                    RenderLayer::getGuiTextured,
                    currentImageTexture,
                    imageX, imageY,
                    0, 0,
                    actualImageWidth, actualImageHeight,
                    actualImageWidth, actualImageHeight);
        } else {
            RenderUtil.fillRect(context, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.CONTAINER_BG);
            String noImg = isCompactMode() ? "..." : "No image";
            int tw = client.textRenderer.getWidth(noImg);
            RenderUtil.drawString(context, client.textRenderer, noImg,
                    containerX + (containerWidth - tw) / 2, containerY + containerHeight / 2 - 4,
                    UITheme.Colors.TEXT_SUBTITLE);
        }

        currentY += containerHeight + UITheme.Dimensions.PADDING;
        contentHeight += containerHeight + UITheme.Dimensions.PADDING;

        String imageDescription = imageController.getCurrentImageDescription();
        if (imageDescription != null && !imageDescription.isEmpty()) {
            int descWidth = width - UITheme.Dimensions.PADDING * 2;
            RenderUtil.drawWrappedText(context, client.textRenderer, imageDescription, x + UITheme.Dimensions.PADDING, currentY, descWidth,
                    UITheme.Colors.TEXT_SUBTITLE);
            int descHeight = RenderUtil.getWrappedTextHeight(client.textRenderer, imageDescription, descWidth);
            currentY += descHeight + 6;
            contentHeight += descHeight + 6;
        }

        if (imageController.hasMultipleImages()) {
            String indicator = String.format("%d / %d", imageController.getCurrentImageIndex() + 1,
                    imageController.getImageCount());
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;
            int btnY = currentY;

            updateCarouselButtons(btnY);

            if (prevImageButton != null) {
                prevImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            RenderUtil.drawString(context, client.textRenderer, indicator, indicatorX, btnY + 4, UITheme.Colors.TEXT_SUBTITLE);

            if (nextImageButton != null) {
                nextImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            currentY += 16 + UITheme.Dimensions.PADDING;
            contentHeight += 16 + UITheme.Dimensions.PADDING;
        }

        String title = postInfo.title() != null ? postInfo.title() : "Untitled";
        RenderUtil.drawWrappedText(context, client.textRenderer, title, x + UITheme.Dimensions.PADDING, currentY,
                width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_PRIMARY);
        int titleHeight = RenderUtil.getWrappedTextHeight(client.textRenderer, title, width - UITheme.Dimensions.PADDING * 2);
        currentY += titleHeight + 8;
        contentHeight += titleHeight + 8;

        String metaLine = buildMetaLine();
        RenderUtil.drawString(context, client.textRenderer, metaLine, x + UITheme.Dimensions.PADDING, currentY,
                UITheme.Colors.TEXT_SUBTITLE);
        currentY += 16;
        contentHeight += 16;

        if (postDetail != null && postDetail.authors() != null && !postDetail.authors().isEmpty()) {
            String authorLine = "By: " + String.join(", ", postDetail.authors());
            RenderUtil.drawWrappedText(context, client.textRenderer, authorLine, x + UITheme.Dimensions.PADDING, currentY,
                    width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_SUBTITLE);
            int authorHeight = RenderUtil.getWrappedTextHeight(client.textRenderer, authorLine, width - UITheme.Dimensions.PADDING * 2);
            currentY += authorHeight + 4;
            contentHeight += authorHeight + 4;
        }

        String[] tags = postInfo.tags();
        if (tags != null && tags.length > 0) {
            RenderUtil.drawString(context, client.textRenderer, "Tags:", x + UITheme.Dimensions.PADDING, currentY,
                    UITheme.Colors.TEXT_SUBTITLE);
            currentY += 12;
            contentHeight += 12;

            int tagX = x + UITheme.Dimensions.PADDING;
            for (String tag : TagUtil.orderTags(tags, server)) {
                String displayTag = TagUtil.formatTagLabel(tag, server);
                int tagWidth = client.textRenderer.getWidth(displayTag) + 8;
                if (tagX + tagWidth > x + width - UITheme.Dimensions.PADDING) {
                    tagX = x + UITheme.Dimensions.PADDING;
                    currentY += 14;
                    contentHeight += 14;
                }
                RenderUtil.fillRect(context, tagX, currentY, tagX + tagWidth, currentY + 12, TagUtil.getTagColor(tag, server));
                RenderUtil.drawString(context, client.textRenderer, displayTag, tagX + 4, currentY + 2, UITheme.Colors.TEXT_TAG);
                tagX += tagWidth + 4;
            }
            currentY += 16;
            contentHeight += 16;
        }

        if (postDetail != null && postDetail.recordSections() != null && !postDetail.recordSections().isEmpty()) {
            currentY += 8;
            contentHeight += 8;
            for (ArchiveRecordSection section : postDetail.recordSections()) {
                if (section == null)
                    continue;
                String header = section.title() != null ? section.title() : "Details";
                RenderUtil.drawString(context, client.textRenderer, header + ":", x + UITheme.Dimensions.PADDING, currentY,
                        UITheme.Colors.TEXT_SUBTITLE);
                currentY += 12;
                contentHeight += 12;

                List<String> lines = section.lines();
                if (lines != null) {
                    for (String line : lines) {
                        if (line == null || line.isEmpty())
                            continue;
                        RenderUtil.drawWrappedText(context, client.textRenderer, line, x + UITheme.Dimensions.PADDING, currentY,
                                width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_TAG);
                        int lineHeight = RenderUtil.getWrappedTextHeight(client.textRenderer, line, width - UITheme.Dimensions.PADDING * 2);
                        currentY += lineHeight;
                        contentHeight += lineHeight;
                    }
                }
                currentY += 6;
                contentHeight += 6;
            }
        } else if (isLoadingDetails) {
            currentY += 8;
            RenderUtil.drawString(context, client.textRenderer, "Loading details...", x + UITheme.Dimensions.PADDING, currentY,
                    UITheme.Colors.TEXT_SUBTITLE);
            contentHeight += 20;
        }

        if (attachmentManager.hasAttachments()) {
            RenderUtil.drawString(context, client.textRenderer, "Attachments:", x + UITheme.Dimensions.PADDING, currentY,
                    UITheme.Colors.TEXT_SUBTITLE);
            currentY += 12;
            contentHeight += 12;

            int rowWidth = width - UITheme.Dimensions.PADDING * 2;
            int rowX = x + UITheme.Dimensions.PADDING;
            for (ArchiveAttachment attachment : attachmentManager.getAvailableFiles()) {
                if (attachment == null)
                    continue;
                int rowY = currentY;
                String nameText = attachment.name() != null ? attachment.name() : "Attachment";
                String meta = attachmentManager.buildAttachmentMeta(attachment);
                int nameHeight = (int) (client.textRenderer.fontHeight * 0.85f) + 6;
                int metaHeight = (meta != null && !meta.isEmpty()) ? client.textRenderer.fontHeight + 2 : 0;
                int descWidth = rowWidth - 12;
                int descHeight = 0;
                if (attachment.description() != null && !attachment.description().isEmpty()) {
                    descHeight = RenderUtil.getWrappedTextHeight(client.textRenderer, attachment.description(), descWidth) + 2;
                }
                int rowHeight = nameHeight + metaHeight + descHeight + 4;

                boolean isHover = renderMouseX >= rowX && renderMouseX <= rowX + rowWidth &&
                        renderMouseY >= rowY && renderMouseY <= rowY + rowHeight;
                int bgColor = isHover ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
                RenderUtil.fillRect(context, rowX, rowY, rowX + rowWidth, rowY + rowHeight, bgColor);

                int cursorY = rowY + 3;
                RenderUtil.drawScaledString(context, nameText, rowX + 6, cursorY, UITheme.Colors.TEXT_PRIMARY, 0.85f,
                        rowWidth - 12);
                cursorY += nameHeight;

                if (metaHeight > 0) {
                    RenderUtil.drawString(context, client.textRenderer, meta, rowX + 6, cursorY - 2, UITheme.Colors.TEXT_SUBTITLE);
                    cursorY += metaHeight;
                }

                if (descHeight > 0) {
                    RenderUtil.drawWrappedText(context, client.textRenderer, attachment.description(), rowX + 6, cursorY, descWidth,
                            UITheme.Colors.TEXT_TAG);
                    cursorY += descHeight;
                }

                attachmentHitboxes.add(new AttachmentHitbox(rowX, rowY, rowX + rowWidth, rowY + rowHeight, attachment));
                currentY += rowHeight + 6;
                contentHeight += rowHeight + 6;
            }

        }

        if (hasWebsiteLink()) {
            int buttonWidth = Math.min(200, width - UITheme.Dimensions.PADDING * 2);
            int buttonX = x + UITheme.Dimensions.PADDING;
            int buttonY = currentY + 4;
            ensureWebsiteButton(buttonWidth, buttonX, buttonY);
            websiteButton.render(context, mouseX, mouseY, delta);
            currentY += UITheme.Dimensions.BUTTON_HEIGHT + 6;
            contentHeight += UITheme.Dimensions.BUTTON_HEIGHT + 6;
        }

        if (hasDiscordThread()) {
            int buttonWidth = Math.min(200, width - UITheme.Dimensions.PADDING * 2);
            int buttonX = x + UITheme.Dimensions.PADDING;
            int buttonY = currentY + 4;
            ensureDiscordButton(buttonWidth, buttonX, buttonY);
            discordThreadButton.render(context, mouseX, mouseY, delta);
            currentY += UITheme.Dimensions.BUTTON_HEIGHT + 10;
            contentHeight += UITheme.Dimensions.BUTTON_HEIGHT + 10;
        }

        contentHeight += UITheme.Dimensions.PADDING * 2;

        RenderUtil.disableScissor(context);

        if (contentHeight > height) {
            scrollBar.setScrollData(contentHeight, height);
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, contentHeight - height));

            if (client != null && client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();
                if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                    double maxScroll = Math.max(0, contentHeight - height);
                    scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
                }
            } else {
                scrollBar.render(context, mouseX, mouseY, delta);
            }
        }

    }

    public boolean hasImageViewerOpen() {
        return imageController.hasImageViewerOpen();
    }

    public void renderImageViewer(DrawContext context, int mouseX, int mouseY, float delta) {
        imageController.renderImageViewer(context, mouseX, mouseY, delta);
    }

    private String buildMetaLine() {
        String channel = postInfo.channelName() != null ? postInfo.channelName() : "Archive";
        String code = postInfo.code() != null ? postInfo.code() : "";
        long updatedAt = postDetail != null ? postDetail.updatedAt() : postInfo.updatedAt();
        long archivedAt = postDetail != null ? postDetail.archivedAt() : postInfo.archivedAt();

        String dateText = updatedAt > 0
                ? "Updated: " + formatDate(updatedAt)
                : (archivedAt > 0 ? "Archived: " + formatDate(archivedAt) : "Date unknown");

        if (!code.isEmpty()) {
            return String.format("%s • %s • %s", channel, code, dateText);
        }
        return String.format("%s • %s", channel, dateText);
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return "Unknown";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(millis));
    }

    private record AttachmentHitbox(int x1, int y1, int x2, int y2, ArchiveAttachment attachment) {
        boolean contains(double px, double py) {
            return px >= x1 && px <= x2 && py >= y1 && py <= y2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (imageController.hasImageViewerOpen()) {
            return imageController.mouseClicked(mouseX, mouseY, button);
        }

        if (websiteButton != null && websiteButton.active && button == 0) {
            if (mouseX >= websiteButton.getX() &&
                    mouseX < websiteButton.getX() + websiteButton.getWidth() &&
                    mouseY >= websiteButton.getY() &&
                    mouseY < websiteButton.getY() + websiteButton.getHeight()) {
                openWebsiteLink();
                return true;
            }
        }

        if (discordThreadButton != null && discordThreadButton.active && button == 0) {
            if (mouseX >= discordThreadButton.getX() &&
                    mouseX < discordThreadButton.getX() + discordThreadButton.getWidth() &&
                    mouseY >= discordThreadButton.getY() &&
                    mouseY < discordThreadButton.getY() + discordThreadButton.getHeight()) {
                openDiscordThread();
                return true;
            }
        }

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        if (scrollBar != null && scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        Identifier currentImageTexture = imageController.getCurrentImageTexture();
        boolean isLoadingImage = imageController.isLoadingImage();
        if (button == 0 && currentImageTexture != null && !isLoadingImage && postInfo != null) {
            int contentStartY = y + UITheme.Dimensions.PADDING;
            int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;

            int containerWidth = getDisplayImageWidth();
            int containerHeight = getDisplayImageHeight();
            int actualImageWidth = getActualImageWidth();
            int actualImageHeight = getActualImageHeight();

            int containerX = x + 1;
            int containerY = currentY;
            int imageX = containerX + (containerWidth - actualImageWidth) / 2;
            int imageY = containerY + (containerHeight - actualImageHeight) / 2;

            if (mouseX >= imageX && mouseX < imageX + actualImageWidth &&
                    mouseY >= imageY && mouseY < imageY + actualImageHeight &&
                    mouseY >= contentStartY && mouseY < y + height) {
                openImageViewer();
                return true;
            }
        }

        if (button == 0 && imageController.hasMultipleImages()) {
            if (prevImageButton != null) {
                boolean isOverPrev = mouseX >= prevImageButton.getX() &&
                        mouseX < prevImageButton.getX() + prevImageButton.getWidth() &&
                        mouseY >= prevImageButton.getY() &&
                        mouseY < prevImageButton.getY() + prevImageButton.getHeight();
                if (isOverPrev) {
                    imageController.previousImage();
                    return true;
                }
            }

            if (nextImageButton != null) {
                boolean isOverNext = mouseX >= nextImageButton.getX() &&
                        mouseX < nextImageButton.getX() + nextImageButton.getWidth() &&
                        mouseY >= nextImageButton.getY() &&
                        mouseY < nextImageButton.getY() + nextImageButton.getHeight();
                if (isOverNext) {
                    imageController.nextImage();
                    return true;
                }
            }
        }

        if (button == 0 && !attachmentHitboxes.isEmpty()) {
            for (AttachmentHitbox hit : attachmentHitboxes) {
                if (hit.contains(mouseX, mouseY) && hit.attachment() != null) {
                    attachmentManager.handleAttachmentClick(hit.attachment());
                    return true;
                }
            }
        }

        return true;
    }

    private void openImageViewer() {
        if (imageController.getCurrentImageTexture() != null && client != null && client.getWindow() != null) {
            imageController.openImageViewer(
                    client.getWindow().getScaledWidth(),
                    client.getWindow().getScaledHeight());
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar != null
                && (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY))) {
            double maxScroll = Math.max(0, contentHeight - height);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (imageController.hasImageViewerOpen()) {
            return imageController.mouseReleased(mouseX, mouseY, button);
        }

        if (scrollBar != null && scrollBar.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (imageController.hasImageViewerOpen()) {
            return true;
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double maxScroll = Math.max(0, contentHeight - height + UITheme.Dimensions.PADDING);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 20));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (imageController.hasImageViewerOpen()) {
            return imageController.keyPressed(keyCode, scanCode, modifiers);
        }

        if (imageController.hasMultipleImages()) {
            if (keyCode == 263) { // Left arrow
                imageController.previousImage();
                return true;
            } else if (keyCode == 262) { // Right arrow
                imageController.nextImage();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}
