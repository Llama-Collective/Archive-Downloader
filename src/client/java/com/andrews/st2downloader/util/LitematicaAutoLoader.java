package com.andrews.st2downloader.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class LitematicaAutoLoader {
	private LitematicaAutoLoader() {}

	public static boolean loadIntoWorld(Path schematicPath) {
		if (!isAvailable()) {
			return false;
		}
		if (schematicPath == null || !Files.exists(schematicPath)) {
			return false;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null) {
			return false;
		}

		try {
			fi.dy.masa.litematica.schematic.LitematicaSchematic schematic =
				fi.dy.masa.litematica.data.SchematicHolder.getInstance().getOrLoad(schematicPath);
			if (schematic == null) {
				System.err.println("Failed to load schematic from " + schematicPath);
				return false;
			}

			String displayName = schematic.getMetadata() != null
				? schematic.getMetadata().getName()
				: null;
			if (displayName == null || displayName.isBlank()) {
				Path fileName = schematicPath.getFileName();
				String fallbackName = fileName != null ? fileName.toString() : schematicPath.toString();
				displayName = stripExtension(fallbackName);
			}

			BlockPos origin = client.player.getBlockPos();
			fi.dy.masa.litematica.schematic.placement.SchematicPlacement placement =
				fi.dy.masa.litematica.schematic.placement.SchematicPlacement.createFor(schematic, origin, displayName, true, true);

			var placementManager = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager();
			placementManager.addSchematicPlacement(placement, true);
			placementManager.setSelectedSchematicPlacement(placement);
			return true;
		} catch (NoClassDefFoundError e) {
			return false;
		} catch (Exception e) {
			System.err.println("Failed to auto-load schematic into Litematica: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("litematica");
	}

	private static String stripExtension(String name) {
		int dotIndex = name.lastIndexOf('.');
		return dotIndex > 0 ? name.substring(0, dotIndex) : name;
	}
}
