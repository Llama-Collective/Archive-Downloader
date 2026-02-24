package com.andrews.archivedownloader.util;

import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WorldEditAutoLoader {
    private WorldEditAutoLoader() {
    }

    public static boolean loadIntoWorld(Path schematicPath) {
        if (!isAvailable()) {
            return false;
        }
        if (schematicPath == null || !Files.isRegularFile(schematicPath)) {
            return false;
        }
        if (!isSupportedSchematic(schematicPath)) {
            return false;
        }

        try {
            Minecraft client = UiMinecraftClient.getInstance().nativeClient();
            if (client == null || client.player == null || client.getSingleplayerServer() == null) {
                return false;
            }

            PlayerList playerList = client.getSingleplayerServer().getPlayerList();
            if (playerList == null) {
                return false;
            }
            ServerPlayer serverPlayer = playerList.getPlayer(client.player.getUUID());
            if (serverPlayer == null) {
                return false;
            }

            Object clipboard = readClipboard(schematicPath);
            if (clipboard == null) {
                return false;
            }
            return setPlayerClipboard(serverPlayer, clipboard);
        } catch (NoClassDefFoundError e) {
            return false;
        } catch (Exception e) {
            System.err.println("Failed to auto-load schematic into WorldEdit: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("worldedit");
    }

    private static Object readClipboard(Path schematicPath) throws Exception {
        Class<?> clipboardFormatsClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
        Class<?> clipboardFormatClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        Class<?> clipboardReaderClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader");

        Object format = clipboardFormatsClass.getMethod("findByFile", File.class).invoke(null, schematicPath.toFile());
        if (format == null) {
            return null;
        }

        Object reader = null;
        try (FileInputStream input = new FileInputStream(schematicPath.toFile())) {
            reader = clipboardFormatClass.getMethod("getReader", java.io.InputStream.class).invoke(format, input);
            return clipboardReaderClass.getMethod("read").invoke(reader);
        } finally {
            closeQuietly(reader);
        }
    }

    private static boolean setPlayerClipboard(ServerPlayer serverPlayer, Object clipboard) throws Exception {
        Class<?> fabricAdapterClass = Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
        Object actor = fabricAdapterClass
            .getMethod("adaptPlayer", ServerPlayer.class)
            .invoke(null, serverPlayer);

        Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
        Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);
        Object sessionManager = worldEditClass.getMethod("getSessionManager").invoke(worldEdit);

        Class<?> sessionOwnerClass = Class.forName("com.sk89q.worldedit.session.SessionOwner");
        Object localSession = sessionManager.getClass().getMethod("get", sessionOwnerClass).invoke(sessionManager, actor);

        Class<?> clipboardClass = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
        Class<?> clipboardHolderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
        Object clipboardHolder = clipboardHolderClass.getConstructor(clipboardClass).newInstance(clipboard);

        localSession.getClass().getMethod("setClipboard", clipboardHolderClass).invoke(localSession, clipboardHolder);
        return true;
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof AutoCloseable autoCloseable) {
                autoCloseable.close();
            } else {
                closeable.getClass().getMethod("close").invoke(closeable);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isSupportedSchematic(Path schematicPath) {
        String name = schematicPath.getFileName() != null
            ? schematicPath.getFileName().toString().toLowerCase(Locale.ROOT)
            : "";
        return name.endsWith(".schem") || name.endsWith(".schematic");
    }
}
