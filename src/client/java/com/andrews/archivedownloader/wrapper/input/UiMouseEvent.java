package com.andrews.archivedownloader.wrapper.input;

//? >=1.21.9
 import net.minecraft.client.input.MouseButtonEvent;

/**
 * Normalized mouse input wrapper so UI widgets do not depend on Minecraft events directly.
 */
public record UiMouseEvent(
        double x,
        double y,
        int button,
        boolean shiftDown,
        boolean controlDown,
        boolean altDown) {

    //? >=1.21.9 {
        public static UiMouseEvent from(MouseButtonEvent event) {
            return new UiMouseEvent(
                    event.x(),
                    event.y(),
                    event.button(),
                    event.hasShiftDown(),
                    event.hasControlDown(),
                    event.hasAltDown());
        }
    //? }

    public static UiMouseEvent from(double x, double y, int button, boolean shiftDown, boolean controlDown, boolean altDown) {
        return new UiMouseEvent(x, y, button, shiftDown, controlDown, altDown);
    }

}
