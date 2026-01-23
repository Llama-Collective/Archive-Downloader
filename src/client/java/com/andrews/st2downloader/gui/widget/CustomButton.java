package com.andrews.st2downloader.gui.widget;

import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.util.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

public class CustomButton extends Button {
    private boolean renderAsXIcon = false;

    public CustomButton(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public void setRenderAsXIcon(boolean renderAsXIcon) {
        this.renderAsXIcon = renderAsXIcon;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int bgColor = getBackgroundColor(mouseX, mouseY);
        drawBackground(context, bgColor);
        drawBorder(context);
        drawText(context);
    }

    private int getBackgroundColor(int mouseX, int mouseY) {
        if (!this.active) {
            return UITheme.Colors.BUTTON_BG_DISABLED;
        }

        boolean isHovered = mouseX >= this.getX() && mouseY >= this.getY() &&
                           mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        return isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
    }

    private void drawBackground(GuiGraphics context, int color) {
        RenderUtil.fillRect(context, this.getX(), this.getY(), this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(), color);
    }

    private void drawBorder(GuiGraphics context) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.getWidth();
        int y2 = y1 + this.getHeight();
        int borderColor = UITheme.Colors.BUTTON_BORDER;
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;

        RenderUtil.fillRect(context, x1, y1, x2, y1 + borderWidth, borderColor);
        RenderUtil.fillRect(context, x1, y2 - borderWidth, x2, y2, borderColor);
        RenderUtil.fillRect(context, x1, y1, x1 + borderWidth, y2, borderColor);
        RenderUtil.fillRect(context, x2 - borderWidth, y1, x2, y2, borderColor);
    }

    private void drawText(GuiGraphics context) {
        Font tr = Minecraft.getInstance().font;
        int textColor = this.active ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_DISABLED;

        String text = getDisplayText();
        int yOffset = this.getMessage().getString().equals("⚙") ? 0 : 1;
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