package com.andrews.archivedownloader.wrapper.gui;

import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.text.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

public abstract class UiTextFieldBase extends EditBox {
    protected UiTextFieldBase(UiMinecraftClient client, int x, int y, int width, int height, UiText text) {
        super(client.font(), x, y, width, height, text.nativeComponent());
    }

    @Override
    public final void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderWidget(UiRenderContext.from(context), mouseX, mouseY, delta);
    }

    protected abstract void renderWidget(UiRenderContext context, int mouseX, int mouseY, float delta);
}
