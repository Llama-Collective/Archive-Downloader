package com.andrews.archivedownloader.wrapper.gui;

import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;

public interface UiEventListener {
    default boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        return false;
    }

    default boolean mouseDragged(UiMouseEvent click, double offsetX, double offsetY) {
        return false;
    }

    default boolean mouseReleased(UiMouseEvent click) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    default void setFocused(boolean focused) {
    }

    default boolean isFocused() {
        return false;
    }
}
