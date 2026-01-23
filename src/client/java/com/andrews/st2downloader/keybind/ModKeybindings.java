package com.andrews.st2downloader.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
	private static final String KEY_OPEN_MENU = "key.st2-downloader.open_menu";
	private static final int DEFAULT_KEY = GLFW.GLFW_KEY_N;

	public static KeyMapping openMenuKey;

	public static void register() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				KEY_OPEN_MENU,
				InputConstants.Type.KEYSYM,
				DEFAULT_KEY,
				KeyMapping.Category.MISC
		));
	}

	private ModKeybindings() {}
}
