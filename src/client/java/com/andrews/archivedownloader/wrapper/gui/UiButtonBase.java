package com.andrews.archivedownloader.wrapper.gui;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.text.UiText;

//? <1.21.11 {
 /*import net.minecraft.client.gui.Font;
*///? }

//? >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
 /*import net.minecraft.client.gui.GuiGraphics;
*///? }

import net.minecraft.client.gui.components.Button;

public abstract class UiButtonBase extends Button {
    protected UiButtonBase(int x, int y, int width, int height, UiText message, OnPress onPress) {
        super(x, y, width, height, message.nativeComponent(), onPress, DEFAULT_NARRATION);
    }

    //? >=26.1 {
    @Override
    protected final void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderContents(UiRenderContext.from(context), mouseX, mouseY, delta);
    }
    //? } else if >=1.21.11 {
    // @Override
    // protected final void renderContents(GuiGraphics context, int mouseX, int
    // mouseY, float delta) {
    // renderContents(UiRenderContext.from(context), mouseX, mouseY, delta);
    // }
    //? } else {
     /*@Override
     public void renderString(GuiGraphics context, Font font, int color) {
         drawText(UiRenderContext.from(context), new UiFont(font), color);
     }
    *///? }

    protected abstract void drawText(UiRenderContext context, UiFont font, int color);

    public final void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        //? >=26.1 {
        super.extractWidgetRenderState(context.graphics(), mouseX, mouseY, delta);
        //? } else {
         /*super.render(context.graphics(), mouseX, mouseY, delta);
        *///? }
    }

    protected void renderContents(UiRenderContext context, int mouseX, int mouseY, float delta) {
        int bgColor = getBackgroundColor(mouseX, mouseY);
        drawBackground(context, bgColor);
        drawBorder(context);
        drawText(context, UiMinecraftClient.getInstance().uiFont(), 0);
    }

    private int getBackgroundColor(int mouseX, int mouseY) {
        if (!this.active) {
            return UITheme.Colors.BUTTON_BG_DISABLED;
        }

        boolean isHovered = mouseX >= this.getX() && mouseY >= this.getY() &&
                mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        return isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
    }

    private void drawBackground(UiRenderContext context, int color) {
        RenderUtil.fillRect(context, this.getX(), this.getY(), this.getX() + this.getWidth(),
                this.getY() + this.getHeight(), color);
    }

    private void drawBorder(UiRenderContext context) {
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

}
