package com.andrews.archivedownloader;

import com.andrews.archivedownloader.gui.ArchiveDownloaderScreen;
import com.andrews.archivedownloader.keybind.ModKeybindings;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class ArchiveDownloaderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeybindings.register();
		registerScreenToggleHandler();
	}

	private static void registerScreenToggleHandler() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ModKeybindings.openMenuKey.isDown()) {
				toggleArchiveDownloaderScreen(Minecraft.getInstance());
			}
		});
	}

	private static void toggleArchiveDownloaderScreen(Minecraft client) {
		if (client.screen instanceof ArchiveDownloaderScreen) {
			client.setScreen(null);
		} else {
			client.setScreen(new ArchiveDownloaderScreen());
		}
	}
}
