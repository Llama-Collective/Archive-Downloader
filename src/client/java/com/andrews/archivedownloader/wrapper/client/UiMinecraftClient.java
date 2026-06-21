package com.andrews.archivedownloader.wrapper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.render.UiTextureId;
import com.andrews.archivedownloader.wrapper.toast.UiBasicToast;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UiMinecraftClient {
    private final Minecraft nativeClient;

    private UiMinecraftClient(Minecraft nativeClient) {
        this.nativeClient = nativeClient;
    }

    public static UiMinecraftClient getInstance() {
        return new UiMinecraftClient(Minecraft.getInstance());
    }

    public static UiMinecraftClient from(Minecraft nativeClient) {
        return new UiMinecraftClient(nativeClient);
    }

    public Minecraft nativeClient() {
        return nativeClient;
    }

    public Font font() {
        return nativeClient.font;
    }

    public UiFont uiFont() {
        return new UiFont(nativeClient.font);
    }

    public long windowHandle() {
        //? >=1.21.9 {
         return nativeClient.getWindow() != null ? nativeClient.getWindow().handle() : 0L;
        //? } else {
        /*return nativeClient.getWindow() != null ? nativeClient.getWindow().getWindow() : 0L;
        *///? }
    }

    public int guiScaledWidth() {
        return nativeClient.getWindow() != null ? nativeClient.getWindow().getGuiScaledWidth() : 0;
    }

    public int guiScaledHeight() {
        return nativeClient.getWindow() != null ? nativeClient.getWindow().getGuiScaledHeight() : 0;
    }

    public void execute(Runnable runnable) {
        nativeClient.execute(runnable);
    }

    public boolean isCurrentScreen(Object nativeScreen) {
        //? >=26.2 {
        return nativeClient.gui.screen() == nativeScreen;
        //? } else {
        // return nativeClient.screen == nativeScreen;
        //? }
    }

    public void playButtonDownSound(Button button) {
        if (button != null && nativeClient.getSoundManager() != null) {
            button.playDownSound(nativeClient.getSoundManager());
        }
    }

    public void showBasicToast(String title, String body) {
        //? >=26.2 {
        FormattedText titleText = FormattedText.of(title != null ? title : "");
        FormattedText bodyText = (body != null && !body.isBlank()) ? FormattedText.of(body) : null;
        nativeClient.execute(() -> nativeClient.gui.toastManager().addToast(new UiBasicToast(titleText, bodyText)));
        //? } else if >=1.21.2 {
        // if (nativeClient.getToastManager() == null) {
        //     return;
        // }
        // FormattedText titleText = FormattedText.of(title != null ? title : "");
        // FormattedText bodyText = (body != null && !body.isBlank()) ? FormattedText.of(body) : null;
        // nativeClient.execute(() -> nativeClient.getToastManager().addToast(new UiBasicToast(titleText, bodyText)));
        //? } else {
        /*if (nativeClient.getToasts() == null) {
            return;
        }
        FormattedText titleText = FormattedText.of(title != null ? title : "");
        FormattedText bodyText = (body != null && !body.isBlank()) ? FormattedText.of(body) : null;
        nativeClient.execute(() -> nativeClient.getToasts().addToast(new UiBasicToast(titleText, bodyText)));
        *///? }
    }

    public UiTextureId registerDynamicTexture(String pathPrefix, NativeImage image) {
        CompletableFuture<UiTextureId> future = new CompletableFuture<>();
        nativeClient.execute(() -> {
            try {
                future.complete(registerDynamicTextureNow(pathPrefix, image));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
    }

    private UiTextureId registerDynamicTextureNow(String pathPrefix, NativeImage image) {
        String id = UUID.randomUUID().toString().replace("-", "");

        //? >=1.21 {
         Identifier texId = Identifier.fromNamespaceAndPath("archivedownloader", pathPrefix + "/" + id);
        //? } else {
        /*Identifier texId = new Identifier("archivedownloader", pathPrefix + "/" + id);
        *///? }

        //? >=1.21.5 {
         nativeClient.getTextureManager().register(texId, new DynamicTexture(() -> pathPrefix, image));
        //? } else {
        /*nativeClient.getTextureManager().register(texId, new DynamicTexture(image));
        *///? }
        return new UiTextureId(texId);
    }
}
