package com.andrews.st2downloader.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.andrews.st2downloader.config.ServerDictionary;
import com.andrews.st2downloader.config.ServerDictionary.ServerEntry;
import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.models.GlobalTag;
import com.andrews.st2downloader.network.ArchiveNetworkManager;

/**
 * Shared helper for consistent tag colors across widgets.
 */
public final class TagUtil {

    private TagUtil() {
    }

    public static int getTagColor(String tag) {
        return getTagColor(tag, null);
    }

    public static int getTagColor(String tag, ServerEntry server) {
        if (tag == null) {
            return UITheme.Colors.BUTTON_BG;
        }
        ServerEntry targetServer = normalizeServer(server);
        GlobalTag globalTag = findGlobalTag(tag, targetServer);
        Integer color = resolveTagColor(globalTag);
        if (color == null) {
            color = resolveTagColor(findMatchingDefaultTag(tag));
        }
        return color != null ? color : UITheme.Colors.BUTTON_BG;
    }

    public static List<String> orderTags(String[] tags) {
        return orderTags(tags, null);
    }

    public static List<String> orderTags(String[] tags, ServerEntry server) {
        List<String> tagList = tags != null ? Arrays.asList(tags) : List.of();
        return orderTags(tagList, server);
    }

    public static List<String> orderTags(List<String> tags) {
        return orderTags(tags, null);
    }

    public static List<String> orderTags(List<String> tags, ServerEntry server) {
        if (tags == null) return List.of();
        ServerEntry targetServer = normalizeServer(server);
        List<GlobalTag> globalTags = ArchiveNetworkManager.getCachedGlobalTags(targetServer);
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GlobalTag globalTag : globalTags) {
            if (globalTag == null || globalTag.name() == null) continue;
            for (String tag : tags) {
                if (tag == null) continue;
                String lower = tag.trim().toLowerCase(Locale.ROOT);
                if (matchesGlobalTag(globalTag, tag) && seen.add(lower)) {
                    ordered.add(tag);
                    break;
                }
            }
        }
        for (String tag : tags) {
            if (tag == null) continue;
            String lower = tag.trim().toLowerCase(Locale.ROOT);
            if (seen.add(lower)) {
                ordered.add(tag);
            }
        }
        return ordered;
    }

    public static String formatTagLabel(String tag) {
        return formatTagLabel(tag, null);
    }

    public static String formatTagLabel(String tag, ServerEntry server) {
        if (tag == null) {
            return "";
        }
        ServerEntry targetServer = normalizeServer(server);
        GlobalTag globalTag = findGlobalTag(tag, targetServer);
        String trimmed = stripLeadingEmoji(tag.trim());
        if (globalTag != null) {
            String label = globalTag.name() != null && !globalTag.name().isBlank()
                ? globalTag.name().trim()
                : trimmed;
            String emoji = globalTag.emoji();
            // strip 65039 VARIATION SELECTOR-16 from emoji if present
            if (emoji != null && emoji.endsWith("\uFE0F")) {
                emoji = emoji.substring(0, emoji.length() - 1);
            }
            if (emoji != null && !emoji.isBlank()) {
                if (label.startsWith(emoji)) {
                    return label;
                }
                return emoji + " " + label;
            }
            return label;
        }
        return trimmed;
    }

    private static GlobalTag findGlobalTag(String tag, ServerEntry server) {
        if (tag == null) {
            return null;
        }
        ServerEntry targetServer = normalizeServer(server);
        for (GlobalTag candidate : ArchiveNetworkManager.getCachedGlobalTags(targetServer)) {
            if (matchesGlobalTag(candidate, tag)) {
                return candidate;
            }
        }
        return null;
    }

    private static GlobalTag findMatchingDefaultTag(String tag) {
        for (GlobalTag candidate : ArchiveNetworkManager.getDefaultGlobalTags()) {
            if (matchesGlobalTag(candidate, tag)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean matchesGlobalTag(GlobalTag candidate, String tagValue) {
        if (candidate == null || candidate.name() == null || tagValue == null) {
            return false;
        }
        String normalizedTag = tagValue.trim().toLowerCase(Locale.ROOT);
        String candidateName = candidate.name().trim().toLowerCase(Locale.ROOT);
        if (candidateName.equals(normalizedTag)) {
            return true;
        }
        String emoji = candidate.emoji();
        if (emoji != null && !emoji.isBlank()) {
            String emojiLower = emoji.trim().toLowerCase(Locale.ROOT);
            if (!emojiLower.isEmpty() && normalizedTag.startsWith(emojiLower)) {
                String stripped = normalizedTag.substring(emojiLower.length()).trim();
                return stripped.equals(candidateName);
            }
        }
        return false;
    }

    private static String stripLeadingEmoji(String value) {
        if (value == null) {
            return "";
        }
        int firstCodePoint = value.codePointAt(0);
        if (Character.getType(firstCodePoint) == Character.OTHER_SYMBOL) {
            return value.substring(value.offsetByCodePoints(0, 1)).trim();
        }
        return value;
    }

    private static Integer resolveTagColor(GlobalTag tag) {
        if (tag == null) {
            return null;
        }
        Long color = tag.colorMod();
        if (color == null) {
            return null;
        }
        // Preserve the 32-bit ARGB bit pattern even when the JSON value exceeds Integer.MAX_VALUE
        return (int) (long) color;
    }

    private static ServerEntry normalizeServer(ServerEntry server) {
        return server != null ? server : ServerDictionary.getDefaultServer();
    }
}
