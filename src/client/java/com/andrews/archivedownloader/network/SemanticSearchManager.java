package com.andrews.archivedownloader.network;

import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class SemanticSearchManager {
	private static final String DEFAULT_WEBSITE_BASE = "https://llamamc.org/website-template";
	private static final String ONNX_RUNTIME_VERSION = "1.26.0";
	private static final String ONNX_RUNTIME_JAR_URL = "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/"
		+ ONNX_RUNTIME_VERSION + "/onnxruntime-" + ONNX_RUNTIME_VERSION + ".jar";
	private static final String QUERY_PREFIX = "Represent this sentence for searching relevant passages: ";
	private static final int MAX_TOKENS = 128;
	private static final int EMBEDDING_DIMENSION = 256;
	private static final int RESULT_TOPK = 20;
	private static final double MIN_SCORE = 0.25;
	private static final double STD_FACTOR = 1.5;
	private static final double QUANTIZE_START = -0.3;
	private static final double QUANTIZE_END = 0.3;
	private static final Gson GSON = new Gson();
	private static final Type EMBEDDINGS_TYPE = new TypeToken<List<EmbeddingEntryRaw>>() {}.getType();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	private static final Map<String, CompletableFuture<SemanticAssets>> ASSET_FUTURES = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<List<EmbeddingEntry>>> EMBEDDING_FUTURES = new ConcurrentHashMap<>();

	private SemanticSearchManager() {
	}

	static void clearMemoryCache() {
		List<CompletableFuture<SemanticAssets>> assetFutures = new ArrayList<>(ASSET_FUTURES.values());
		ASSET_FUTURES.clear();
		EMBEDDING_FUTURES.clear();
		for (CompletableFuture<SemanticAssets> future : assetFutures) {
			if (future == null) {
				continue;
			}
			if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
				try {
					future.join().close();
				} catch (Exception ignored) {
				}
			} else {
				future.thenAccept(SemanticAssets::close);
			}
		}
	}

	static CompletableFuture<List<SemanticScore>> search(ServerEntry server, String query, long indexUpdatedAt) {
		String trimmed = query != null ? query.trim() : "";
		if (trimmed.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}

		ServerEntry targetServer = ArchiveNetworkManager.normalizeServer(server);
		String websiteBase = DEFAULT_WEBSITE_BASE;

		CompletableFuture<SemanticAssets> assetsFuture = ASSET_FUTURES.computeIfAbsent(websiteBase, key ->
			loadAssets(key).whenComplete((assets, throwable) -> {
				if (throwable != null) {
					ASSET_FUTURES.remove(key);
				}
			})
		);

		String embeddingsVersion = Long.toString(Math.max(0, indexUpdatedAt));
		String embeddingsKey = ArchiveNetworkManager.serverKey(targetServer) + "::" + embeddingsVersion;
		CompletableFuture<List<EmbeddingEntry>> embeddingsFuture = EMBEDDING_FUTURES.computeIfAbsent(embeddingsKey, key ->
			loadEmbeddings(targetServer, embeddingsVersion).whenComplete((entries, throwable) -> {
				if (throwable != null) {
					EMBEDDING_FUTURES.remove(key);
				}
			})
		);

		return assetsFuture.thenCombine(embeddingsFuture, (assets, embeddings) -> new SemanticSearchRequest(assets, embeddings, trimmed))
			.thenApplyAsync(request -> {
				byte[] queryEmbedding = request.assets.embed(QUERY_PREFIX + request.query);
				List<SemanticScore> scored = new ArrayList<>(request.embeddings.size());
				for (EmbeddingEntry entry : request.embeddings) {
					double score = cosineSimilarity(queryEmbedding, entry.embedding, EMBEDDING_DIMENSION);
					scored.add(new SemanticScore(entry.identifier, score));
				}
				return selectSemanticScores(scored);
			});
	}

	private static CompletableFuture<SemanticAssets> loadAssets(String websiteBase) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Path runtimeJar = globalCachePath("onnxruntime", "onnxruntime-" + ONNX_RUNTIME_VERSION + ".jar");
				readCachedOrFetch(runtimeJar, ONNX_RUNTIME_JAR_URL);
				byte[] modelBytes = readCachedOrFetch(
					globalCachePath(websiteBase, "model_quantized.ort"),
					joinUrl(websiteBase, "/models/embeddings/model_quantized.ort")
				);
				String tokenizerJson = new String(readCachedOrFetch(
					globalCachePath(websiteBase, "tokenizer.json"),
					joinUrl(websiteBase, "/models/embeddings/tokenizer.json")
				), StandardCharsets.UTF_8);
				readCachedOrFetch(
					globalCachePath(websiteBase, "config.json"),
					joinUrl(websiteBase, "/models/embeddings/config.json")
				);
				readCachedOrFetch(
					globalCachePath(websiteBase, "tokenizer_config.json"),
					joinUrl(websiteBase, "/models/embeddings/tokenizer_config.json")
				);

				WordPieceTokenizer tokenizer = WordPieceTokenizer.fromJson(tokenizerJson);
				OrtModel model = OrtModel.load(runtimeJar, modelBytes);
				return new SemanticAssets(tokenizer, model);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	private static CompletableFuture<List<EmbeddingEntry>> loadEmbeddings(ServerEntry server, String indexVersion) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String url = ArchiveNetworkManager.buildRawUrl(server, "embeddings.json");
				byte[] bytes = readCachedOrFetch(serverCachePath(server, "embeddings.json"), url, indexVersion);
				List<EmbeddingEntryRaw> rawEntries = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), EMBEDDINGS_TYPE);
				if (rawEntries == null || rawEntries.isEmpty()) {
					return List.of();
				}

				List<EmbeddingEntry> entries = new ArrayList<>(rawEntries.size());
				Base64.Decoder decoder = Base64.getDecoder();
				for (EmbeddingEntryRaw raw : rawEntries) {
					if (raw == null || raw.identifier == null || raw.embedding == null) {
						continue;
					}
					byte[] embedding = decoder.decode(raw.embedding);
					if (embedding.length >= EMBEDDING_DIMENSION) {
						entries.add(new EmbeddingEntry(normalizeIdentifier(raw.identifier), embedding));
					}
				}
				return List.copyOf(entries);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	private static byte[] readCachedOrFetch(Path cachePath, String url) {
		return readCachedOrFetch(cachePath, url, null);
	}

	private static byte[] readCachedOrFetch(Path cachePath, String url, String cacheVersion) {
		boolean hasCached = false;
		boolean cacheIsFresh = false;
		try {
			hasCached = Files.exists(cachePath) && Files.isRegularFile(cachePath) && Files.size(cachePath) > 0;
			cacheIsFresh = hasCached && cacheVersionMatches(cachePath, cacheVersion);
			if (cacheIsFresh) {
				return Files.readAllBytes(cachePath);
			}
		} catch (Exception ignored) {
		}

		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", ArchiveNetworkManager.USER_AGENT)
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
				throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
			}
			Path parent = cachePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(cachePath, response.body());
			writeCacheVersion(cachePath, cacheVersion);
			return response.body();
		} catch (Exception e) {
			try {
				if (hasCached && cacheIsFresh) {
					return Files.readAllBytes(cachePath);
				}
			} catch (Exception ignored) {
			}
			throw new CompletionException(e);
		}
	}

	private static boolean cacheVersionMatches(Path cachePath, String expectedVersion) {
		if (expectedVersion == null || expectedVersion.isBlank()) {
			return true;
		}
		try {
			Path versionPath = cacheVersionPath(cachePath);
			if (!Files.exists(versionPath) || !Files.isRegularFile(versionPath)) {
				return false;
			}
			return expectedVersion.equals(Files.readString(versionPath, StandardCharsets.UTF_8).trim());
		} catch (Exception e) {
			return false;
		}
	}

	private static void writeCacheVersion(Path cachePath, String version) {
		if (version == null || version.isBlank()) {
			return;
		}
		try {
			Files.writeString(cacheVersionPath(cachePath), version, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[SemanticSearch] Failed to write cache version: " + e.getMessage());
		}
	}

	private static Path cacheVersionPath(Path cachePath) {
		Path fileName = cachePath.getFileName();
		String name = fileName != null ? fileName.toString() : "cache";
		return cachePath.resolveSibling(name + ".index-version");
	}

	private static List<SemanticScore> selectSemanticScores(List<SemanticScore> scored) {
		if (scored == null || scored.isEmpty()) {
			return List.of();
		}

		double mean = 0;
		for (SemanticScore item : scored) {
			mean += item.score();
		}
		mean /= scored.size();

		double variance = 0;
		for (SemanticScore item : scored) {
			double diff = item.score() - mean;
			variance += diff * diff;
		}
		variance /= scored.size();

		double std = Math.sqrt(variance);
		List<Double> sortedScores = scored.stream().map(SemanticScore::score).sorted().toList();
		int p90Index = Math.max(0, (int) Math.floor(0.9 * (sortedScores.size() - 1)));
		double p90 = sortedScores.get(p90Index);
		double threshold = Math.max(Math.max(p90, mean + STD_FACTOR * std), MIN_SCORE);

		List<SemanticScore> ranked = scored.stream()
			.sorted(Comparator.comparingDouble(SemanticScore::score).reversed())
			.toList();
		List<SemanticScore> selected = ranked.stream()
			.filter(item -> item.score() >= threshold)
			.limit(RESULT_TOPK)
			.toList();
		return selected.isEmpty() ? ranked.stream().limit(RESULT_TOPK).toList() : selected;
	}

	private static byte[] quantize(float[] embedding) {
		int length = Math.min(EMBEDDING_DIMENSION, embedding.length);
		float[] truncated = new float[EMBEDDING_DIMENSION];
		double norm = 0;
		for (int i = 0; i < length; i++) {
			truncated[i] = embedding[i];
			norm += embedding[i] * embedding[i];
		}
		norm = Math.sqrt(norm);
		if (norm > 0) {
			for (int i = 0; i < length; i++) {
				truncated[i] = (float) (truncated[i] / norm);
			}
		}

		byte[] quantized = new byte[EMBEDDING_DIMENSION];
		double step = (QUANTIZE_END - QUANTIZE_START) / 255.0;
		for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
			int value = (int) Math.round((truncated[i] - QUANTIZE_START) / step - 128.0);
			quantized[i] = (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, value));
		}
		return quantized;
	}

	private static double cosineSimilarity(byte[] vecA, byte[] vecB, int length) {
		long dotProduct = 0;
		long normA = 0;
		long normB = 0;
		int safeLength = Math.min(length, Math.min(vecA.length, vecB.length));
		for (int i = 0; i < safeLength; i++) {
			int a = vecA[i];
			int b = vecB[i];
			dotProduct += (long) a * b;
			normA += (long) a * a;
			normB += (long) b * b;
		}
		if (normA == 0 || normB == 0) {
			return 0;
		}
		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}

	private static String normalizeWebsiteBase(String websiteBase) {
		String normalized = websiteBase != null ? websiteBase.trim() : "";
		if (normalized.isEmpty()) {
			normalized = DEFAULT_WEBSITE_BASE;
		}
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	private static String joinUrl(String base, String path) {
		return normalizeWebsiteBase(base) + (path.startsWith("/") ? path : "/" + path);
	}

	private static Path globalCachePath(String websiteBase, String fileName) {
		Path path = getCacheRoot().resolve("assets");
		for (String segment : readableCacheSegments(websiteBase)) {
			path = path.resolve(segment);
		}
		return path.resolve(fileName);
	}

	private static Path serverCachePath(ServerEntry server, String fileName) {
		return getCacheRoot().resolve("servers").resolve(ArchiveNetworkManager.serverKey(server)).resolve(fileName);
	}

	private static Path getCacheRoot() {
		return FabricLoader.getInstance().getConfigDir().resolve("archivedownloader/semantic-cache");
	}

	private static List<String> readableCacheSegments(String source) {
		String value = normalizeWebsiteBase(source);
		try {
			URI uri = URI.create(value);
			List<String> segments = new ArrayList<>();
			if (uri.getHost() != null && !uri.getHost().isBlank()) {
				segments.add(safeCacheSegment(uri.getHost()));
			}
			String path = uri.getPath();
			if (path != null && !path.isBlank()) {
				for (String segment : path.split("/")) {
					if (!segment.isBlank()) {
						segments.add(safeCacheSegment(segment));
					}
				}
			}
			if (!segments.isEmpty()) {
				return segments;
			}
		} catch (IllegalArgumentException ignored) {
		}
		return List.of(safeCacheSegment(value));
	}

	private static String safeCacheSegment(String value) {
		String normalized = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
		normalized = normalized.replaceAll("[^a-z0-9._-]+", "-");
		normalized = normalized.replaceAll("^-+|-+$", "");
		return normalized.isEmpty() ? "unknown" : normalized;
	}

	private static String normalizeIdentifier(String value) {
		return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
	}

	record SemanticScore(String identifier, double score) {
	}

	private record SemanticSearchRequest(SemanticAssets assets, List<EmbeddingEntry> embeddings, String query) {
	}

	private record EmbeddingEntry(String identifier, byte[] embedding) {
	}

	private static class EmbeddingEntryRaw {
		String identifier;
		String embedding;
	}

	private static final class SemanticAssets {
		private final WordPieceTokenizer tokenizer;
		private final OrtModel model;
		private final AtomicBoolean closed = new AtomicBoolean(false);

		private SemanticAssets(WordPieceTokenizer tokenizer, OrtModel model) {
			this.tokenizer = tokenizer;
			this.model = model;
		}

		private byte[] embed(String text) {
			if (closed.get()) {
				throw new CompletionException(new IllegalStateException("Semantic search model is closed"));
			}
			EncodedInput encoded = tokenizer.encode(text);
			return quantize(model.embed(encoded));
		}

		private void close() {
			if (closed.compareAndSet(false, true)) {
				model.close();
			}
		}
	}

	private static final class OrtModel {
		@SuppressWarnings("unused")
		private final byte[] modelBytes;
		private final Object environment;
		private final Object session;
		private final Method createTensorMethod;
		private final Method getInputNamesMethod;
		private final Method runMethod;
		private final Method resultGetByNameMethod;
		private final Method resultGetByIndexMethod;
		private final Method tensorGetValueMethod;
		private final URLClassLoader loader;
		private final AtomicBoolean closed = new AtomicBoolean(false);

		private OrtModel(
			byte[] modelBytes,
			Object environment,
			Object session,
			Method createTensorMethod,
			Method getInputNamesMethod,
			Method runMethod,
			Method resultGetByNameMethod,
			Method resultGetByIndexMethod,
			Method tensorGetValueMethod,
			URLClassLoader loader
		) {
			this.modelBytes = modelBytes;
			this.environment = environment;
			this.session = session;
			this.createTensorMethod = createTensorMethod;
			this.getInputNamesMethod = getInputNamesMethod;
			this.runMethod = runMethod;
			this.resultGetByNameMethod = resultGetByNameMethod;
			this.resultGetByIndexMethod = resultGetByIndexMethod;
			this.tensorGetValueMethod = tensorGetValueMethod;
			this.loader = loader;
		}

		static OrtModel load(Path runtimeJar, byte[] modelBytes) throws Exception {
			URLClassLoader loader = new URLClassLoader(
				new URL[] { runtimeJar.toUri().toURL() },
				SemanticSearchManager.class.getClassLoader()
			);
			Class<?> environmentClass = Class.forName("ai.onnxruntime.OrtEnvironment", true, loader);
			Class<?> sessionClass = Class.forName("ai.onnxruntime.OrtSession", true, loader);
			Class<?> optionsClass = Class.forName("ai.onnxruntime.OrtSession$SessionOptions", true, loader);
			Class<?> resultClass = Class.forName("ai.onnxruntime.OrtSession$Result", true, loader);
			Class<?> tensorClass = Class.forName("ai.onnxruntime.OnnxTensor", true, loader);

			Object environment = environmentClass.getMethod("getEnvironment").invoke(null);
			Object options = optionsClass.getConstructor().newInstance();
			Method addConfigEntry = optionsClass.getMethod("addConfigEntry", String.class, String.class);
			addConfigEntry.invoke(options, "session.load_model_format", "ORT");
			addConfigEntry.invoke(options, "session.use_ort_model_bytes_directly", "1");
			Object session = environmentClass.getMethod("createSession", byte[].class, optionsClass).invoke(environment, modelBytes, options);

			return new OrtModel(
				modelBytes,
				environment,
				session,
				tensorClass.getMethod("createTensor", environmentClass, Object.class),
				sessionClass.getMethod("getInputNames"),
				sessionClass.getMethod("run", Map.class),
				resultClass.getMethod("get", String.class),
				resultClass.getMethod("get", int.class),
				tensorClass.getMethod("getValue"),
				loader
			);
		}

		private float[] embed(EncodedInput encoded) {
			if (closed.get()) {
				throw new CompletionException(new IllegalStateException("ONNX semantic search session is closed"));
			}
			Object inputIds = null;
			Object attentionMask = null;
			Object tokenTypeIds = null;
			Object result = null;
			try {
				inputIds = createTensor(new long[][] { encoded.inputIds() });
				attentionMask = createTensor(new long[][] { encoded.attentionMask() });
				tokenTypeIds = createTensor(new long[][] { encoded.tokenTypeIds() });

				@SuppressWarnings("unchecked")
				Set<String> inputNames = (Set<String>) getInputNamesMethod.invoke(session);
				Map<String, Object> inputs = new HashMap<>();
				if (inputNames.contains("input_ids")) {
					inputs.put("input_ids", inputIds);
				}
				if (inputNames.contains("attention_mask")) {
					inputs.put("attention_mask", attentionMask);
				}
				if (inputNames.contains("token_type_ids")) {
					inputs.put("token_type_ids", tokenTypeIds);
				}

				result = runMethod.invoke(session, inputs);
				return extractEmbedding(result, encoded.attentionMask());
			} catch (Exception e) {
				throw new CompletionException(e);
			} finally {
				closeQuietly(result);
				closeQuietly(inputIds);
				closeQuietly(attentionMask);
				closeQuietly(tokenTypeIds);
			}
		}

		private Object createTensor(Object value) throws Exception {
			return createTensorMethod.invoke(null, new Object[] { environment, value });
		}

		private float[] extractEmbedding(Object result, long[] attentionMask) throws Exception {
			Object preferred = firstTensor(result, "sentence_embedding", "embeddings", "last_hidden_state", "token_embeddings");
			if (preferred == null) {
				preferred = resultGetByIndexMethod.invoke(result, 0);
			}
			Object value = tensorGetValueMethod.invoke(preferred);
			if (value instanceof float[][] twoDimensional) {
				return normalize(copyRow(twoDimensional[0]));
			}
			if (value instanceof float[][][] threeDimensional) {
				return normalize(meanPool(threeDimensional[0], attentionMask));
			}
			throw new IllegalStateException("Unsupported embedding tensor shape: " + value.getClass().getName());
		}

		private Object firstTensor(Object result, String... names) throws Exception {
			for (String name : names) {
				@SuppressWarnings("unchecked")
				Optional<Object> value = (Optional<Object>) resultGetByNameMethod.invoke(result, name);
				if (value.isPresent()) {
					return value.get();
				}
			}
			return null;
		}

		private static float[] copyRow(float[] row) {
			float[] out = new float[row.length];
			System.arraycopy(row, 0, out, 0, row.length);
			return out;
		}

		private static float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
			if (tokenEmbeddings.length == 0) {
				return new float[0];
			}
			int dimensions = tokenEmbeddings[0].length;
			float[] pooled = new float[dimensions];
			int count = 0;
			for (int token = 0; token < tokenEmbeddings.length && token < attentionMask.length; token++) {
				if (attentionMask[token] == 0) {
					continue;
				}
				count++;
				for (int dim = 0; dim < dimensions; dim++) {
					pooled[dim] += tokenEmbeddings[token][dim];
				}
			}
			if (count > 0) {
				for (int dim = 0; dim < dimensions; dim++) {
					pooled[dim] /= count;
				}
			}
			return pooled;
		}

		private static float[] normalize(float[] vector) {
			double norm = 0;
			for (float value : vector) {
				norm += value * value;
			}
			norm = Math.sqrt(norm);
			if (norm > 0) {
				for (int i = 0; i < vector.length; i++) {
					vector[i] = (float) (vector[i] / norm);
				}
			}
			return vector;
		}

		private static void closeQuietly(Object value) {
			if (value instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (Exception ignored) {
				}
			}
		}

		private void close() {
			if (closed.compareAndSet(false, true)) {
				closeQuietly(session);
				closeQuietly(loader);
			}
		}
	}

	private record EncodedInput(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
	}

	private static final class WordPieceTokenizer {
		private static final int PAD_ID = 0;
		private static final int UNK_ID = 100;
		private static final int CLS_ID = 101;
		private static final int SEP_ID = 102;
		private static final int MAX_INPUT_CHARS_PER_WORD = 100;
		private final Map<String, Integer> vocab;

		private WordPieceTokenizer(Map<String, Integer> vocab) {
			this.vocab = vocab;
		}

		static WordPieceTokenizer fromJson(String tokenizerJson) {
			JsonObject root = GSON.fromJson(tokenizerJson, JsonObject.class);
			JsonObject vocabObject = root.getAsJsonObject("model").getAsJsonObject("vocab");
			Map<String, Integer> vocab = new LinkedHashMap<>();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : vocabObject.entrySet()) {
				vocab.put(entry.getKey(), entry.getValue().getAsInt());
			}
			return new WordPieceTokenizer(vocab);
		}

		EncodedInput encode(String text) {
			List<Integer> tokenIds = new ArrayList<>();
			tokenIds.add(CLS_ID);
			for (String token : basicTokenize(text)) {
				tokenIds.addAll(wordPieceTokenize(token));
				if (tokenIds.size() >= MAX_TOKENS - 1) {
					break;
				}
			}
			if (tokenIds.size() > MAX_TOKENS - 1) {
				tokenIds = new ArrayList<>(tokenIds.subList(0, MAX_TOKENS - 1));
			}
			tokenIds.add(SEP_ID);

			long[] inputIds = new long[MAX_TOKENS];
			long[] attentionMask = new long[MAX_TOKENS];
			long[] tokenTypeIds = new long[MAX_TOKENS];
			for (int i = 0; i < tokenIds.size() && i < MAX_TOKENS; i++) {
				inputIds[i] = tokenIds.get(i);
				attentionMask[i] = 1;
			}
			for (int i = tokenIds.size(); i < MAX_TOKENS; i++) {
				inputIds[i] = PAD_ID;
			}
			return new EncodedInput(inputIds, attentionMask, tokenTypeIds);
		}

		private List<Integer> wordPieceTokenize(String token) {
			if (token.length() > MAX_INPUT_CHARS_PER_WORD) {
				return List.of(UNK_ID);
			}
			List<Integer> pieces = new ArrayList<>();
			int start = 0;
			while (start < token.length()) {
				int end = token.length();
				Integer currentId = null;
				while (start < end) {
					String sub = token.substring(start, end);
					if (start > 0) {
						sub = "##" + sub;
					}
					currentId = vocab.get(sub);
					if (currentId != null) {
						break;
					}
					end--;
				}
				if (currentId == null) {
					return List.of(UNK_ID);
				}
				pieces.add(currentId);
				start = end;
			}
			return pieces;
		}

		private static List<String> basicTokenize(String text) {
			String normalized = normalizeForBert(text);
			List<String> tokens = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			for (int offset = 0; offset < normalized.length();) {
				int codePoint = normalized.codePointAt(offset);
				offset += Character.charCount(codePoint);
				if (Character.isWhitespace(codePoint)) {
					flushToken(tokens, current);
				} else if (isChineseChar(codePoint)) {
					flushToken(tokens, current);
					tokens.add(new String(Character.toChars(codePoint)));
				} else if (isPunctuation(codePoint)) {
					flushToken(tokens, current);
					tokens.add(new String(Character.toChars(codePoint)));
				} else {
					current.appendCodePoint(codePoint);
				}
			}
			flushToken(tokens, current);
			return tokens;
		}

		private static void flushToken(List<String> tokens, StringBuilder current) {
			if (!current.isEmpty()) {
				tokens.add(current.toString());
				current.setLength(0);
			}
		}

		private static String normalizeForBert(String text) {
			if (text == null || text.isBlank()) {
				return "";
			}
			String lower = text.toLowerCase(Locale.ROOT);
			String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
			StringBuilder cleaned = new StringBuilder();
			for (int offset = 0; offset < decomposed.length();) {
				int codePoint = decomposed.codePointAt(offset);
				offset += Character.charCount(codePoint);
				int type = Character.getType(codePoint);
				if (type == Character.NON_SPACING_MARK || Character.isISOControl(codePoint)) {
					continue;
				}
				cleaned.appendCodePoint(codePoint);
			}
			return cleaned.toString();
		}

		private static boolean isPunctuation(int codePoint) {
			if ((codePoint >= 33 && codePoint <= 47)
				|| (codePoint >= 58 && codePoint <= 64)
				|| (codePoint >= 91 && codePoint <= 96)
				|| (codePoint >= 123 && codePoint <= 126)) {
				return true;
			}
			int type = Character.getType(codePoint);
			return type == Character.CONNECTOR_PUNCTUATION
				|| type == Character.DASH_PUNCTUATION
				|| type == Character.START_PUNCTUATION
				|| type == Character.END_PUNCTUATION
				|| type == Character.INITIAL_QUOTE_PUNCTUATION
				|| type == Character.FINAL_QUOTE_PUNCTUATION
				|| type == Character.OTHER_PUNCTUATION;
		}

		private static boolean isChineseChar(int codePoint) {
			return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
				|| (codePoint >= 0x3400 && codePoint <= 0x4DBF)
				|| (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
				|| (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
				|| (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
				|| (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
				|| (codePoint >= 0xF900 && codePoint <= 0xFAFF)
				|| (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
		}
	}
}
