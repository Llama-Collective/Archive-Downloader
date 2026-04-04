package com.andrews.archivedownloader.wrapper.gui;

import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.text.UiText;

//? >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
// import net.minecraft.client.gui.GuiGraphics;
//? }

import net.minecraft.client.gui.components.EditBox;

public abstract class UiTextFieldBase extends EditBox {
    protected UiTextFieldBase(UiMinecraftClient client, int x, int y, int width, int height, UiText text) {
        super(client.font(), x, y, width, height, text.nativeComponent());
    }

    public final void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        //? >=26.1 {
        super.extractRenderState(context.graphics(), mouseX, mouseY, delta);
        //? } else {
        /*super.render(context.graphics(), mouseX, mouseY, delta);
        *///? }
    }

    //? >=26.1 {
    @Override
    public final void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderWidget(UiRenderContext.from(context), mouseX, mouseY, delta);
    }
    //? } else {
    // @Override
    // public final void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
    //     renderWidget(UiRenderContext.from(context), mouseX, mouseY, delta);
    // }
    //? }

    protected abstract void renderWidget(UiRenderContext context, int mouseX, int mouseY, float delta);
}
