package com.andrews.archivedownloader.util;

import com.andrews.archivedownloader.models.ArchiveReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReferenceUtils {
	private static final String REF_TYPE_DISCORD_LINK = "discordLink";
	private static final String REF_TYPE_DICTIONARY_TERM = "dictionaryTerm";
	private static final String REF_TYPE_ARCHIVED_POST = "archivedPost";
	private static final String REF_TYPE_USER_MENTION = "userMention";
	private static final String REF_TYPE_CHANNEL_MENTION = "channelMention";
	private static final String DISCORD_LINK_PATH_PREFIX = "/discord-link";

	private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)(?:\\s+\"([^\"]+)\")?\\)");
	private static final Pattern DISCORD_LINK_PATTERN = Pattern.compile("https?://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/channels/(\\d+)/(\\d+)(?:/(\\d+))?");

	private ReferenceUtils() {
	}

	public static String transformOutputWithReferencesForMod(
		String text,
		List<ArchiveReference> references,
		Function<String, String> dictionaryTooltipLookup
	) {
		if (text == null || text.isEmpty() || references == null || references.isEmpty()) {
			return text != null ? text : "";
		}

		List<ReferenceMatch> matches = findMatchesWithinText(text, references);
		if (matches.isEmpty()) {
			return text;
		}

		List<ReferenceMatch> filtered = new ArrayList<>();
		for (ReferenceMatch match : matches) {
			if (shouldIncludeMatch(text, text.substring(match.start(), match.end()), match.start(), match.end())) {
				filtered.add(match);
			}
		}
		if (filtered.isEmpty()) {
			return text;
		}

		filtered.sort(Comparator.comparingInt(ReferenceMatch::start));
		List<List<ReferenceMatch>> grouped = new ArrayList<>();
		List<ReferenceMatch> currentGroup = new ArrayList<>();
		int lastEnd = -1;
		for (ReferenceMatch match : filtered) {
			if (match.start() >= lastEnd) {
				if (!currentGroup.isEmpty()) {
					grouped.add(currentGroup);
				}
				currentGroup = new ArrayList<>();
				currentGroup.add(match);
			} else {
				currentGroup.add(match);
			}
			lastEnd = Math.max(lastEnd, match.end());
		}
		if (!currentGroup.isEmpty()) {
			grouped.add(currentGroup);
		}

		List<ReferenceMatch> deduped = new ArrayList<>();
		for (List<ReferenceMatch> group : grouped) {
			ReferenceMatch longest = null;
			for (ReferenceMatch match : group) {
				if (longest == null || (match.end() - match.start()) > (longest.end() - longest.start())) {
					longest = match;
				}
			}
			if (longest != null) {
				deduped.add(longest);
			}
		}

		List<RegexMatch> hyperlinks = findRegexMatches(text, MARKDOWN_LINK_PATTERN);
		Set<String> dictionarySeen = new HashSet<>();
		StringBuilder out = new StringBuilder();
		int currentIndex = 0;
		for (ReferenceMatch match : deduped) {
			boolean isHeader = isMatchInMarkdownHeader(text, match.start());
			RegexMatch hyperlink = findContainingHyperlink(hyperlinks, match.start(), match.end());
			int fullStart = hyperlink != null ? hyperlink.start() : match.start();
			int fullEnd = hyperlink != null ? hyperlink.end() : match.end();
			String fullMatchedText = text.substring(fullStart, fullEnd);
			String hyperlinkText = hyperlink != null && !hyperlink.groups().isEmpty() ? hyperlink.groups().get(0) : null;
			String hyperlinkURL = hyperlink != null && hyperlink.groups().size() > 1 ? hyperlink.groups().get(1) : null;
			String hyperlinkTitle = hyperlink != null && hyperlink.groups().size() > 2 ? hyperlink.groups().get(2) : null;

			String replacement = replaceReferenceForMod(
				match.reference(),
				fullMatchedText,
				isHeader,
				hyperlink != null,
				hyperlinkText,
				hyperlinkURL,
				hyperlinkTitle,
				dictionarySeen,
				dictionaryTooltipLookup
			);
			if (replacement == null) {
				continue;
			}

			if (currentIndex < fullStart) {
				out.append(text, currentIndex, fullStart);
			}
			out.append(replacement);
			currentIndex = fullEnd;
		}

		if (currentIndex < text.length()) {
			out.append(text.substring(currentIndex));
		}
		return out.toString();
	}

	public static String buildReferenceLabel(ArchiveReference reference) {
		if (reference == null) {
			return "";
		}
		String type = safeTrim(reference.type());
		if (REF_TYPE_DICTIONARY_TERM.equals(type)) {
			String term = safeTrim(reference.term());
			if (!term.isEmpty()) {
				return term;
			}
			if (reference.matches() != null && !reference.matches().isEmpty()) {
				return safeTrim(reference.matches().get(0));
			}
			return safeTrim(reference.id());
		}
		if (REF_TYPE_ARCHIVED_POST.equals(type)) {
			String code = safeTrim(reference.code());
			String name = safeTrim(reference.name());
			if (!code.isEmpty() && !name.isEmpty()) {
				return code + " " + name;
			}
			return !code.isEmpty() ? code : name;
		}
		if (REF_TYPE_DISCORD_LINK.equals(type)) {
			String serverName = safeTrim(reference.serverName());
			return !serverName.isEmpty() ? "Discord message (" + serverName + ")" : "Discord message";
		}
		if (REF_TYPE_CHANNEL_MENTION.equals(type)) {
			String channelName = safeTrim(reference.channelName());
			return !channelName.isEmpty() ? "#" + channelName : "Unknown Channel";
		}
		if (REF_TYPE_USER_MENTION.equals(type)) {
			if (reference.user() == null) {
				return "Unknown User";
			}
			String displayName = safeTrim(reference.user().displayName());
			String username = safeTrim(reference.user().username());
			return !displayName.isEmpty() ? displayName : (!username.isEmpty() ? username : "Unknown User");
		}
		if (reference.matches() != null && !reference.matches().isEmpty()) {
			return safeTrim(reference.matches().get(0));
		}
		return safeTrim(reference.url());
	}

	public static String buildReferenceUrl(ArchiveReference reference, String dictionaryTerm) {
		if (reference == null) {
			return "";
		}
		String type = safeTrim(reference.type());
		if (REF_TYPE_DICTIONARY_TERM.equals(type)) {
			String id = safeTrim(reference.id());
			if (id.isEmpty()) {
				return "";
			}
			return "/dictionary/" + encodePathSegment(id);
		}
		if (REF_TYPE_ARCHIVED_POST.equals(type)) {
			String id = safeTrim(reference.id());
			if (id.isEmpty()) {
				return "";
			}
			return "/archive/" + encodePathSegment(id);
		}
		if (REF_TYPE_CHANNEL_MENTION.equals(type)) {
			return safeTrim(reference.channelURL());
		}
		return safeTrim(reference.url());
	}

	private static String buildReferenceUrlForMod(ArchiveReference reference, String dictionaryTerm) {
		if (reference == null) {
			return "";
		}
		String type = safeTrim(reference.type());
		if (REF_TYPE_DICTIONARY_TERM.equals(type)) {
			String id = safeTrim(reference.id());
			if (id.isEmpty()) {
				return "";
			}
			// Use canonical ID path for in-mod links to avoid slug round-tripping.
			return "/dictionary/" + encodePathSegment(id);
		}
		if (REF_TYPE_ARCHIVED_POST.equals(type)) {
			String id = safeTrim(reference.id());
			if (!id.isEmpty()) {
				// Use direct post id for in-mod navigation.
				return "/archive/" + encodePathSegment(id);
			}
			return "";
		}
		return buildReferenceUrl(reference, dictionaryTerm);
	}

	public static String buildDictionarySlug(String id, String term) {
		String safeId = safeTrim(id);
		String primary = slugifyName(term);
		if (primary.isEmpty()) {
			return safeId;
		}
		return safeId + "-" + primary;
	}

	private static String replaceReferenceForMod(
		ArchiveReference reference,
		String matchedText,
		boolean isHeader,
		boolean isWithinHyperlink,
		String hyperlinkText,
		String hyperlinkURL,
		String hyperlinkTitle,
		Set<String> dictionarySeen,
		Function<String, String> dictionaryTooltipLookup
	) {
		if (reference == null || isHeader) {
			return null;
		}
		String type = safeTrim(reference.type());
		if (REF_TYPE_DICTIONARY_TERM.equals(type)) {
			String dictionaryId = safeTrim(reference.id());
			if (dictionaryId.isEmpty() || dictionarySeen.contains(dictionaryId) || isWithinHyperlink) {
				return null;
			}
			String tooltip = dictionaryTooltipLookup != null ? safeTrim(dictionaryTooltipLookup.apply(dictionaryId)) : "";
			String safeTitle = sanitizeMarkdownLinkTitle(tooltip);
			String newURL = buildReferenceUrlForMod(reference, safeTrim(reference.term()));
			if (newURL.isEmpty()) {
				return null;
			}
			dictionarySeen.add(dictionaryId);
			if (!safeTitle.isEmpty()) {
				return "[" + matchedText + "](" + newURL + " \"" + safeTitle + "\")";
			}
			return "[" + matchedText + "](" + newURL + ")";
		}

		if (REF_TYPE_ARCHIVED_POST.equals(type)) {
			String newURL = buildReferenceUrlForMod(reference, "");
			if (newURL.isEmpty()) {
				return null;
			}
			String safeTitle = sanitizeMarkdownLinkTitle(safeTrim(reference.name()));
			if (isWithinHyperlink) {
				String linkText = hyperlinkText != null ? hyperlinkText : "";
				if (!safeTrim(reference.code()).isEmpty() && linkText.toUpperCase(Locale.ROOT).equals(reference.code())) {
					if (!safeTitle.isEmpty()) {
						return "[" + linkText + " " + safeTitle + "](" + newURL + ")";
					}
					return "[" + linkText + "](" + newURL + ")";
				}
				String title = !safeTitle.isEmpty() ? safeTitle : sanitizeMarkdownLinkTitle(linkText);
				if (!title.isEmpty()) {
					return "[" + linkText + "](" + newURL + " \"" + title + "\")";
				}
				return "[" + linkText + "](" + newURL + ")";
			}
			if (DISCORD_LINK_PATTERN.matcher(matchedText.trim()).matches()) {
				String code = safeTrim(reference.code());
				return code + (!safeTitle.isEmpty() ? " " + safeTitle : "");
			}
			if (!safeTitle.isEmpty()) {
				return "[" + matchedText + "](" + newURL + " \"" + safeTitle + "\")";
			}
			return "[" + matchedText + "](" + newURL + ")";
		}

		if (REF_TYPE_DISCORD_LINK.equals(type)) {
			String rendered = matchedText;
			if (!isWithinHyperlink) {
				String url = safeTrim(reference.url());
				if (url.isEmpty() && hyperlinkURL != null) {
					url = hyperlinkURL;
				}
				if (!url.isEmpty()) {
					String serverName = safeTrim(reference.serverName());
					String tooltip = "Message " + (!serverName.isEmpty() ? "on " + serverName + " Discord" : "on Discord");
					String linkTarget = buildDiscordLinkTargetForMod(reference, url);
					rendered = "[[Discord Link]](" + linkTarget + " \"" + sanitizeMarkdownLinkTitle(tooltip) + "\")";
				}
			}
			return rendered;
		}

		if (REF_TYPE_USER_MENTION.equals(type)) {
			if (reference.user() == null) {
				return "Unknown User";
			}
			String displayName = safeTrim(reference.user().displayName());
			String username = safeTrim(reference.user().username());
			return !displayName.isEmpty() ? displayName : (!username.isEmpty() ? username : "Unknown User");
		}

		if (REF_TYPE_CHANNEL_MENTION.equals(type)) {
			if (isWithinHyperlink) {
				return null;
			}
			String channelName = safeTrim(reference.channelName());
			String channelUrl = safeTrim(reference.channelURL());
			if (!channelName.isEmpty() && !channelUrl.isEmpty()) {
				return "[#" + channelName + "](" + channelUrl + ")";
			}
			return "[Unknown Channel](# \"ID: " + safeTrim(reference.channelID()) + "\")";
		}

		return null;
	}

	private static List<ReferenceMatch> findMatchesWithinText(String text, List<ArchiveReference> references) {
		List<ReferenceMatch> results = new ArrayList<>();
		for (ArchiveReference reference : references) {
			if (reference == null || reference.matches() == null) {
				continue;
			}
			for (String matchText : reference.matches()) {
				String needle = safeTrim(matchText);
				if (needle.isEmpty()) {
					continue;
				}
				int startIndex = 0;
				while (startIndex < text.length()) {
					int index = text.indexOf(needle, startIndex);
					if (index == -1) {
						break;
					}
					results.add(new ReferenceMatch(reference, index, index + needle.length()));
					startIndex = index + needle.length();
				}
			}
		}
		return results;
	}

	private static boolean shouldIncludeMatch(String text, String term, int start, int end) {
		if (text == null || term == null || term.isEmpty()) {
			return false;
		}
		boolean isTermAllCaps = term.toUpperCase(Locale.ROOT).equals(term);
		String matchedText = text.substring(start, end);
		if (isTermAllCaps && !matchedText.equals(term)) {
			return false;
		}

		Character before = start > 0 ? text.charAt(start - 1) : null;
		Character after = end < text.length() ? text.charAt(end) : null;

		if (!term.isEmpty() && Character.isDigit(term.charAt(0))) {
			if (before != null && (Character.isDigit(before) || before == '.')) {
				return false;
			}
		}

		char last = term.charAt(term.length() - 1);
		if (Character.isDigit(last) || last == 'x' || last == '.') {
			if (after != null && Character.isDigit(after)) {
				return false;
			}
		}

		String getSliceAtEnd2 = slice(text, end, 2);
		String getSliceAtEnd3 = slice(text, end, 3);
		Character getCharAt0 = charAt(text, end);
		Character getCharAt1 = charAt(text, end + 1);
		Character getCharAt2 = charAt(text, end + 2);
		Character getCharAt3 = charAt(text, end + 3);

		// If an all-caps token is immediately followed by 3 digits (e.g. ABC123),
		// treat it as part of a code and skip matching just the letters.
		if (isTermAllCaps && getSliceAtEnd3.matches("\\d{3}")) {
			return false;
		}

		boolean startSatisfied = !isWordChar(before);
		boolean endingSatisfied = !isWordChar(after);
		if (startSatisfied && endingSatisfied) {
			return true;
		}

		String[] words = term.split(" ");
		String lastWord = words.length > 0 ? words[words.length - 1] : term;
		boolean hasNoNumbers = !lastWord.matches(".*[0-9].*");
		if (hasNoNumbers && startSatisfied && !endingSatisfied) {
			if (getCharAt0 != null && getCharAt0 == 's' && !isWordChar(getCharAt1)) {
				endingSatisfied = true;
			} else if ("ed".equals(getSliceAtEnd2) && !isWordChar(getCharAt2)) {
				endingSatisfied = true;
			} else if ("ing".equals(getSliceAtEnd3) && !isWordChar(getCharAt3)) {
				endingSatisfied = true;
			} else if ("er".equals(getSliceAtEnd2) && !isWordChar(getCharAt2)) {
				endingSatisfied = true;
			} else if ("est".equals(getSliceAtEnd3) && !isWordChar(getCharAt3)) {
				endingSatisfied = true;
			}
			if (endingSatisfied) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWordChar(Character ch) {
		return ch != null && ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
	}

	private static Character charAt(String text, int index) {
		if (index < 0 || index >= text.length()) {
			return null;
		}
		return text.charAt(index);
	}

	private static String slice(String text, int index, int length) {
		int end = Math.min(text.length(), Math.max(index, 0) + Math.max(length, 0));
		if (index < 0 || index >= text.length() || end <= index) {
			return "";
		}
		return text.substring(index, end);
	}

	private static List<RegexMatch> findRegexMatches(String text, Pattern pattern) {
		List<RegexMatch> matches = new ArrayList<>();
		if (text == null || text.isEmpty() || pattern == null) {
			return matches;
		}
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			List<String> groups = new ArrayList<>();
			for (int i = 1; i <= matcher.groupCount(); i++) {
				groups.add(matcher.group(i));
			}
			matches.add(new RegexMatch(matcher.group(), matcher.start(), matcher.end(), groups));
			if (matcher.start() == matcher.end()) {
				break;
			}
		}
		return matches;
	}

	private static RegexMatch findContainingHyperlink(List<RegexMatch> links, int start, int end) {
		if (links == null) {
			return null;
		}
		for (RegexMatch link : links) {
			if (link != null && start >= link.start() && end <= link.end()) {
				return link;
			}
		}
		return null;
	}

	private static boolean isMatchInMarkdownHeader(String text, int start) {
		if (text == null || start < 0 || start > text.length()) {
			return false;
		}
		int lastNewline = text.lastIndexOf('\n', Math.max(0, start - 1));
		int lineStart = lastNewline >= 0 ? lastNewline + 1 : 0;
		int i = lineStart;
		while (i < start && text.charAt(i) == ' ') {
			i++;
		}
		int hashCount = 0;
		while (i < start && text.charAt(i) == '#') {
			hashCount++;
			i++;
		}
		return hashCount > 0;
	}

	private static String slugifyName(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
		String withoutDiacritics = normalized.replaceAll("\\p{M}", "");
		String replaced = withoutDiacritics.replaceAll("[^a-zA-Z0-9]+", "-");
		return replaced.replaceAll("^-+|-+$", "");
	}

	private static String sanitizeMarkdownLinkTitle(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("\"", "'").replace("\n", " ").trim();
	}

	private static String buildDiscordLinkTargetForMod(ArchiveReference reference, String messageUrl) {
		String target = safeTrim(messageUrl);
		if (target.isEmpty()) {
			return "";
		}
		String joinUrl = reference != null ? safeTrim(reference.serverJoinURL()) : "";
		String serverName = reference != null ? safeTrim(reference.serverName()) : "";
		if (joinUrl.isEmpty()) {
			return target;
		}
		StringBuilder builder = new StringBuilder(DISCORD_LINK_PATH_PREFIX)
			.append("?url=")
			.append(encodeQuerySegment(target))
			.append("&join=")
			.append(encodeQuerySegment(joinUrl));
		if (!serverName.isEmpty()) {
			builder.append("&server=").append(encodeQuerySegment(serverName));
		}
		return builder.toString();
	}

	private static String encodePathSegment(String value) {
		return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String encodeQuerySegment(String value) {
		return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String safeTrim(String value) {
		return value != null ? value.trim() : "";
	}

	private record ReferenceMatch(ArchiveReference reference, int start, int end) {
	}

	private record RegexMatch(String match, int start, int end, List<String> groups) {
	}
}
