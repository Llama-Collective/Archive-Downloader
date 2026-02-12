package com.andrews.archivedownloader.util;

//? <1.21.5
//import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;

import net.fabricmc.loader.api.FabricLoader;

public final class LitematicaAutoLoader {
	private LitematicaAutoLoader() {}

	public static boolean loadIntoWorld(Path schematicPath) {
		if (!isAvailable()) {
			return false;
		}
		if (schematicPath == null || !Files.exists(schematicPath)) {
			return false;
		}

		UiMinecraftClient client = UiMinecraftClient.getInstance();
		if (client.nativeClient() == null || client.nativeClient().player == null || client.nativeClient().level == null) {
			return false;
		}

		try {
			//? >=1.21.5 {
			fi.dy.masa.litematica.schematic.LitematicaSchematic schematic =
				fi.dy.masa.litematica.data.SchematicHolder.getInstance().getOrLoad(schematicPath);
			//? } else {
			/*File file = schematicPath.toFile();
			fi.dy.masa.litematica.schematic.LitematicaSchematic schematic =
					fi.dy.masa.litematica.data.SchematicHolder.getInstance().getOrLoad(file);
			*///? }
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

			var origin = client.nativeClient().player.blockPosition();
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

	public static Path getLitematicaSchematicBaseDirectory() {
		if (!isAvailable()) {
			return null;
		}
		try {
			//? >=1.21.5 {
			return fi.dy.masa.litematica.data.DataManager.getSchematicsBaseDirectory();
			//? } else {
			/*File dir = fi.dy.masa.litematica.data.DataManager.getSchematicsBaseDirectory();
			return dir != null ? dir.toPath() : null;
			*///? }
		} catch (NoClassDefFoundError e) {
			return null;
		} catch (Exception e) {
			System.err.println("Failed to get Litematica schematics directory: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}
