package com.andrews.archivedownloader.mixin.client;

import com.andrews.archivedownloader.gui.ArchiveDownloaderScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.selection.SelectionMode;

@Mixin(GuiMainMenu.class)
public abstract class LitematicaGuiMainMenuMixin extends GuiBase {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void archive$addArchiveButton(CallbackInfo ci) {
        final int width = archive$getButtonWidth();
        boolean syncmaticaPresent = FabricLoader.getInstance().isModLoaded("syncmatica")
                || FabricLoader.getInstance().isModLoaded("syncmatica_r");
        if (syncmaticaPresent) {
            // Syncmatica adds two buttons in the third column; stack ours beneath them
            final int x = 52 + 2 * width;
            final int y = 30 + 88;
            archive$createArchiveButton(x, y, width);
        } else {
            // Otherwise place in an extra column to the right
            final int x = 52 + 2 * width;
            final int y = 30;
            archive$createArchiveButton(x, y, width);
        }
    }

    @Unique
    private void archive$createArchiveButton(int x, int y, int width) {
        String label = "Archive Browser";
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, label, null, new String[] { "Open the Llama Collective archive browser" });
        addButton(button, (btn, mouseButton) -> Minecraft.getInstance().setScreen(new ArchiveDownloaderScreen()));
    }

    @Unique
    private int archive$getButtonWidth() {
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
