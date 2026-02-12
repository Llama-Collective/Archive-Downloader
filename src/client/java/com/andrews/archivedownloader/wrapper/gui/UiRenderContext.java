package com.andrews.archivedownloader.wrapper.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Thin wrapper around Minecraft's GuiGraphics so widgets can avoid direct game API coupling.
 */
public record UiRenderContext(GuiGraphics graphics) {
    public static UiRenderContext from(GuiGraphics graphics) {
        return new UiRenderContext(graphics);
    }
}
