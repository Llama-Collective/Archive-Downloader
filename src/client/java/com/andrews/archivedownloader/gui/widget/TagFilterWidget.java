package com.andrews.archivedownloader.gui.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.util.TagUtil;

public class TagFilterWidget {
    public enum TagState {
        INCLUDE, EXCLUDE
    }

    private int x;
    private int y;
    private int width;
    private int height;
    private final int rowHeight = 18;
    private ScrollBar scrollBar;
    private int lastBoxHeight = 0;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private List<String> tags = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();
    private Map<String, TagState> tagStates = new HashMap<>();
    private ServerEntry server = ServerDictionary.getDefaultServer();
    private final List<TagHitbox> hitboxes = new ArrayList<>();
    private BiConsumer<String, TagState> onToggle;

    public void setBounds(int x, int y, int width, int height) {
        boolean needsNewScrollBar = this.scrollBar == null || this.width != width || this.height != height
                || this.x != x || this.y != y;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (needsNewScrollBar) {
            this.scrollBar = new ScrollBar(
                    x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.BORDER_WIDTH,
                    y + this.rowHeight, height - this.rowHeight - UITheme.Dimensions.PADDING);
        }
    }

    public void setData(List<String> tags, Map<String, Integer> counts, Map<String, TagState> tagStates) {
        this.tags = tags != null ? tags : new ArrayList<>();
        this.counts = counts != null ? counts : new HashMap<>();
        this.tagStates = tagStates != null ? tagStates : new HashMap<>();
    }

    public void setOnToggle(BiConsumer<String, TagState> callback) {
        this.onToggle = callback;
    }

    public void setServer(ServerEntry server) {
        this.server = server != null ? server : ServerDictionary.getDefaultServer();
    }

    public void render(GuiGraphics context, Font font, int mouseX, int mouseY, float delta, long windowHandle) {
        if (scrollBar == null) {
            scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.BORDER_WIDTH,
                    y + rowHeight, height - rowHeight - UITheme.Dimensions.PADDING);
        }
        hitboxes.clear();
        int content = rowHeight + tags.size() * rowHeight + UITheme.Dimensions.PADDING;
        contentHeight = content;
        int boxHeight = Math.min(height, contentHeight);
        lastBoxHeight = boxHeight;
        double maxScroll = Math.max(0, contentHeight - boxHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int innerWidth = width;

        RenderUtil.fillRect(context, x, y, x + innerWidth, y + boxHeight, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, x, y, x + innerWidth, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + innerWidth - UITheme.Dimensions.BORDER_WIDTH, y, x + innerWidth, y + boxHeight,
                UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + boxHeight - UITheme.Dimensions.BORDER_WIDTH, x + innerWidth, y + boxHeight,
                UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawScaledString(context, "Tags", x + UITheme.Dimensions.PADDING, y + 4, UITheme.Colors.TEXT_PRIMARY,
                0.9f);

        int clipTop = y + rowHeight;
        int clipBottom = y + boxHeight - UITheme.Dimensions.PADDING;
        int currentY = y + rowHeight - (int) scrollOffset;
        RenderUtil.enableScissor(context, x + 1, clipTop, x + innerWidth - 1, clipBottom);

        int scrollbarWidth = scrollBar.isVisible() ? UITheme.Dimensions.SCROLLBAR_WIDTH : 0;

        for (String tag : tags) {
            TagState state = tagStates.get(tag.toLowerCase());
            int bgColor = UITheme.Colors.PANEL_BG;
            if (state == TagState.INCLUDE) {
                bgColor = 0x5532cd32;
            } else if (state == TagState.EXCLUDE) {
                bgColor = 0x55cd3232;
            }
            if (currentY + rowHeight >= clipTop && currentY <= clipBottom) {
                RenderUtil.fillRect(context, x + 1, currentY, x + innerWidth - 1, currentY + rowHeight, bgColor);

                int swatchColor = getTagSwatchColor(tag);
                int textColor = UITheme.Colors.TEXT_PRIMARY;
                int textHeight = (int) (font.lineHeight * 0.85f);
                int centerOffset = (rowHeight - textHeight) / 2;
                String displayTag = TagUtil.formatTagLabel(tag, server);
                RenderUtil.fillRect(context, x + UITheme.Dimensions.PADDING, currentY + 4, x + UITheme.Dimensions.PADDING + 6,
                        currentY + rowHeight - 4, swatchColor);
                RenderUtil.drawScaledString(context, displayTag, x + UITheme.Dimensions.PADDING + 10, currentY + centerOffset,
                        textColor, 0.85f, innerWidth - 50);

                Integer count = counts.get(tag.toLowerCase());
                if (count != null) {
                    String countText = String.valueOf(count);
                    RenderUtil.drawString(context, font, countText,
                            x + innerWidth - UITheme.Dimensions.PADDING - font.width(countText) - scrollbarWidth,
                            currentY + 4, UITheme.Colors.TEXT_SUBTITLE);
                }
            }

            hitboxes.add(new TagHitbox(tag, x + 1, currentY, x + innerWidth - 1, currentY + rowHeight));
            currentY += rowHeight;
        }

        RenderUtil.disableScissor(context);

        if (scrollBar != null) {
            scrollBar.setScrollData(contentHeight, boxHeight);
            if (maxScroll <= 0) {
                scrollBar.setScrollPercentage(0);
                scrollOffset = 0;
            } else {
                scrollBar.setScrollPercentage(scrollOffset / maxScroll);
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
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (scrollBar != null && scrollBar.mouseClicked(click, doubled)) {
            double maxScroll = Math.max(0, contentHeight - lastBoxHeight);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        for (TagHitbox hit : hitboxes) {
            if (hit.contains(mouseX, mouseY)) {
                String key = hit.tag().toLowerCase();
                TagState current = tagStates.get(key);
                TagState next;
                if (current == null) {
                    next = TagState.INCLUDE;
                } else if (current == TagState.INCLUDE) {
                    next = TagState.EXCLUDE;
                } else {
                    next = null;
                }
                if (next == null) {
                    tagStates.remove(key);
                } else {
                    tagStates.put(key, next);
                }
                if (onToggle != null) {
                    onToggle.accept(hit.tag(), next);
                }
                return true;
            }
        }

        // check if mouse is within bounds but didn't hit any items
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            return true;
        }

        return false;
    }

    public boolean handleScroll(double mouseX, double mouseY, double verticalAmount) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            double maxScroll = Math.max(0, contentHeight - lastBoxHeight);
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 12, maxScroll));
            if (scrollBar != null && maxScroll > 0) {
                scrollBar.setScrollPercentage(scrollOffset / maxScroll);
            }
            return true;
        }
        return false;
    }

    private int getTagSwatchColor(String tag) {
        return TagUtil.getTagColor(tag, server);
    }

    private record TagHitbox(String tag, int x1, int y1, int x2, int y2) {
        boolean contains(double px, double py) {
            return px >= x1 && px <= x2 && py >= y1 && py <= y2;
        }
    }
}
