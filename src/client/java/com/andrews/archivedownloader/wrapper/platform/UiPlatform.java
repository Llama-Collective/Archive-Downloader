package com.andrews.archivedownloader.wrapper.platform;

//? >=1.21.11 {
 import net.minecraft.util.Util;
//? } else
//import net.minecraft.Util;

import java.net.URI;
import java.net.URISyntaxException;

public final class UiPlatform {
    private UiPlatform() {}

    public static void openUri(String url) {
        String normalized = normalizeUrlForOpen(url);
        Util.getPlatform().openUri(normalized);
    }

    private static String normalizeUrlForOpen(String url) {
        String safe = url != null ? url.trim() : "";
        if (safe.isEmpty()) {
            return safe;
        }

        if (isValidUri(safe)) {
            return safe;
        }

        int firstHash = safe.indexOf('#');
        if (firstHash >= 0) {
            String prefix = safe.substring(0, firstHash + 1);
            String fragment = safe.substring(firstHash + 1);
            // URI fragments cannot contain raw '#'; encode additional ones.
            fragment = fragment.replace("#", "%23");
            safe = prefix + fragment;
        }

        // Spaces are illegal unless encoded.
        safe = safe.replace(" ", "%20");
        return safe;
    }

    private static boolean isValidUri(String value) {
        try {
            new URI(value);
            return true;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
