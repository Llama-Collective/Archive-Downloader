package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.models.ArchiveAttachment;
import com.andrews.archivedownloader.models.ArchivePostDetail;
import com.andrews.archivedownloader.models.ArchivePostSummary;
import com.andrews.archivedownloader.models.ArchiveRecordSection;
import com.andrews.archivedownloader.network.ArchiveNetworkManager;
import com.andrews.archivedownloader.util.AttachmentManager;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.util.TagUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.platform.UiPlatform;
import com.andrews.archivedownloader.wrapper.render.UiRenderPipeline;
import com.andrews.archivedownloader.wrapper.render.UiTextureId;
import com.andrews.archivedownloader.wrapper.text.UiText;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class PostDetailPanel implements UiRenderable, UiEventListener {
    private static final int MAX_IMAGE_SIZE = 120;
    private static final String DICTIONARY_PATH_PREFIX = "/dictionary/";
    private static final String ARCHIVE_PATH_PREFIX = "/archive/";
    private static final String DISCORD_LINK_PATH_PREFIX = "/discord-link";
    private static final int TOOLTIP_PADDING = 6;
    private static final int TOOLTIP_MAX_WIDTH = 260;

    private int x;
    private int y;
    private int width;
    private int height;

    private ArchivePostSummary postInfo;
    private ArchivePostDetail postDetail;
    private boolean isLoadingDetails = false;

    private final UiMinecraftClient client;
    private final PostImageController imageController;
    private final AttachmentManager attachmentManager;
    private final MarkdownRenderer recordMarkdownRenderer = new MarkdownRenderer();

    private double scrollOffset = 0;
    private double pendingRestoredScrollOffset = -1;
    private int contentHeight = 0;

    private CustomButton prevImageButton;
    private CustomButton nextImageButton;
    private CustomButton headerBackButton;
    private CustomButton headerCloseButton;
    private CustomButton discordThreadButton;
    private CustomButton websiteButton;

    private ScrollBar scrollBar;
    private final List<AttachmentHitbox> attachmentHitboxes = new ArrayList<>();

    private Consumer<String> discordLinkOpener;
    private ServerEntry server = ServerDictionary.getDefaultServer();
    private DictionaryDefinitionPopup dictionaryPopup;
    private String requestedDictionaryId;
    private String hoveredDictionaryTooltip;
    private int tooltipMouseX;
    private int tooltipMouseY;
    private final Map<String, String> cachedPostTooltipsByLink = new HashMap<>();
    private final Set<String> pendingPostTooltipLinks = new HashSet<>();
    private final Deque<ArchivePostSummary> postHistory = new ArrayDeque<>();
    private Runnable onCloseRequested = () -> {
    };

    public PostDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = UiMinecraftClient.getInstance();
        this.imageController = new PostImageController(this.client);
        this.attachmentManager = new AttachmentManager(this.client);
        this.recordMarkdownRenderer.setOnLinkClicked(this::handleMarkdownLinkClicked);
        this.scrollBar = new ScrollBar(x + width - 8, y, height);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - 8, y, height);
    }

    public void setDiscordLinkOpener(Consumer<String> opener) {
        this.discordLinkOpener = opener;
    }

    public void setOnCloseRequested(Runnable onCloseRequested) {
        this.onCloseRequested = onCloseRequested != null ? onCloseRequested : () -> {
        };
    }

    public void setOnLitematicaLoadSuccess(Runnable callback) {
        attachmentManager.setOnLitematicaLoadSuccess(callback);
    }

    public ArchivePostSummary getCurrentPostSummary() {
        return postInfo;
    }

    public double getScrollOffset() {
        return Math.max(0, scrollOffset);
    }

    public void setScrollOffset(double scrollOffset) {
        pendingRestoredScrollOffset = Math.max(0, scrollOffset);
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
        this.attachmentManager.setServer(this.server);
        this.imageController.setServer(this.server);
        this.cachedPostTooltipsByLink.clear();
        this.pendingPostTooltipLinks.clear();
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
        int indicatorWidth = client.font().width(indicator);
        int indicatorX = x + (width - indicatorWidth) / 2;

        int prevBtnX = indicatorX - btnWidth - btnSpacing;
        int nextBtnX = indicatorX + indicatorWidth + btnSpacing;

        if (prevImageButton == null) {
            prevImageButton = new CustomButton(prevBtnX, imageNavY, btnWidth, btnHeight,
                    UiText.of("<"), btn -> imageController.previousImage());
        } else {
            prevImageButton.setX(prevBtnX);
            prevImageButton.setY(imageNavY);
            prevImageButton.setWidth(btnWidth);
        }

        if (nextImageButton == null) {
            nextImageButton = new CustomButton(nextBtnX, imageNavY, btnWidth, btnHeight,
                    UiText.of(">"), btn -> imageController.nextImage());
        } else {
            nextImageButton.setX(nextBtnX);
            nextImageButton.setY(imageNavY);
            nextImageButton.setWidth(btnWidth);
        }
    }

    public void setPost(ArchivePostSummary post) {
        openPost(post);
    }

    public void openPost(ArchivePostSummary post) {
        loadPost(post, true, false);
    }

    private void navigateToPost(ArchivePostSummary post) {
        loadPost(post, false, true);
    }

    private void loadPost(ArchivePostSummary post, boolean resetHistory, boolean pushCurrentToHistory) {
        if (post == null) {
            clear();
            return;
        }

        if (samePost(this.postInfo, post)) {
            return;
        }
        if (resetHistory) {
            postHistory.clear();
        } else if (pushCurrentToHistory && postInfo != null) {
            postHistory.addLast(postInfo);
        }

        clearDownloadState();
        imageController.clear();
        closeDictionaryPopup();
        cachedPostTooltipsByLink.clear();
        pendingPostTooltipLinks.clear();

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.scrollOffset = 0;
        this.pendingRestoredScrollOffset = -1;
        this.attachmentHitboxes.clear();

        ArchiveNetworkManager.getPostDetails(server, post)
                .thenAccept(this::handlePostDetailLoaded)
                .exceptionally(throwable -> {
                    client.execute(() -> {
                        isLoadingDetails = false;
                        System.err.println(
                                "[PostDetailPanel] Failed to load post details: " + throwable.getMessage());
                    });
                    return null;
                });
    }

    private void goBack() {
        if (!postHistory.isEmpty()) {
            ArchivePostSummary previous = postHistory.removeLast();
            loadPost(previous, false, false);
            return;
        }
        requestClose();
    }

    private void requestClose() {
        closeTransientUi();
        onCloseRequested.run();
    }

    private static boolean samePost(ArchivePostSummary left, ArchivePostSummary right) {
        if (left == null || right == null) {
            return false;
        }
        String leftId = left.id() != null ? left.id().trim() : "";
        String rightId = right.id() != null ? right.id().trim() : "";
        if (!leftId.isEmpty() && !rightId.isEmpty()) {
            return leftId.equals(rightId);
        }
        String leftCode = left.code() != null ? left.code().trim() : "";
        String rightCode = right.code() != null ? right.code().trim() : "";
        return !leftCode.isEmpty() && leftCode.equals(rightCode);
    }

    private void handlePostDetailLoaded(ArchivePostDetail detail) {
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


    public void clear() {
        this.postInfo = null;
        this.postDetail = null;
        this.isLoadingDetails = false;
        this.scrollOffset = 0;
        this.pendingRestoredScrollOffset = -1;
        this.hoveredDictionaryTooltip = null;
        this.cachedPostTooltipsByLink.clear();
        this.pendingPostTooltipLinks.clear();
        this.postHistory.clear();
        imageController.clear();
        clearDownloadState();
        closeDictionaryPopup();
    }

    private void clearDownloadState() {
        attachmentManager.clear();
        this.discordThreadButton = null;
        this.websiteButton = null;
    }

    private void closeDictionaryPopup() {
        dictionaryPopup = null;
        requestedDictionaryId = null;
    }

    private boolean hasDiscordThread() {
        String url = getDiscordThreadUrl();
        return url != null && !url.isBlank();
    }

    private boolean hasWebsiteLink() {
        String websiteBase = getWebsiteBase();
        if (websiteBase == null || websiteBase.isBlank() || postInfo == null) {
            return false;
        }
        String slug = buildEntrySlug(postInfo.code(), postInfo.title());
        String id = getCurrentPostId();
        return (slug != null && !slug.isBlank()) || (id != null && !id.isBlank());
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
                    UiText.of("Open Discord Thread"),
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
                    UiText.of("Open On Website"),
                    button -> openWebsiteLink());
        }
        websiteButton.active = hasWebsiteLink();
        websiteButton.setWidth(width);
        websiteButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
        websiteButton.setX(xPos);
        websiteButton.setY(yPos);
    }

    private void ensureHeaderNavButtons(int contentY) {
        int sidePadding = UITheme.Dimensions.PADDING;
        int buttonHeight = UITheme.Dimensions.BUTTON_HEIGHT;
        int closeButtonWidth = 72;
        int gap = 8;
        int closeX = x + width - sidePadding - closeButtonWidth;
        int backX = x + sidePadding;
        int availableBackWidth = Math.max(80, closeX - gap - backX);
        String backLabel = buildBackButtonLabel();
        int desiredBackWidth = Math.max(120, client.font().width(backLabel) + 18);
        int backButtonWidth = Math.min(availableBackWidth, desiredBackWidth);

        if (headerBackButton == null) {
            headerBackButton = new CustomButton(
                backX,
                contentY,
                backButtonWidth,
                buttonHeight,
                UiText.of(backLabel),
                button -> goBack()
            );
        } else {
            headerBackButton.setMessage(UiText.of(backLabel));
            headerBackButton.setX(backX);
            headerBackButton.setY(contentY);
            headerBackButton.setWidth(backButtonWidth);
            headerBackButton.setHeight(buttonHeight);
        }
        headerBackButton.active = true;

        if (headerCloseButton == null) {
            headerCloseButton = new CustomButton(
                closeX,
                contentY,
                closeButtonWidth,
                buttonHeight,
                UiText.of("Close"),
                button -> requestClose()
            );
        } else {
            headerCloseButton.setX(closeX);
            headerCloseButton.setY(contentY);
            headerCloseButton.setWidth(closeButtonWidth);
            headerCloseButton.setHeight(buttonHeight);
        }
        headerCloseButton.active = true;
    }

    private String buildBackButtonLabel() {
        if (postHistory.isEmpty()) {
            return "< Back to archives";
        }
        ArchivePostSummary previous = postHistory.peekLast();
        if (previous == null) {
            return "< Back";
        }
        String code = previous.code() != null ? previous.code().trim() : "";
        if (!code.isEmpty()) {
            return "< Back to " + code;
        }
        String title = previous.title() != null ? previous.title().trim() : "";
        if (title.isEmpty()) {
            return "< Back";
        }
        return "< Back to " + title;
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
            UiPlatform.openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open Discord thread: " + e.getMessage());
        }
    }

    private void openWebsiteLink() {
        String websiteBase = getWebsiteBase();
        if (websiteBase == null || websiteBase.isBlank() || postInfo == null) {
            return;
        }
        String normalizedBase = normalizeWebsiteBase(websiteBase);
        String slug = buildEntrySlug(postInfo.code(), postInfo.title());
        String url = normalizedBase + "/archives/" + slug + "/";
        
        try {
            UiPlatform.openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open website: " + e.getMessage());
        }
    }

    private void handleMarkdownLinkClicked(String linkUrl) {
        String target = linkUrl != null ? linkUrl.trim() : "";
        if (target.isEmpty()) {
            return;
        }

        String dictionaryId = extractDictionaryIdFromLink(target);
        if (!dictionaryId.isEmpty()) {
            openDictionaryPopup(dictionaryId);
            return;
        }

        if (openPostFromLink(target)) {
            return;
        }

        if (isInternalDiscordLink(target)) {
            if (discordLinkOpener != null) {
                discordLinkOpener.accept(target);
                return;
            }
            String fallbackUrl = extractDiscordMessageUrlFromInternalLink(target);
            if (!fallbackUrl.isEmpty()) {
                openExternalLink(fallbackUrl);
            }
            return;
        }

        if (target.startsWith("/")) {
            String websiteUrl = toAbsoluteWebsiteUrl(target);
            if (websiteUrl.isEmpty()) {
                return;
            }
            target = websiteUrl;
        }

        if (isDiscordUrl(target) && discordLinkOpener != null) {
            discordLinkOpener.accept(target);
            return;
        }

        openExternalLink(target);
    }

    private boolean openPostFromLink(String linkUrl) {
        String postId = extractPostIdFromLink(linkUrl);
        if (postId.isEmpty()) {
            return false;
        }

        ArchiveNetworkManager.findPostSummary(server, postId, "")
            .thenAccept(summary -> client.execute(() -> {
                if (summary == null) {
                    return;
                }
                closeDictionaryPopup();
                navigateToPost(summary);
            }));
        return true;
    }

    private void openDictionaryPopup(String dictionaryId) {
        String trimmed = dictionaryId != null ? dictionaryId.trim() : "";
        if (trimmed.isEmpty()) {
            return;
        }
        String requestKey = trimmed.toLowerCase(Locale.ROOT);
        requestedDictionaryId = requestKey;
        if (dictionaryPopup == null) {
            dictionaryPopup = new DictionaryDefinitionPopup(this::closeDictionaryPopup, this::handleMarkdownLinkClicked);
        }
        dictionaryPopup.setWebsiteBase(getWebsiteBase());
        dictionaryPopup.setDiscordThreadOpener(this::openDiscordLink);
        dictionaryPopup.setLoading(trimmed);

        ArchiveNetworkManager.getDictionaryEntry(server, trimmed)
            .thenAccept(entry -> client.execute(() -> {
                if (dictionaryPopup == null || !requestKey.equals(requestedDictionaryId)) {
                    return;
                }
                dictionaryPopup.setEntry(entry);
            }))
            .exceptionally(throwable -> {
                client.execute(() -> {
                    if (dictionaryPopup == null || !requestKey.equals(requestedDictionaryId)) {
                        return;
                    }
                    String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "Unknown error";
                    dictionaryPopup.setError(trimmed, message);
                });
                return null;
            });
    }

    private String toAbsoluteWebsiteUrl(String path) {
        String websiteBase = normalizeWebsiteBase(getWebsiteBase());
        if (websiteBase.isEmpty()) {
            return "";
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return websiteBase + normalizedPath;
    }

    private void openExternalLink(String url) {
        try {
            UiPlatform.openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open link: " + e.getMessage());
        }
    }

    private void openDiscordLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (discordLinkOpener != null) {
            discordLinkOpener.accept(url);
            return;
        }
        openExternalLink(url);
    }

    private static boolean isDiscordUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("discord://")
            || lower.startsWith("https://discord.com/")
            || lower.startsWith("http://discord.com/")
            || lower.startsWith("https://discordapp.com/")
            || lower.startsWith("http://discordapp.com/");
    }

    private static boolean isInternalDiscordLink(String link) {
        String path = extractPath(link);
        return !path.isEmpty() && path.startsWith(DISCORD_LINK_PATH_PREFIX);
    }

    private static String extractDictionaryIdFromLink(String link) {
        String path = extractPath(link);
        if (path.isEmpty() || !path.startsWith(DICTIONARY_PATH_PREFIX)) {
            return "";
        }
        String segment = path.substring(DICTIONARY_PATH_PREFIX.length());
        if (segment.isEmpty()) {
            return "";
        }
        int slash = segment.indexOf('/');
        if (slash >= 0) {
            segment = segment.substring(0, slash);
        }
        String decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8).trim();
        if (decoded.isEmpty()) {
            return "";
        }
        return decoded;
    }

    private static String extractPostIdFromLink(String link) {
        String path = extractPath(link);
        if (path.isEmpty() || !path.startsWith(ARCHIVE_PATH_PREFIX)) {
            return "";
        }
        String segment = path.substring(ARCHIVE_PATH_PREFIX.length());
        if (segment.isEmpty()) {
            return "";
        }
        int slash = segment.indexOf('/');
        if (slash >= 0) {
            segment = segment.substring(0, slash);
        }
        return URLDecoder.decode(segment, StandardCharsets.UTF_8).trim();
    }

    private static String extractPath(String link) {
        String trimmed = link != null ? link.trim() : "";
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("/")) {
            int query = trimmed.indexOf('?');
            int hash = trimmed.indexOf('#');
            int cut = -1;
            if (query >= 0 && hash >= 0) {
                cut = Math.min(query, hash);
            } else if (query >= 0) {
                cut = query;
            } else if (hash >= 0) {
                cut = hash;
            }
            return cut >= 0 ? trimmed.substring(0, cut) : trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            return path != null ? path : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractDiscordMessageUrlFromInternalLink(String link) {
        String path = extractPath(link);
        if (path.isEmpty() || !path.startsWith(DISCORD_LINK_PATH_PREFIX)) {
            return "";
        }
        String query = extractQuery(link);
        if (query.isEmpty()) {
            return "";
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).trim();
            if (!"url".equalsIgnoreCase(key)) {
                continue;
            }
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8).trim();
        }
        return "";
    }

    private static String extractQuery(String link) {
        String trimmed = link != null ? link.trim() : "";
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("/")) {
            int queryStart = trimmed.indexOf('?');
            if (queryStart < 0) {
                return "";
            }
            String queryPart = trimmed.substring(queryStart + 1);
            int hash = queryPart.indexOf('#');
            return hash >= 0 ? queryPart.substring(0, hash) : queryPart;
        }

        try {
            URI uri = URI.create(trimmed);
            String query = uri.getRawQuery();
            return query != null ? query : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getWebsiteBase() {
        return server != null ? server.websiteBase() : null;
    }

    private static String normalizeWebsiteBase(String base) {
        String normalized = base != null ? base.trim() : "";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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

    private static boolean isDictionaryLink(String linkUrl) {
        return !extractDictionaryIdFromLink(linkUrl).isEmpty();
    }

    private static boolean isPostLink(String linkUrl) {
        return !extractPostIdFromLink(linkUrl).isEmpty();
    }

    private static String stripDictionaryTooltipPrefix(String tooltip) {
        if (tooltip == null) {
            return null;
        }
        String trimmed = tooltip.trim();
        if (trimmed.regionMatches(true, 0, "Definition:", 0, "Definition:".length())) {
            trimmed = trimmed.substring("Definition:".length()).trim();
        }
        return trimmed;
    }

    private void requestPostTooltipLookup(String linkUrl) {
        String target = linkUrl != null ? linkUrl.trim() : "";
        if (target.isEmpty()
            || cachedPostTooltipsByLink.containsKey(target)
            || pendingPostTooltipLinks.contains(target)) {
            return;
        }

        String postId = extractPostIdFromLink(target);
        if (postId.isEmpty()) {
            return;
        }

        pendingPostTooltipLinks.add(target);
        ArchiveNetworkManager.findPostSummary(server, postId, "")
            .thenAccept(summary -> client.execute(() -> {
                pendingPostTooltipLinks.remove(target);
                if (summary == null) {
                    cachedPostTooltipsByLink.putIfAbsent(target, "");
                    return;
                }
                String title = summary.title() != null ? summary.title().trim() : "";
                cachedPostTooltipsByLink.put(target, title);
            }))
            .exceptionally(throwable -> {
                client.execute(() -> pendingPostTooltipLinks.remove(target));
                return null;
            });
    }

    private void renderDictionaryTooltip(UiRenderContext renderContext, String text, int mouseX, int mouseY) {
        if (text == null || text.isBlank() || dictionaryPopup != null) {
            return;
        }

        UiFont font = client.uiFont();
        int maxTextWidth = Math.min(TOOLTIP_MAX_WIDTH, Math.max(100, width - 40));
        List<String> lines = wrapTooltipText(font, text, maxTextWidth);
        if (lines.isEmpty()) {
            return;
        }

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = textWidth + TOOLTIP_PADDING * 2;
        int lineHeight = Math.max(font.lineHeight(), 1) + 1;
        int boxHeight = lines.size() * lineHeight + TOOLTIP_PADDING * 2;

        int screenWidth = client.guiScaledWidth();
        int screenHeight = client.guiScaledHeight();
        int tooltipX = mouseX + 10;
        int tooltipY = mouseY + 12;
        if (tooltipX + boxWidth > screenWidth - 4) {
            tooltipX = Math.max(4, mouseX - boxWidth - 10);
        }
        if (tooltipY + boxHeight > screenHeight - 4) {
            tooltipY = Math.max(4, mouseY - boxHeight - 8);
        }

        RenderUtil.fillRect(renderContext, tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + boxHeight, 0xF0181820);
        RenderUtil.fillRect(renderContext, tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + 1, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, tooltipX, tooltipY + boxHeight - 1, tooltipX + boxWidth, tooltipY + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, tooltipX, tooltipY, tooltipX + 1, tooltipY + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, tooltipX + boxWidth - 1, tooltipY, tooltipX + boxWidth, tooltipY + boxHeight, UITheme.Colors.BUTTON_BORDER);

        int textY = tooltipY + TOOLTIP_PADDING;
        for (String line : lines) {
            RenderUtil.drawString(renderContext, font, line, tooltipX + TOOLTIP_PADDING, textY, UITheme.Colors.TEXT_PRIMARY);
            textY += lineHeight;
        }
    }

    private static List<String> wrapTooltipText(UiFont font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }

    @Override
    public void render(UiRenderContext renderContext, int mouseX, int mouseY, float delta) {
        var context = renderContext.graphics();
        int renderMouseX = mouseX;
        int renderMouseY = mouseY;
        hoveredDictionaryTooltip = null;

        RenderUtil.fillRect(renderContext, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        
        if (postInfo == null) {
            String text = "Select a schematic to view details";
            int textWidth = client.font().width(text);
            RenderUtil.drawString(renderContext, client.uiFont(), text,
                    x + (width - textWidth) / 2, y + height / 2 - 4, UITheme.Colors.TEXT_SUBTITLE);
            return;
        }

        int contentStartY = y + UITheme.Dimensions.PADDING;
        int currentY = contentStartY - (int) scrollOffset;
        contentHeight = 0;
        attachmentHitboxes.clear();

        ensureHeaderNavButtons(currentY);
        if (headerBackButton != null) {
            headerBackButton.render(context, renderMouseX, renderMouseY, delta);
        }
        if (headerCloseButton != null) {
            headerCloseButton.render(context, renderMouseX, renderMouseY, delta);
        }
        currentY += UITheme.Dimensions.BUTTON_HEIGHT + 10;
        contentHeight += UITheme.Dimensions.BUTTON_HEIGHT + 10;

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int actualImageWidth = getActualImageWidth();
        int actualImageHeight = getActualImageHeight();

        int containerX = x + 1;
        int containerY = currentY;

        int imageX = containerX + (containerWidth - actualImageWidth) / 2;
        int imageY = containerY + (containerHeight - actualImageHeight) / 2;

        UiTextureId currentImageTexture = imageController.getCurrentImageTexture();
        boolean isLoadingImage = imageController.isLoadingImage();

        if (isLoadingImage) {
            RenderUtil.fillRect(renderContext, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.CONTAINER_BG);
            LoadingSpinner spinner = imageController.getLoadingSpinner();
            spinner.setPosition(
                    containerX + containerWidth / 2 - spinner.getWidth() / 2,
                    containerY + containerHeight / 2 - spinner.getHeight() / 2);
            spinner.render(renderContext, mouseX, mouseY, delta);
        } else if (currentImageTexture != null) {
            RenderUtil.fillRect(renderContext, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.PANEL_BG);
            RenderUtil.blit(
                    renderContext,
                    UiRenderPipeline.GUI_TEXTURED,
                    currentImageTexture,
                    imageX, imageY,
                    0, 0,
                    actualImageWidth, actualImageHeight,
                    actualImageWidth, actualImageHeight);
        } else {
            RenderUtil.fillRect(renderContext, containerX, containerY, containerX + containerWidth, containerY + containerHeight,
                    UITheme.Colors.CONTAINER_BG);
            String noImg = isCompactMode() ? "..." : "No image";
            int tw = client.font().width(noImg);
            RenderUtil.drawString(renderContext, client.uiFont(), noImg,
                    containerX + (containerWidth - tw) / 2, containerY + containerHeight / 2 - 4,
                    UITheme.Colors.TEXT_SUBTITLE);
        }

        currentY += containerHeight + UITheme.Dimensions.PADDING;
        contentHeight += containerHeight + UITheme.Dimensions.PADDING;

        String imageDescription = imageController.getCurrentImageDescription();
        if (imageDescription != null && !imageDescription.isEmpty()) {
            int descWidth = width - UITheme.Dimensions.PADDING * 2;
            RenderUtil.drawWrappedText(renderContext, client.uiFont(), imageDescription, x + UITheme.Dimensions.PADDING, currentY, descWidth,
                    UITheme.Colors.TEXT_SUBTITLE);
            int descHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), imageDescription, descWidth);
            currentY += descHeight + 6;
            contentHeight += descHeight + 6;
        }

        if (imageController.hasMultipleImages()) {
            String indicator = String.format("%d / %d", imageController.getCurrentImageIndex() + 1,
                    imageController.getImageCount());
            int indicatorWidth = client.font().width(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;
            int btnY = currentY;

            updateCarouselButtons(btnY);

            if (prevImageButton != null) {
                prevImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            RenderUtil.drawString(renderContext, client.uiFont(), indicator, indicatorX, btnY + 4, UITheme.Colors.TEXT_SUBTITLE);

            if (nextImageButton != null) {
                nextImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            currentY += 16 + UITheme.Dimensions.PADDING;
            contentHeight += 16 + UITheme.Dimensions.PADDING;
        }

        String title = postInfo.title() != null ? postInfo.title() : "Untitled";
        RenderUtil.drawWrappedText(renderContext, client.uiFont(), title, x + UITheme.Dimensions.PADDING, currentY,
                width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_PRIMARY);
        int titleHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), title, width - UITheme.Dimensions.PADDING * 2);
        currentY += titleHeight + 8;
        contentHeight += titleHeight + 8;

        String metaLine = buildMetaLine();
        RenderUtil.drawString(renderContext, client.uiFont(), metaLine, x + UITheme.Dimensions.PADDING, currentY,
                UITheme.Colors.TEXT_SUBTITLE);
        currentY += 16;
        contentHeight += 16;

        if (postDetail != null && postDetail.authors() != null && !postDetail.authors().isEmpty()) {
            String authorLine = "By: " + String.join(", ", postDetail.authors());
            RenderUtil.drawWrappedText(renderContext, client.uiFont(), authorLine, x + UITheme.Dimensions.PADDING, currentY,
                    width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_SUBTITLE);
            int authorHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), authorLine, width - UITheme.Dimensions.PADDING * 2);
            currentY += authorHeight + 4;
            contentHeight += authorHeight + 4;
        }

        String[] tags = postInfo.tags();
        if (tags != null && tags.length > 0) {
            RenderUtil.drawString(renderContext, client.uiFont(), "Tags:", x + UITheme.Dimensions.PADDING, currentY,
                    UITheme.Colors.TEXT_SUBTITLE);
            currentY += 12;
            contentHeight += 12;

            int tagX = x + UITheme.Dimensions.PADDING;
            for (String tag : TagUtil.orderTags(tags, server)) {
                String displayTag = TagUtil.formatTagLabel(tag, server);
                int tagWidth = client.font().width(displayTag) + 8;
                if (tagX + tagWidth > x + width - UITheme.Dimensions.PADDING) {
                    tagX = x + UITheme.Dimensions.PADDING;
                    currentY += 14;
                    contentHeight += 14;
                }
                RenderUtil.fillRect(renderContext, tagX, currentY, tagX + tagWidth, currentY + 12, TagUtil.getTagColor(tag, server));
                RenderUtil.drawString(renderContext, client.uiFont(), displayTag, tagX + 4, currentY + 2, UITheme.Colors.TEXT_TAG);
                tagX += tagWidth + 4;
            }
            currentY += 16;
            contentHeight += 16;
        }

        String detailMarkdown = postDetail != null ? postDetail.recordMarkdown() : "";
        if (postDetail != null && detailMarkdown != null && !detailMarkdown.isBlank()) {
            currentY += 8;
            contentHeight += 8;

            int markdownX = x + UITheme.Dimensions.PADDING;
            int markdownWidth = Math.max(1, width - UITheme.Dimensions.PADDING * 2);
            recordMarkdownRenderer.setMarkdown(detailMarkdown);
            recordMarkdownRenderer.setVerticalOffset(0);
            recordMarkdownRenderer.setBounds(markdownX, currentY, markdownWidth, 1);
            int markdownHeight = Math.max(1, recordMarkdownRenderer.getRequiredHeight(client.uiFont()));
            recordMarkdownRenderer.setBounds(markdownX, currentY, markdownWidth, markdownHeight);
            recordMarkdownRenderer.render(renderContext, client.uiFont(), mouseX, mouseY);

            String hoveredLink = recordMarkdownRenderer.getHoveredLink();
            String rawHoveredTooltip = recordMarkdownRenderer.getHoveredLinkTooltip();
            String dictionaryTooltip = stripDictionaryTooltipPrefix(rawHoveredTooltip);
            if (isDictionaryLink(hoveredLink) && dictionaryTooltip != null && !dictionaryTooltip.isBlank()) {
                hoveredDictionaryTooltip = dictionaryTooltip;
                tooltipMouseX = mouseX;
                tooltipMouseY = mouseY;
            } else if (isPostLink(hoveredLink)) {
                String postTooltip = rawHoveredTooltip != null ? rawHoveredTooltip.trim() : "";
                if (postTooltip.isEmpty()) {
                    postTooltip = cachedPostTooltipsByLink.getOrDefault(hoveredLink, "");
                }
                if (postTooltip.isEmpty()) {
                    requestPostTooltipLookup(hoveredLink);
                } else {
                    hoveredDictionaryTooltip = postTooltip;
                    tooltipMouseX = mouseX;
                    tooltipMouseY = mouseY;
                }
            } else if (hoveredLink != null && !hoveredLink.isBlank()) {
                String linkTooltip = rawHoveredTooltip != null ? rawHoveredTooltip.trim() : "";
                if (linkTooltip.isEmpty()) {
                    linkTooltip = hoveredLink.trim();
                }
                if (!linkTooltip.isEmpty()) {
                    hoveredDictionaryTooltip = linkTooltip;
                    tooltipMouseX = mouseX;
                    tooltipMouseY = mouseY;
                }
            }

            currentY += markdownHeight + 8;
            contentHeight += markdownHeight + 8;
        } else if (postDetail != null && postDetail.recordSections() != null && !postDetail.recordSections().isEmpty()) {
            currentY += 8;
            contentHeight += 8;
            for (ArchiveRecordSection section : postDetail.recordSections()) {
                if (section == null)
                    continue;
                String header = section.title() != null ? section.title() : "Details";
                RenderUtil.drawString(renderContext, client.uiFont(), header + ":", x + UITheme.Dimensions.PADDING, currentY,
                        UITheme.Colors.TEXT_SUBTITLE);
                currentY += 12;
                contentHeight += 12;

                List<String> lines = section.lines();
                if (lines != null) {
                    for (String line : lines) {
                        if (line == null || line.isEmpty())
                            continue;
                        RenderUtil.drawWrappedText(renderContext, client.uiFont(), line, x + UITheme.Dimensions.PADDING, currentY,
                                width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_TAG);
                        int lineHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), line, width - UITheme.Dimensions.PADDING * 2);
                        currentY += lineHeight;
                        contentHeight += lineHeight;
                    }
                }
                currentY += 6;
                contentHeight += 6;
            }
        } else if (isLoadingDetails) {
            currentY += 8;
            RenderUtil.drawString(renderContext, client.uiFont(), "Loading details...", x + UITheme.Dimensions.PADDING, currentY,
                    UITheme.Colors.TEXT_SUBTITLE);
            contentHeight += 20;
        }

        if (attachmentManager.hasAttachments()) {
            RenderUtil.drawString(renderContext, client.uiFont(), "Attachments:", x + UITheme.Dimensions.PADDING, currentY,
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
                int nameHeight = (int) (client.font().lineHeight * 0.85f) + 6;
                int metaHeight = (meta != null && !meta.isEmpty()) ? client.font().lineHeight + 2 : 0;
                int descWidth = rowWidth - 12;
                int descHeight = 0;
                if (attachment.description() != null && !attachment.description().isEmpty()) {
                    descHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), attachment.description(), descWidth) + 2;
                }
                int rowHeight = nameHeight + metaHeight + descHeight + 4;

                boolean isHover = renderMouseX >= rowX && renderMouseX <= rowX + rowWidth &&
                        renderMouseY >= rowY && renderMouseY <= rowY + rowHeight;
                int bgColor = isHover ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
                RenderUtil.fillRect(renderContext, rowX, rowY, rowX + rowWidth, rowY + rowHeight, bgColor);

                int cursorY = rowY + 3;
                RenderUtil.drawScaledString(renderContext, nameText, rowX + 6, cursorY, UITheme.Colors.TEXT_PRIMARY, 0.85f,
                        rowWidth - 12);
                cursorY += nameHeight;

                if (metaHeight > 0) {
                    RenderUtil.drawString(renderContext, client.uiFont(), meta, rowX + 6, cursorY - 2, UITheme.Colors.TEXT_SUBTITLE);
                    cursorY += metaHeight;
                }

                if (descHeight > 0) {
                    RenderUtil.drawWrappedText(renderContext, client.uiFont(), attachment.description(), rowX + 6, cursorY, descWidth,
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
        double maxContentScroll = getMaxScrollOffset();
        if (pendingRestoredScrollOffset >= 0) {
            scrollOffset = pendingRestoredScrollOffset;
            pendingRestoredScrollOffset = -1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxContentScroll));

        if (contentHeight > height) {
            scrollBar.setScrollData(contentHeight, height);
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, contentHeight - height));

            if (client.windowHandle() != 0L) {
                long windowHandle = client.windowHandle();
                if (scrollBar.updateAndRender(renderContext, mouseX, mouseY, delta, windowHandle)) {
                    double maxScroll = getMaxScrollOffset();
                    scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
                }
            } else {
                scrollBar.render(renderContext, mouseX, mouseY, delta);
            }
        }

        if (hoveredDictionaryTooltip != null && !hoveredDictionaryTooltip.isBlank()) {
            renderDictionaryTooltip(renderContext, hoveredDictionaryTooltip, tooltipMouseX, tooltipMouseY);
        }

        if (dictionaryPopup != null) {
            dictionaryPopup.render(renderContext, mouseX, mouseY, delta);
        }
    }

    public boolean hasImageViewerOpen() {
        return imageController.hasImageViewerOpen();
    }

    public boolean hasDictionaryPopupOpen() {
        return dictionaryPopup != null;
    }

    public void closeTransientUi() {
        closeDictionaryPopup();
    }

    public void renderImageViewer(UiRenderContext context, int mouseX, int mouseY, float delta) {
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
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (dictionaryPopup != null) {
            return dictionaryPopup.mouseClicked(click, doubled);
        }

        if (imageController.hasImageViewerOpen()) {
            return imageController.mouseClicked(click, doubled);
        }

        if (button == 0 && headerBackButton != null && headerBackButton.active) {
            if (mouseX >= headerBackButton.getX() &&
                mouseX < headerBackButton.getX() + headerBackButton.getWidth() &&
                mouseY >= headerBackButton.getY() &&
                mouseY < headerBackButton.getY() + headerBackButton.getHeight()) {
                goBack();
                return true;
            }
        }

        if (button == 0 && headerCloseButton != null && headerCloseButton.active) {
            if (mouseX >= headerCloseButton.getX() &&
                mouseX < headerCloseButton.getX() + headerCloseButton.getWidth() &&
                mouseY >= headerCloseButton.getY() &&
                mouseY < headerCloseButton.getY() + headerCloseButton.getHeight()) {
                requestClose();
                return true;
            }
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

        if (scrollBar != null && scrollBar.mouseClicked(click, doubled)) {
            return true;
        }

        if (postDetail != null) {
            String markdown = postDetail.recordMarkdown();
            if (markdown != null && !markdown.isBlank() && recordMarkdownRenderer.mouseClicked(click, doubled)) {
                return true;
            }
        }

        UiTextureId currentImageTexture = imageController.getCurrentImageTexture();
        boolean isLoadingImage = imageController.isLoadingImage();
        if (button == 0 && currentImageTexture != null && !isLoadingImage && postInfo != null) {
            int contentStartY = y + UITheme.Dimensions.PADDING;
            int currentY = contentStartY - (int) scrollOffset;

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
                    attachmentManager.handleAttachmentClick(hit.attachment(), click.shiftDown());
                    return true;
                }
            }
        }

        return true;
    }

    private void openImageViewer() {
        if (imageController.getCurrentImageTexture() != null && client.windowHandle() != 0L) {
            imageController.openImageViewer(
                    client.guiScaledWidth(),
                    client.guiScaledHeight());
        }
    }

    private double getMaxScrollOffset() {
        return Math.max(0, contentHeight - height);
    }

    @Override
    public boolean mouseDragged(UiMouseEvent click, double offsetX, double offsetY) {
        if (dictionaryPopup != null) {
            return dictionaryPopup.mouseDragged(click, offsetX, offsetY);
        }
        if (scrollBar != null
                && (scrollBar.isDragging() || scrollBar.mouseDragged(click, offsetX, offsetY))) {
            double maxScroll = getMaxScrollOffset();
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent click) {
        if (dictionaryPopup != null) {
            return dictionaryPopup.mouseReleased(click);
        }
        if (imageController.hasImageViewerOpen()) {
            return imageController.mouseReleased(click);
        }

        if (scrollBar != null && scrollBar.mouseReleased(click)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dictionaryPopup != null) {
            return dictionaryPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (imageController.hasImageViewerOpen()) {
            return true;
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double maxScroll = getMaxScrollOffset();
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 20));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dictionaryPopup != null) {
            if (keyCode == 256) { // Esc
                closeDictionaryPopup();
                return true;
            }
            return true;
        }
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
