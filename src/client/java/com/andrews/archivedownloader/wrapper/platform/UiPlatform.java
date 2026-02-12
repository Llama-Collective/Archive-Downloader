package com.andrews.archivedownloader.wrapper.platform;

//? >=1.21.11 {
 import net.minecraft.util.Util;
//? } else
//import net.minecraft.Util;

public final class UiPlatform {
    private UiPlatform() {}

    public static void openUri(String url) {
        Util.getPlatform().openUri(url);
    }
}
