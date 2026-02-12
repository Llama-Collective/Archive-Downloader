package com.andrews.archivedownloader.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
	private static final String KEY_OPEN_MENU = "key.archive-downloader.open_menu";
	private static final int DEFAULT_KEY = GLFW.GLFW_KEY_N;

	public static KeyMapping openMenuKey;

	public static void register() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				KEY_OPEN_MENU,
				InputConstants.Type.KEYSYM,
				DEFAULT_KEY,
				//? >=1.21.9 {
				 KeyMapping.Category.MISC
				//? } else {
				/*KeyMapping.CATEGORY_MISC
				*///? }

		));
	}

	private ModKeybindings() {}
}
