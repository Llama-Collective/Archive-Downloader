package com.andrews.st2downloader.gui.widget;

import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.models.ArchiveChannel;
import com.andrews.st2downloader.util.RenderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;

public class ChannelFilterPanel implements Drawable, Element {
    private static final int HEADER_HEIGHT = 28;
    private static final int ITEM_HEIGHT = 22;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private int height;

    private ScrollBar scrollBar;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private List<ArchiveChannel> channels = new ArrayList<>();
    private String selectedPath = null;
    private Consumer<String> onSelectionChanged;
    private Consumer<ArchiveChannel> onHoverChanged;
    private final Map<String, List<ArchiveChannel>> channelsByCategory = new HashMap<>();
    private final Map<String, Integer> channelCounts = new HashMap<>();

    public ChannelFilterPanel(int x, int y, int width, int height) {
        this.client = MinecraftClient.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y + HEADER_HEIGHT, height - HEADER_HEIGHT - 10);
    }

    public void setDimensions(int x, int y, int width, int height) {
        boolean needsNewScrollBar = this.scrollBar == null || this.width != width || this.height != height
                || this.x != x || this.y != y;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (needsNewScrollBar) {
            this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y + HEADER_HEIGHT, height - HEADER_HEIGHT - 10);
        }
    }

    public void setOnSelectionChanged(Consumer<String> callback) {
        this.onSelectionChanged = callback;
    }

    public void setOnHoverChanged(Consumer<ArchiveChannel> callback) {
        this.onHoverChanged = callback;
    }

    public void setChannels(List<ArchiveChannel> channels) {
        this.channels = channels != null ? channels : new ArrayList<>();
        this.scrollOffset = 0;
        regroupChannels();
        ensureCountsForChannels();
    }

    public void setChannelCounts(Map<String, Integer> counts) {
        channelCounts.clear();
        if (counts != null) {
            channelCounts.putAll(counts);
        }
        ensureCountsForChannels();
    }

    private void ensureCountsForChannels() {
        for (ArchiveChannel channel : channels) {
            if (channel == null || channel.path() == null) continue;
            channelCounts.putIfAbsent(channel.path(), channel.entryCount());
        }
    }

    public String getSelectedChannelPath() {
        return selectedPath;
    }

    public void clearSelection() {
        selectedPath = null;
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(null);
        }
    }

    private void regroupChannels() {
        channelsByCategory.clear();
        for (ArchiveChannel channel : channels) {
            String category = channel.category() != null ? channel.category() : "Channels";
            channelsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(channel);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);

        RenderUtil.fillRect(context, x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        

        float scale = 0.9f;
        RenderUtil.drawScaledString(context, "Channels", x + UITheme.Dimensions.PADDING, y + UITheme.Dimensions.PADDING + 2, UITheme.Colors.TEXT_PRIMARY, scale);

        String reset = "Reset";
        int resetWidth = (int) (client.textRenderer.getWidth(reset) * scale);
        int resetX = x + width - resetWidth - UITheme.Dimensions.PADDING;
        int resetY = y + UITheme.Dimensions.PADDING + 2;
        RenderUtil.drawScaledString(context, reset, resetX, resetY, UITheme.Colors.TEXT_PRIMARY, scale);

        int listStartY = y + HEADER_HEIGHT - (int) scrollOffset;
        contentHeight = HEADER_HEIGHT;

        int scrollbarWidth = this.scrollBar.isVisible() ? (UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING / 2) : 0;

        RenderUtil.enableScissor(context, x, y + HEADER_HEIGHT, x + width - scrollbarWidth, y + height - 10);

        int currentY = listStartY;
        for (Map.Entry<String, List<ArchiveChannel>> entry : channelsByCategory.entrySet()) {
            String category = entry.getKey();
            List<ArchiveChannel> categoryChannels = entry.getValue();

            RenderUtil.drawScaledString(context, category, x + UITheme.Dimensions.PADDING, currentY + 4, UITheme.Colors.TEXT_SUBTITLE, scale);
            currentY += (int) (ITEM_HEIGHT * scale) + 2;
            contentHeight += ITEM_HEIGHT;

            for (ArchiveChannel channel : categoryChannels) {
                boolean selected = channel.path().equals(selectedPath);
                int bgColor = selected ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.PANEL_BG;
                RenderUtil.fillRect(context, x + UITheme.Dimensions.PADDING / 2, currentY, x + width - scrollbarWidth - UITheme.Dimensions.PADDING / 2, currentY + ITEM_HEIGHT - 2, bgColor);
                String path = channel.path();
                int count = channelCounts.getOrDefault(path, channel.entryCount());
                String countText = String.valueOf(count);
                int countWidth = client.textRenderer.getWidth(countText);
                float textScale = 0.9f;
                int codeWidth = client.textRenderer.getWidth(channel.code()) + 4;

                RenderUtil.drawScaledString(context, channel.code(), x + UITheme.Dimensions.PADDING, currentY + 6, UITheme.Colors.TEXT_MUTED, textScale);
                RenderUtil.drawScaledString(context, channel.name(), x + UITheme.Dimensions.PADDING + codeWidth, currentY + 6, UITheme.Colors.TEXT_PRIMARY, textScale);
                RenderUtil.drawScaledString(context, countText, x + width - scrollbarWidth - UITheme.Dimensions.PADDING - countWidth - 6, currentY + 6, UITheme.Colors.TEXT_SUBTITLE, textScale);

                currentY += ITEM_HEIGHT;
                contentHeight += ITEM_HEIGHT;
            }
            currentY += 4;
            contentHeight += 4;
        }

        RenderUtil.disableScissor(context);

        int visibleHeight = height - HEADER_HEIGHT - 10;
        double scrollable = Math.max(0, contentHeight - height);
        scrollBar.setScrollData(contentHeight - HEADER_HEIGHT, visibleHeight);
        if (scrollable > 0) {
            scrollBar.setScrollPercentage(scrollOffset / scrollable);
        }
        updateHover(mouseX, mouseY);

        if (client != null && client.getWindow() != null) {
            boolean changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());
            if (changed || scrollBar.isDragging()) {
                scrollOffset = scrollBar.getScrollPercentage() * scrollable;
            }
        } else {
            scrollBar.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        if (scrollBar.mouseClicked(click, doubled)) {
            return true;
        }

        String reset = "Reset";
        int resetWidth = client.textRenderer.getWidth(reset);
        int resetX = x + width - resetWidth - UITheme.Dimensions.PADDING;
        int resetY = y + UITheme.Dimensions.PADDING + 2;
        if (mouseX >= resetX && mouseX <= resetX + resetWidth && mouseY >= resetY && mouseY <= resetY + UITheme.Typography.TEXT_HEIGHT) {
            clearSelection();
            return true;
        }

        int listStartY = y + HEADER_HEIGHT - (int) scrollOffset;
        int currentY = listStartY;

        for (Map.Entry<String, List<ArchiveChannel>> entry : channelsByCategory.entrySet()) {
            currentY += ITEM_HEIGHT; // skip category row
            for (ArchiveChannel channel : entry.getValue()) {
                if (mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT) {
                    if (selectedPath != null && selectedPath.equals(channel.path())) {
                        // already selected, but unselect on click
                        selectedPath = null;
                        if (onSelectionChanged != null) {
                            onSelectionChanged.accept(null);
                        }
                        return true;
                    }
                    selectedPath = channel.path();
                    if (onSelectionChanged != null) {
                        onSelectionChanged.accept(selectedPath);
                    }
                    if (onHoverChanged != null) {
                        onHoverChanged.accept(channel);
                    }
                    return true;
                }
                currentY += ITEM_HEIGHT;
            }
            currentY += 4;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 12, Math.max(0, contentHeight - height)));
            scrollBar.setScrollPercentage((float) (scrollOffset / Math.max(1, contentHeight - height)));
            updateHover(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (scrollBar.mouseDragged(click, offsetX, offsetY)) {
            scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - height);
            updateHover(click.x(), click.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        scrollBar.mouseReleased(click);
        updateHover(click.x(), click.y());
        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    private void updateHover(double mouseX, double mouseY) {
        if (onHoverChanged == null) {
            return;
        }
        ArchiveChannel hovered = null;
        int listStartY = y + HEADER_HEIGHT - (int) scrollOffset;
        int currentY = listStartY;
        int scrollbarWidth = this.scrollBar.isVisible() ? (UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING / 2) : 0;

        float scale = 0.9f;
        for (Map.Entry<String, List<ArchiveChannel>> entry : channelsByCategory.entrySet()) {
            // skip category row height
            currentY += (int) (ITEM_HEIGHT * scale) + 2;
            List<ArchiveChannel> categoryChannels = entry.getValue();
            for (ArchiveChannel channel : categoryChannels) {
                if (mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT &&
                    mouseX >= x && mouseX < x + width - scrollbarWidth) {
                    hovered = channel;
                    break;
                }
                currentY += ITEM_HEIGHT;
            }
            if (hovered != null) break;
            currentY += 4;
        }
        onHoverChanged.accept(hovered);
        
    }
}
