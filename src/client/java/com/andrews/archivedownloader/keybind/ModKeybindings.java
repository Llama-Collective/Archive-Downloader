package com.andrews.archivedownloader.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
	private static final String KEY_OPEN_MENU = "key.archive-downloader.open_menu";
	private static final int DEFAULT_KEY = GLFW.GLFW_KEY_N;

	public static KeyBinding openMenuKey;

	public static void register() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				KEY_OPEN_MENU,
				InputUtil.Type.KEYSYM,
				DEFAULT_KEY,
				KeyBinding.MISC_CATEGORY
		));
	}

	private ModKeybindings() {}
}
