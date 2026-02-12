package com.andrews.archivedownloader.wrapper.text;

import net.minecraft.network.chat.Component;

public record UiText(Component nativeComponent) {
    public static UiText of(String text) {
        return new UiText(Component.nullToEmpty(text));
    }

    public static UiText literal(String text) {
        return new UiText(Component.literal(text));
    }

    public String string() {
        return nativeComponent.getString();
    }
}
