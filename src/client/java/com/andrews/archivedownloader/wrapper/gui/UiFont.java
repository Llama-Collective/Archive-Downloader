package com.andrews.archivedownloader.wrapper.gui;

import net.minecraft.client.gui.Font;

public record UiFont(Font nativeFont) {
    public int width(String text) {
        return nativeFont.width(text);
    }

    public int lineHeight() {
        return nativeFont.lineHeight;
    }
}
