package com.andrews.archivedownloader.wrapper.input;

import com.mojang.blaze3d.platform.InputConstants;
//? >=26.3 {
import org.lwjgl.sdl.SDLMouse;
//? } else {
//import org.lwjgl.glfw.GLFW;
//? }

public final class UiInput {
    private UiInput() {}

    //? >=26.3 {
    public static final int KEY_LEFT_SUPER = InputConstants.KEY_LGUI;
    public static final int KEY_RIGHT_SUPER = InputConstants.KEY_RGUI;
    //? } else {
    //public static final int KEY_LEFT_SUPER = GLFW.GLFW_KEY_LEFT_SUPER;
    //public static final int KEY_RIGHT_SUPER = GLFW.GLFW_KEY_RIGHT_SUPER;
    //? }

    public static boolean isKeyDown(long windowHandle, int key) {
        //? >=26.3 {
        return InputConstants.isKeyDown(key);
        //? } else {
        //return GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
        //? }
    }

    public static boolean isLeftMouseDown(long windowHandle) {
        //? >=26.3 {
        return (SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, null)
                & SDLMouse.SDL_BUTTON_LMASK) != 0;
        //? } else {
        //return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        //? }
    }

    /** Keep widget button numbers stable across GLFW and SDL. */
    public static int normalizeMouseButton(int button) {
        //? >=26.3 {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
        //? } else {
        //return button;
        //? }
    }
}
