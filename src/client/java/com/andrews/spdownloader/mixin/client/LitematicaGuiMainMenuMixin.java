package com.andrews.spdownloader.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.andrews.spdownloader.gui.LitematicDownloaderScreen;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.selection.SelectionMode;

@Mixin(value = GuiMainMenu.class, remap = false)
public abstract class LitematicaGuiMainMenuMixin extends GuiBase {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void st2$addArchiveButton(CallbackInfo ci) {
        final int width = st2$getButtonWidth();
        boolean syncmaticaPresent = FabricLoader.getInstance().isModLoaded("syncmatica");
        boolean ST2DownloaderPresent = FabricLoader.getInstance().isModLoaded("archive-downloader");
        if (syncmaticaPresent) {
            // Syncmatica adds two buttons in the third column; stack ours beneath them
            final int x = 52 + 2 * width;
            final int y = 30 + 88 + (ST2DownloaderPresent ? 22 : 0);
            st2$createArchiveButton(x, y, width);
        } else {
            // Otherwise place in an extra column to the right
            final int x = 52 + 2 * width;
            final int y = 30 + (ST2DownloaderPresent ? 22 : 0);
            st2$createArchiveButton(x, y, width);
        }
    }

    @Unique
    private void st2$createArchiveButton(int x, int y, int width) {
        String label = "Share-Projects Browser";
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, label, null, new String[] { "Open the Share-Projects browser" });
        addButton(button, (btn, mouseButton) -> MinecraftClient.getInstance().setScreen(new LitematicDownloaderScreen()));
    }

    @Unique
    private int st2$getButtonWidth() {
        int width = 0;
        for (SelectionMode mode : SelectionMode.values()) {
            String label = StringUtils.translate("litematica.gui.button.area_selection_mode", mode.getDisplayName());
            width = Math.max(width, getStringWidth(label) + 10);
        }
        // Fallback width for our button text
        width = Math.max(width, getStringWidth("Archive Browser") + 30);
        return width;
    }

}
