package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.models.ArchiveChannel;
import com.andrews.archivedownloader.util.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ChannelDescriptionWidget {
    private int x;
    private int y;
    private int width;
    private int height;
    private ArchiveChannel channel;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setChannel(ArchiveChannel channel) {
        this.channel = channel;
    }

    public void render(GuiGraphics context, Font font) {
        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height, UITheme.Colors.BUTTON_BORDER);

        int textX = x + UITheme.Dimensions.PADDING;
        int textY = y + UITheme.Dimensions.PADDING;

        String desc = channel != null && channel.description() != null
            ? channel.description()
            : "Click on a channel to filter posts";
        int maxWidth = width - UITheme.Dimensions.PADDING * 2;
        RenderUtil.drawWrappedText(context, font, desc, textX, textY, maxWidth, UITheme.Colors.TEXT_PRIMARY);
    }
}
