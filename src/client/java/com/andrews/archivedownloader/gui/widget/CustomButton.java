package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiButtonBase;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.text.UiText;
public class CustomButton extends UiButtonBase {
    private boolean renderAsXIcon = false;

    public CustomButton(int x, int y, int width, int height, UiText message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    public void setMessage(UiText message) {
        super.setMessage(message.nativeComponent());
    }

    public void setRenderAsXIcon(boolean renderAsXIcon) {
        this.renderAsXIcon = renderAsXIcon;
    }

    @Override
    protected void drawText(UiRenderContext context, UiFont font, int color) {
        var tr = UiMinecraftClient.getInstance().uiFont();
        int textColor = this.active ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_DISABLED;

        String text = getDisplayText();
        int yOffset = 0;
        int centerX = this.getX() + this.getWidth() / 2;
        int centerY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2 + yOffset;

        RenderUtil.drawCenteredString(context, tr, text, centerX, centerY, textColor);
    }

    private String getDisplayText() {
        if (renderAsXIcon) {
            return "✕";
        }
        return this.getMessage().getString();
    }
}
