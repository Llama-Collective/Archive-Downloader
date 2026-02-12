package com.andrews.archivedownloader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.util.LitematicaAutoLoader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadSettings {
	private static final String CONFIG_FILE = "archive-downloader-settings.json";
	private static final String CONFIG_SUBDIR = "archivedownloader";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String KEY_JOINED_DISCORD_SERVERS = "joinedDiscordServers";
	private static final String KEY_SERVER_API_TOKENS = "serverApiTokens";
	private static DownloadSettings INSTANCE;

	private JsonObject config;

	private DownloadSettings() {
		this.config = loadConfig();
		applyDefaults();
	}

	public static DownloadSettings getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new DownloadSettings();
		}
		return INSTANCE;
	}

	private JsonObject loadConfig() {
		File configFile = getConfigFile();
		File legacyConfigFile = getLegacyConfigFile();
		if (!configFile.exists() && legacyConfigFile.exists()) {
			try {
				File parentDir = configFile.getParentFile();
				if (parentDir != null && !parentDir.exists()) {
					parentDir.mkdirs();
				}
				Files.move(legacyConfigFile.toPath(), configFile.toPath());
			} catch (IOException e) {
				System.err.println("Failed to migrate legacy settings: " + e.getMessage());
			}
		}
		if (!configFile.exists()) {
			return new JsonObject();
		}
		try (FileReader reader = new FileReader(configFile)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			return json != null ? json : new JsonObject();
		} catch (IOException e) {
			System.err.println("Failed to load settings: " + e.getMessage());
			return new JsonObject();
		}
	}

	private void applyDefaults() {
		
		setDefault("downloadPath", Path.of("schematics").toString());
		setDefault("successToastsEnabled", false);
		setDefault("errorToastsEnabled", true);
		setDefault("infoToastsEnabled", false);
		setDefault("warningToastsEnabled", true);
		setDefault("sortOption", "newest");
		setDefault("itemsPerPage", 20);
		setDefault("tagFilter", "");
		setDefault("joinedDiscord", false);
		setDefault("selectedServerId", getDefaultServerId());
		ensureJoinedDiscordMap();
		ensureServerApiTokensMap();
	}

	private void setDefault(String key, Object value) {
		if (!config.has(key)) {
			if (value instanceof String) {
				config.addProperty(key, (String) value);
			} else if (value instanceof Number) {
				config.addProperty(key, (Number) value);
			} else if (value instanceof Boolean) {
				config.addProperty(key, (Boolean) value);
			}
		}
	}

	public String getDownloadPath() {
		Path litematicaBasePath = LitematicaAutoLoader.getLitematicaSchematicBaseDirectory();
		if (litematicaBasePath != null && litematicaBasePath.toFile().exists()) {
			return litematicaBasePath.toString();
		}
		return config.get("downloadPath").getAsString();
	}

	public String getAbsoluteDownloadPath() {
		return getAbsoluteDownloadPath(ServerDictionary.getDefaultServer());
	}

	public String getDownloadPathForServer(ServerEntry server) {
		Path configuredPath = Path.of(getDownloadPath());
		Path root = configuredPath;

		String fileName = configuredPath.getFileName() != null ? configuredPath.getFileName().toString() : "";
		boolean endsWithKnownServer = ServerDictionary.getServers().stream()
			.anyMatch(entry -> entry.downloadFolder() != null && !entry.downloadFolder().isBlank()
				&& entry.downloadFolder().equalsIgnoreCase(fileName));

		if (endsWithKnownServer && configuredPath.getParent() != null) {
			root = configuredPath.getParent();
		}

		String folder = server != null && server.downloadFolder() != null && !server.downloadFolder().isBlank()
			? server.downloadFolder()
			: fileName;

		return root.resolve(folder).toString();
	}

	public String getAbsoluteDownloadPath(ServerEntry server) {
		Path gamePath = FabricLoader.getInstance().getGameDir();
		return gamePath.resolve(getDownloadPathForServer(server)).toString();
	}

	public String getGameDirectory() {
		return FabricLoader.getInstance().getGameDir().toString();
	}

	private boolean isToastEnabled(String type) {
		return config.get(type + "ToastsEnabled").getAsBoolean();
	}

	public String getSortOption() {
		return config.get("sortOption").getAsString();
	}

	public int getItemsPerPage() {
		return config.get("itemsPerPage").getAsInt();
	}

	public String getTagFilter() {
		return config.get("tagFilter").getAsString();
	}

	private void set(String key, Object value) {
		if (value instanceof String) {
			config.addProperty(key, (String) value);
		} else if (value instanceof Number) {
			config.addProperty(key, (Number) value);
		} else if (value instanceof Boolean) {
			config.addProperty(key, (Boolean) value);
		}
		save();
	}

	public boolean isSuccessToastsEnabled() {
		return isToastEnabled("success");
	}

	public void setSuccessToastsEnabled(boolean enabled) {
		set("successToastsEnabled", enabled);
	}

	public boolean isErrorToastsEnabled() {
		return isToastEnabled("error");
	}

	public void setErrorToastsEnabled(boolean enabled) {
		set("errorToastsEnabled", enabled);
	}

	public boolean isInfoToastsEnabled() {
		return isToastEnabled("info");
	}

	public void setInfoToastsEnabled(boolean enabled) {
		set("infoToastsEnabled", enabled);
	}

	public boolean isWarningToastsEnabled() {
		return isToastEnabled("warning");
	}

	public void setWarningToastsEnabled(boolean enabled) {
		set("warningToastsEnabled", enabled);
	}

	public void setDownloadPath(String path) {
		set("downloadPath", path);
	}

	public void setSortOption(String sortOption) {
		set("sortOption", sortOption);
	}

	public void setItemsPerPage(int itemsPerPage) {
		set("itemsPerPage", itemsPerPage);
	}

	public void setTagFilter(String tagFilter) {
		set("tagFilter", tagFilter != null ? tagFilter : "");
	}

	public boolean hasJoinedDiscord() {
		return hasJoinedDiscord(ServerDictionary.getDefaultServer());
	}

	public void setJoinedDiscord(boolean joined) {
		setJoinedDiscord(ServerDictionary.getDefaultServer(), joined);
	}

	public boolean hasJoinedDiscord(ServerEntry server) {
		ServerEntry target = server != null ? server : ServerDictionary.getDefaultServer();
		String key = getServerKey(target);
		JsonObject map = ensureJoinedDiscordMap();
		if (map.has(key)) {
			return map.get(key).getAsBoolean();
		}
		// Fallback to legacy global flag for compatibility
		return config.has("joinedDiscord") && config.get("joinedDiscord").getAsBoolean();
	}

	public void setJoinedDiscord(ServerEntry server, boolean joined) {
		ServerEntry target = server != null ? server : ServerDictionary.getDefaultServer();
		JsonObject map = ensureJoinedDiscordMap();
		map.addProperty(getServerKey(target), joined);
		config.add(KEY_JOINED_DISCORD_SERVERS, map);
		save();
	}

	public ServerEntry getSelectedServer() {
		String id = config.has("selectedServerId") ? config.get("selectedServerId").getAsString() : getDefaultServerId();
		return ServerDictionary.findById(id).orElse(ServerDictionary.getDefaultServer());
	}

	public void setSelectedServer(ServerEntry server) {
		String id = server != null && server.id() != null && !server.id().isBlank()
			? server.id()
			: getDefaultServerId();
		set("selectedServerId", id);
	}

	public boolean hasApiToken(ServerEntry server) {
		return !getApiToken(server).isBlank();
	}

	public String getApiToken(ServerEntry server) {
		ServerEntry target = server != null ? server : ServerDictionary.getDefaultServer();
		JsonObject map = ensureServerApiTokensMap();
		String key = getServerKey(target);
		if (!map.has(key)) {
			return "";
		}
		return map.get(key).getAsString().trim();
	}

	public void setApiToken(ServerEntry server, String token) {
		ServerEntry target = server != null ? server : ServerDictionary.getDefaultServer();
		JsonObject map = ensureServerApiTokensMap();
		String key = getServerKey(target);
		String normalized = token != null ? token.trim() : "";
		if (normalized.isEmpty()) {
			map.remove(key);
		} else {
			map.addProperty(key, normalized);
		}
		config.add(KEY_SERVER_API_TOKENS, map);
		save();
	}

	private File getConfigFile() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		return configDir.resolve(CONFIG_SUBDIR).resolve(CONFIG_FILE).toFile();
	}

	private File getLegacyConfigFile() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		return configDir.resolve(CONFIG_FILE).toFile();
	}

	private JsonObject ensureJoinedDiscordMap() {
		if (!config.has(KEY_JOINED_DISCORD_SERVERS) || !config.get(KEY_JOINED_DISCORD_SERVERS).isJsonObject()) {
			config.add(KEY_JOINED_DISCORD_SERVERS, new JsonObject());
		}
		return config.getAsJsonObject(KEY_JOINED_DISCORD_SERVERS);
	}

	private JsonObject ensureServerApiTokensMap() {
		if (!config.has(KEY_SERVER_API_TOKENS) || !config.get(KEY_SERVER_API_TOKENS).isJsonObject()) {
			config.add(KEY_SERVER_API_TOKENS, new JsonObject());
		}
		return config.getAsJsonObject(KEY_SERVER_API_TOKENS);
	}

	private String getServerKey(ServerEntry server) {
		if (server == null) return "default";
		if (server.id() != null && !server.id().isBlank()) {
			return server.id().toLowerCase();
		}
		if (server.name() != null && !server.name().isBlank()) {
			return server.name().toLowerCase();
		}
		return "default";
	}

	private String getDefaultServerId() {
		ServerEntry def = ServerDictionary.getDefaultServer();
		if (def != null && def.id() != null && !def.id().isBlank()) {
			return def.id();
		}
		return "default";
	}

	private void save() {
		try {
			File configFile = getConfigFile();
			File parentDir = configFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					System.err.println("Failed to create config directory");
					return;
				}
			}
			try (FileWriter writer = new FileWriter(configFile)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			System.err.println("Failed to save settings: " + e.getMessage());
		}
	}
}
