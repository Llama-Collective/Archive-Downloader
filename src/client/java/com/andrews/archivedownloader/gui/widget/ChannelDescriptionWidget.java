package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.models.ArchiveChannel;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;

public class ChannelDescriptionWidget implements UiEventListener {
    private int x;
    private int y;
    private int width;
    private int height;
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setChannel(ArchiveChannel channel) {
        String description = channel != null && channel.description() != null
            ? channel.description()
            : "Click on a channel to filter posts";
        markdownRenderer.setMarkdown(description);
    }

    public int getRequiredHeight(UiFont font) {
        int textX = x + UITheme.Dimensions.PADDING;
        int textY = y + UITheme.Dimensions.PADDING;
        int maxWidth = Math.max(1, width - UITheme.Dimensions.PADDING * 2);
        int maxHeight = Math.max(0, height - UITheme.Dimensions.PADDING * 2);
        markdownRenderer.setBounds(textX, textY, maxWidth, maxHeight);
        return markdownRenderer.getRequiredHeight(font) + UITheme.Dimensions.PADDING * 2;
    }

    public void render(UiRenderContext context, UiFont font, int mouseX, int mouseY) {
        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height, UITheme.Colors.BUTTON_BORDER);

        int textX = x + UITheme.Dimensions.PADDING;
        int textY = y + UITheme.Dimensions.PADDING;
        int maxWidth = Math.max(1, width - UITheme.Dimensions.PADDING * 2);
        int maxHeight = Math.max(0, height - UITheme.Dimensions.PADDING * 2);
        markdownRenderer.setBounds(textX, textY, maxWidth, maxHeight);
        markdownRenderer.render(context, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        if (click == null || !isInside(click.x(), click.y())) {
            return false;
        }
        if (markdownRenderer.mouseClicked(click, doubled)) {
            return true;
        }
        return click.button() == 0;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
