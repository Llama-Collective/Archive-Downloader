package com.andrews.archivedownloader.network;

import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.config.DownloadSettings;
import com.andrews.archivedownloader.models.ArchiveConfigJson;
import com.andrews.archivedownloader.models.ArchiveAttachment;
import com.andrews.archivedownloader.models.ArchiveChannel;
import com.andrews.archivedownloader.models.ArchiveImageInfo;
import com.andrews.archivedownloader.models.ArchivePostDetail;
import com.andrews.archivedownloader.models.ArchivePostSummary;
import com.andrews.archivedownloader.models.ArchiveRecordSection;
import com.andrews.archivedownloader.models.ArchiveSearchResult;
import com.andrews.archivedownloader.models.DiscordPostReference;
import com.andrews.archivedownloader.models.GlobalTag;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
				.thenApply(data -> toSubmissionPostDetail(targetServer, summary, data));
		}
		return fetchEntryDataAsync(targetServer, summary.channelPath(), summary.entryPath())
			.thenApply(data -> toPostDetail(targetServer, summary, data));
	}

	public static CompletableFuture<ArchivePostDetail> getPostDetails(ArchivePostSummary summary) {
		return getPostDetails(ServerDictionary.getDefaultServer(), summary);
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

	public static void clearCache(ServerEntry server) {
		String key = serverKey(normalizeServer(server));
		CACHED_INDEXES.remove(key);
		INDEX_FUTURES.remove(key);
		CACHED_SCHEMA_STYLES.remove(key);
		CACHED_SUBMISSION_SUMMARIES.remove(key);
	}

	public static void clearCache() {
		CACHED_INDEXES.clear();
		INDEX_FUTURES.clear();
		CACHED_SCHEMA_STYLES.clear();
		CACHED_SUBMISSION_SUMMARIES.clear();
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

	private static CompletableFuture<ArchiveConfigJson> fetchArchiveConfigAsync(ServerEntry server) {
		return fetchJsonAsync(server, "config.json")
			.thenApply(json -> GSON.fromJson(json, ArchiveConfigJson.class));
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
				Map<String, StyleInfo> styles = index.schemaStyles() != null ? index.schemaStyles() : Map.of();
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
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET();
		applyApiAuthorization(builder, server, url);

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
				return parsed;
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

	private static ArchivePostDetail toSubmissionPostDetail(ServerEntry server, ArchivePostSummary summary, ApiSubmissionDetailsData data) {
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
				imageInfos.add(new ArchiveImageInfo(imageUrl, image.description, image.width, image.height));
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
					attachment.litematic != null ? new ArchiveAttachment.LitematicInfo(
						attachment.litematic.version,
						attachment.litematic.size,
						attachment.litematic.error
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

	private static ArchivePostDetail toPostDetail(ServerEntry server, ArchivePostSummary summary, ArchiveEntryData data) {
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
				String url = resolveImagePath(server, image.path, summary.channelPath(), summary.entryPath());
				if (url != null && !url.isEmpty()) {
					images.add(url);
					imageInfos.add(new ArchiveImageInfo(
						url,
						image.description,
						image.width,
						image.height
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
					attachment.litematic != null ? new ArchiveAttachment.LitematicInfo(
						attachment.litematic.version,
						attachment.litematic.size,
						attachment.litematic.error
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
			archivedAt,
			updatedAt
		);
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
					markdown.append(prefix).append(stripUrls(item.getAsString())).append("\n");
				} else if (item.isJsonObject()) {
					JsonObject obj = item.getAsJsonObject();
					markdown.append(prefix);
					if (obj.has("title")) {
						markdown.append(stripUrls(obj.get("title").getAsString())).append("\n");
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
				markdown.append(stripUrls(obj.toString()));
			}
		} else {
			markdown.append(stripUrls(value.getAsString()));
		}
		return stripUrls(markdown.toString().trim());
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
				markdown.append(prefix).append(stripUrls(item.getAsString())).append("\n");
			} else if (item.isJsonObject()) {
				JsonObject child = item.getAsJsonObject();
				if (child.has("title")) {
					markdown.append(prefix).append(stripUrls(child.get("title").getAsString())).append("\n");
				}
				if (child.has("items")) {
					markdown.append(nestedListToMarkdown(child, indentLevel + (isOrdered ? 2 : 1)));
				}
			}
		}
		return stripUrls(markdown.toString());
	}

	private static String stripUrls(String text) {
		if (text == null || text.isEmpty()) return "";
		String withoutMarkdownLinks = text.replaceAll("\\[([^\\]]+)\\]\\(https?://[^\\s)]+\\)", "$1 (link removed)");
		return withoutMarkdownLinks.replaceAll("https?://\\S+", "(link removed)");
	}

	private static CompletableFuture<PersistentIndexData> fetchPersistentIndexAsync(ServerEntry server) {
		String url = buildRawUrl(server, "persistent.idx");
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
				return PersistentIndexParser.parse(body);
			});
	}

	private static CompletableFuture<ArchiveEntryData> fetchEntryDataAsync(ServerEntry server, String channelPath, String entryPath) {
		String path = normalizePath(channelPath) + "/" + normalizePath(entryPath) + "/data.json";
		return fetchJsonAsync(server, path).thenApply(json -> GSON.fromJson(json, ArchiveEntryData.class));
	}

	private static CompletableFuture<String> fetchJsonAsync(ServerEntry server, String path) {
		String url = buildRawUrl(server, path);
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
			});
	}

	private static String resolveImagePath(ServerEntry server, String path, String channelPath, String entryPath) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		return buildRawUrl(server, basePath + "/" + path);
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

	private static class PersistentIndexParser {
		private static final int SUPPORTED_VERSION = 1;

		static PersistentIndexData parse(byte[] buffer) {
			ByteBuffer data = ByteBuffer.wrap(buffer);
			data.order(ByteOrder.BIG_ENDIAN);

			int version = Short.toUnsignedInt(data.getShort());
			if (version != SUPPORTED_VERSION) {
				throw new CompletionException(new IllegalArgumentException("Unsupported persistent index version: " + version));
			}
			long updatedAt = data.getLong();

			data.order(ByteOrder.LITTLE_ENDIAN);
			List<String> allTags = readStringList(data);
			List<String> allAuthors = readStringList(data);
			List<String> allCategories = readStringList(data);

			int schemaStylesLength = data.getInt();
			if (schemaStylesLength < 0) {
				long unsigned = Integer.toUnsignedLong(schemaStylesLength);
				throw new CompletionException(new IllegalArgumentException("Invalid schema styles length: " + unsigned));
			}
			byte[] stylesBytes = new byte[schemaStylesLength];
			data.get(stylesBytes);
			Map<String, StyleInfo> schemaStyles = parseStyles(stylesBytes);

			List<PersistentChannel> channels = new ArrayList<>();
			while (data.hasRemaining()) {
				channels.add(readChannel(data));
			}

			return new PersistentIndexData(updatedAt, allTags, allAuthors, allCategories, schemaStyles, channels);
		}

		private static List<String> readStringList(ByteBuffer buffer) {
			int count = Short.toUnsignedInt(buffer.getShort());
			List<String> values = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				values.add(readString(buffer));
			}
			return values;
		}

		private static PersistentChannel readChannel(ByteBuffer buffer) {
			String code = readString(buffer);
			String name = readString(buffer);
			String description = readString(buffer);
			int category = Short.toUnsignedInt(buffer.getShort());

			int tagCount = Short.toUnsignedInt(buffer.getShort());
			List<Integer> tags = new ArrayList<>(tagCount);
			for (int i = 0; i < tagCount; i++) {
				tags.add(Short.toUnsignedInt(buffer.getShort()));
			}

			String path = readString(buffer);

			long entriesCountUnsigned = Integer.toUnsignedLong(buffer.getInt());
			if (entriesCountUnsigned > Integer.MAX_VALUE) {
				throw new CompletionException(new IllegalArgumentException("Channel entries exceed max int: " + entriesCountUnsigned));
			}
			int entriesCount = (int) entriesCountUnsigned;

			List<PersistentEntry> entries = new ArrayList<>(entriesCount);
			for (int i = 0; i < entriesCount; i++) {
				entries.add(readEntry(buffer));
			}

			return new PersistentChannel(code, name, description, category, tags, path, entries);
		}

		private static PersistentEntry readEntry(ByteBuffer buffer) {
			String id = readString(buffer);
			List<String> codes = parseCodes(readString(buffer));
			String name = readString(buffer);

			int authorCount = Short.toUnsignedInt(buffer.getShort());
			List<Integer> authors = new ArrayList<>(authorCount);
			for (int i = 0; i < authorCount; i++) {
				authors.add(Short.toUnsignedInt(buffer.getShort()));
			}

			int tagCount = Short.toUnsignedInt(buffer.getShort());
			List<Integer> tags = new ArrayList<>(tagCount);
			for (int i = 0; i < tagCount; i++) {
				tags.add(Short.toUnsignedInt(buffer.getShort()));
			}

			buffer.order(ByteOrder.BIG_ENDIAN);
			long updatedAt = buffer.getLong();
			long archivedAt = buffer.getLong();
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			String path = readString(buffer);

			int mainImageLength = Short.toUnsignedInt(buffer.getShort());
			String mainImagePath = null;
			if (mainImageLength > 0) {
				byte[] bytes = new byte[mainImageLength];
				buffer.get(bytes);
				mainImagePath = new String(bytes, StandardCharsets.UTF_8);
			}

			return new PersistentEntry(id, codes, name, authors, tags, updatedAt, archivedAt, path, mainImagePath);
		}

		private static String readString(ByteBuffer buffer) {
			int length = Short.toUnsignedInt(buffer.getShort());
			if (length == 0) {
				return "";
			}
			byte[] bytes = new byte[length];
			buffer.get(bytes);
			return new String(bytes, StandardCharsets.UTF_8);
		}

		private static List<String> parseCodes(String codesString) {
			if (codesString == null || codesString.isBlank()) {
				return List.of();
			}
			return Arrays.stream(codesString.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		}

		private static Map<String, StyleInfo> parseStyles(byte[] stylesBytes) {
			if (stylesBytes == null || stylesBytes.length == 0) {
				return Map.of();
			}
			String json = new String(stylesBytes, StandardCharsets.UTF_8);
			return GSON.fromJson(json, new TypeToken<Map<String, StyleInfo>>() {}.getType());
		}
	}

	private record PersistentIndexData(
		long updatedAt,
		List<String> allTags,
		List<String> allAuthors,
		List<String> allCategories,
		Map<String, StyleInfo> schemaStyles,
		List<PersistentChannel> channels
	) {
	}

	private record PersistentChannel(
		String code,
		String name,
		String description,
		int category,
		List<Integer> tags,
		String path,
		List<PersistentEntry> entries
	) {
	}

	private record PersistentEntry(
		String id,
		List<String> codes,
		String name,
		List<Integer> authors,
		List<Integer> tags,
		long updatedAt,
		long archivedAt,
		String path,
		String mainImagePath
	) {
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
	}

	private static class ApiAttachmentData {
		String id;
		String name;
		String url;
		String downloadUrl;
		String description;
		String contentType;
		ArchiveLitematicInfo litematic;
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
		Long archivedAt;
		Long updatedAt;
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
	}

	private static class ArchiveAttachmentData {
		@SuppressWarnings("unused")
		String id;
		String name;
		String url;
		String downloadUrl;
		String description;
		String contentType;
		ArchiveLitematicInfo litematic;
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
