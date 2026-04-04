package com.andrews.archivedownloader.wrapper.gui;

//? >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
 /*import net.minecraft.client.gui.GuiGraphics;
*///? }

/**
 * Thin wrapper around Minecraft's GuiGraphics so widgets can avoid direct game API coupling.
 */

//? >=26.1 {
public record UiRenderContext(GuiGraphicsExtractor graphics) {
    public static UiRenderContext from(GuiGraphicsExtractor graphics) {
        return new UiRenderContext(graphics);
    }
}
//? } else {
 /*public record UiRenderContext(GuiGraphics graphics) {
     public static UiRenderContext from(GuiGraphics graphics) {
         return new UiRenderContext(graphics);
     }
 }
*///? }