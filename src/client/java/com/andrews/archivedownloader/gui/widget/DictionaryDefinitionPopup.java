package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.models.ArchiveDictionaryEntry;
import com.andrews.archivedownloader.models.ArchiveDictionaryReferencedPost;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.platform.UiPlatform;
import com.andrews.archivedownloader.wrapper.text.UiText;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;
import java.util.function.Consumer;

public class DictionaryDefinitionPopup implements UiRenderable, UiEventListener {
    private static final int MAX_POPUP_WIDTH = 620;
    private static final int MIN_POPUP_WIDTH = 360;
    private static final int MAX_POPUP_HEIGHT = 480;
    private static final int MIN_POPUP_HEIGHT = 260;
    private static final int OUTER_PADDING = 12;
    private static final String DICTIONARY_PATH_PREFIX = "/dictionary/";
    private static final String ARCHIVE_PATH_PREFIX = "/archive/";
    private static final int TOOLTIP_PADDING = 6;
    private static final int TOOLTIP_MAX_WIDTH = 260;
    private static final int REFERENCED_BY_SECTION_SPACING = 10;
    private static final int REFERENCED_BY_HEADER_HEIGHT = 12;
    private static final int REFERENCED_BY_CARD_HEIGHT = 38;
    private static final int REFERENCED_BY_CARD_GAP = 6;
    private static final int REFERENCED_BY_CARD_PADDING_X = 8;
    private static final int REFERENCED_BY_CHIP_HEIGHT = 12;
    private static final int REFERENCED_BY_CHIP_PADDING_X = 4;
    private static final int REFERENCED_BY_CARD_BG = 0xFF2B2B2B;
    private static final int REFERENCED_BY_CARD_BG_HOVER = 0xFF343434;
    private static final int REFERENCED_BY_CARD_BORDER = 0xFF4E4E4E;
    private static final int REFERENCED_BY_LINK_COLOR = 0xFF66B3FF;
    private static final int REFERENCED_BY_LINK_HOVER_COLOR = 0xFF99C8FF;
    private static final int REFERENCED_BY_CHIP_BG = 0xFF303030;
    private static final int REFERENCED_BY_CHIP_BORDER = 0xFF4E4E4E;
    private static final int ACTION_SECTION_PADDING_TOP = 8;
    private static final int ACTION_BUTTON_GAP = 6;
    private static final int ACTION_SECTION_SPACING = 8;

    private final Runnable onClose;
    private final Consumer<String> onLinkClicked;
    private Consumer<String> onDiscordThreadClicked;
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    private CustomButton closeButton;
    private CustomButton websiteButton;
    private CustomButton discordStatusButton;
    private ScrollBar scrollBar;
    private int scrollBarX = Integer.MIN_VALUE;
    private int scrollBarY = Integer.MIN_VALUE;
    private int scrollBarHeight = Integer.MIN_VALUE;

    private int popupX;
    private int popupY;
    private int popupWidth;
    private int popupHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int totalContentHeight = 0;
    private double scrollOffset = 0;
    private boolean wasEscapePressed = false;

    private String title = "Dictionary";
    private String subtitle = "";
    private String markdownBody = "Loading term...";
    private ArchiveDictionaryEntry currentEntry;
    private String websiteBase = "";
    private List<ArchiveDictionaryReferencedPost> referencedByPosts = List.of();
    private final List<ReferencedByHitbox> referencedByHitboxes = new ArrayList<>();
    private String hoveredDictionaryTooltip = null;
    private int tooltipMouseX = 0;
    private int tooltipMouseY = 0;

    public DictionaryDefinitionPopup(Runnable onClose, Consumer<String> onLinkClicked) {
        this.onClose = onClose != null ? onClose : () -> {
        };
        this.onLinkClicked = onLinkClicked != null ? onLinkClicked : link -> {
        };
        markdownRenderer.setOnLinkClicked(this.onLinkClicked);
        initCloseButton();
    }

    public void setLoading(String term) {
        String safe = term != null && !term.isBlank() ? term : "Dictionary";
        this.title = safe;
        this.subtitle = "Loading definition...";
        this.markdownBody = "Loading term...";
        this.currentEntry = null;
        this.referencedByPosts = List.of();
        this.referencedByHitboxes.clear();
        this.scrollOffset = 0;
    }

    public void setError(String term, String message) {
        String safe = term != null && !term.isBlank() ? term : "Dictionary";
        this.title = safe;
        this.subtitle = "Failed to load definition";
        String detail = message != null && !message.isBlank() ? message : "Try again later.";
        this.markdownBody = "Could not load this dictionary entry.\n\n" + detail;
        this.currentEntry = null;
        this.referencedByPosts = List.of();
        this.referencedByHitboxes.clear();
        this.scrollOffset = 0;
    }

    public void setWebsiteBase(String websiteBase) {
        this.websiteBase = normalizeWebsiteBase(websiteBase);
    }

    public void setDiscordThreadOpener(Consumer<String> opener) {
        this.onDiscordThreadClicked = opener;
    }

    public void setEntry(ArchiveDictionaryEntry entry) {
        if (entry == null) {
            setError("Dictionary", "Entry is unavailable.");
            return;
        }
        String primary = entry.terms() != null && !entry.terms().isEmpty()
            ? entry.terms().get(0)
            : entry.id();
        title = primary != null && !primary.isBlank() ? primary : "Dictionary";
        subtitle = entry.updatedAt() > 0
            ? "Updated " + DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.updatedAt()))
            : "";
        currentEntry = entry;
        markdownBody = buildMarkdownBody(entry);
        referencedByPosts = entry.referencedByPosts();
        referencedByHitboxes.clear();
        scrollOffset = 0;
    }

    private String buildMarkdownBody(ArchiveDictionaryEntry entry) {
        StringBuilder markdown = new StringBuilder();
        if (entry.definitionMarkdown() != null && !entry.definitionMarkdown().isBlank()) {
            markdown.append(entry.definitionMarkdown().trim());
        } else {
            markdown.append("_No definition available._");
        }
        return markdown.toString().trim();
    }

    private static String sanitizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Reference";
        }
        return label
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)");
    }

    private void initCloseButton() {
        if (closeButton == null) {
            closeButton = new CustomButton(0, 0, 72, UITheme.Dimensions.BUTTON_HEIGHT, UiText.of("Close"), button -> onClose.run());
        }
    }

    @Override
    public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        UiMinecraftClient client = UiMinecraftClient.getInstance();
        long windowHandle = client.windowHandle();
        hoveredDictionaryTooltip = null;
        if (windowHandle != 0L) {
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
            if (escapePressed && !wasEscapePressed) {
                onClose.run();
            }
            wasEscapePressed = escapePressed;
        }

        updateLayout(client.guiScaledWidth(), client.guiScaledHeight());
        initCloseButton();
        ensureActionButtons();

        RenderUtil.fillRect(context, 0, 0, client.guiScaledWidth(), client.guiScaledHeight(), UITheme.Colors.OVERLAY_BG);
        RenderUtil.fillRect(context, popupX, popupY, popupX + popupWidth, popupY + popupHeight, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, popupX, popupY, popupX + popupWidth, popupY + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, popupX, popupY + popupHeight - UITheme.Dimensions.BORDER_WIDTH, popupX + popupWidth, popupY + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, popupX, popupY, popupX + UITheme.Dimensions.BORDER_WIDTH, popupY + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, popupX + popupWidth - UITheme.Dimensions.BORDER_WIDTH, popupY, popupX + popupWidth, popupY + popupHeight, UITheme.Colors.BUTTON_BORDER);
        renderContentArea(context, mouseX, mouseY, delta, windowHandle);
        if (hoveredDictionaryTooltip != null && !hoveredDictionaryTooltip.isBlank()) {
            renderDictionaryTooltip(context, hoveredDictionaryTooltip, tooltipMouseX, tooltipMouseY);
        }
    }

    private void renderContentArea(UiRenderContext context, int mouseX, int mouseY, float delta, long windowHandle) {
        UiMinecraftClient client = UiMinecraftClient.getInstance();
        UiFont font = client.uiFont();
        int rawWidth = contentWidth;
        int rawHeight = contentHeight;
        int scrollbarSpace = UITheme.Dimensions.SCROLLBAR_WIDTH + 4;
        int headerHeight = getHeaderSectionHeight();
        int actionHeight = getActionSectionHeight();

        markdownRenderer.setMarkdown(markdownBody);
        markdownRenderer.setVerticalOffset(0);
        int textWidth = rawWidth;
        int markdownHeight = measureMarkdownHeight(font, textWidth);
        int referencedByHeight = getReferencedBySectionHeight();
        int measuredHeight = headerHeight + markdownHeight + referencedByHeight + actionHeight;
        boolean needsScroll = measuredHeight > rawHeight;
        textWidth = needsScroll ? Math.max(60, rawWidth - scrollbarSpace) : rawWidth;
        if (textWidth != rawWidth) {
            markdownHeight = measureMarkdownHeight(font, textWidth);
            measuredHeight = headerHeight + markdownHeight + referencedByHeight + actionHeight;
            needsScroll = measuredHeight > rawHeight;
            if (!needsScroll) {
                textWidth = rawWidth;
                markdownHeight = measureMarkdownHeight(font, textWidth);
                measuredHeight = headerHeight + markdownHeight + referencedByHeight + actionHeight;
            }
        }

        totalContentHeight = measuredHeight;
        double maxScroll = Math.max(0, totalContentHeight - rawHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int scrollPixels = (int) Math.round(scrollOffset);
        int headerY = contentY - scrollPixels;
        closeButton.setX(contentX + textWidth - closeButton.getWidth());
        closeButton.setY(headerY);
        if (headerY + closeButton.getHeight() >= contentY && headerY <= contentY + rawHeight) {
            closeButton.render(context.graphics(), mouseX, mouseY, delta);
        }
        if (headerY + 12 >= contentY && headerY <= contentY + rawHeight) {
            RenderUtil.drawString(context, font, title, contentX, headerY + 1, UITheme.Colors.TEXT_PRIMARY);
        }
        if (!subtitle.isBlank() && headerY + 24 >= contentY && headerY <= contentY + rawHeight) {
            RenderUtil.drawString(context, font, subtitle, contentX, headerY + 14, UITheme.Colors.TEXT_SUBTITLE);
        }

        int markdownStartY = contentY + headerHeight;
        int markdownBoundsY = contentY + Math.max(0, headerHeight - scrollPixels);
        int markdownVerticalOffset = Math.max(0, scrollPixels - headerHeight);
        markdownRenderer.setBounds(contentX, markdownBoundsY, textWidth, rawHeight);
        markdownRenderer.setVerticalOffset(markdownVerticalOffset);
        markdownRenderer.render(context, font, mouseX, mouseY);
        String hoveredLink = markdownRenderer.getHoveredLink();
        String rawHoveredTooltip = markdownRenderer.getHoveredLinkTooltip();
        String dictionaryTooltip = stripDictionaryTooltipPrefix(rawHoveredTooltip);
        if (isDictionaryLink(hoveredLink) && dictionaryTooltip != null && !dictionaryTooltip.isBlank()) {
            hoveredDictionaryTooltip = dictionaryTooltip;
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
        } else if (isPostLink(hoveredLink)) {
            String postTooltip = rawHoveredTooltip != null ? rawHoveredTooltip.trim() : "";
            if (!postTooltip.isBlank()) {
                hoveredDictionaryTooltip = postTooltip;
            }
            tooltipMouseX = mouseX;
            tooltipMouseY = mouseY;
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
        renderReferencedBySection(context, font, mouseX, mouseY, textWidth, rawHeight, markdownHeight, scrollPixels, markdownStartY);

        if (actionHeight > 0) {
            int actionSectionY = markdownStartY + markdownHeight + referencedByHeight + ACTION_SECTION_SPACING;
            layoutActionButtons(textWidth, actionSectionY, scrollPixels);
            int dividerY = actionSectionY - scrollPixels + (ACTION_SECTION_SPACING / 2);
            if (dividerY >= contentY && dividerY <= contentY + rawHeight) {
                RenderUtil.fillRect(context, contentX, dividerY, contentX + textWidth, dividerY + 1, UITheme.Colors.BUTTON_BORDER);
            }
            if (websiteButton != null && websiteButton.active
                && websiteButton.getY() + websiteButton.getHeight() >= contentY
                && websiteButton.getY() <= contentY + rawHeight) {
                websiteButton.render(context.graphics(), mouseX, mouseY, delta);
            }
            if (discordStatusButton != null && discordStatusButton.active
                && discordStatusButton.getY() + discordStatusButton.getHeight() >= contentY
                && discordStatusButton.getY() <= contentY + rawHeight) {
                discordStatusButton.render(context.graphics(), mouseX, mouseY, delta);
            }
        }

        if (needsScroll) {
            int barX = contentX + textWidth + 4;
            if (scrollBar == null || scrollBarX != barX || scrollBarY != contentY || scrollBarHeight != rawHeight) {
                scrollBar = new ScrollBar(barX, contentY, rawHeight);
                scrollBarX = barX;
                scrollBarY = contentY;
                scrollBarHeight = rawHeight;
            }
            scrollBar.setScrollData(totalContentHeight, rawHeight);
            if (maxScroll > 0) {
                scrollBar.setScrollPercentage(scrollOffset / maxScroll);
            } else {
                scrollBar.setScrollPercentage(0);
            }
            boolean changed = false;
            if (windowHandle != 0L) {
                changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle);
            } else {
                scrollBar.render(context, mouseX, mouseY, delta);
            }
            if (changed || scrollBar.isDragging()) {
                scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            }
        } else if (scrollBar != null) {
            scrollBar = null;
            scrollBarX = Integer.MIN_VALUE;
            scrollBarY = Integer.MIN_VALUE;
            scrollBarHeight = Integer.MIN_VALUE;
            scrollOffset = 0;
        }
    }

    private int measureMarkdownHeight(UiFont font, int width) {
        markdownRenderer.setBounds(contentX, contentY, width, 1);
        return Math.max(1, markdownRenderer.getRequiredHeight(font));
    }

    private int getHeaderSectionHeight() {
        int textHeight = subtitle == null || subtitle.isBlank() ? 14 : 26;
        return Math.max(UITheme.Dimensions.BUTTON_HEIGHT, textHeight) + 8;
    }

    private int getActionSectionHeight() {
        int visibleCount = (hasWebsitePage() ? 1 : 0) + (hasDiscordStatusThread() ? 1 : 0);
        if (visibleCount <= 0) {
            return 0;
        }
        int buttonsHeight = visibleCount * UITheme.Dimensions.BUTTON_HEIGHT;
        int gaps = Math.max(0, visibleCount - 1) * ACTION_BUTTON_GAP;
        return ACTION_SECTION_SPACING + ACTION_SECTION_PADDING_TOP + buttonsHeight + gaps;
    }

    private int getReferencedBySectionHeight() {
        if (referencedByPosts == null || referencedByPosts.isEmpty()) {
            return 0;
        }
        int cardsHeight = referencedByPosts.size() * REFERENCED_BY_CARD_HEIGHT;
        int gaps = Math.max(0, referencedByPosts.size() - 1) * REFERENCED_BY_CARD_GAP;
        return REFERENCED_BY_SECTION_SPACING + REFERENCED_BY_HEADER_HEIGHT + 2 + cardsHeight + gaps;
    }

    private void renderReferencedBySection(
        UiRenderContext context,
        UiFont font,
        int mouseX,
        int mouseY,
        int width,
        int height,
        int markdownHeight,
        int scrollPixels,
        int markdownStartY
    ) {
        referencedByHitboxes.clear();
        if (referencedByPosts == null || referencedByPosts.isEmpty()) {
            return;
        }

        int sectionY = markdownStartY + markdownHeight + REFERENCED_BY_SECTION_SPACING;
        int headerY = sectionY - scrollPixels;
        int cardsY = sectionY + REFERENCED_BY_HEADER_HEIGHT + 2;
        int viewportBottom = contentY + height;

        RenderUtil.enableScissor(context, contentX, contentY, contentX + width, viewportBottom);
        if (headerY + REFERENCED_BY_HEADER_HEIGHT >= contentY && headerY <= viewportBottom) {
            RenderUtil.drawString(context, font, "Referenced By", contentX, headerY, UITheme.Colors.TEXT_SUBTITLE);
        }

        int currentCardY = cardsY;
        for (ArchiveDictionaryReferencedPost post : referencedByPosts) {
            String targetPath = buildReferencedByTargetPath(post);
            int drawY = currentCardY - scrollPixels;
            int cardX = contentX;
            int cardWidth = width;
            boolean visible = drawY + REFERENCED_BY_CARD_HEIGHT >= contentY && drawY <= viewportBottom;
            boolean hovered = visible
                && mouseX >= cardX
                && mouseX < cardX + cardWidth
                && mouseY >= drawY
                && mouseY < drawY + REFERENCED_BY_CARD_HEIGHT
                && !targetPath.isEmpty();

            if (visible) {
                int cardColor = hovered ? REFERENCED_BY_CARD_BG_HOVER : REFERENCED_BY_CARD_BG;
                RenderUtil.fillRect(context, cardX, drawY, cardX + cardWidth, drawY + REFERENCED_BY_CARD_HEIGHT, cardColor);
                RenderUtil.drawBorder(context, cardX, drawY, cardWidth, REFERENCED_BY_CARD_HEIGHT, REFERENCED_BY_CARD_BORDER);
                renderReferencedByCardContent(context, font, post, cardX, drawY, cardWidth, hovered);
            }

            if (!targetPath.isEmpty()) {
                referencedByHitboxes.add(new ReferencedByHitbox(cardX, drawY, cardWidth, REFERENCED_BY_CARD_HEIGHT, targetPath));
            }
            currentCardY += REFERENCED_BY_CARD_HEIGHT + REFERENCED_BY_CARD_GAP;
        }
        RenderUtil.disableScissor(context);
    }

    private void renderReferencedByCardContent(
        UiRenderContext context,
        UiFont font,
        ArchiveDictionaryReferencedPost post,
        int x,
        int y,
        int width,
        boolean hovered
    ) {
        String openLabel = "Open";
        int openColor = hovered ? REFERENCED_BY_LINK_HOVER_COLOR : REFERENCED_BY_LINK_COLOR;
        int openWidth = font.width(openLabel);
        int openX = x + width - REFERENCED_BY_CARD_PADDING_X - openWidth;
        int titleMaxWidth = Math.max(40, openX - (x + REFERENCED_BY_CARD_PADDING_X) - 8);

        String titleText = safeTrim(post != null ? post.title() : "");
        if (titleText.isEmpty()) {
            titleText = safeTrim(post != null ? post.code() : "");
        }
        if (titleText.isEmpty()) {
            titleText = "Unknown Entry";
        }
        titleText = trimToWidth(font, titleText, titleMaxWidth);
        RenderUtil.drawString(context, font, titleText, x + REFERENCED_BY_CARD_PADDING_X, y + 5, UITheme.Colors.TEXT_PRIMARY);

        if (!buildReferencedByTargetPath(post).isEmpty()) {
            RenderUtil.drawString(context, font, openLabel, openX, y + 5, openColor);
        }

        int chipY = y + 20;
        int chipX = x + REFERENCED_BY_CARD_PADDING_X;
        String channelChip = safeTrim(post != null ? post.channelCode() : "");
        if (channelChip.isEmpty()) {
            channelChip = "CH";
        }
        String channelName = safeTrim(post != null ? post.channelName() : "");
        String channelLabel = channelName.isEmpty() ? channelChip : channelChip + " " + channelName;
        chipX += renderChip(context, font, channelLabel, chipX, chipY) + 4;

        String code = safeTrim(post != null ? post.code() : "");
        if (!code.isEmpty()) {
            chipX += renderChip(context, font, code, chipX, chipY) + 4;
        }

        String ageLabel = formatAgeLabel(post);
        if (!ageLabel.isEmpty()) {
            RenderUtil.drawString(context, font, ageLabel, chipX, chipY + 2, UITheme.Colors.TEXT_SUBTITLE);
        }
    }

    private int renderChip(UiRenderContext context, UiFont font, String text, int x, int y) {
        String safeText = safeTrim(text);
        if (safeText.isEmpty()) {
            return 0;
        }
        int chipWidth = font.width(safeText) + REFERENCED_BY_CHIP_PADDING_X * 2;
        RenderUtil.fillRect(context, x, y, x + chipWidth, y + REFERENCED_BY_CHIP_HEIGHT, REFERENCED_BY_CHIP_BG);
        RenderUtil.drawBorder(context, x, y, chipWidth, REFERENCED_BY_CHIP_HEIGHT, REFERENCED_BY_CHIP_BORDER);
        RenderUtil.drawString(context, font, safeText, x + REFERENCED_BY_CHIP_PADDING_X, y + 2, UITheme.Colors.TEXT_TAG);
        return chipWidth;
    }

    private static String trimToWidth(UiFont font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String candidate = text;
        while (!candidate.isEmpty() && font.width(candidate + "...") > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? "..." : candidate + "...";
    }

    private static String formatAgeLabel(ArchiveDictionaryReferencedPost post) {
        if (post == null) {
            return "";
        }
        long timestamp = post.updatedAt() > 0 ? post.updatedAt() : post.archivedAt();
        if (timestamp <= 0) {
            return "";
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - timestamp);
        long days = ageMillis / 86_400_000L;
        if (days <= 0) {
            return "today";
        }
        return days + "d ago";
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String buildReferencedByTargetPath(ArchiveDictionaryReferencedPost post) {
        if (post == null) {
            return "";
        }
        String id = safeTrim(post.id());
        if (id.isEmpty()) {
            return "";
        }
        return "/archive/" + encodePathSegment(id);
    }

    private void updateLayout(int screenWidth, int screenHeight) {
        popupWidth = Math.max(MIN_POPUP_WIDTH, Math.min(MAX_POPUP_WIDTH, screenWidth - 40));
        popupHeight = Math.max(MIN_POPUP_HEIGHT, Math.min(MAX_POPUP_HEIGHT, screenHeight - 40));
        popupX = (screenWidth - popupWidth) / 2;
        popupY = (screenHeight - popupHeight) / 2;
        contentX = popupX + OUTER_PADDING;
        contentY = popupY + OUTER_PADDING;
        contentWidth = popupWidth - OUTER_PADDING * 2;
        contentHeight = Math.max(1, popupHeight - OUTER_PADDING * 2);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (!isInsidePopup(mouseX, mouseY)) {
            onClose.run();
            return true;
        }
        if (button != 0) {
            return true;
        }

        if (closeButton != null && isOver(closeButton, mouseX, mouseY)) {
            onClose.run();
            return true;
        }

        if (websiteButton != null && websiteButton.active && isOver(websiteButton, mouseX, mouseY)) {
            openWebsitePage();
            return true;
        }
        if (discordStatusButton != null && discordStatusButton.active && isOver(discordStatusButton, mouseX, mouseY)) {
            openDiscordStatusThread();
            return true;
        }

        if (scrollBar != null && scrollBar.mouseClicked(click, doubled)) {
            double maxScroll = Math.max(0, totalContentHeight - contentHeight);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }

        ReferencedByHitbox hitbox = findReferencedByHitbox(mouseX, mouseY);
        if (hitbox != null && hitbox.targetPath() != null && !hitbox.targetPath().isBlank()) {
            onLinkClicked.accept(hitbox.targetPath());
            return true;
        }

        if (markdownRenderer.mouseClicked(click, doubled)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < contentY + contentHeight) {
            double maxScroll = Math.max(0, totalContentHeight - contentHeight);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 18));
            if (scrollBar != null && maxScroll > 0) {
                scrollBar.setScrollPercentage(scrollOffset / maxScroll);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(UiMouseEvent click, double offsetX, double offsetY) {
        if (scrollBar != null && scrollBar.mouseDragged(click, offsetX, offsetY)) {
            double maxScroll = Math.max(0, totalContentHeight - contentHeight);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent click) {
        if (scrollBar != null) {
            scrollBar.mouseReleased(click);
            return true;
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

    private boolean isInsidePopup(double mouseX, double mouseY) {
        return mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= popupY && mouseY < popupY + popupHeight;
    }

    private static boolean isOver(CustomButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
            && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private ReferencedByHitbox findReferencedByHitbox(double mouseX, double mouseY) {
        if (mouseX < contentX || mouseX >= contentX + contentWidth || mouseY < contentY || mouseY >= contentY + contentHeight) {
            return null;
        }
        for (ReferencedByHitbox hitbox : referencedByHitboxes) {
            if (mouseX >= hitbox.x() && mouseX < hitbox.x() + hitbox.width()
                && mouseY >= hitbox.y() && mouseY < hitbox.y() + hitbox.height()) {
                return hitbox;
            }
        }
        return null;
    }

    private static boolean isDictionaryLink(String linkUrl) {
        String path = extractPath(linkUrl);
        return !path.isEmpty() && path.startsWith(DICTIONARY_PATH_PREFIX);
    }

    private static boolean isPostLink(String linkUrl) {
        return !extractPostIdFromLink(linkUrl).isEmpty();
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

    private void renderDictionaryTooltip(UiRenderContext renderContext, String text, int mouseX, int mouseY) {
        if (text == null || text.isBlank()) {
            return;
        }

        UiMinecraftClient client = UiMinecraftClient.getInstance();
        UiFont font = client.uiFont();
        int maxTextWidth = Math.min(TOOLTIP_MAX_WIDTH, Math.max(100, popupWidth - 40));
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

    private void ensureActionButtons() {
        if (websiteButton == null) {
            websiteButton = new CustomButton(0, 0, 180, UITheme.Dimensions.BUTTON_HEIGHT, UiText.of("Open On Website"), button -> openWebsitePage());
        }
        if (discordStatusButton == null) {
            discordStatusButton = new CustomButton(0, 0, 180, UITheme.Dimensions.BUTTON_HEIGHT, UiText.of("Open Discord Thread"), button -> openDiscordStatusThread());
        }
    }

    private void layoutActionButtons(int textWidth, int actionSectionY, int scrollPixels) {
        int buttonWidth = Math.min(220, textWidth);
        int buttonX = contentX;
        int nextY = actionSectionY + ACTION_SECTION_PADDING_TOP - scrollPixels;

        boolean showWebsite = hasWebsitePage();
        websiteButton.active = showWebsite;
        if (showWebsite) {
            websiteButton.setWidth(buttonWidth);
            websiteButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
            websiteButton.setX(buttonX);
            websiteButton.setY(nextY);
            nextY += UITheme.Dimensions.BUTTON_HEIGHT + ACTION_BUTTON_GAP;
        }

        boolean showDiscord = hasDiscordStatusThread();
        discordStatusButton.active = showDiscord;
        if (showDiscord) {
            discordStatusButton.setWidth(buttonWidth);
            discordStatusButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
            discordStatusButton.setX(buttonX);
            discordStatusButton.setY(nextY);
        }
    }

    private boolean hasWebsitePage() {
        return !buildWebsiteUrl().isEmpty();
    }

    private boolean hasDiscordStatusThread() {
        return !getStatusThreadUrl().isEmpty();
    }

    private void openWebsitePage() {
        String url = buildWebsiteUrl();
        if (url.isEmpty()) {
            return;
        }
        try {
            UiPlatform.openUri(url);
        } catch (Exception ignored) {
        }
    }

    private void openDiscordStatusThread() {
        String url = getStatusThreadUrl();
        if (url.isEmpty()) {
            return;
        }
        if (onDiscordThreadClicked != null) {
            onDiscordThreadClicked.accept(url);
            return;
        }
        onLinkClicked.accept(url);
    }

    private String getStatusThreadUrl() {
        if (currentEntry == null) {
            return "";
        }
        String raw = currentEntry.statusURL();
        return raw != null ? raw.trim() : "";
    }

    private String buildWebsiteUrl() {
        String base = normalizeWebsiteBase(websiteBase);
        if (base.isEmpty() || currentEntry == null) {
            return "";
        }
        String slug = buildDictionarySlug(currentEntry);
        if (slug.isEmpty()) {
            return "";
        }
        return base + "/dictionary/" + encodePathSegment(slug) + "/";
    }

    private static String buildDictionarySlug(ArchiveDictionaryEntry entry) {
        if (entry == null) {
            return "";
        }
        String base = safeTrim(entry.id());
        if (base.isEmpty()) {
            return "";
        }
        String primaryTerm = "";
        if (entry.terms() != null && !entry.terms().isEmpty() && entry.terms().get(0) != null) {
            primaryTerm = entry.terms().get(0);
        }
        String primary = slugifyName(primaryTerm);
        if (primary.isEmpty()) {
            return base;
        }
        return base + "-" + primary;
    }

    private static String slugifyName(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        String withoutDiacritics = normalized.replaceAll("[\\u0300-\\u036f]", "");
        String replaced = withoutDiacritics.replaceAll("[^a-zA-Z0-9]+", "-");
        return replaced.replaceAll("^-+|-+$", "");
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String normalizeWebsiteBase(String base) {
        String normalized = base != null ? base.trim() : "";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record ReferencedByHitbox(int x, int y, int width, int height, String targetPath) {
    }
}
