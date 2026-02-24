package com.andrews.archivedownloader.network;

import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.config.DownloadSettings;
import com.andrews.archivedownloader.models.ArchiveConfigJson;
import com.andrews.archivedownloader.models.ArchiveAttachment;
import com.andrews.archivedownloader.models.ArchiveChannel;
import com.andrews.archivedownloader.models.ArchiveDictionaryEntry;
import com.andrews.archivedownloader.models.ArchiveDictionaryReferencedPost;
import com.andrews.archivedownloader.models.ArchiveDictionaryReference;
import com.andrews.archivedownloader.models.ArchiveImageInfo;
import com.andrews.archivedownloader.models.ArchivePostDetail;
import com.andrews.archivedownloader.models.ArchivePostSummary;
import com.andrews.archivedownloader.models.ArchiveReference;
import com.andrews.archivedownloader.models.ArchiveRecordSection;
import com.andrews.archivedownloader.models.ArchiveSearchResult;
import com.andrews.archivedownloader.models.DiscordPostReference;
import com.andrews.archivedownloader.models.GlobalTag;
import com.andrews.archivedownloader.util.ReferenceUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class ArchiveNetworkManager {
	private static final String DEFAULT_BRANCH = "main";
	private static final String API_SUBMISSION_CHANNEL_CATEGORY = "Submission Status";
	private static final String API_SUBMISSION_CHANNEL_PATH_LEGACY = "__api_submission__";
	private static final String API_SUBMISSION_STATUS_PATH_PREFIX = "__api_submission_status__/";
	private static final List<String> SUBMISSION_STATUS_ORDER = List.of(
		"new",
		"need_endorsement",
		"waiting",
		"accepted",
		"rejected",
		"retracted"
	);
	private static final String RAW_BASE = "https://raw.githubusercontent.com";
	private static final String MEDIA_BASE = "https://media.githubusercontent.com/media";
	private static final String OFFLINE_CACHE_DIR = "archivedownloader/offlinecache";
	public static final String USER_AGENT = "ArchiveDownloader/1.0 (+https://github.com/Llama-Collective/Archive-Downloader)";

	private static final int TIMEOUT_SECONDS = 10;
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
		.build();

	private static final Map<String, ArchiveIndexCache> CACHED_INDEXES = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<ArchiveIndexCache>> INDEX_FUTURES = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, StyleInfo>> CACHED_SCHEMA_STYLES = new ConcurrentHashMap<>();
	private static final Map<String, List<GlobalTag>> CACHED_GLOBAL_TAGS = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<List<GlobalTag>>> GLOBAL_TAG_FUTURES = new ConcurrentHashMap<>();
	private static final Map<String, List<ArchivePostSummary>> CACHED_SUBMISSION_SUMMARIES = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> CACHED_DICTIONARY_SUMMARIES = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<Map<String, String>>> DICTIONARY_SUMMARY_FUTURES = new ConcurrentHashMap<>();
	private static final Map<String, ArchiveDictionaryEntry> CACHED_DICTIONARY_ENTRIES = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<ArchiveDictionaryEntry>> DICTIONARY_ENTRY_FUTURES = new ConcurrentHashMap<>();
	private static final List<GlobalTag> DEFAULT_GLOBAL_TAGS = List.of(
		new GlobalTag("Untested", "\u2049", "#fcd34d", 0xFF8C6E00L, null),
		new GlobalTag("Broken", "\uD83D\uDC94", "#ff6969", 0xFF8B1A1AL, null),
		new GlobalTag("Tested & Functional", "\u2705", "#34d399", 0xFF1E7F1EL, null),
		new GlobalTag("Recommended", "\u2B50", "#29b0ff", 0xFF0066CCL, true)
	);

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		ServerEntry server,
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		ServerEntry targetServer = normalizeServer(server);
		return ensureIndexLoaded(targetServer).thenApply(index -> {
			List<ArchivePostSummary> filtered = new ArrayList<>(filterPosts(index.posts(), query, tag, includeTags, excludeTags, channelPaths));
			sortPosts(filtered, sort);

			Map<String, Integer> channelCounts = new LinkedHashMap<>();
			for (ArchiveChannel channel : index.channels()) {
				if (channel != null && channel.path() != null) {
					channelCounts.put(channel.path(), 0);
				}
			}
			Map<String, Integer> tagCounts = new LinkedHashMap<>();
			for (ArchivePostSummary post : filtered) {
				if (post == null || post.channelPath() == null) continue;
				String path = post.channelPath();
				channelCounts.put(path, channelCounts.getOrDefault(path, 0) + 1);
				if (post.tags() != null) {
					for (String tag2 : post.tags()) {
						if (tag2 == null) continue;
						String key = tag2.toLowerCase(Locale.ROOT);
						tagCounts.put(key, tagCounts.getOrDefault(key, 0) + 1);
					}
				}
			}

			int totalItems = filtered.size();
			int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) Math.max(itemsPerPage, 1)));

			int startIndex = Math.max(0, (page - 1) * Math.max(itemsPerPage, 1));
			int endIndex = Math.min(filtered.size(), startIndex + Math.max(itemsPerPage, 1));
			List<ArchivePostSummary> pageItems = filtered.subList(
				Math.min(startIndex, filtered.size()),
				Math.min(endIndex, filtered.size())
			);

			return new ArchiveSearchResult(pageItems, totalPages, totalItems, channelCounts, tagCounts);
		});
	}

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		return searchPosts(ServerDictionary.getDefaultServer(), query, sort, tag, includeTags, excludeTags, channelPaths, page, itemsPerPage);
	}

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		String query,
		String sort,
		String tag,
		int page,
		int itemsPerPage
	) {
		return searchPosts(ServerDictionary.getDefaultServer(), query, sort, tag, null, null, null, page, itemsPerPage);
	}

	public static CompletableFuture<ArchiveSearchResult> searchSubmissionPosts(
		ServerEntry server,
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		ServerEntry targetServer = normalizeServer(server);
		String submissionCacheKey = serverKey(targetServer);
		String apiBase = normalizeApiBase(targetServer.apiBase());
		String token = normalizeApiTokenValue(DownloadSettings.getInstance().getApiToken(targetServer));
		if (apiBase.isBlank()) {
			return CompletableFuture.failedFuture(new RuntimeException("This server does not expose submissions API"));
		}
		if (token.isBlank()) {
			return CompletableFuture.failedFuture(new RuntimeException("No API token configured for this server"));
		}

		boolean needsClientFiltering = isClientFilterRequired(query, sort, tag, includeTags, excludeTags, channelPaths);
		List<ArchivePostSummary> cachedSummaries = CACHED_SUBMISSION_SUMMARIES.get(submissionCacheKey);
		if (needsClientFiltering) {
			CompletableFuture<List<ArchivePostSummary>> source = cachedSummaries != null
				? CompletableFuture.completedFuture(cachedSummaries)
				: fetchAllSubmissionPages(targetServer).thenApply(all -> {
					CACHED_SUBMISSION_SUMMARIES.put(submissionCacheKey, List.copyOf(all));
					return all;
				});
			return source
				.thenApply(all -> buildFilteredSubmissionResult(all, query, sort, tag, includeTags, excludeTags, channelPaths, page, itemsPerPage));
		}
		if (cachedSummaries != null) {
			return CompletableFuture.completedFuture(
				buildFilteredSubmissionResult(cachedSummaries, query, sort, tag, includeTags, excludeTags, channelPaths, page, itemsPerPage)
			);
		}

		int safePage = Math.max(1, page);
		int safePageSize = Math.min(200, Math.max(1, itemsPerPage));
		return fetchSubmissionPage(targetServer, safePage, safePageSize)
			.thenApply(pageData -> {
				if (pageData.totalPages() <= 1) {
					CACHED_SUBMISSION_SUMMARIES.put(submissionCacheKey, List.copyOf(pageData.posts()));
				}
				Map<String, Integer> channelCounts = pageData.totalPages() <= 1
					? computeSubmissionStatusCounts(pageData.posts())
					: Map.of();
				Map<String, Integer> tagCounts = pageData.totalPages() <= 1
					? computeTagCounts(pageData.posts())
					: Map.of();
				return new ArchiveSearchResult(
					pageData.posts(),
					Math.max(1, pageData.totalPages()),
					Math.max(0, pageData.total()),
					channelCounts,
					tagCounts
				);
			});
	}

	public static boolean hasApiAccessConfigured(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		if (normalizeApiBase(targetServer.apiBase()).isBlank()) {
			return false;
		}
		return DownloadSettings.getInstance().hasApiToken(targetServer);
	}

	public static List<String> getSubmissionStatuses() {
		return SUBMISSION_STATUS_ORDER;
	}

	public static String submissionStatusPath(String status) {
		return API_SUBMISSION_STATUS_PATH_PREFIX + normalizeSubmissionStatus(status);
	}

	public static String submissionStatusLabel(String status) {
		String normalized = normalizeSubmissionStatus(status);
		if (normalized.isEmpty()) {
			return "Unknown";
		}
		String[] parts = normalized.split("_");
		List<String> words = new ArrayList<>();
		for (String part : parts) {
			if (part == null || part.isBlank()) {
				continue;
			}
			words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
		}
		return words.isEmpty() ? "Unknown" : String.join(" ", words);
	}

	public static CompletableFuture<ApiTokenValidationResult> validateApiToken(ServerEntry server, String tokenInput) {
		ServerEntry targetServer = normalizeServer(server);
		String apiBase = getApiBase(targetServer);
		if (apiBase.isBlank()) {
			return CompletableFuture.completedFuture(new ApiTokenValidationResult(
				false,
				"This server does not expose submissions API."
			));
		}

		String token = normalizeApiTokenValue(tokenInput);
		if (token.isBlank()) {
			return CompletableFuture.completedFuture(new ApiTokenValidationResult(false, "Token is empty."));
		}

		String url = apiBase + "/submissions?page=1&pageSize=1";
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.header("Authorization", "Bearer " + token)
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() == 200) {
					JsonObject parsed = GSON.fromJson(response.body(), JsonObject.class);
					if (parsed == null || (parsed.has("ok") && !parsed.get("ok").getAsBoolean())) {
						String error = parseApiErrorMessage(response.body());
						if (error.isBlank()) {
							error = "API response was not successful.";
						}
						return new ApiTokenValidationResult(false, error);
					}
					return new ApiTokenValidationResult(true, "Token accepted.");
				}
				if (response.statusCode() == 401) {
					return new ApiTokenValidationResult(false, "Unauthorized. Check your token.");
				}
				String error = parseApiErrorMessage(response.body());
				if (error.isBlank()) {
					error = "HTTP " + response.statusCode();
				}
				return new ApiTokenValidationResult(false, error);
			})
			.exceptionally(throwable -> {
				Throwable root = throwable;
				while (root.getCause() != null) {
					root = root.getCause();
				}
				String message = root.getMessage() != null ? root.getMessage() : "Unable to reach API.";
				return new ApiTokenValidationResult(false, message);
			});
	}

	public static boolean isApiSubmissionSummary(ArchivePostSummary summary) {
		if (summary == null || summary.channelPath() == null) {
			return false;
		}
		String path = summary.channelPath();
		return path.startsWith(API_SUBMISSION_STATUS_PATH_PREFIX) || API_SUBMISSION_CHANNEL_PATH_LEGACY.equals(path);
	}

	public static String getApiBase(ServerEntry server) {
		return normalizeApiBase(normalizeServer(server).apiBase());
	}

	public static boolean isApiUrlForServer(ServerEntry server, String url) {
		String apiBase = getApiBase(server);
		if (apiBase.isBlank() || url == null || url.isBlank()) {
			return false;
		}
		return url.equals(apiBase) || url.startsWith(apiBase + "/");
	}

	public static HttpRequest.Builder applyApiAuthorization(HttpRequest.Builder builder, ServerEntry server, String url) {
		if (builder == null) {
			return null;
		}
		if (!isApiUrlForServer(server, url)) {
			return builder;
		}
		String token = normalizeApiTokenValue(DownloadSettings.getInstance().getApiToken(normalizeServer(server)));
		if (!token.isBlank()) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder;
	}

	public static CompletableFuture<ArchivePostDetail> getPostDetails(ServerEntry server, ArchivePostSummary summary) {
		ServerEntry targetServer = normalizeServer(server);
		if (summary == null) {
			return CompletableFuture.failedFuture(new RuntimeException("Post summary is missing"));
		}
		if (isApiSubmissionSummary(summary)) {
			return fetchSubmissionDetail(targetServer, summary.id())
				.thenCompose(data -> getDictionarySummariesSafe(targetServer)
					.thenApply(summaries -> toSubmissionPostDetail(targetServer, summary, data, summaries)));
		}
		return fetchEntryDataAsync(targetServer, summary.channelPath(), summary.entryPath(), summary.updatedAt())
			.thenCompose(data -> getDictionarySummariesSafe(targetServer)
				.thenApply(summaries -> toPostDetail(targetServer, summary, data, summaries)));
	}

	public static CompletableFuture<ArchivePostDetail> getPostDetails(ArchivePostSummary summary) {
		return getPostDetails(ServerDictionary.getDefaultServer(), summary);
	}

	public static CompletableFuture<ArchivePostSummary> findPostSummary(ServerEntry server, String postId, String postSlug) {
		ServerEntry targetServer = normalizeServer(server);
		String normalizedId = safeTrim(postId);
		String normalizedSlug = safeTrim(postSlug).toLowerCase(Locale.ROOT);
		if (normalizedId.isEmpty() && normalizedSlug.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}

		return ensureIndexLoaded(targetServer).thenApply(index -> {
			ArchivePostSummary fromIndex = findPostSummaryInList(index.posts(), normalizedId, normalizedSlug);
			if (fromIndex != null) {
				return fromIndex;
			}
			List<ArchivePostSummary> submissionSummaries = CACHED_SUBMISSION_SUMMARIES.get(serverKey(targetServer));
			return findPostSummaryInList(submissionSummaries, normalizedId, normalizedSlug);
		});
	}

	public static CompletableFuture<List<ArchiveChannel>> getChannels(ServerEntry server) {
		return ensureIndexLoaded(normalizeServer(server)).thenApply(ArchiveIndexCache::channels);
	}

	public static CompletableFuture<List<ArchiveChannel>> getChannels() {
		return getChannels(ServerDictionary.getDefaultServer());
	}

	public static CompletableFuture<List<GlobalTag>> getGlobalTags(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);
		List<GlobalTag> cached = CACHED_GLOBAL_TAGS.get(key);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}
		return GLOBAL_TAG_FUTURES.computeIfAbsent(key, k -> loadGlobalTagsAsync(targetServer));
	}

	public static List<GlobalTag> getCachedGlobalTags(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		return CACHED_GLOBAL_TAGS.getOrDefault(serverKey(targetServer), DEFAULT_GLOBAL_TAGS);
	}

	public static List<GlobalTag> getDefaultGlobalTags() {
		return DEFAULT_GLOBAL_TAGS;
	}

	public static CompletableFuture<ArchiveDictionaryEntry> getDictionaryEntry(ServerEntry server, String dictionaryId) {
		ServerEntry targetServer = normalizeServer(server);
		String normalizedId = safeTrim(dictionaryId);
		if (normalizedId.isEmpty()) {
			return CompletableFuture.failedFuture(new RuntimeException("Dictionary id is required"));
		}
		String key = dictionaryEntryKey(targetServer, normalizedId);
		ArchiveDictionaryEntry cached = CACHED_DICTIONARY_ENTRIES.get(key);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		return DICTIONARY_ENTRY_FUTURES.computeIfAbsent(key, k ->
			fetchDictionaryEntryDataAsync(targetServer, normalizedId)
				.thenCompose(data -> getDictionarySummariesSafe(targetServer)
					.thenCombine(
						getPostSummariesByCodeSafe(targetServer),
						(summaries, postSummariesByCode) -> toDictionaryEntry(targetServer, normalizedId, data, summaries, postSummariesByCode)
					))
				.whenComplete((entry, throwable) -> {
					DICTIONARY_ENTRY_FUTURES.remove(k);
					if (throwable == null && entry != null) {
						CACHED_DICTIONARY_ENTRIES.put(k, entry);
					}
				})
		);
	}

	public static void clearCache(ServerEntry server) {
		String key = serverKey(normalizeServer(server));
		CACHED_INDEXES.remove(key);
		INDEX_FUTURES.remove(key);
		CACHED_SCHEMA_STYLES.remove(key);
		CACHED_SUBMISSION_SUMMARIES.remove(key);
		CACHED_DICTIONARY_SUMMARIES.remove(key);
		DICTIONARY_SUMMARY_FUTURES.remove(key);
		CACHED_DICTIONARY_ENTRIES.keySet().removeIf(k -> k.startsWith(key + "::"));
		DICTIONARY_ENTRY_FUTURES.keySet().removeIf(k -> k.startsWith(key + "::"));
	}

	public static void clearCache() {
		CACHED_INDEXES.clear();
		INDEX_FUTURES.clear();
		CACHED_SCHEMA_STYLES.clear();
		CACHED_SUBMISSION_SUMMARIES.clear();
		CACHED_DICTIONARY_SUMMARIES.clear();
		DICTIONARY_SUMMARY_FUTURES.clear();
		CACHED_DICTIONARY_ENTRIES.clear();
		DICTIONARY_ENTRY_FUTURES.clear();
	}

	private static CompletableFuture<List<GlobalTag>> loadGlobalTagsAsync(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);
		return fetchArchiveConfigAsync(targetServer)
			.thenApply(ArchiveNetworkManager::extractGlobalTags)
			.exceptionally(throwable -> {
				System.err.println("Failed to load global tags for " + key + ": " + throwable.getMessage());
				return DEFAULT_GLOBAL_TAGS;
			})
			.whenComplete((tags, throwable) -> {
				List<GlobalTag> safe = tags != null && !tags.isEmpty() ? tags : DEFAULT_GLOBAL_TAGS;
				CACHED_GLOBAL_TAGS.put(key, safe);
				GLOBAL_TAG_FUTURES.remove(key);
			});
	}

	private static ArchivePostSummary findPostSummaryInList(
		List<ArchivePostSummary> posts,
		String normalizedId,
		String normalizedSlug
	) {
		if (posts == null || posts.isEmpty()) {
			return null;
		}
		for (ArchivePostSummary post : posts) {
			if (post == null) {
				continue;
			}
			if (!normalizedId.isEmpty() && normalizedId.equalsIgnoreCase(safeTrim(post.id()))) {
				return post;
			}
			if (normalizedSlug.isEmpty()) {
				continue;
			}
			String postCode = safeTrim(post.code());
			if (!postCode.isEmpty() && normalizedSlug.equals(postCode.toLowerCase(Locale.ROOT))) {
				return post;
			}
			String postSlug = buildEntrySlugFromSummary(post);
			if (!postSlug.isEmpty() && normalizedSlug.equals(postSlug.toLowerCase(Locale.ROOT))) {
				return post;
			}
		}
		return null;
	}

	private static String buildEntrySlugFromSummary(ArchivePostSummary post) {
		if (post == null) {
			return "";
		}
		String code = safeTrim(post.code());
		String titleSlug = slugifyName(post.title());
		if (titleSlug.isEmpty()) {
			return code;
		}
		if (code.isEmpty()) {
			return titleSlug;
		}
		return code + "-" + titleSlug;
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

	private static CompletableFuture<ArchiveConfigJson> fetchArchiveConfigAsync(ServerEntry server) {
		return fetchJsonAsync(server, "config.json")
			.thenApply(json -> GSON.fromJson(json, ArchiveConfigJson.class));
	}

	private static CompletableFuture<Map<String, String>> getDictionarySummariesSafe(ServerEntry server) {
		return ensureDictionarySummariesLoaded(server).exceptionally(throwable -> {
			System.err.println("Failed to load dictionary summaries for " + serverKey(server) + ": " + throwable.getMessage());
			return Map.of();
		});
	}

	private static CompletableFuture<Map<String, ArchivePostSummary>> getPostSummariesByCodeSafe(ServerEntry server) {
		return ensureIndexLoaded(server)
			.thenApply(index -> buildPostSummaryLookupByCode(index.posts()))
			.exceptionally(throwable -> {
				System.err.println("Failed to load post summaries for dictionary referencedBy on " + serverKey(server) + ": " + throwable.getMessage());
				return Map.of();
			});
	}

	private static Map<String, ArchivePostSummary> buildPostSummaryLookupByCode(List<ArchivePostSummary> posts) {
		if (posts == null || posts.isEmpty()) {
			return Map.of();
		}
		Map<String, ArchivePostSummary> byCode = new LinkedHashMap<>();
		for (ArchivePostSummary post : posts) {
			if (post == null) {
				continue;
			}
			String code = safeTrim(post.code());
			if (code.isEmpty()) {
				continue;
			}
			byCode.putIfAbsent(code.toLowerCase(Locale.ROOT), post);
		}
		return Map.copyOf(byCode);
	}

	private static CompletableFuture<Map<String, String>> ensureDictionarySummariesLoaded(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);
		Map<String, String> cached = CACHED_DICTIONARY_SUMMARIES.get(key);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}
		return DICTIONARY_SUMMARY_FUTURES.computeIfAbsent(key, ignored ->
			fetchDictionaryConfigAsync(targetServer)
				.thenApply(ArchiveNetworkManager::extractDictionarySummaries)
				.whenComplete((summaries, throwable) -> {
					DICTIONARY_SUMMARY_FUTURES.remove(key);
					if (throwable == null && summaries != null) {
						CACHED_DICTIONARY_SUMMARIES.put(key, summaries);
					}
				})
		);
	}

	private static CompletableFuture<DictionaryConfigData> fetchDictionaryConfigAsync(ServerEntry server) {
		return fetchJsonAsync(server, "dictionary/config.json")
			.thenApply(json -> GSON.fromJson(json, DictionaryConfigData.class));
	}

	private static CompletableFuture<DictionaryEntryData> fetchDictionaryEntryDataAsync(ServerEntry server, String dictionaryId) {
		String normalizedId = safeTrim(dictionaryId);
		if (normalizedId.isEmpty()) {
			return CompletableFuture.failedFuture(new RuntimeException("Dictionary id is required"));
		}
		return fetchJsonAsync(server, "dictionary/entries/" + encodePathSegment(normalizedId) + ".json")
			.thenApply(json -> GSON.fromJson(json, DictionaryEntryData.class));
	}

	private static Map<String, String> extractDictionarySummaries(DictionaryConfigData config) {
		if (config == null || config.entries == null || config.entries.isEmpty()) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (DictionaryIndexData entry : config.entries) {
			if (entry == null) {
				continue;
			}
			String id = safeTrim(entry.id);
			if (id.isEmpty()) {
				continue;
			}
			result.put(id, safeTrim(entry.summary));
		}
		return Map.copyOf(result);
	}

	private static String dictionaryEntryKey(ServerEntry server, String dictionaryId) {
		return serverKey(server) + "::" + safeTrim(dictionaryId).toLowerCase(Locale.ROOT);
	}

	private static List<GlobalTag> extractGlobalTags(ArchiveConfigJson config) {
		if (config == null || config.globalTags() == null || config.globalTags().isEmpty()) {
			return DEFAULT_GLOBAL_TAGS;
		}
		return config.globalTags();
	}

	private static CompletableFuture<ArchiveIndexCache> loadIndexAsync(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);

		return fetchPersistentIndexAsync(targetServer)
			.thenApply(index -> {
				Map<String, StyleInfo> styles = parseSchemaStyles(index.schemaStylesBytes());
				CACHED_SCHEMA_STYLES.put(key, styles);
				return buildCacheFromPersistentIndex(index);
			})
			.whenComplete((cache, throwable) -> {
				if (throwable == null && cache != null) {
					CACHED_INDEXES.put(key, cache);
				} else {
					INDEX_FUTURES.remove(key);
					CACHED_SCHEMA_STYLES.remove(key);
				}
			});
	}

	private static ArchiveIndexCache buildCacheFromPersistentIndex(PersistentIndexData index) {
		if (index == null) {
			return new ArchiveIndexCache(List.of(), List.of());
		}

		List<String> allTags = index.allTags() != null ? index.allTags() : List.of();
		List<String> allAuthors = index.allAuthors() != null ? index.allAuthors() : List.of();
		List<String> allCategories = index.allCategories() != null ? index.allCategories() : List.of();
		List<PersistentChannel> channelsFromIndex = index.channels() != null ? index.channels() : List.of();

		List<ArchivePostSummary> posts = new ArrayList<>();
		List<ArchiveChannel> channels = new ArrayList<>();

		for (PersistentChannel channel : channelsFromIndex) {
			if (channel == null) {
				continue;
			}
			List<PersistentEntry> channelEntries = channel.entries() != null ? channel.entries() : List.of();
			String category = safeGet(allCategories, channel.category());
			List<String> channelTags = mapIndicesToList(channel.tags(), allTags);

			channels.add(new ArchiveChannel(
				channel.code(),
				channel.name(),
				channel.code(),
				category,
				channel.path(),
				channel.description(),
				channelEntries.size(),
				channelTags
			));

			for (PersistentEntry entry : channelEntries) {
				if (entry == null) {
					continue;
				}
				String[] entryTags = mapIndicesToArray(entry.tags(), allTags);
				String[] entryAuthors = mapIndicesToArray(entry.authors(), allAuthors);
				long archivedAt = entry.archivedAt();
				long updatedAt = entry.updatedAt();

				posts.add(new ArchivePostSummary(
					entry.id(),
					entry.name(),
					channel.name(),
					channel.code(),
					category,
					channel.path(),
					entry.path(),
					getPrimaryCode(entry.codes()),
					entryTags,
					entryAuthors,
					archivedAt,
					updatedAt
				));
			}
		}

		return new ArchiveIndexCache(posts, channels);
	}

	private static Map<String, StyleInfo> parseSchemaStyles(byte[] stylesBytes) {
		if (stylesBytes == null || stylesBytes.length == 0) {
			return Map.of();
		}
		String json = new String(stylesBytes, StandardCharsets.UTF_8);
		Map<String, StyleInfo> styles = GSON.fromJson(json, new TypeToken<Map<String, StyleInfo>>() {}.getType());
		return styles != null ? styles : Map.of();
	}

	private static CompletableFuture<ArchiveIndexCache> ensureIndexLoaded(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);
		ArchiveIndexCache cached = CACHED_INDEXES.get(key);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		return INDEX_FUTURES.computeIfAbsent(key, k -> loadIndexAsync(targetServer));
	}

	private static ServerEntry normalizeServer(ServerEntry server) {
		return server != null ? server : ServerDictionary.getDefaultServer();
	}

	private static String serverKey(ServerEntry server) {
		ServerEntry target = normalizeServer(server);
		if (target.id() != null && !target.id().isBlank()) {
			return target.id().toLowerCase(Locale.ROOT);
		}
		return target.name() != null ? target.name().toLowerCase(Locale.ROOT) : "default";
	}

	private static Map<String, StyleInfo> getSchemaStyles(ServerEntry server) {
		return CACHED_SCHEMA_STYLES.getOrDefault(serverKey(server), Map.of());
	}

	private static List<ArchivePostSummary> filterPosts(List<ArchivePostSummary> posts, String query, String tagFilter, List<String> includeTags, List<String> excludeTags, List<String> channelPaths) {
		String normalizedQuery = query != null ? query.toLowerCase(Locale.ROOT).trim() : "";
		String normalizedTag = tagFilter != null ? tagFilter.toLowerCase(Locale.ROOT).trim() : "";
		List<String> normalizedChannels = channelPaths != null
			? channelPaths.stream().map(p -> p != null ? p.toLowerCase(Locale.ROOT) : "").toList()
			: List.of();
		List<String> normalizedInclude = includeTags != null
			? includeTags.stream().filter(t -> t != null && !t.isEmpty()).map(t -> t.toLowerCase(Locale.ROOT)).toList()
			: List.of();
		List<String> normalizedExclude = excludeTags != null
			? excludeTags.stream().filter(t -> t != null && !t.isEmpty()).map(t -> t.toLowerCase(Locale.ROOT)).toList()
			: List.of();

		return posts.stream()
			.filter(post -> matchesQuery(post, normalizedQuery))
			.filter(post -> normalizedTag.isEmpty() || hasTagMatch(post, normalizedTag))
			.filter(post -> normalizedInclude.isEmpty() || hasAllTags(post, normalizedInclude))
			.filter(post -> normalizedExclude.isEmpty() || !hasAnyTag(post, normalizedExclude))
			.filter(post -> normalizedChannels.isEmpty() ||
				(post.channelPath() != null && normalizedChannels.contains(post.channelPath().toLowerCase(Locale.ROOT))))
			.toList();
	}

	private static boolean matchesQuery(ArchivePostSummary post, String normalizedQuery) {
		if (post == null) {
			return false;
		}
		if (normalizedQuery == null || normalizedQuery.isEmpty()) {
			return true;
		}
		if (post.title() != null && post.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
			return true;
		}
		if (post.code() != null && post.code().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
			return true;
		}
		for (String author : post.authors()) {
			if (author != null && author.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTagMatch(ArchivePostSummary post, String normalizedTag) {
		for (String tag : post.tags()) {
			if (tag != null && tag.toLowerCase(Locale.ROOT).contains(normalizedTag)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAllTags(ArchivePostSummary post, List<String> required) {
		for (String req : required) {
			boolean found = false;
			for (String tag : post.tags()) {
				if (tag != null && tag.toLowerCase(Locale.ROOT).equals(req)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}

	private static boolean hasAnyTag(ArchivePostSummary post, List<String> tags) {
		for (String tag : post.tags()) {
			if (tag == null) continue;
			String lowered = tag.toLowerCase(Locale.ROOT);
			if (tags.contains(lowered)) return true;
		}
		return false;
	}

	private static void sortPosts(List<ArchivePostSummary> posts, String sort) {
		String selectedSort = (sort == null || sort.isEmpty()) ? "newest" : sort;
		Comparator<ArchivePostSummary> comparator;

		switch (selectedSort) {
			case "updated" -> comparator = Comparator.comparingLong(ArchiveNetworkManager::getUpdatedTimestamp).reversed();
			case "name" -> comparator = Comparator.comparing(
				p -> p.title() != null ? p.title().toLowerCase(Locale.ROOT) : ""
			);
			case "code" -> comparator = Comparator.comparing(
				p -> p.code() != null ? p.code().toLowerCase(Locale.ROOT) : ""
			);
			default -> comparator = Comparator.comparingLong(ArchiveNetworkManager::getUpdatedTimestamp).reversed();
		}

		posts.sort(comparator);
	}

	private static long getUpdatedTimestamp(ArchivePostSummary post) {
		if (post == null) return 0L;
		long updated = post.updatedAt();
		if (updated > 0) return updated;
		return post.archivedAt();
	}

	private static boolean isClientFilterRequired(
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths
	) {
		if (!safeTrim(query).isEmpty()) {
			return true;
		}
		if (!safeTrim(tag).isEmpty()) {
			return true;
		}
		if (includeTags != null && !includeTags.isEmpty()) {
			return true;
		}
		if (excludeTags != null && !excludeTags.isEmpty()) {
			return true;
		}
		if (channelPaths != null && !channelPaths.isEmpty()) {
			return true;
		}
		String selectedSort = safeTrim(sort);
		return !selectedSort.isEmpty() && !"newest".equalsIgnoreCase(selectedSort);
	}

	private static CompletableFuture<List<ArchivePostSummary>> fetchAllSubmissionPages(ServerEntry server) {
		final int pageSize = 200;
		return fetchSubmissionPage(server, 1, pageSize).thenCompose(firstPage -> {
			if (firstPage.totalPages() <= 1) {
				return CompletableFuture.completedFuture(firstPage.posts());
			}

			List<CompletableFuture<SubmissionPage>> remaining = new ArrayList<>();
			for (int page = 2; page <= firstPage.totalPages(); page++) {
				remaining.add(fetchSubmissionPage(server, page, pageSize));
			}

			CompletableFuture<Void> combined = CompletableFuture.allOf(remaining.toArray(new CompletableFuture[0]));
			return combined.thenApply(ignored -> {
				List<SubmissionPage> pages = new ArrayList<>();
				pages.add(firstPage);
				for (CompletableFuture<SubmissionPage> future : remaining) {
					pages.add(future.join());
				}
				pages.sort(Comparator.comparingInt(SubmissionPage::page));

				List<ArchivePostSummary> all = new ArrayList<>();
				for (SubmissionPage page : pages) {
					if (page.posts() != null && !page.posts().isEmpty()) {
						all.addAll(page.posts());
					}
				}
				return all;
			});
		});
	}

	private static ArchiveSearchResult buildFilteredSubmissionResult(
		List<ArchivePostSummary> posts,
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		List<ArchivePostSummary> filtered = new ArrayList<>(filterPosts(posts, query, tag, includeTags, excludeTags, channelPaths));
		// Preserve API/server ordering for submissions review mode.

		int safeItemsPerPage = Math.max(itemsPerPage, 1);
		int totalItems = filtered.size();
		int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safeItemsPerPage));
		int startIndex = Math.max(0, (Math.max(page, 1) - 1) * safeItemsPerPage);
		int endIndex = Math.min(filtered.size(), startIndex + safeItemsPerPage);
		List<ArchivePostSummary> pageItems = filtered.subList(
			Math.min(startIndex, filtered.size()),
			Math.min(endIndex, filtered.size())
		);

		Map<String, Integer> channelCounts = computeSubmissionStatusCounts(filtered);
		Map<String, Integer> tagCounts = computeTagCounts(filtered);

		return new ArchiveSearchResult(pageItems, totalPages, totalItems, channelCounts, tagCounts);
	}

	private static Map<String, Integer> computeTagCounts(List<ArchivePostSummary> posts) {
		Map<String, Integer> tagCounts = new LinkedHashMap<>();
		if (posts == null || posts.isEmpty()) {
			return tagCounts;
		}
		for (ArchivePostSummary post : posts) {
			if (post == null || post.tags() == null) {
				continue;
			}
			for (String tag : post.tags()) {
				String key = safeTrim(tag).toLowerCase(Locale.ROOT);
				if (!key.isEmpty()) {
					tagCounts.put(key, tagCounts.getOrDefault(key, 0) + 1);
				}
			}
		}
		return tagCounts;
	}

	private static Map<String, Integer> computeSubmissionStatusCounts(List<ArchivePostSummary> posts) {
		Map<String, Integer> channelCounts = new LinkedHashMap<>();
		for (String status : SUBMISSION_STATUS_ORDER) {
			channelCounts.put(submissionStatusPath(status), 0);
		}
		if (posts == null || posts.isEmpty()) {
			return channelCounts;
		}
		for (ArchivePostSummary post : posts) {
			if (post == null) {
				continue;
			}
			String path = normalizeSubmissionPath(post.channelPath());
			channelCounts.put(path, channelCounts.getOrDefault(path, 0) + 1);
		}
		return channelCounts;
	}

	private static CompletableFuture<SubmissionPage> fetchSubmissionPage(ServerEntry server, int page, int pageSize) {
		String apiBase = getApiBase(server);
		if (apiBase.isBlank()) {
			return CompletableFuture.failedFuture(new RuntimeException("Missing apiBase for server"));
		}
		String path = "/submissions?page=" + Math.max(1, page) + "&pageSize=" + Math.max(1, Math.min(200, pageSize));
		return fetchApiJsonAsync(server, apiBase + path).thenApply(response -> {
			JsonObject paginationObj = response.has("pagination") && response.get("pagination").isJsonObject()
				? response.getAsJsonObject("pagination")
				: new JsonObject();

			int resolvedPage = getInt(paginationObj, "page", Math.max(1, page));
			int resolvedPageSize = getInt(paginationObj, "pageSize", Math.max(1, pageSize));
			int total = getInt(paginationObj, "total", 0);
			int totalPages = getInt(paginationObj, "totalPages", 1);

			List<ArchivePostSummary> summaries = new ArrayList<>();
			JsonArray submissions = response.has("submissions") && response.get("submissions").isJsonArray()
				? response.getAsJsonArray("submissions")
				: new JsonArray();

			for (JsonElement element : submissions) {
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				ApiSubmissionSummaryData raw = GSON.fromJson(element, ApiSubmissionSummaryData.class);
				ArchivePostSummary mapped = toSubmissionSummary(raw);
				if (mapped != null) {
					summaries.add(mapped);
				}
			}
			return new SubmissionPage(summaries, resolvedPage, resolvedPageSize, total, Math.max(1, totalPages));
		});
	}

	private static CompletableFuture<ApiSubmissionDetailsData> fetchSubmissionDetail(ServerEntry server, String submissionId) {
		String id = safeTrim(submissionId);
		if (id.isEmpty()) {
			return CompletableFuture.failedFuture(new RuntimeException("Submission id is missing"));
		}
		String apiBase = getApiBase(server);
		if (apiBase.isBlank()) {
			return CompletableFuture.failedFuture(new RuntimeException("Missing apiBase for server"));
		}
		String url = apiBase + "/submission/" + encodePathSegment(id);
		return fetchApiJsonAsync(server, url).thenApply(response -> {
			if (!response.has("submission") || !response.get("submission").isJsonObject()) {
				throw new CompletionException(new RuntimeException("Malformed submission detail response"));
			}
			ApiSubmissionDetailsData data = GSON.fromJson(response.getAsJsonObject("submission"), ApiSubmissionDetailsData.class);
			if (data == null) {
				throw new CompletionException(new RuntimeException("Submission payload is empty"));
			}
			return data;
		});
	}

	private static CompletableFuture<JsonObject> fetchApiJsonAsync(ServerEntry server, String url) {
		ServerEntry targetServer = normalizeServer(server);
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET();
		applyApiAuthorization(builder, targetServer, url);

		return HTTP_CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					String error = parseApiErrorMessage(response.body());
					if (error.isBlank()) {
						error = "HTTP " + response.statusCode();
					}
					throw new CompletionException(new RuntimeException("API error: " + error));
				}
				JsonObject parsed = GSON.fromJson(response.body(), JsonObject.class);
				if (parsed == null) {
					throw new CompletionException(new RuntimeException("Empty API response"));
				}
				if (parsed.has("ok") && parsed.get("ok").isJsonPrimitive() && !parsed.get("ok").getAsBoolean()) {
					String error = parsed.has("error") ? parsed.get("error").getAsString() : "Unknown API error";
					throw new CompletionException(new RuntimeException("API error: " + error));
				}
				return response.body();
			})
			.thenApply(body -> {
				writeOfflineApiText(targetServer, url, body);
				return GSON.fromJson(body, JsonObject.class);
			})
			.handle((parsed, throwable) -> {
				if (throwable == null) {
					return parsed;
				}
				String cached = readOfflineApiText(targetServer, url);
				if (cached != null && !cached.isBlank()) {
					JsonObject fallback = GSON.fromJson(cached, JsonObject.class);
						if (fallback != null) {
							return fallback;
						}
				}
				throw unwrapCompletionException(throwable);
			});
	}

	private static ArchivePostSummary toSubmissionSummary(ApiSubmissionSummaryData submission) {
		if (submission == null) {
			return null;
		}
		String id = safeTrim(submission.id);
		if (id.isEmpty()) {
			return null;
		}
		long createdAt = submission.timestamp != null && submission.timestamp.createdMs != null
			? submission.timestamp.createdMs
			: 0L;
		long updatedAt = submission.timestamp != null && submission.timestamp.updatedMs != null
			? submission.timestamp.updatedMs
			: createdAt;
		String title = safeTrim(submission.name);
		if (title.isEmpty()) {
			title = id;
		}
		String status = normalizeSubmissionStatus(submission.status);
		return new ArchivePostSummary(
			id,
			title,
			submissionStatusLabel(status),
			status.toUpperCase(Locale.ROOT),
			API_SUBMISSION_CHANNEL_CATEGORY,
			submissionStatusPath(status),
			id,
			status,
			toStringArray(submission.tags),
			toStringArray(submission.authors),
			createdAt,
			updatedAt
		);
	}

	private static ArchivePostDetail toSubmissionPostDetail(
		ServerEntry server,
		ArchivePostSummary summary,
		ApiSubmissionDetailsData data,
		Map<String, String> dictionarySummaries
	) {
		String detailId = !safeTrim(data.id).isEmpty() ? safeTrim(data.id) : safeTrim(summary.id());
		long createdAt = data.timestamp != null && data.timestamp.createdMs != null
			? data.timestamp.createdMs
			: summary.archivedAt();
		long updatedAt = data.timestamp != null && data.timestamp.updatedMs != null
			? data.timestamp.updatedMs
			: Math.max(summary.updatedAt(), createdAt);

		List<String> authorNames = toAuthorNames(data.authors);
		if (authorNames.isEmpty() && summary.authors() != null) {
			authorNames = Arrays.asList(summary.authors());
		}
		List<String> tagNames = toTagNames(data.tags);
		if (tagNames.isEmpty() && summary.tags() != null) {
			tagNames = Arrays.asList(summary.tags());
		}
		String status = normalizeSubmissionStatus(!safeTrim(data.status).isEmpty() ? data.status : summary.code());

		ArchivePostSummary enrichedSummary = new ArchivePostSummary(
			!detailId.isEmpty() ? detailId : summary.id(),
			!safeTrim(data.name).isEmpty() ? safeTrim(data.name) : summary.title(),
			submissionStatusLabel(status),
			status.toUpperCase(Locale.ROOT),
			API_SUBMISSION_CHANNEL_CATEGORY,
			submissionStatusPath(status),
			!detailId.isEmpty() ? detailId : summary.entryPath(),
			status,
			tagNames.toArray(new String[0]),
			authorNames.toArray(new String[0]),
			createdAt,
			updatedAt
		);

		List<String> imageUrls = new ArrayList<>();
		List<ArchiveImageInfo> imageInfos = new ArrayList<>();
		if (data.images != null) {
			for (ApiImageData image : data.images) {
				if (image == null) {
					continue;
				}
				String imageUrl = buildSubmissionImageUrl(server, detailId, image);
				if (imageUrl.isBlank()) {
					continue;
				}
				imageUrls.add(imageUrl);
				imageInfos.add(new ArchiveImageInfo(imageUrl, image.description, image.width, image.height, image.hash));
			}
		}

		List<ArchiveAttachment> attachments = new ArrayList<>();
		if (data.attachments != null) {
			for (ApiAttachmentData attachment : data.attachments) {
				if (attachment == null) {
					continue;
				}
				ArchiveAttachment.YoutubeInfo youtubeInfo = null;
				if (attachment.youtube != null) {
					youtubeInfo = new ArchiveAttachment.YoutubeInfo(
						attachment.youtube.title,
						attachment.youtube.author_name,
						attachment.youtube.author_url
					);
				}
				boolean canDownload = attachment.canDownload == null || attachment.canDownload;
				String downloadUrl = buildSubmissionAttachmentUrl(server, detailId, attachment);
				String sizeText = attachment.litematic != null ? attachment.litematic.size : null;
				attachments.add(new ArchiveAttachment(
					!safeTrim(attachment.name).isEmpty() ? attachment.name : "Attachment",
					downloadUrl,
					attachment.contentType,
					canDownload,
					sizeText,
					attachment.description,
					attachment.hash,
					attachment.litematic != null ? new ArchiveAttachment.LitematicInfo(
						attachment.litematic.version,
						attachment.litematic.size,
						attachment.litematic.error
					) : null,
					attachment.schematic != null ? new ArchiveAttachment.SchematicInfo(
						attachment.schematic.version,
						attachment.schematic.size,
						attachment.schematic.error
					) : null,
					attachment.wdl != null ? new ArchiveAttachment.WdlInfo(
						attachment.wdl.version,
						attachment.wdl.error
					) : null,
					youtubeInfo
				));
			}
		}

		JsonObject records = data.revision != null ? data.revision.records : null;
		Map<String, StyleInfo> recordStyles = data.revision != null && data.revision.styles != null
			? data.revision.styles
			: Map.of();
		List<ArchiveRecordSection> recordSections = toRecordSections(records, Map.of(), recordStyles);
		List<ArchiveReference> references = data.revision != null && data.revision.references != null
			? data.revision.references
			: (data.references != null ? data.references : List.of());
		String recordMarkdown = ReferenceUtils.transformOutputWithReferencesForWebsiteStyle(
			postToMarkdown(records, recordStyles, Map.of()),
			references,
			id -> dictionarySummaries != null ? dictionarySummaries.get(id) : null
		);

		DiscordPostReference discordPost = null;
		if (!safeTrim(data.threadUrl).isEmpty() || !safeTrim(data.threadId).isEmpty()) {
			discordPost = new DiscordPostReference(
				null,
				safeTrim(data.threadId),
				List.of(),
				safeTrim(data.threadUrl),
				null,
				null
			);
		}

		return new ArchivePostDetail(
			enrichedSummary,
			authorNames,
			imageUrls,
			imageInfos,
			attachments,
			discordPost,
			recordSections,
			recordMarkdown,
			createdAt,
			updatedAt
		);
	}

	private static String[] toStringArray(List<String> values) {
		if (values == null || values.isEmpty()) {
			return new String[0];
		}
		List<String> sanitized = new ArrayList<>();
		for (String value : values) {
			String trimmed = safeTrim(value);
			if (!trimmed.isEmpty()) {
				sanitized.add(trimmed);
			}
		}
		return sanitized.toArray(new String[0]);
	}

	private static List<String> toAuthorNames(List<ApiAuthorData> authors) {
		if (authors == null || authors.isEmpty()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (ApiAuthorData author : authors) {
			if (author == null) {
				continue;
			}
			String display = !safeTrim(author.displayName).isEmpty() ? safeTrim(author.displayName) : safeTrim(author.username);
			if (!display.isEmpty() && !names.contains(display)) {
				names.add(display);
			}
		}
		return names;
	}

	private static List<String> toTagNames(List<ApiTagData> tags) {
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (ApiTagData tag : tags) {
			if (tag == null) {
				continue;
			}
			String name = safeTrim(tag.name);
			if (!name.isEmpty() && !names.contains(name)) {
				names.add(name);
			}
		}
		return names;
	}

	private static String buildSubmissionImageUrl(ServerEntry server, String submissionId, ApiImageData image) {
		if (image == null) {
			return "";
		}
		String imageId = safeTrim(image.id);
		if (!imageId.isEmpty() && !safeTrim(submissionId).isEmpty()) {
			return buildSubmissionApiUrl(server, submissionId, "images", imageId);
		}
		if (!safeTrim(image.url).isEmpty()) {
			return image.url;
		}
		return "";
	}

	private static String buildSubmissionAttachmentUrl(ServerEntry server, String submissionId, ApiAttachmentData attachment) {
		if (attachment == null) {
			return "";
		}
		String attachmentId = safeTrim(attachment.id);
		if (!attachmentId.isEmpty() && !safeTrim(submissionId).isEmpty()) {
			return buildSubmissionApiUrl(server, submissionId, "attachments", attachmentId);
		}
		if (!safeTrim(attachment.downloadUrl).isEmpty()) {
			return attachment.downloadUrl;
		}
		if (!safeTrim(attachment.url).isEmpty()) {
			return attachment.url;
		}
		return "";
	}

	private static String buildSubmissionApiUrl(ServerEntry server, String submissionId, String resourceType, String resourceId) {
		String apiBase = getApiBase(server);
		if (apiBase.isBlank()) {
			return "";
		}
		return apiBase
			+ "/submission/" + encodePathSegment(submissionId)
			+ "/" + resourceType
			+ "/" + encodePathSegment(resourceId);
	}

	private static int getInt(JsonObject obj, String key, int fallback) {
		if (obj == null || key == null || key.isEmpty() || !obj.has(key)) {
			return fallback;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String parseApiErrorMessage(String body) {
		String text = safeTrim(body);
		if (text.isEmpty()) {
			return "";
		}
		try {
			JsonObject parsed = GSON.fromJson(text, JsonObject.class);
			if (parsed != null && parsed.has("error")) {
				return safeTrim(parsed.get("error").getAsString());
			}
		} catch (Exception ignored) {
		}
		return text.length() > 160 ? text.substring(0, 160) : text;
	}

	private static String normalizeApiBase(String apiBase) {
		String normalized = safeTrim(apiBase);
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	private static String normalizeSubmissionStatus(String status) {
		String normalized = safeTrim(status).toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			return "unknown";
		}
		return normalized;
	}

	private static String normalizeSubmissionPath(String path) {
		String normalizedPath = safeTrim(path);
		if (normalizedPath.startsWith(API_SUBMISSION_STATUS_PATH_PREFIX)) {
			String status = normalizedPath.substring(API_SUBMISSION_STATUS_PATH_PREFIX.length());
			return submissionStatusPath(status);
		}
		if (API_SUBMISSION_CHANNEL_PATH_LEGACY.equals(normalizedPath)) {
			return submissionStatusPath("unknown");
		}
		if (normalizedPath.contains("/")) {
			String[] parts = normalizedPath.split("/");
			return submissionStatusPath(parts[parts.length - 1]);
		}
		return submissionStatusPath(normalizedPath);
	}

	private static String normalizeApiTokenValue(String tokenInput) {
		String token = safeTrim(tokenInput);
		if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
			token = token.substring(7).trim();
		}
		return token;
	}

	private static String encodePathSegment(String value) {
		return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String safeTrim(String value) {
		return value != null ? value.trim() : "";
	}

	private static ArchivePostDetail toPostDetail(
		ServerEntry server,
		ArchivePostSummary summary,
		ArchiveEntryData data,
		Map<String, String> dictionarySummaries
	) {
		List<String> authors = new ArrayList<>();
		if (data.authors != null) {
			for (ArchiveAuthor author : data.authors) {
				if (author != null) {
					String name = author.displayName != null && !author.displayName.isEmpty()
						? author.displayName
						: author.username;
					if (name != null && !name.isEmpty()) {
						authors.add(name);
					}
				}
			}
		}

		if (authors.isEmpty() && summary.authors() != null) {
			for (String author : summary.authors()) {
				if (author != null && !author.isBlank()) {
					authors.add(author);
				}
			}
		}

		List<String> images = new ArrayList<>();
		List<ArchiveImageInfo> imageInfos = new ArrayList<>();
		if (data.images != null) {
			for (ArchiveImageData image : data.images) {
				String url = buildPostImageUrl(server, image, summary.channelPath(), summary.entryPath());
				if (url != null && !url.isEmpty()) {
					images.add(url);
					imageInfos.add(new ArchiveImageInfo(
						url,
						image.description,
						image.width,
						image.height,
						image.hash
					));
				}
			}
		}

		List<ArchiveAttachment> attachments = new ArrayList<>();
		if (data.attachments != null) {
			for (ArchiveAttachmentData attachment : data.attachments) {
				boolean canDownload = attachment.canDownload == null || attachment.canDownload;
				String downloadUrl = buildAttachmentOpenUrl(server, attachment, summary.channelPath(), summary.entryPath());
				String sizeText = attachment.litematic != null ? attachment.litematic.size : null;
				ArchiveAttachment.YoutubeInfo youtubeInfo = null;
				if (attachment.youtube != null) {
					youtubeInfo = new ArchiveAttachment.YoutubeInfo(
						attachment.youtube.title,
						attachment.youtube.author_name,
						attachment.youtube.author_url
					);
				}
				attachments.add(new ArchiveAttachment(
					attachment.name != null ? attachment.name : "Attachment",
					downloadUrl,
					attachment.contentType,
					canDownload,
					sizeText,
					attachment.description,
					attachment.hash,
					attachment.litematic != null ? new ArchiveAttachment.LitematicInfo(
						attachment.litematic.version,
						attachment.litematic.size,
						attachment.litematic.error
					) : null,
					attachment.schematic != null ? new ArchiveAttachment.SchematicInfo(
						attachment.schematic.version,
						attachment.schematic.size,
						attachment.schematic.error
					) : null,
					attachment.wdl != null ? new ArchiveAttachment.WdlInfo(
						attachment.wdl.version,
						attachment.wdl.error
					) : null,
					youtubeInfo
				));
			}
		}

		List<ArchiveRecordSection> recordSections = toRecordSections(
			data.records,
			getSchemaStyles(server),
			data.styles != null ? data.styles : Map.of()
		);
		String recordMarkdown = ReferenceUtils.transformOutputWithReferencesForWebsiteStyle(
			postToMarkdown(
				data.records,
				data.styles != null ? data.styles : Map.of(),
				getSchemaStyles(server)
			),
			data.references != null ? data.references : List.of(),
			id -> dictionarySummaries != null ? dictionarySummaries.get(id) : null
		);

		long archivedAt = data.archivedAt != null ? data.archivedAt : summary.archivedAt();
		long updatedAt = data.updatedAt != null ? data.updatedAt : summary.updatedAt();
		DiscordPostReference discordPost = toDiscordPostReference(data.post);

		return new ArchivePostDetail(
			summary,
			authors,
			images,
			imageInfos,
			attachments,
			discordPost,
			recordSections,
			recordMarkdown,
			archivedAt,
			updatedAt
		);
	}

	private static String buildPostImageUrl(
		ServerEntry server,
		ArchiveImageData image,
		String channelPath,
		String entryPath
	) {
		if (image == null) {
			return "";
		}
		String fromPath = resolveImagePath(server, image.path, channelPath, entryPath);
		if (!safeTrim(fromPath).isEmpty()) {
			return fromPath;
		}
		if (!safeTrim(image.url).isEmpty()) {
			return image.url;
		}
		return "";
	}

	private static DiscordPostReference toDiscordPostReference(ArchiveDiscordPostReference post) {
		if (post == null) {
			return null;
		}
		List<String> continuing = post.continuingMessageIds != null ? post.continuingMessageIds : List.of();
		return new DiscordPostReference(
			post.forumId,
			post.threadId,
			continuing,
			post.threadURL,
			post.attachmentMessageId,
			post.uploadMessageId
		);
	}

	private static ArchiveDictionaryEntry toDictionaryEntry(
		ServerEntry server,
		String requestedId,
		DictionaryEntryData data,
		Map<String, String> dictionarySummaries,
		Map<String, ArchivePostSummary> postSummariesByCode
	) {
		String safeId = safeTrim(data != null && data.id != null ? data.id : requestedId);
		if (safeId.isEmpty()) {
			throw new CompletionException(new RuntimeException("Dictionary entry id is missing"));
		}
		List<String> terms = data != null && data.terms != null ? data.terms : List.of();
		List<ArchiveReference> references = data != null && data.references != null ? data.references : List.of();
		String definition = data != null && data.definition != null ? data.definition : "";
		String definitionMarkdown = ReferenceUtils.transformOutputWithReferencesForWebsiteStyle(
			definition,
			references,
			id -> dictionarySummaries != null ? dictionarySummaries.get(id) : null
		);
		List<ArchiveDictionaryReference> renderedReferences = toDictionaryReferenceList(references);
		List<String> referencedBy = data != null && data.referencedBy != null ? data.referencedBy : List.of();
		List<ArchiveDictionaryReferencedPost> referencedByPosts = toDictionaryReferencedByPosts(referencedBy, postSummariesByCode);
		long updatedAt = data != null && data.updatedAt != null ? data.updatedAt : 0L;
		String summary = dictionarySummaries != null ? safeTrim(dictionarySummaries.get(safeId)) : "";

		return new ArchiveDictionaryEntry(
			safeId,
			terms,
			summary,
			definitionMarkdown,
			renderedReferences,
			referencedBy,
			referencedByPosts,
			data != null ? data.threadURL : "",
			data != null ? data.statusURL : "",
			updatedAt
		);
	}

	private static List<ArchiveDictionaryReference> toDictionaryReferenceList(List<ArchiveReference> references) {
		if (references == null || references.isEmpty()) {
			return List.of();
		}
		List<ArchiveDictionaryReference> items = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (ArchiveReference reference : references) {
			if (reference == null) {
				continue;
			}
			String label = ReferenceUtils.buildReferenceLabel(reference);
			String url = ReferenceUtils.buildReferenceUrl(reference, null);
			String key = safeTrim(reference.type()) + "|" + safeTrim(label) + "|" + safeTrim(url);
			if (key.isBlank() || !seen.add(key)) {
				continue;
			}
			items.add(new ArchiveDictionaryReference(safeTrim(reference.type()), label, url));
		}
		return items;
	}

	private static List<ArchiveDictionaryReferencedPost> toDictionaryReferencedByPosts(
		List<String> referencedByCodes,
		Map<String, ArchivePostSummary> postSummariesByCode
	) {
		if (referencedByCodes == null || referencedByCodes.isEmpty()) {
			return List.of();
		}
		List<ArchiveDictionaryReferencedPost> posts = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String rawCode : referencedByCodes) {
			String code = safeTrim(rawCode);
			if (code.isEmpty()) {
				continue;
			}
			String key = code.toLowerCase(Locale.ROOT);
			if (!seen.add(key)) {
				continue;
			}
			ArchivePostSummary summary = postSummariesByCode != null ? postSummariesByCode.get(key) : null;
			if (summary == null) {
				posts.add(new ArchiveDictionaryReferencedPost(
					"",
					code,
					code,
					"",
					"",
					"",
					0L,
					0L
				));
				continue;
			}
			posts.add(new ArchiveDictionaryReferencedPost(
				safeTrim(summary.id()),
				safeTrim(summary.title()),
				code,
				safeTrim(summary.channelCode()),
				safeTrim(summary.channelName()),
				safeTrim(summary.channelPath()),
				summary.updatedAt(),
				summary.archivedAt()
			));
		}
		return List.copyOf(posts);
	}

	private static String postToMarkdown(JsonObject records, Map<String, StyleInfo> recordStyles, Map<String, StyleInfo> schemaStyles) {
		if (records == null || records.entrySet().isEmpty()) {
			return "";
		}

		StringBuilder markdown = new StringBuilder();
		boolean isFirst = true;
		Set<String> parentsRecorded = new HashSet<>();

		for (Map.Entry<String, JsonElement> entry : records.entrySet()) {
			String key = entry.getKey();
			String[] keyParts = key.split(":");
			for (int i = keyParts.length - 1; i > 0; i--) {
				String parentKey = String.join(":", Arrays.copyOfRange(keyParts, 0, i));
				if (!parentsRecorded.contains(parentKey)) {
					StyleInfo parentStyle = getEffectiveStyle(parentKey, schemaStyles, recordStyles);
					String headerText = safeTrim(parentStyle.headerText);
					if (!headerText.isEmpty()) {
						markdown.append("\n")
							.append("#".repeat(Math.max(1, parentStyle.depth != null ? parentStyle.depth : 1)))
							.append(" ")
							.append(headerText)
							.append("\n");
					}
					parentsRecorded.add(parentKey);
				} else {
					break;
				}
			}

			parentsRecorded.add(key);
			StyleInfo style = getEffectiveStyle(key, schemaStyles, recordStyles);
			String text = submissionRecordToMarkdown(entry.getValue(), style);
			if (!text.isEmpty()) {
				if (!"description".equals(key) || !isFirst) {
					String headerText = safeTrim(style.headerText);
					if (!headerText.isEmpty()) {
						markdown.append("\n")
							.append("#".repeat(Math.max(1, style.depth != null ? style.depth : 1)))
							.append(" ")
							.append(headerText)
							.append("\n");
					}
				}
				isFirst = false;
			}
			markdown.append(text);
		}

		return markdown.toString().trim();
	}

	private static List<ArchiveRecordSection> toRecordSections(JsonObject records, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		List<ArchiveRecordSection> sections = new ArrayList<>();
		if (records == null || records.entrySet().isEmpty()) {
			return sections;
		}

		LinkedHashMap<String, SectionLines> orderedSections = new LinkedHashMap<>();
		boolean isFirst = true;

		for (Map.Entry<String, JsonElement> entry : records.entrySet()) {
			String key = entry.getKey();
			ensureParentSections(key, orderedSections, schemaStyles, recordStyles);

			StyleInfo style = getEffectiveStyle(key, schemaStyles, recordStyles);
			String text = submissionRecordToMarkdown(entry.getValue(), style);
			if (text.isEmpty()) {
				continue;
			}

			String headerText = style.headerText.isBlank() && isFirst
				? "Description"
				: style.headerText;
			SectionLines section = orderedSections.computeIfAbsent(key, k -> new SectionLines(headerText));
			section.addLines(text.split("\\r?\\n"));
			isFirst = false;
		}

		for (SectionLines value : orderedSections.values()) {
			if (!value.lines.isEmpty()) {
				sections.add(new ArchiveRecordSection(value.title, new ArrayList<>(value.lines)));
			}
		}
		return sections;
	}

	private static void ensureParentSections(String key, LinkedHashMap<String, SectionLines> map, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		String[] parts = key.split(":");
		if (parts.length <= 1) return;
		for (int i = 1; i < parts.length; i++) {
			String parentKey = String.join(":", Arrays.copyOfRange(parts, 0, i));
			if (!map.containsKey(parentKey)) {
				StyleInfo parentStyle = getEffectiveStyle(parentKey, schemaStyles, recordStyles);
				map.put(parentKey, new SectionLines(parentStyle.headerText));
			}
		}
	}

	private static StyleInfo getEffectiveStyle(String key, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		StyleInfo style = new StyleInfo();
		style.depth = 2;
		style.headerText = capitalizeFirstSegment(key);
		style.isOrdered = false;

		StyleInfo schemaStyle = schemaStyles != null ? schemaStyles.get(key) : null;
		if (schemaStyle != null) {
			if (schemaStyle.depth != null) style.depth = schemaStyle.depth;
			if (schemaStyle.headerText != null) style.headerText = schemaStyle.headerText;
			if (schemaStyle.isOrdered != null) style.isOrdered = schemaStyle.isOrdered;
		}

		StyleInfo recordStyle = recordStyles != null ? recordStyles.get(key) : null;
		if (recordStyle != null) {
			if (recordStyle.depth != null) style.depth = recordStyle.depth;
			if (recordStyle.headerText != null) style.headerText = recordStyle.headerText;
			if (recordStyle.isOrdered != null) style.isOrdered = recordStyle.isOrdered;
		}
		return style;
	}

	private static String capitalizeFirstSegment(String key) {
		if (key == null || key.isEmpty()) return "";
		String[] parts = key.split(":");
		String target = parts[parts.length - 1];
		if (target.isEmpty()) return "";
		return target.substring(0, 1).toUpperCase(Locale.ROOT) + target.substring(1);
	}

	private static String submissionRecordToMarkdown(JsonElement value, StyleInfo style) {
		if (value == null || value.isJsonNull()) return "";
		StringBuilder markdown = new StringBuilder();
		if (value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				JsonElement item = array.get(i);
				boolean ordered = style.isOrdered != null && style.isOrdered;
				String prefix = ordered ? (i + 1) + ". " : "- ";
				if (item.isJsonPrimitive()) {
					markdown.append(prefix).append(item.getAsString()).append("\n");
				} else if (item.isJsonObject()) {
					JsonObject obj = item.getAsJsonObject();
					markdown.append(prefix);
					if (obj.has("title")) {
						markdown.append(obj.get("title").getAsString()).append("\n");
					}
					if (obj.has("items")) {
						markdown.append(nestedListToMarkdown(obj, ordered ? 2 : 1));
					}
				}
			}
		} else if (value.isJsonObject()) {
			JsonObject obj = value.getAsJsonObject();
			if (obj.has("items")) {
				markdown.append(nestedListToMarkdown(obj, 0));
			} else {
				markdown.append(obj.toString());
			}
		} else {
			markdown.append(value.getAsString());
		}
		return markdown.toString().trim();
	}

	private static String nestedListToMarkdown(JsonObject nestedList, int indentLevel) {
		StringBuilder markdown = new StringBuilder();
		boolean isOrdered = nestedList.has("isOrdered") && nestedList.get("isOrdered").getAsBoolean();
		JsonArray items = nestedList.has("items") && nestedList.get("items").isJsonArray() ? nestedList.getAsJsonArray("items") : new JsonArray();
		String indent = "  ".repeat(Math.max(indentLevel, 0));

		for (int i = 0; i < items.size(); i++) {
			JsonElement item = items.get(i);
			String prefix = isOrdered ? (indent + (i + 1) + ". ") : (indent + "- ");
			if (item.isJsonPrimitive()) {
				markdown.append(prefix).append(item.getAsString()).append("\n");
			} else if (item.isJsonObject()) {
				JsonObject child = item.getAsJsonObject();
				if (child.has("title")) {
					markdown.append(prefix).append(child.get("title").getAsString()).append("\n");
				}
				if (child.has("items")) {
					markdown.append(nestedListToMarkdown(child, indentLevel + (isOrdered ? 2 : 1)));
				}
			}
		}
		return markdown.toString();
	}

	private static CompletableFuture<PersistentIndexData> fetchPersistentIndexAsync(ServerEntry server) {
		String url = buildRawUrl(server, "persistent.idx");
		ServerEntry targetServer = normalizeServer(server);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
			.header("Accept", "application/octet-stream")
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode() + " for " + url));
				}
				byte[] body = response.body();
				if (body == null || body.length == 0) {
					throw new CompletionException(new RuntimeException("Empty persistent index for " + url));
				}
				return body;
			})
			.thenApply(body -> {
				writeOfflineRepoBytes(targetServer, "persistent.idx", body);
				return PersistentIndexParser.parse(body);
			})
			.handle((parsed, throwable) -> {
				if (throwable == null) {
					return parsed;
				}
					byte[] cached = readOfflineRepoBytes(targetServer, "persistent.idx");
					if (cached != null && cached.length > 0) {
						try {
							return PersistentIndexParser.parse(cached);
						} catch (Exception ignored) {
						}
					}
					throw unwrapCompletionException(throwable);
				});
		}

	private static CompletableFuture<ArchiveEntryData> fetchEntryDataAsync(ServerEntry server, String channelPath, String entryPath, long expectedUpdatedAt) {
		String path = normalizePath(channelPath) + "/" + normalizePath(entryPath) + "/data.json";
		ServerEntry targetServer = normalizeServer(server);
		if (expectedUpdatedAt > 0) {
			String cached = readOfflineRepoText(targetServer, path);
			ArchiveEntryData cachedData = tryParseArchiveEntryData(cached);
			if (cachedData != null
				&& cachedData.updatedAt != null
				&& cachedData.updatedAt == expectedUpdatedAt) {
				return CompletableFuture.completedFuture(cachedData);
			}
		}
		return fetchJsonAsync(server, path).thenApply(json -> GSON.fromJson(json, ArchiveEntryData.class));
	}

	private static ArchiveEntryData tryParseArchiveEntryData(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return GSON.fromJson(json, ArchiveEntryData.class);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static CompletableFuture<String> fetchJsonAsync(ServerEntry server, String path) {
		String url = buildRawUrl(server, path);
		ServerEntry targetServer = normalizeServer(server);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode() + " for " + url));
				}
				return response.body();
			})
			.thenApply(body -> {
				writeOfflineRepoText(targetServer, path, body);
				return body;
			})
			.handle((body, throwable) -> {
				if (throwable == null) {
					return body;
				}
					String cached = readOfflineRepoText(targetServer, path);
					if (cached != null) {
						return cached;
					}
				throw unwrapCompletionException(throwable);
			});
	}

	private static String resolveImagePath(ServerEntry server, String path, String channelPath, String entryPath) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String trimmed = path.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.startsWith("https://") || lower.startsWith("http://")) {
			return trimmed;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		String relPath = normalizePath(basePath + "/" + trimmed);
		String extension = extensionFromName(trimmed);
		boolean shouldUseLfs = ServerDictionary.getLfsExtensions().contains(extension);
		return shouldUseLfs ? buildMediaUrl(server, relPath) : buildRawUrl(server, relPath);
	}

	private static String resolveAttachmentPath(ServerEntry server, String path, String channelPath, String entryPath) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		return buildRawUrl(server, basePath + "/" + path);
	}

	private static String buildRawUrl(ServerEntry server, String path) {
		ServerEntry target = normalizeServer(server);
		String owner = target.owner() != null && !target.owner().isBlank() ? target.owner() : "Storage-Tech-2";
		String repo = target.repo() != null && !target.repo().isBlank() ? target.repo() : "Archive";
		String branch = target.branch() != null && !target.branch().isBlank() ? target.branch() : DEFAULT_BRANCH;
		return RAW_BASE + "/" + owner + "/" + repo + "/" + branch + "/" + normalizePath(path);
	}

	private static String buildAttachmentOpenUrl(ServerEntry server, ArchiveAttachmentData attachment, String channelPath, String entryPath) {
		if (attachment == null) return null;
		if (attachment.downloadUrl != null && !attachment.downloadUrl.isBlank()) {
			return attachment.downloadUrl;
		}
		if (attachment.path == null || attachment.path.isBlank()) {
			return attachment.url;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		String relPath = normalizePath(basePath + "/" + attachment.path);
		String extension = extensionFromName(attachment.name);
		boolean shouldUseLfs = ServerDictionary.getLfsExtensions().contains(extension);
		return shouldUseLfs ? buildMediaUrl(server, relPath) : buildRawUrl(server, relPath);
	}

	private static String extensionFromName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot >= fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String buildMediaUrl(ServerEntry server, String path) {
		ServerEntry target = normalizeServer(server);
		String owner = target.owner() != null && !target.owner().isBlank() ? target.owner() : "Storage-Tech-2";
		String repo = target.repo() != null && !target.repo().isBlank() ? target.repo() : "Archive";
		String branch = target.branch() != null && !target.branch().isBlank() ? target.branch() : DEFAULT_BRANCH;
		return MEDIA_BASE + "/" + owner + "/" + repo + "/refs/heads/" + branch + "/" + normalizePath(path);
	}

	private static String normalizePath(String path) {
		if (path == null) {
			return "";
		}
		return path.startsWith("/") ? path.substring(1) : path;
	}

	private static CompletionException unwrapCompletionException(Throwable throwable) {
		if (throwable instanceof CompletionException completion && completion.getCause() != null) {
			return new CompletionException(completion.getCause());
		}
		return new CompletionException(throwable);
	}

	private static Path getOfflineCacheRoot() {
		return FabricLoader.getInstance().getConfigDir().resolve(OFFLINE_CACHE_DIR);
	}

	private static Path getOfflineCacheServerDir(ServerEntry server) {
		return getOfflineCacheRoot().resolve(serverKey(server));
	}

	private static void writeOfflineRepoText(ServerEntry server, String repoPath, String content) {
		if (content == null) {
			return;
		}
		writeOfflineRepoBytes(server, repoPath, content.getBytes(StandardCharsets.UTF_8));
	}

	private static String readOfflineRepoText(ServerEntry server, String repoPath) {
		byte[] bytes = readOfflineRepoBytes(server, repoPath);
		if (bytes == null) {
			return null;
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void writeOfflineApiText(ServerEntry server, String url, String content) {
		if (content == null) {
			return;
		}
		writeOfflineApiBytes(server, url, content.getBytes(StandardCharsets.UTF_8));
	}

	private static String readOfflineApiText(ServerEntry server, String url) {
		byte[] bytes = readOfflineApiBytes(server, url);
		if (bytes == null) {
			return null;
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void writeOfflineRepoBytes(ServerEntry server, String repoPath, byte[] content) {
		if (content == null) {
			return;
		}
		try {
			Path file = resolveOfflinePath(getOfflineCacheServerDir(server).resolve("repo"), normalizePath(repoPath));
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(file, content);
		} catch (Exception e) {
			System.err.println("[OfflineCache] Failed to write cache: " + e.getMessage());
		}
	}

	private static byte[] readOfflineRepoBytes(ServerEntry server, String repoPath) {
		try {
			Path file = resolveOfflinePath(getOfflineCacheServerDir(server).resolve("repo"), normalizePath(repoPath));
			if (!Files.exists(file) || !Files.isRegularFile(file)) {
				return null;
			}
			return Files.readAllBytes(file);
		} catch (Exception e) {
			return null;
		}
	}

	private static void writeOfflineApiBytes(ServerEntry server, String url, byte[] content) {
		if (content == null) {
			return;
		}
		try {
			Path relative = apiRelativePath(url);
			Path file = resolveOfflinePath(getOfflineCacheServerDir(server).resolve("api"), relative.toString());
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(file, content);
		} catch (Exception e) {
			System.err.println("[OfflineCache] Failed to write API cache: " + e.getMessage());
		}
	}

	private static byte[] readOfflineApiBytes(ServerEntry server, String url) {
		try {
			Path relative = apiRelativePath(url);
			Path file = resolveOfflinePath(getOfflineCacheServerDir(server).resolve("api"), relative.toString());
			if (!Files.exists(file) || !Files.isRegularFile(file)) {
				return null;
			}
			return Files.readAllBytes(file);
		} catch (Exception e) {
			return null;
		}
	}

	private static Path apiRelativePath(String url) {
		URI uri = URI.create(url);
		String rawPath = normalizePath(uri.getPath());
		if (rawPath.isBlank()) {
			rawPath = "root";
		}
		if (rawPath.endsWith("/")) {
			rawPath += "index";
		}
		String query = uri.getQuery();
		if (query != null && !query.isBlank()) {
			rawPath += "__q_" + sanitizePathComponent(query);
		}
		if (!rawPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
			rawPath += ".json";
		}
		return Path.of(rawPath);
	}

	private static String sanitizePathComponent(String value) {
		if (value == null || value.isBlank()) {
			return "empty";
		}
		String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
		return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
	}

	private static Path resolveOfflinePath(Path baseDir, String relativePath) {
		Path resolved = baseDir.resolve(relativePath).normalize();
		if (!resolved.startsWith(baseDir.normalize())) {
			throw new IllegalArgumentException("Invalid offline cache path");
		}
		return resolved;
	}

	private static List<String> mapIndicesToList(List<Integer> indices, List<String> values) {
		if (indices == null || indices.isEmpty() || values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> mapped = new ArrayList<>();
		for (Integer index : indices) {
			String value = safeGet(values, index);
			if (value != null && !value.isBlank()) {
				mapped.add(value);
			}
		}
		return mapped;
	}

	private static String[] mapIndicesToArray(List<Integer> indices, List<String> values) {
		List<String> mapped = mapIndicesToList(indices, values);
		return mapped.toArray(new String[0]);
	}

	private static String safeGet(List<String> list, Integer index) {
		if (list == null || index == null) {
			return null;
		}
		if (index < 0 || index >= list.size()) {
			return null;
		}
		return list.get(index);
	}

	private static String getPrimaryCode(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return null;
		}
		return codes.get(0);
	}

	private record ArchiveIndexCache(List<ArchivePostSummary> posts, List<ArchiveChannel> channels) {
	}

	private record SubmissionPage(
		List<ArchivePostSummary> posts,
		int page,
		int pageSize,
		int total,
		int totalPages
	) {
	}

	public record ApiTokenValidationResult(
		boolean valid,
		String message
	) {
	}

	private static class ApiSubmissionTimestampData {
		Long createdMs;
		Long updatedMs;
	}

	private static class ApiSubmissionSummaryData {
		String id;
		String name;
		String status;
		ApiSubmissionTimestampData timestamp;
		List<String> authors;
		List<String> tags;
	}

	private static class ApiSubmissionRevisionData {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		Long timestamp;
		JsonObject records;
		Map<String, StyleInfo> styles;
		List<ArchiveReference> references;
	}

	private static class ApiSubmissionDetailsData {
		String id;
		String name;
		String status;
		String threadId;
		String threadUrl;
		List<ApiTagData> tags;
		List<ApiAuthorData> authors;
		List<ApiImageData> images;
		List<ApiAttachmentData> attachments;
		ApiSubmissionRevisionData revision;
		ApiSubmissionTimestampData timestamp;
		List<ArchiveReference> references;
	}

	private static class ApiTagData {
		@SuppressWarnings("unused")
		String id;
		String name;
	}

	private static class ApiAuthorData {
		String username;
		String displayName;
	}

	private static class ApiImageData {
		String id;
		String description;
		String url;
		Integer width;
		Integer height;
		String hash;
	}

	private static class ApiAttachmentData {
		String id;
		String name;
		String url;
		String downloadUrl;
		String description;
		String contentType;
		String hash;
		ArchiveLitematicInfo litematic;
		ArchiveSchematicInfo schematic;
		ArchiveWdlInfo wdl;
		ArchiveYoutubeInfo youtube;
		Boolean canDownload;
	}

	private static class ArchiveEntryData {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		String name;
		@SuppressWarnings("unused")
		String code;
		List<ArchiveAuthor> authors;
		@SuppressWarnings("unused")
		List<ArchiveAuthor> endorsers;
		@SuppressWarnings("unused")
		List<ArchiveTag> tags;
		List<ArchiveImageData> images;
		List<ArchiveAttachmentData> attachments;
		ArchiveDiscordPostReference post;
		JsonObject records;
		Map<String, StyleInfo> styles;
		List<ArchiveReference> references;
		@SuppressWarnings("unused")
		List<ArchiveReference> author_references;
		Long archivedAt;
		Long updatedAt;
	}

	private static class DictionaryConfigData {
		List<DictionaryIndexData> entries;
	}

	private static class DictionaryIndexData {
		String id;
		List<String> terms;
		String summary;
		Long updatedAt;
	}

	private static class DictionaryEntryData {
		String id;
		List<String> terms;
		String definition;
		String threadURL;
		String statusURL;
		@SuppressWarnings("unused")
		String statusMessageID;
		Long updatedAt;
		List<ArchiveReference> references;
		List<String> referencedBy;
	}

	private static class ArchiveImageData {
		@SuppressWarnings("unused")
		String name;
		@SuppressWarnings("unused")
		String url;
		String description;
		@SuppressWarnings("unused")
		String contentType;
		@SuppressWarnings("unused")
		Boolean canDownload;
		String path;
		Integer width;
		Integer height;
		String hash;
	}

	private static class ArchiveAttachmentData {
		@SuppressWarnings("unused")
		String id;
		String name;
		String url;
		String downloadUrl;
		String description;
		String contentType;
		String hash;
		ArchiveLitematicInfo litematic;
		ArchiveSchematicInfo schematic;
		ArchiveWdlInfo wdl;
		ArchiveYoutubeInfo youtube;
		Boolean canDownload;
		String path;
	}

	private static class ArchiveLitematicInfo {
		String version;
		String size;
		String error;
	}

	private static class ArchiveSchematicInfo {
		String version;
		String size;
		String error;
	}

	private static class ArchiveWdlInfo {
		String version;
		String error;
	}

	private static class ArchiveYoutubeInfo {
		String title;
		String author_name;
		String author_url;
		@SuppressWarnings("unused")
		String thumbnail_url;
		@SuppressWarnings("unused")
		Integer thumbnail_width;
		@SuppressWarnings("unused")
		Integer thumbnail_height;
		@SuppressWarnings("unused")
		Integer width;
		@SuppressWarnings("unused")
		Integer height;
	}

	private static class ArchiveAuthor {
		String username;
		String displayName;
		@SuppressWarnings("unused")
		String url;
		@SuppressWarnings("unused")
		String iconURL;
	}

	private static class ArchiveTag {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		String name;
	}

	private static class ArchiveDiscordPostReference {
		String forumId;
		String threadId;
		List<String> continuingMessageIds;
		String threadURL;
		String attachmentMessageId;
		String uploadMessageId;
	}

	private static class StyleInfo {
		Integer depth;
		String headerText;
		Boolean isOrdered;
	}

	private static class SectionLines {
		final String title;
		final List<String> lines = new ArrayList<>();

		SectionLines(String title) {
			this.title = title != null ? title : "";
		}

		void addLines(String... items) {
			if (items == null) return;
			for (String item : items) {
				if (item == null) continue;
				String trimmed = item.stripTrailing();
				if (!trimmed.isEmpty()) {
					lines.add(trimmed);
				}
			}
		}
	}
}
