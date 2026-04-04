package com.andrews.archivedownloader.wrapper.gui;

import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.text.UiText;

//? >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
// import net.minecraft.client.gui.GuiGraphics;
//? }


import net.minecraft.client.gui.screens.Screen;
//? >=1.21.9
 import net.minecraft.client.input.MouseButtonEvent;

/**
 * Wrapper base for Minecraft screens so feature screens can operate on wrapper input/render types.
 */
public abstract class UiScreenBase extends Screen {
    protected UiScreenBase(UiText title) {
        super(title.nativeComponent());
    }

    protected final UiMinecraftClient uiClientOrNull() {
        return this.minecraft != null ? UiMinecraftClient.from(this.minecraft) : null;
    }

    protected final UiMinecraftClient uiClient() {
        UiMinecraftClient client = uiClientOrNull();
        return client != null ? client : UiMinecraftClient.getInstance();
    }

    protected final void executeOnClient(Runnable runnable) {
        UiMinecraftClient client = uiClientOrNull();
        if (client != null) {
            client.execute(runnable);
        }
    }

    protected final boolean isCurrentScreenInstance() {
        UiMinecraftClient client = uiClientOrNull();
        return client != null && client.isCurrentScreen(this);
    }

    //? >=26.1 {
    @Override
    public final void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderScreen(UiRenderContext.from(context), mouseX, mouseY, delta);
    }
    //? } else {
    // @Override
    // public final void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
    //     renderScreen(UiRenderContext.from(context), mouseX, mouseY, delta);
    // }
    //? }

    protected void renderScreen(UiRenderContext context, int mouseX, int mouseY, float delta) {
        //? >=26.1 {
        super.extractRenderState(context.graphics(), mouseX, mouseY, delta);
        //? } else {
        /*super.render(context.graphics(), mouseX, mouseY, delta);
        *///? }
    }

    //? >=1.21.9 {
    @Override
    public final boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return onMouseClicked(UiMouseEvent.from(click), doubled);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        return onMouseDragged(UiMouseEvent.from(click), offsetX, offsetY);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent click) {
        return onMouseReleased(UiMouseEvent.from(click));
    }
    //? } else {
    /*@Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onMouseClicked(UiMouseEvent.from(mouseX, mouseY, button, hasShiftDown(), hasControlDown(), hasAltDown()), false);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
        return onMouseDragged(UiMouseEvent.from(mouseX, mouseY, button, hasShiftDown(), hasControlDown(), hasAltDown()), offsetX, offsetY);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onMouseReleased(UiMouseEvent.from(mouseX, mouseY, button, hasShiftDown(), hasControlDown(), hasAltDown()));
    }
    *///? }

    protected boolean onMouseClicked(UiMouseEvent click, boolean doubled) {
        return false;
    }

    protected boolean onMouseDragged(UiMouseEvent click, double offsetX, double offsetY) {
        return false;
    }

    protected boolean onMouseReleased(UiMouseEvent click) {
        return false;
    }
}
