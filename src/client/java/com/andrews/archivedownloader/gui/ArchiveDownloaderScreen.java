package com.andrews.archivedownloader.gui;

import com.andrews.archivedownloader.ArchiveDownloader;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.andrews.archivedownloader.config.DownloadSettings;
import com.andrews.archivedownloader.config.ServerDictionary;
import com.andrews.archivedownloader.config.ServerDictionary.ServerEntry;
import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.gui.widget.ApiTokenPopup;
import com.andrews.archivedownloader.gui.widget.ChannelDescriptionWidget;
import com.andrews.archivedownloader.gui.widget.ChannelFilterPanel;
import com.andrews.archivedownloader.gui.widget.CustomButton;
import com.andrews.archivedownloader.gui.widget.CustomTextField;
import com.andrews.archivedownloader.gui.widget.DiscordJoinPopup;
import com.andrews.archivedownloader.gui.widget.LoadingSpinner;
import com.andrews.archivedownloader.gui.widget.PostDetailPanel;
import com.andrews.archivedownloader.gui.widget.PostGridWidget;
import com.andrews.archivedownloader.gui.widget.ScrollBar;
import com.andrews.archivedownloader.gui.widget.SemanticSearchConsentPopup;
import com.andrews.archivedownloader.gui.widget.TagFilterWidget;
import com.andrews.archivedownloader.gui.widget.UpdateAvailablePopup;
import com.andrews.archivedownloader.models.ArchiveChannel;
import com.andrews.archivedownloader.models.ArchivePostSummary;
import com.andrews.archivedownloader.models.ArchiveSearchResult;
import com.andrews.archivedownloader.models.GlobalTag;
import com.andrews.archivedownloader.network.ArchiveNetworkManager;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.util.TagUtil;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiScreenBase;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.text.UiText;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.platform.UiPlatform;

public class ArchiveDownloaderScreen extends UiScreenBase {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int SIDEBAR_WIDTH = 200;
    private static final int SERVER_DROPDOWN_ITEM_HEIGHT = 18;
    private static final String DISCORD_INVITE_URL = "https://discord.gg/hztJMTsx2m";
    private static final String SUBMISSIONS_URL = "https://discord.com/channels/1375556143186837695/1375575317007040654";
    private static final String INTERNAL_DISCORD_LINK_PATH_PREFIX = "/discord-link";
    private static boolean updatePopupShownThisSession = false;
    private static String sessionSearchQuery = "";
    private static String sessionSelectedChannelPath = null;
    private static boolean sessionShowSubmissionsView = false;
    private static double sessionGridScrollOffset = 0;
    private static String sessionDetailPostId = "";
    private static String sessionDetailPostSlug = "";
    private static double sessionDetailPostScrollOffset = 0;
    private static final Map<String, TagState> sessionTagStates = new HashMap<>();
    private ServerEntry selectedServer = DownloadSettings.getInstance().getSelectedServer();

    private CustomTextField searchField;
    private PostGridWidget postGrid;
    private PostDetailPanel detailPanel;
    private ChannelFilterPanel channelPanel;
    private ChannelDescriptionWidget channelDescriptionWidget;
    private TagFilterWidget tagFilterWidget;
    private CustomButton serverButton;
    private CustomButton channelToggleButton;
    private CustomButton closeButton;
    private CustomButton submissionsButton;
    private LoadingSpinner loadingSpinner;
    private DiscordJoinPopup discordPopup;
    private UpdateAvailablePopup updatePopup;
    private ApiTokenPopup apiTokenPopup;
    private SemanticSearchConsentPopup semanticSearchPopup;
    private String pendingDiscordUrl;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private int itemsPerPage = 20;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;
    private String currentSearchQuery = "";
    private String currentTagFilter = "";
    private String selectedSort = "newest";
    private String selectedChannelPath = null;
    private boolean noResultsFound = false;
    private boolean initialized = false;
    private boolean pendingSessionGridRestore = false;
    private double restoreGridScrollOffset = 0;
    private boolean pendingSessionPostRestore = false;
    private boolean restoringSessionPost = false;
    private String restoreDetailPostId = "";
    private String restoreDetailPostSlug = "";
    private double restoreDetailPostScrollOffset = 0;
    private List<ArchiveChannel> channels = new ArrayList<>();
    private List<ArchivePostSummary> currentPosts = new ArrayList<>();
    private final Map<String, Double> semanticScores = new HashMap<>();
    private int semanticSearchGeneration = 0;
    private int semanticInsertIndex = -1;
    private boolean showDetailOverlay = false;
    private boolean showChannelPanel = false;
    private boolean showServerDropdown = false;
    private boolean showSubmissionsView = false;
    private boolean submissionDataComplete = false;
    private ArchiveChannel hoveredChannel = null;
    private ServerEntry hoveredServer = null;
    private ScrollBar serverDropdownScrollBar;
    private double serverDropdownScrollOffset = 0;
    private int serverDropdownScrollBarX = Integer.MIN_VALUE;
    private int serverDropdownScrollBarY = Integer.MIN_VALUE;
    private int serverDropdownScrollBarHeight = Integer.MIN_VALUE;

    private enum TagState {
        INCLUDE, EXCLUDE
    }

    private final Map<String, TagState> tagStates = new HashMap<>();
    private final Map<String, Integer> tagCounts = new HashMap<>();
    private final Map<String, Integer> baseTagCounts = new HashMap<>();
    private final Map<String, Integer> channelCounts = new HashMap<>();

    public ArchiveDownloaderScreen() {
        super(UiText.of("Litematic Downloader"));
        currentSearchQuery = sessionSearchQuery;
        selectedChannelPath = sessionSelectedChannelPath;
        showSubmissionsView = sessionShowSubmissionsView;
        tagStates.putAll(sessionTagStates);
        restoreGridScrollOffset = Math.max(0, sessionGridScrollOffset);
        pendingSessionGridRestore = restoreGridScrollOffset > 0;
        restoreDetailPostId = safeTrim(sessionDetailPostId);
        restoreDetailPostSlug = safeTrim(sessionDetailPostSlug);
        restoreDetailPostScrollOffset = Math.max(0, sessionDetailPostScrollOffset);
        pendingSessionPostRestore = !restoreDetailPostId.isEmpty() || !restoreDetailPostSlug.isEmpty();
    }

    @Override
    protected void init() {
        super.init();

        hoveredServer = null;
        showServerDropdown = false;
        String previousSearchText = (searchField != null) ? searchField.getValue() : currentSearchQuery;
        if (!canShowSubmissionsToggle()) {
            showSubmissionsView = false;
            submissionDataComplete = false;
        }

        int headerSpacing = 8;
        int closeButtonSize = 20;
        int submissionsWidth = 56;
        int serverButtonWidth = 100;
        int channelButtonWidth = 60;

        loadingSpinner = new LoadingSpinner(this.width / 2 - 16, this.height / 2 - 16);

        if (serverButton == null) {
            serverButton = new CustomButton(
                    PADDING,
                    PADDING,
                    serverButtonWidth,
                    SEARCH_BAR_HEIGHT,
                    UiText.of(getServerButtonLabel()),
                    button -> {
                        showServerDropdown = !showServerDropdown;
                        hoveredServer = null;
                    });
        } else {
            serverButton.setWidth(serverButtonWidth);
            serverButton.setHeight(SEARCH_BAR_HEIGHT);
            serverButton.setX(PADDING);
            serverButton.setY(PADDING);
            serverButton.setMessage(UiText.of(getServerButtonLabel()));
        }

        UiMinecraftClient uiClient = uiClientOrNull();
        if (uiClient != null) {
            int channelButtonX = PADDING + serverButtonWidth + headerSpacing;
            int startX = channelButtonX + channelButtonWidth + headerSpacing;
            int rightReserve = PADDING + closeButtonSize + headerSpacing + submissionsWidth + headerSpacing;
            int availableWidth = Math.max(60, this.width - startX - rightReserve);
            int searchBarWidth = Math.max(120, availableWidth);

            searchField = new CustomTextField(
                    uiClient,
                    startX,
                    PADDING,
                    searchBarWidth,
                    SEARCH_BAR_HEIGHT,
                    UiText.of("Search"));
            searchField.setHint(UiText.of(showSubmissionsView
                    ? "Search submissions, status, authors"
                    : "Search posts, codes, tags"));
            searchField.setOnEnterPressed(this::performSearch);
            searchField.setOnClearPressed(this::performSearch);
            searchField.setOnChanged(() -> {
                currentPage = 1;
                performSearch();
            });
            if (!previousSearchText.isEmpty()) {
                searchField.setValue(previousSearchText);
            }

            submissionsButton = new CustomButton(
                    startX + searchBarWidth + headerSpacing,
                    PADDING,
                    submissionsWidth,
                    SEARCH_BAR_HEIGHT,
                    UiText.of(getSubmissionsButtonLabel()),
                    button -> requestDiscordLink(getSubmissionsUrlForServer()));
        }

        channelToggleButton = new CustomButton(
                PADDING + serverButtonWidth + headerSpacing,
                PADDING,
                channelButtonWidth,
                SEARCH_BAR_HEIGHT,
                UiText.of("Filters"),
                button -> {
                    showChannelPanel = !showChannelPanel;
                    this.init();
                });

        int gridY = PADDING + PADDING / 2 + SEARCH_BAR_HEIGHT;
        int gridHeight = this.height - gridY - PADDING;
        int gridWidth = this.width - PADDING;

        if (postGrid == null) {
            postGrid = new PostGridWidget(PADDING / 2, gridY, gridWidth, gridHeight, this::onPostClick);
            postGrid.setOnEndReached(this::loadNextPage);
            postGrid.setServer(selectedServer);
        } else {
            postGrid.setDimensions(PADDING / 2, gridY, gridWidth, gridHeight);
            postGrid.setOnEndReached(this::loadNextPage);
            postGrid.setServer(selectedServer);
        }

        if (detailPanel == null) {
            detailPanel = new PostDetailPanel(0, 0, this.width, this.height);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
            detailPanel.setOnCloseRequested(() -> showDetailOverlay = false);
            detailPanel.setOnLitematicaLoadSuccess(this::onClose);
            detailPanel.setServer(selectedServer);
        } else {
            detailPanel.setDimensions(0, 0, this.width, this.height);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
            detailPanel.setOnCloseRequested(() -> showDetailOverlay = false);
            detailPanel.setOnLitematicaLoadSuccess(this::onClose);
            detailPanel.setServer(selectedServer);
        }

        if (showChannelPanel) {
            int channelHeight = this.height - (PADDING * 3 + SEARCH_BAR_HEIGHT);
            if (channelPanel == null) {
                channelPanel = new ChannelFilterPanel(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING,
                        channelHeight);
                channelPanel.setOnSelectionChanged(path -> {
                    selectedChannelPath = path;
                    currentPage = 1;
                    resetTagStatesForChannel(path);
                    performSearch();
                });
                channelPanel.setOnHoverChanged(channel -> hoveredChannel = channel);
                channelPanel.setChannels(channels);
                channelPanel.setSelectedChannelPath(selectedChannelPath);
                channelPanel.setChannelCounts(channelCounts);
            } else {
                channelPanel.setDimensions(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING,
                        channelHeight);
                channelPanel.setSelectedChannelPath(selectedChannelPath);
                channelPanel.setChannelCounts(channelCounts);
            }

            if (channelDescriptionWidget == null) {
                channelDescriptionWidget = new ChannelDescriptionWidget();
            }
            if (tagFilterWidget == null) {
                tagFilterWidget = new TagFilterWidget();
                tagFilterWidget.setServer(selectedServer);
                tagFilterWidget.setOnToggle((tag, state) -> {
                    String key = tag != null ? tag.toLowerCase() : "";
                    if (key.isEmpty())
                        return;
                    if (state == null) {
                        tagStates.remove(key);
                    } else {
                        tagStates.put(key,
                                state == TagFilterWidget.TagState.INCLUDE ? TagState.INCLUDE : TagState.EXCLUDE);
                    }
                    currentPage = 1;
                    performSearch();
                });
            } else {
                tagFilterWidget.setServer(selectedServer);
            }
        }

        closeButton = new CustomButton(
                this.width - PADDING - closeButtonSize,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                UiText.of("X"),
                button -> this.onClose());
        closeButton.setRenderAsXIcon(true);
        if (!initialized) {
            initialized = true;
            initializeFirstOpenData();
        } else {
            updatePaginationButtons();
        }
    }

    private void initializeFirstOpenData() {
        ServerDictionary.ensureLoaded().thenRun(() -> {
            UiMinecraftClient client = uiClientOrNull();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                if (!isCurrentScreenInstance()) {
                    return;
                }
                refreshSelectedServerFromSettings();
                maybeShowUpdatePopup();
                loadChannels();
                performSearch();
            });
        });
    }

    private void refreshSelectedServerFromSettings() {
        selectedServer = DownloadSettings.getInstance().getSelectedServer();
        if (selectedServer == null) {
            selectedServer = ServerDictionary.getDefaultServer();
        }
        if (!canUseSubmissionApi(selectedServer)) {
            showSubmissionsView = false;
            submissionDataComplete = false;
        }
        if (serverButton != null) {
            serverButton.setMessage(UiText.literal(getServerButtonLabel()));
        }
        if (submissionsButton != null) {
            submissionsButton.setMessage(UiText.literal(getSubmissionsButtonLabel()));
        }
        if (postGrid != null) {
            postGrid.setServer(selectedServer);
        }
        if (detailPanel != null) {
            detailPanel.setServer(selectedServer);
        }
        if (tagFilterWidget != null) {
            tagFilterWidget.setServer(selectedServer);
        }
    }

    private void maybeShowUpdatePopup() {
        if (updatePopupShownThisSession || updatePopup != null) {
            return;
        }
        String modPageUrl = ServerDictionary.getModPageUrl();
        String latestVersion = ServerDictionary.getLatestVersion();
        String currentVersion = getCurrentModVersion();
        if (modPageUrl == null || modPageUrl.isBlank()) {
            return;
        }
        if (!ServerDictionary.isUpdateAvailable(currentVersion)) {
            return;
        }

        String safeCurrent = currentVersion == null || currentVersion.isBlank() ? "unknown" : currentVersion;
        String safeLatest = latestVersion == null || latestVersion.isBlank() ? "latest" : latestVersion;
        String message = "You are on v" + safeCurrent + ". Version v" + safeLatest
                + " is available. Open the mod page to update.";
        updatePopup = new UpdateAvailablePopup(
                "Update Available",
                message,
                () -> {
                    openUrlSafe(modPageUrl);
                    clearUpdatePopup();
                },
                this::clearUpdatePopup);
        updatePopupShownThisSession = true;
    }

    private String getCurrentModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(ArchiveDownloader.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
                mouseX >= button.getX() &&
                mouseX < button.getX() + button.getWidth() &&
                mouseY >= button.getY() &&
                mouseY < button.getY() + button.getHeight();
    }

    private void performSearch() {
        if (isLoading)
            return;

        currentSearchQuery = searchField != null ? searchField.getValue().trim() : "";
        if (shouldPromptForSemanticSearch(currentSearchQuery)) {
            showSemanticSearchConsentPopup();
            return;
        }
        semanticSearchGeneration++;
        currentTagFilter = "";
        currentPage = 1;
        noResultsFound = false;

        if (detailPanel != null) {
            detailPanel.clear();
        }

        loadPage(false);
    }

    private boolean shouldPromptForSemanticSearch(String query) {
        if (showSubmissionsView || query == null || query.isBlank()) {
            return false;
        }
        DownloadSettings settings = DownloadSettings.getInstance();
        return !settings.hasSemanticSearchConsentDecision() && semanticSearchPopup == null;
    }

    private void showSemanticSearchConsentPopup() {
        semanticSearchPopup = new SemanticSearchConsentPopup(
                () -> {
                    DownloadSettings.getInstance().acceptSemanticSearchDownloads();
                    semanticSearchPopup = null;
                    performSearch();
                },
                () -> {
                    DownloadSettings.getInstance().declineSemanticSearchDownloads();
                    semanticSearchPopup = null;
                    performSearch();
                });
    }

    private void loadPage(boolean append) {
        if (isLoading || isLoadingMore) {
            return;
        }

        ServerEntry requestServer = selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
        if (append) {
            isLoadingMore = true;
        } else {
            isLoading = true;
        }

        List<String> channelFilter = selectedChannelPath != null ? List.of(selectedChannelPath) : null;
        List<String> includeTags = getTagList(TagState.INCLUDE);
        List<String> excludeTags = getTagList(TagState.EXCLUDE);

        CompletableFuture<ArchiveSearchResult> searchFuture;
        if (showSubmissionsView && canUseSubmissionApi(requestServer)) {
            searchFuture = ArchiveNetworkManager.searchSubmissionPosts(
                    requestServer,
                    currentSearchQuery,
                    selectedSort,
                    currentTagFilter,
                    includeTags,
                    excludeTags,
                    channelFilter,
                    currentPage,
                    itemsPerPage);
        } else {
            searchFuture = ArchiveNetworkManager.searchPosts(
                    requestServer,
                    currentSearchQuery,
                    selectedSort,
                    currentTagFilter,
                    includeTags,
                    excludeTags,
                    channelFilter,
                    currentPage,
                    itemsPerPage);
        }

        searchFuture
                .thenAccept(result -> handleSearchResponse(requestServer, result, append))
                .exceptionally(throwable -> {
                    executeOnClient(() -> {
                        isLoading = false;
                        isLoadingMore = false;
                        updatePaginationButtons();

                        String errorMessage = throwable.getMessage();
                        String userMessage;

                        if (errorMessage != null) {
                            if (errorMessage.contains("UnknownHostException") ||
                                    errorMessage.contains("ConnectException") ||
                                    errorMessage.contains("SocketTimeoutException") ||
                                    errorMessage.contains("NoRouteToHostException")) {
                                userMessage = "Network error: No internet connection";
                            } else if (errorMessage.contains("Unauthorized") || errorMessage.contains("401")) {
                                userMessage = "Unauthorized: check your API token";
                            } else if (errorMessage.contains("HTTP error")) {
                                userMessage = "Server error: " + errorMessage;
                            } else {
                                userMessage = "Search failed: " + errorMessage;
                            }
                        } else {
                            userMessage = "Search failed: Unknown error";
                        }

                        System.err.println(userMessage);
                        System.err.println("Error loading posts: " + errorMessage);
                    });
                    return null;
                });
    }

    private void handleSearchResponse(ServerEntry responseServer, ArchiveSearchResult response, boolean append) {
        UiMinecraftClient client = uiClientOrNull();
        if (client != null) {
            if (!isActiveServer(responseServer)) {
                client.execute(() -> {
                    isLoading = false;
                    isLoadingMore = false;
                });
                return;
            }
            client.execute(() -> {
                if (response == null) {
                    isLoading = false;
                    isLoadingMore = false;
                    return;
                }
                boolean semanticWillRun = canRunSemanticSearch(currentSearchQuery);
                List<ArchivePostSummary> previousSemanticPosts = semanticWillRun ? getCurrentSemanticPosts() : List.of();
                Map<String, Double> previousSemanticScores = semanticWillRun && !previousSemanticPosts.isEmpty()
                        ? new HashMap<>(semanticScores)
                        : Map.of();

                totalPages = response.totalPages();
                totalItems = response.totalItems();
                channelCounts.clear();
                if (response.channelCounts() != null) {
                    channelCounts.putAll(response.channelCounts());
                }
                submissionDataComplete = !showSubmissionsView || !channelCounts.isEmpty();

                List<ArchivePostSummary> posts = response.posts();
                if (posts != null) {
                    if (isLoadingMore) {
                        if (semanticInsertIndex >= 0) {
                            int insertIndex = Math.min(semanticInsertIndex, currentPosts.size());
                            currentPosts.addAll(insertIndex, posts);
                            semanticInsertIndex += posts.size();
                        } else {
                            currentPosts.addAll(posts);
                        }
                        if (postGrid != null) {
                            if (semanticInsertIndex >= 0) {
                                double previousScroll = postGrid.getScrollOffset();
                                postGrid.resetPosts(new ArrayList<>(currentPosts));
                                postGrid.setSemanticScores(semanticScores);
                                postGrid.setScrollOffset(previousScroll);
                            } else {
                                postGrid.appendPosts(posts);
                            }
                            postGrid.setExpectedTotalPosts(getExpectedTotalPosts());
                        }
                    } else {
                        currentPosts.clear();
                        currentPosts.addAll(posts);
                        restorePreviousSemanticResults(previousSemanticPosts, previousSemanticScores);
                        if (postGrid != null) {
                            postGrid.resetPosts(new ArrayList<>(currentPosts));
                            postGrid.setSemanticScores(semanticScores);
                            postGrid.setExpectedTotalPosts(getExpectedTotalPosts());
                            maybeRestoreSessionGridScroll();
                        }
                    }
                } else if (!isLoadingMore && postGrid != null) {
                    semanticScores.clear();
                    semanticInsertIndex = -1;
                    postGrid.resetPosts(new ArrayList<>());
                    postGrid.setSemanticScores(Map.of());
                    postGrid.setExpectedTotalPosts(getExpectedTotalPosts());
                    maybeRestoreSessionGridScroll();
                }

                if (showSubmissionsView && channelCounts.isEmpty() && currentPage >= totalPages) {
                    channelCounts.putAll(buildSubmissionStatusCounts(currentPosts));
                    submissionDataComplete = true;
                }
                if (channelPanel != null) {
                    channelPanel.setChannelCounts(channelCounts);
                }
                updateTagCounts(response.tagCounts());
                maybeRestoreSessionPost();

                isLoading = false;
                isLoadingMore = false;
                updatePaginationButtons();

                noResultsFound = currentPosts.isEmpty();
                if (!append) {
                    startSemanticSearch(responseServer, semanticSearchGeneration);
                }
            });
        }
    }

    private void startSemanticSearch(ServerEntry requestServer, int generation) {
        if (!canRunSemanticSearch(currentSearchQuery)) {
            return;
        }

        List<String> channelFilter = selectedChannelPath != null ? List.of(selectedChannelPath) : null;
        List<String> includeTags = getTagList(TagState.INCLUDE);
        List<String> excludeTags = getTagList(TagState.EXCLUDE);
        String query = currentSearchQuery;
        String sort = selectedSort;
        String tag = currentTagFilter;

        ArchiveNetworkManager.searchSemanticPosts(
                requestServer,
                query,
                sort,
                tag,
                includeTags,
                excludeTags,
                channelFilter)
            .thenAccept(result -> handleSemanticSearchResponse(requestServer, generation, query, result))
            .exceptionally(throwable -> {
                System.err.println("[SemanticSearch] Background search failed: " + throwable.getMessage());
                return null;
            });
    }

    private void handleSemanticSearchResponse(ServerEntry responseServer, int generation, String query, ArchiveSearchResult response) {
        UiMinecraftClient client = uiClientOrNull();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (generation != semanticSearchGeneration || !isActiveServer(responseServer) || !query.equals(currentSearchQuery)) {
                return;
            }
            List<ArchivePostSummary> posts = response != null && response.posts() != null ? response.posts() : List.of();
            double previousScroll = postGrid != null ? postGrid.getScrollOffset() : 0;
            removeSemanticResults();
            semanticScores.clear();
            if (posts.isEmpty()) {
                rebuildTagCountsWithSemanticPosts();
                if (postGrid != null) {
                    postGrid.resetPosts(new ArrayList<>(currentPosts));
                    postGrid.setSemanticScores(Map.of());
                    postGrid.setExpectedTotalPosts(getExpectedTotalPosts());
                    postGrid.setScrollOffset(previousScroll);
                }
                noResultsFound = currentPosts.isEmpty();
                return;
            }

            semanticInsertIndex = currentPosts.size();
            currentPosts.addAll(posts);
            if (response.semanticScores() != null) {
                semanticScores.putAll(response.semanticScores());
            }
            rebuildTagCountsWithSemanticPosts();
            if (postGrid != null) {
                postGrid.resetPosts(new ArrayList<>(currentPosts));
                postGrid.setSemanticScores(semanticScores);
                postGrid.setExpectedTotalPosts(getExpectedTotalPosts());
                postGrid.setScrollOffset(previousScroll);
            }
            noResultsFound = currentPosts.isEmpty();
        });
    }

    private boolean canRunSemanticSearch(String query) {
        return !showSubmissionsView && DownloadSettings.getInstance().isSemanticSearchEnabled()
                && query != null && !query.isBlank();
    }

    private List<ArchivePostSummary> getCurrentSemanticPosts() {
        if (semanticInsertIndex < 0 || semanticInsertIndex >= currentPosts.size()) {
            return List.of();
        }
        return new ArrayList<>(currentPosts.subList(semanticInsertIndex, currentPosts.size()));
    }

    private void restorePreviousSemanticResults(List<ArchivePostSummary> posts, Map<String, Double> scores) {
        semanticScores.clear();
        semanticInsertIndex = -1;
        if (posts == null || posts.isEmpty()) {
            return;
        }

        List<ArchivePostSummary> filtered = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ArchivePostSummary post : currentPosts) {
            addPostKeys(seen, post);
        }
        for (ArchivePostSummary post : posts) {
            if (post == null || hasPostKey(seen, post)) {
                continue;
            }
            addPostKeys(seen, post);
            filtered.add(post);
        }
        if (filtered.isEmpty()) {
            return;
        }

        semanticInsertIndex = currentPosts.size();
        currentPosts.addAll(filtered);
        if (scores != null && !scores.isEmpty()) {
            semanticScores.putAll(scores);
        }
    }

    private void removeSemanticResults() {
        if (semanticInsertIndex >= 0 && semanticInsertIndex < currentPosts.size()) {
            currentPosts.subList(semanticInsertIndex, currentPosts.size()).clear();
        }
        semanticInsertIndex = -1;
    }

    private int getExpectedTotalPosts() {
        return Math.max(totalItems + getSemanticResultCount(), currentPosts.size());
    }

    private int getSemanticResultCount() {
        return semanticInsertIndex >= 0 ? Math.max(0, currentPosts.size() - semanticInsertIndex) : 0;
    }

    private boolean hasPostKey(java.util.Set<String> keys, ArchivePostSummary post) {
        String id = normalizePostKey(post != null ? post.id() : null);
        if (!id.isEmpty() && keys.contains("id:" + id)) {
            return true;
        }
        String code = normalizePostKey(post != null ? post.code() : null);
        return !code.isEmpty() && keys.contains("code:" + code);
    }

    private void addPostKeys(java.util.Set<String> keys, ArchivePostSummary post) {
        String id = normalizePostKey(post != null ? post.id() : null);
        if (!id.isEmpty()) {
            keys.add("id:" + id);
        }
        String code = normalizePostKey(post != null ? post.code() : null);
        if (!code.isEmpty()) {
            keys.add("code:" + code);
        }
    }

    private String normalizePostKey(String value) {
        return value != null ? value.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    private void updatePaginationButtons() {
        // no-op with infinite scroll
    }

    private void loadNextPage() {
        if (isLoadingMore || isLoading)
            return;
        if (currentPage >= totalPages)
            return;
        currentPage++;
        loadPage(true);
    }

    private boolean isActiveServer(ServerEntry server) {
        if (server == null) {
            return false;
        }
        ServerEntry active = selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
        if (active.id() != null && server.id() != null) {
            return active.id().equalsIgnoreCase(server.id());
        }
        if (active.name() != null && server.name() != null) {
            return active.name().equalsIgnoreCase(server.name());
        }
        return server == active;
    }

    private ServerEntry getActiveServer() {
        return selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
    }

    private String getServerButtonLabel() {
        ServerEntry server = getActiveServer();
        if (server != null && server.name() != null && !server.name().isBlank()) {
            return server.name();
        }
        return "Select Server";
    }

    private String getDiscordInviteUrlForServer() {
        ServerEntry server = getActiveServer();
        if (server != null && server.discordInviteUrl() != null && !server.discordInviteUrl().isBlank()) {
            return server.discordInviteUrl();
        }
        return DISCORD_INVITE_URL;
    }

    private String getSubmissionsUrlForServer() {
        ServerEntry server = getActiveServer();
        if (server != null && server.submissionsUrl() != null && !server.submissionsUrl().isBlank()) {
            return server.submissionsUrl();
        }
        return SUBMISSIONS_URL;
    }

    private boolean canUseSubmissionApi(ServerEntry server) {
        return ArchiveNetworkManager.hasApiAccessConfigured(server);
    }

    private boolean canShowSubmissionsToggle() {
        return canUseSubmissionApi(getActiveServer());
    }

    private String getSubmissionsButtonLabel() {
        if (!canShowSubmissionsToggle()) {
            return "Submit";
        }
        return showSubmissionsView ? "Review" : "Archive";
    }

    private void toggleSubmissionsView() {
        if (!canShowSubmissionsToggle()) {
            showSubmissionsView = false;
            submissionDataComplete = false;
            return;
        }
        showSubmissionsView = !showSubmissionsView;
        resetViewStateForModeSwitch();
        this.init();
        loadChannels();
        performSearch();
    }

    private void resetViewStateForModeSwitch() {
        hoveredChannel = null;
        selectedChannelPath = null;
        tagStates.clear();
        tagCounts.clear();
        baseTagCounts.clear();
        channelCounts.clear();
        channels = new ArrayList<>();
        currentPosts.clear();
        currentPage = 1;
        totalPages = 1;
        totalItems = 0;
        noResultsFound = false;
        isLoading = false;
        isLoadingMore = false;
        submissionDataComplete = false;
        if (channelPanel != null) {
            channelPanel.setChannels(channels);
            channelPanel.setChannelCounts(channelCounts);
        }
        if (postGrid != null) {
            postGrid.resetPosts(new ArrayList<>());
        }
        if (detailPanel != null) {
            detailPanel.clear();
        }
    }

    private void onServerSelected(ServerEntry server) {
        ServerEntry target = server != null ? server : ServerDictionary.getDefaultServer();
        if (isActiveServer(target)) {
            showServerDropdown = false;
            return;
        }
        selectedServer = target;
        showServerDropdown = false;
        hoveredServer = null;
        hoveredChannel = null;
        selectedChannelPath = null;
        tagStates.clear();
        tagCounts.clear();
        baseTagCounts.clear();
        channelCounts.clear();
        channels = new ArrayList<>();
        currentPosts.clear();
        currentSearchQuery = "";
        currentTagFilter = "";
        currentPage = 1;
        totalPages = 1;
        totalItems = 0;
        noResultsFound = false;
        isLoading = false;
        isLoadingMore = false;
        showSubmissionsView = false;
        submissionDataComplete = false;
        if (searchField != null) {
            searchField.setValue("");
        }

        DownloadSettings.getInstance().setSelectedServer(target);
        if (channelPanel != null) {
            channelPanel.setChannels(channels);
            channelPanel.setChannelCounts(channelCounts);
        }
        if (serverButton != null) {
            serverButton.setMessage(UiText.of(getServerButtonLabel()));
        }
        if (postGrid != null) {
            postGrid.setServer(target);
            postGrid.resetPosts(new ArrayList<>());
        }
        if (detailPanel != null) {
            detailPanel.setServer(target);
            detailPanel.clear();
        }
        if (tagFilterWidget != null) {
            tagFilterWidget.setServer(target);
        }

        this.init();
        loadChannels();
        performSearch();
    }

    private void loadChannels() {
        ServerEntry requestServer = selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
        if (showSubmissionsView) {
            UiMinecraftClient client = uiClientOrNull();
            if (client != null) {
                client.execute(() -> {
                    List<ArchiveChannel> statusChannels = new ArrayList<>();
                    for (String status : ArchiveNetworkManager.getSubmissionStatuses()) {
                        String path = ArchiveNetworkManager.submissionStatusPath(status);
                        String name = ArchiveNetworkManager.submissionStatusLabel(status);
                        int count = channelCounts.containsKey(path) ? channelCounts.get(path) : -1;
                        statusChannels.add(new ArchiveChannel(
                                "submission-status-" + status,
                                name,
                                "-",
                                "Submission Status",
                                path,
                                "Show only submissions in status: " + name,
                                count,
                                List.of()));
                    }
                    channels = statusChannels;
                    if (selectedChannelPath != null) {
                        boolean exists = channels.stream()
                                .anyMatch(channel -> channel != null && selectedChannelPath.equals(channel.path()));
                        if (!exists) {
                            selectedChannelPath = null;
                        }
                    }
                    if (channelPanel != null) {
                        channelPanel.setChannels(channels);
                        channelPanel.setSelectedChannelPath(selectedChannelPath);
                        channelPanel.setChannelCounts(channelCounts);
                    }
                });
            }
            return;
        }
        ArchiveNetworkManager.getGlobalTags(requestServer);
        ArchiveNetworkManager.getChannels(requestServer)
                .thenAccept(list -> {
                    if (!isActiveServer(requestServer)) {
                        return;
                    }
                    UiMinecraftClient client = uiClientOrNull();
                    if (client != null) {
                        client.execute(() -> {
                            channels = list != null ? new ArrayList<>(list) : new ArrayList<>();
                            for (ArchiveChannel channel : channels) {
                                if (channel != null && channel.path() != null) {
                                    channelCounts.putIfAbsent(channel.path(), channel.entryCount());
                                }
                            }
                            if (channelPanel != null) {
                                channelPanel.setChannels(channels);
                                channelPanel.setSelectedChannelPath(selectedChannelPath);
                                channelPanel.setChannelCounts(channelCounts);
                            }
                        });
                    }
                })
                .exceptionally(throwable -> {
                    System.err.println("Failed to load channels: " + throwable.getMessage());
                    return null;
                });
    }

    private void onPostClick(ArchivePostSummary post) {
        if (detailPanel != null && post != null) {
            detailPanel.setDimensions(0, 0, this.width, this.height);
            detailPanel.openPost(post);
            showDetailOverlay = true;
        }
    }

    private void focusSearchField(boolean focused) {
        searchField.setFocused(focused);
        this.setFocused(focused ? searchField : null);
    }

    @Override
    protected void renderScreen(UiRenderContext renderContext, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = showChannelPanel ? SIDEBAR_WIDTH : 0;
        super.renderScreen(renderContext, mouseX, mouseY, delta);

        if (postGrid != null) {
            postGrid.setBlocked(showChannelPanel || showServerDropdown || apiTokenPopup != null || semanticSearchPopup != null);
            postGrid.render(renderContext, mouseX, mouseY, delta);
        }

        if (isLoading) {
            loadingSpinner.render(renderContext, mouseX, mouseY, delta);
        }

        if (noResultsFound) {
            String noResultsText = "No results found :(";
            RenderUtil.drawString(
                    renderContext,
                    UiMinecraftClient.getInstance().uiFont(),
                    noResultsText,
                    leftPanelWidth + PADDING + 20,
                    this.height / 2 + 10,
                    0xFFFFFFFF);
        }

        if (showChannelPanel && channelPanel != null) {
            RenderUtil.fillRect(renderContext, 0, 0, this.width, this.height, 0x55000000);
            channelPanel.render(renderContext, mouseX, mouseY, delta);
            renderChannelDescription(renderContext, mouseX, mouseY, delta);

        }

        // Header controls rendered last so they remain visible and bright even when
        // overlay dimming is active
        if (serverButton != null) {
            serverButton.render(renderContext, mouseX, mouseY, delta);
        }

        if (channelToggleButton != null) {
            channelToggleButton.render(renderContext, mouseX, mouseY, delta);
        }

        if (submissionsButton != null) {
            submissionsButton.render(renderContext, mouseX, mouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(renderContext, mouseX, mouseY, delta);
        }

        if (searchField != null) {
            searchField.render(renderContext, mouseX, mouseY, delta);
        }

        if (showServerDropdown) {
            renderServerDropdown(renderContext, mouseX, mouseY, delta);
        }

        if (showDetailOverlay && detailPanel != null) {
            RenderUtil.fillRect(renderContext, 0, 0, this.width, this.height, 0xAA000000);
            detailPanel.render(renderContext, mouseX, mouseY, delta);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.renderImageViewer(renderContext, mouseX, mouseY, delta);
        }

        if (discordPopup != null) {
            discordPopup.render(renderContext, mouseX, mouseY, delta);
        }
        if (updatePopup != null) {
            updatePopup.render(renderContext, mouseX, mouseY, delta);
        }
        if (apiTokenPopup != null) {
            apiTokenPopup.render(renderContext, mouseX, mouseY, delta);
        }
        if (semanticSearchPopup != null) {
            semanticSearchPopup.render(renderContext, mouseX, mouseY, delta);
        }
    }

    @Override
    protected boolean onMouseClicked(UiMouseEvent mouseEvent, boolean doubled) {
        double mouseX = mouseEvent.x();
        double mouseY = mouseEvent.y();
        int button = mouseEvent.button();
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;

        if (apiTokenPopup != null) {
            return apiTokenPopup.mouseClicked(mouseEvent, doubled);
        }

        if (semanticSearchPopup != null) {
            return semanticSearchPopup.mouseClicked(mouseEvent, doubled);
        }

        if (updatePopup != null) {
            return updatePopup.mouseClicked(mouseEvent, doubled);
        }

        if (discordPopup != null) {
            return discordPopup.mouseClicked(mouseEvent, doubled);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            return detailPanel.mouseClicked(mouseEvent, doubled);
        }

        if (showDetailOverlay && detailPanel != null) {
            if (detailPanel.mouseClicked(mouseEvent, doubled)) {
                return true;
            }
            detailPanel.closeTransientUi();
            showDetailOverlay = false;
            return true;
        }

        if (button == 0 && serverButton != null && isMouseOverButton(serverButton, mouseX, mouseY)) {
            UiMinecraftClient client = uiClientOrNull();
            if (serverButton.active && client != null) {
                client.playButtonDownSound(serverButton);
            }
            showServerDropdown = !showServerDropdown;
            hoveredServer = null;
            return true;
        }

        if (showServerDropdown) {
            if (handleServerDropdownClick(mouseEvent, doubled)) {
                return true;
            }
            showServerDropdown = false;
        }

        if (button == 0 && channelToggleButton != null && isMouseOverButton(channelToggleButton, mouseX, mouseY)) {
            if (channelToggleButton.active) {
                UiMinecraftClient client = uiClientOrNull();
                if (client != null) {
                    client.playButtonDownSound(channelToggleButton);
                }
                showChannelPanel = !showChannelPanel;
                this.init();
            }
            return true;
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        if (button == 0 && submissionsButton != null && isMouseOverButton(submissionsButton, mouseX, mouseY)) {
            if (mouseEvent.shiftDown()) {
                openApiTokenPrompt();
            } else if (canShowSubmissionsToggle()) {
                toggleSubmissionsView();
            } else {
                requestDiscordLink(getSubmissionsUrlForServer());
            }
            return true;
        }

        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                if (mouseEvent.shiftDown()) {
                    focusSearchField(false);
                    showSemanticSearchConsentPopup();
                    return true;
                }
                focusSearchField(true);
                return true;
            } else {
                focusSearchField(false);
            }
        }

        // tagField removed

        if (channelOverlayOpen) {
            if (channelPanel != null && channelPanel.mouseClicked(mouseEvent, doubled)) {
                return true;
            }
            if (channelDescriptionWidget != null && channelDescriptionWidget.mouseClicked(mouseEvent, doubled)) {
                return true;
            }
            if (tagFilterWidget != null && tagFilterWidget.mouseClicked(mouseEvent, doubled)) {
                return true;
            }
            showChannelPanel = false;
            return true;
        }

        if (postGrid != null && postGrid.mouseClicked(mouseEvent, doubled)) {
            return true;
        }

        return super.onMouseClicked(mouseEvent, doubled);
    }

    @Override
    protected boolean onMouseDragged(UiMouseEvent mouseEvent, double offsetX, double offsetY) {
        if (apiTokenPopup != null) {
            return true;
        }
        if (semanticSearchPopup != null) {
            return true;
        }
        if (showServerDropdown) {
            return false;
        }
        if (updatePopup != null) {
            return true;
        }
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseDragged(mouseEvent, offsetX, offsetY)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseDragged(mouseEvent, offsetX, offsetY)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseDragged(mouseEvent, offsetX, offsetY)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(UiMouseEvent mouseEvent) {
        if (apiTokenPopup != null) {
            return true;
        }
        if (semanticSearchPopup != null) {
            return true;
        }
        if (showServerDropdown) {
            return false;
        }
        if (updatePopup != null) {
            return true;
        }
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseReleased(mouseEvent)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseReleased(mouseEvent)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseReleased(mouseEvent)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (apiTokenPopup != null) {
            return apiTokenPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (semanticSearchPopup != null) {
            return semanticSearchPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (showServerDropdown) {
            handleServerDropdownScroll(mouseX, mouseY, verticalAmount);
            return true;
        }
        if (updatePopup != null) {
            return true;
        }
        if (discordPopup != null) {
            if (discordPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (showChannelPanel && channelPanel != null) {
            if (channelPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            if (tagFilterWidget != null && tagFilterWidget.handleScroll(mouseX, mouseY, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (showDetailOverlay && detailPanel != null
                && detailPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (postGrid != null && postGrid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (apiTokenPopup != null) {
            clearApiTokenPopup();
            return false;
        }
        if (semanticSearchPopup != null) {
            DownloadSettings.getInstance().declineSemanticSearchDownloads();
            semanticSearchPopup = null;
            performSearch();
            return false;
        }
        if (showServerDropdown) {
            showServerDropdown = false;
            return false;
        }
        if (updatePopup != null) {
            clearUpdatePopup();
            return false;
        }
        if (discordPopup != null) {
            clearDiscordPopup();
            return false;
        }
        if (detailPanel != null && detailPanel.hasDictionaryPopupOpen()) {
            detailPanel.keyPressed(256, 0, 0); // 256 = GLFW_KEY_ESCAPE
            return false;
        }
        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.keyPressed(256, 0, 0); // 256 = GLFW_KEY_ESCAPE
            return false;
        }
        if (showChannelPanel) {
            showChannelPanel = false;
            this.init();
            return false;
        }
        if (showDetailOverlay) {
            if (detailPanel != null) {
                detailPanel.closeTransientUi();
            }
            showDetailOverlay = false;
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        persistSessionUiState();
        clearUpdatePopup();
        clearDiscordPopup();
        clearApiTokenPopup();
        ArchiveNetworkManager.clearCache();
        super.onClose();
    }

    private void persistSessionUiState() {
        sessionSearchQuery = searchField != null ? searchField.getValue().trim() : currentSearchQuery;
        sessionSelectedChannelPath = selectedChannelPath;
        sessionShowSubmissionsView = showSubmissionsView;
        sessionGridScrollOffset = postGrid != null ? Math.max(0, postGrid.getScrollOffset()) : 0;
        persistSessionPostState();
        sessionTagStates.clear();
        sessionTagStates.putAll(tagStates);
    }

    private void maybeRestoreSessionGridScroll() {
        if (!pendingSessionGridRestore || postGrid == null) {
            return;
        }
        postGrid.setScrollOffset(restoreGridScrollOffset);
        pendingSessionGridRestore = false;
    }

    private void persistSessionPostState() {
        if (!showDetailOverlay || detailPanel == null) {
            clearSessionPostState();
            return;
        }
        ArchivePostSummary currentPost = detailPanel.getCurrentPostSummary();
        if (currentPost == null) {
            clearSessionPostState();
            return;
        }
        sessionDetailPostId = safeTrim(currentPost.id());
        sessionDetailPostSlug = buildPostSlug(currentPost);
        sessionDetailPostScrollOffset = Math.max(0, detailPanel.getScrollOffset());
    }

    private void clearSessionPostState() {
        sessionDetailPostId = "";
        sessionDetailPostSlug = "";
        sessionDetailPostScrollOffset = 0;
    }

    private void maybeRestoreSessionPost() {
        if (!pendingSessionPostRestore || restoringSessionPost || detailPanel == null) {
            return;
        }

        String postId = safeTrim(restoreDetailPostId);
        String postSlug = safeTrim(restoreDetailPostSlug);
        if (postId.isEmpty() && postSlug.isEmpty()) {
            pendingSessionPostRestore = false;
            return;
        }

        ArchivePostSummary cached = findPostSummaryInCurrentPosts(postId, postSlug);
        if (cached != null) {
            restoreSessionPost(cached);
            return;
        }

        ServerEntry requestServer = selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
        restoringSessionPost = true;
        ArchiveNetworkManager.findPostSummary(requestServer, postId, postSlug)
            .thenAccept(summary -> {
                UiMinecraftClient client = uiClientOrNull();
                if (client == null) {
                    return;
                }
                client.execute(() -> {
                    restoringSessionPost = false;
                    if (!pendingSessionPostRestore || !isActiveServer(requestServer)) {
                        return;
                    }
                    if (summary == null) {
                        pendingSessionPostRestore = false;
                        return;
                    }
                    restoreSessionPost(summary);
                });
            })
            .exceptionally(throwable -> {
                UiMinecraftClient client = uiClientOrNull();
                if (client != null) {
                    client.execute(() -> {
                        restoringSessionPost = false;
                        pendingSessionPostRestore = false;
                    });
                } else {
                    restoringSessionPost = false;
                    pendingSessionPostRestore = false;
                }
                return null;
            });
    }

    private ArchivePostSummary findPostSummaryInCurrentPosts(String postId, String postSlug) {
        if (currentPosts == null || currentPosts.isEmpty()) {
            return null;
        }
        for (ArchivePostSummary post : currentPosts) {
            if (post == null) {
                continue;
            }
            String candidateId = safeTrim(post.id());
            if (!postId.isEmpty() && postId.equals(candidateId)) {
                return post;
            }
            if (!postSlug.isEmpty() && postSlug.equals(buildPostSlug(post))) {
                return post;
            }
        }
        return null;
    }

    private void restoreSessionPost(ArchivePostSummary post) {
        if (detailPanel == null || post == null) {
            pendingSessionPostRestore = false;
            restoringSessionPost = false;
            return;
        }
        detailPanel.setDimensions(0, 0, this.width, this.height);
        detailPanel.openPost(post);
        detailPanel.setScrollOffset(restoreDetailPostScrollOffset);
        showDetailOverlay = true;
        pendingSessionPostRestore = false;
        restoringSessionPost = false;
    }

    private static String buildPostSlug(ArchivePostSummary post) {
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

    private static String safeTrim(String value) {
        return value != null ? value.trim() : "";
    }

    private void renderChannelDescription(UiRenderContext renderContext, int mouseX, int mouseY, float delta) {
        if (channelPanel == null)
            return;
        ArchiveChannel channel = hoveredChannel != null ? hoveredChannel
                : channels.stream()
                        .filter(c -> selectedChannelPath != null && selectedChannelPath.equals(c.path()))
                        .findFirst()
                        .orElse(null);

        int desiredWidth = 260;
        int available = this.width
                - (channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 3 : PADDING * 2);
        int boxWidth = Math.min(desiredWidth, available);
        int boxX = channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 2
                : this.width - boxWidth - PADDING;
        if (boxX + boxWidth > this.width - PADDING) {
            boxX = Math.max(PADDING, this.width - boxWidth - PADDING);
        }
        int boxHeight = 60;
        int boxY = PADDING + SEARCH_BAR_HEIGHT + PADDING;

        if (channelDescriptionWidget != null) {
            channelDescriptionWidget.setBounds(boxX, boxY, boxWidth, boxHeight);
            channelDescriptionWidget.setChannel(channel);
            channelDescriptionWidget.render(renderContext, uiClient().uiFont(), mouseX, mouseY);
        }

        int tagY = boxY + boxHeight + UITheme.Dimensions.PADDING;
        int tagHeight = this.height - tagY - PADDING;
        if (tagFilterWidget != null) {
            tagFilterWidget.setBounds(boxX, tagY, boxWidth, tagHeight);
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
            UiMinecraftClient client = uiClientOrNull();
            long windowHandle = client != null ? client.windowHandle() : 0L;
            tagFilterWidget.render(renderContext, uiClient().uiFont(), mouseX, mouseY, delta, windowHandle);
        }
    }

    private void renderServerDropdown(UiRenderContext renderContext, int mouseX, int mouseY, float delta) {
        if (serverButton == null)
            return;
        List<ServerEntry> servers = ServerDictionary.getServers();
        if (servers.isEmpty())
            return;

        ServerDropdownLayout layout = buildServerDropdownLayout(servers);
        syncServerDropdownScroll(layout);
        int baseX = layout.x();
        int baseY = layout.y();
        int width = layout.width();
        int itemHeight = layout.itemHeight();
        int viewportHeight = layout.viewportHeight();
        int contentHeight = layout.contentHeight();
        int listRight = baseX + width - (layout.hasScrollbar() ? UITheme.Dimensions.SCROLLBAR_WIDTH : 0);

        hoveredServer = null;

        RenderUtil.fillRect(renderContext, baseX, baseY - 2, baseX + width, baseY + viewportHeight + 2,
                UITheme.Colors.PANEL_BG_SECONDARY);

        RenderUtil.enableScissor(renderContext, baseX, baseY, baseX + width, baseY + viewportHeight);
        int listStartY = baseY - (int) serverDropdownScrollOffset;
        for (int i = 0; i < servers.size(); i++) {
            ServerEntry server = servers.get(i);
            int itemY = listStartY + i * itemHeight;
            if (itemY + itemHeight <= baseY || itemY >= baseY + viewportHeight) {
                continue;
            }
            boolean hovered = mouseX >= baseX && mouseX < listRight && mouseY >= itemY
                    && mouseY < itemY + itemHeight;
            boolean selected = isActiveServer(server);
            if (hovered) {
                hoveredServer = server;
            }

            int bgColor = selected ? UITheme.Colors.BUTTON_BG : UITheme.Colors.CONTAINER_BG;
            if (hovered) {
                bgColor = UITheme.Colors.BUTTON_BG_HOVER;
            }

            RenderUtil.fillRect(renderContext, baseX + 1, itemY, listRight - 1, itemY + itemHeight, bgColor);
            String serverName = server.name() != null && !server.name().isBlank()
                    ? server.name()
                    : (server.id() != null ? server.id() : "Server");
            RenderUtil.drawString(
                    renderContext,
                    UiMinecraftClient.getInstance().uiFont(),
                    serverName,
                    baseX + UITheme.Dimensions.PADDING,
                    itemY + 4,
                    UITheme.Colors.TEXT_PRIMARY);
        }
        RenderUtil.disableScissor(renderContext);

        if (layout.hasScrollbar() && serverDropdownScrollBar != null) {
            serverDropdownScrollBar.setScrollData(contentHeight, viewportHeight);
            serverDropdownScrollBar.setScrollPercentage(layout.maxScroll() > 0
                    ? serverDropdownScrollOffset / layout.maxScroll()
                    : 0);
            UiMinecraftClient client = uiClientOrNull();
            long windowHandle = client != null ? client.windowHandle() : 0L;
            if (windowHandle != 0L) {
                boolean changed = serverDropdownScrollBar.updateAndRender(renderContext, mouseX, mouseY, delta, windowHandle);
                if (changed || serverDropdownScrollBar.isDragging()) {
                    serverDropdownScrollOffset = serverDropdownScrollBar.getScrollPercentage() * layout.maxScroll();
                }
            } else {
                serverDropdownScrollBar.render(renderContext, mouseX, mouseY, delta);
            }
        }

        ServerEntry descServer = hoveredServer != null ? hoveredServer : getActiveServer();
        renderServerDescriptionBox(renderContext, descServer, baseX, baseY, width, itemHeight, viewportHeight);
    }

    private ServerDropdownLayout buildServerDropdownLayout(List<ServerEntry> servers) {
        int itemHeight = SERVER_DROPDOWN_ITEM_HEIGHT;
        int labelWidth = 0;
        for (ServerEntry server : servers) {
            if (server == null)
                continue;
            String name = server.name() != null ? server.name() : "Server";
            labelWidth = Math.max(labelWidth, uiClient().uiFont().width(name));
        }
        int contentHeight = servers.size() * itemHeight;
        int width = Math.max(140, labelWidth + UITheme.Dimensions.PADDING * 2);
        int x = serverButton != null ? serverButton.getX() : PADDING;
        int y = (serverButton != null ? serverButton.getY() + serverButton.getHeight() : PADDING) + 4;
        int availableHeight = Math.max(itemHeight, this.height - y - PADDING);
        int viewportHeight = Math.min(contentHeight, availableHeight);
        boolean hasScrollbar = contentHeight > viewportHeight;
        if (hasScrollbar) {
            width += UITheme.Dimensions.SCROLLBAR_WIDTH;
        }
        if (x + width > this.width - PADDING) {
            x = Math.max(PADDING, this.width - width - PADDING);
        }
        return new ServerDropdownLayout(x, y, width, itemHeight, contentHeight, viewportHeight, hasScrollbar);
    }

    private record ServerDropdownLayout(int x, int y, int width, int itemHeight, int contentHeight, int viewportHeight,
            boolean hasScrollbar) {
        int maxScroll() {
            return Math.max(0, contentHeight - viewportHeight);
        }
    }

    // Tag rendering handled by TagFilterWidget; this method kept for compatibility.
    private void renderServerDescriptionBox(UiRenderContext renderContext, ServerEntry server, int dropdownX, int dropdownY,
            int dropdownWidth, int itemHeight, int dropdownHeight) {
        if (server == null || server.description() == null || server.description().isBlank()) {
            return;
        }
        int boxPadding = UITheme.Dimensions.PADDING;
        int boxX = dropdownX + dropdownWidth + boxPadding;
        int boxY = dropdownY - 2;
        int maxWidth = this.width - boxX - PADDING;
        if (maxWidth <= 60) {
            return;
        }
        int boxWidth = Math.min(240, maxWidth);
        int textWidth = boxWidth - boxPadding * 2;
        int textHeight = RenderUtil.getWrappedTextHeight(UiMinecraftClient.getInstance().uiFont(), server.description(), textWidth);
        int boxHeight = Math.max(dropdownHeight + 4, textHeight + boxPadding * 2);

        RenderUtil.fillRect(renderContext, boxX, boxY, boxX + boxWidth, boxY + boxHeight, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(renderContext, boxX, boxY, boxX + boxWidth, boxY + 1, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight,
                UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, boxX, boxY, boxX + 1, boxY + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(renderContext, boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight,
                UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawWrappedText(
                renderContext,
                UiMinecraftClient.getInstance().uiFont(),
                server.description(),
                boxX + boxPadding,
                boxY + boxPadding,
                textWidth,
                UITheme.Colors.TEXT_SUBTITLE);
    }

    private boolean handleServerDropdownClick(UiMouseEvent click, boolean doubled) {
        if (!showServerDropdown || serverButton == null) {
            return false;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        List<ServerEntry> servers = ServerDictionary.getServers();
        if (servers.isEmpty()) {
            showServerDropdown = false;
            return false;
        }
        ServerDropdownLayout layout = buildServerDropdownLayout(servers);
        syncServerDropdownScroll(layout);
        if (layout.hasScrollbar() && serverDropdownScrollBar != null && serverDropdownScrollBar.mouseClicked(click, doubled)) {
            serverDropdownScrollOffset = serverDropdownScrollBar.getScrollPercentage() * layout.maxScroll();
            return true;
        }
        int x = layout.x();
        int y = layout.y();
        int width = layout.width();
        int itemHeight = layout.itemHeight();
        int visibleHeight = layout.viewportHeight();
        int listRight = x + width - (layout.hasScrollbar() ? UITheme.Dimensions.SCROLLBAR_WIDTH : 0);

        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + visibleHeight + 2;
        if (!inside) {
            showServerDropdown = false;
            hoveredServer = null;
            return false;
        }

        boolean insideList = mouseX >= x && mouseX < listRight && mouseY >= y && mouseY < y + visibleHeight;
        if (insideList) {
            int index = (int) ((mouseY - y + serverDropdownScrollOffset) / itemHeight);
            if (index >= 0 && index < servers.size()) {
                onServerSelected(servers.get(index));
                return true;
            }
        }
        return true;
    }

    private void syncServerDropdownScroll(ServerDropdownLayout layout) {
        int maxScroll = layout.maxScroll();
        if (maxScroll <= 0) {
            serverDropdownScrollOffset = 0;
        } else {
            serverDropdownScrollOffset = Math.max(0, Math.min(serverDropdownScrollOffset, maxScroll));
        }
        ensureServerDropdownScrollBar(layout);
        if (layout.hasScrollbar() && serverDropdownScrollBar != null) {
            serverDropdownScrollBar.setScrollData(layout.contentHeight(), layout.viewportHeight());
            serverDropdownScrollBar.setScrollPercentage(maxScroll > 0 ? serverDropdownScrollOffset / maxScroll : 0);
        }
    }

    private void ensureServerDropdownScrollBar(ServerDropdownLayout layout) {
        if (!layout.hasScrollbar()) {
            serverDropdownScrollBar = null;
            serverDropdownScrollBarX = Integer.MIN_VALUE;
            serverDropdownScrollBarY = Integer.MIN_VALUE;
            serverDropdownScrollBarHeight = Integer.MIN_VALUE;
            return;
        }
        int x = layout.x() + layout.width() - UITheme.Dimensions.SCROLLBAR_WIDTH;
        int y = layout.y();
        int height = layout.viewportHeight();
        boolean needsNew = serverDropdownScrollBar == null
                || x != serverDropdownScrollBarX
                || y != serverDropdownScrollBarY
                || height != serverDropdownScrollBarHeight;
        if (needsNew) {
            serverDropdownScrollBar = new ScrollBar(x, y, height);
            serverDropdownScrollBarX = x;
            serverDropdownScrollBarY = y;
            serverDropdownScrollBarHeight = height;
        }
    }

    private boolean handleServerDropdownScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!showServerDropdown || serverButton == null) {
            return false;
        }
        List<ServerEntry> servers = ServerDictionary.getServers();
        if (servers.isEmpty()) {
            return false;
        }
        ServerDropdownLayout layout = buildServerDropdownLayout(servers);
        syncServerDropdownScroll(layout);
        boolean inside = mouseX >= layout.x() && mouseX < layout.x() + layout.width()
                && mouseY >= layout.y() - 2 && mouseY < layout.y() + layout.viewportHeight() + 2;
        if (!inside) {
            return false;
        }
        int maxScroll = layout.maxScroll();
        if (maxScroll <= 0) {
            serverDropdownScrollOffset = 0;
            return true;
        }
        serverDropdownScrollOffset = Math.max(0, Math.min(maxScroll, serverDropdownScrollOffset - verticalAmount * 12));
        if (serverDropdownScrollBar != null) {
            serverDropdownScrollBar.setScrollPercentage(serverDropdownScrollOffset / maxScroll);
        }
        return true;
    }

    private List<String> getDisplayedTags() {
        ServerEntry server = getActiveServer();
        if (showSubmissionsView) {
            List<String> collected = new ArrayList<>();
            for (ArchivePostSummary post : currentPosts) {
                if (post == null || post.tags() == null) {
                    continue;
                }
                for (String tag : post.tags()) {
                    if (tag != null && !tag.isBlank() && !collected.contains(tag)) {
                        collected.add(tag);
                    }
                }
            }
            return TagUtil.orderTags(collected, server);
        }
        if (selectedChannelPath != null) {
            List<String> tags = channels.stream()
                    .filter(c -> selectedChannelPath.equals(c.path()))
                    .findFirst()
                    .map(c -> c.availableTags() != null ? c.availableTags() : List.<String>of())
                    .orElse(List.of());
            return TagUtil.orderTags(tags, server);
        }
        boolean hasSearchQuery = currentSearchQuery != null && !currentSearchQuery.isBlank();
        if (hasSearchQuery) {
            List<String> countedTags = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                int count = entry.getValue() != null ? entry.getValue() : 0;
                if (count > 0 && !countedTags.contains(entry.getKey())) {
                    countedTags.add(entry.getKey());
                }
            }
            for (String tag : tagStates.keySet()) {
                if (tag != null && !tag.isBlank() && !countedTags.contains(tag)) {
                    countedTags.add(tag);
                }
            }
            if (!countedTags.isEmpty()) {
                return TagUtil.orderTags(countedTags, server);
            }
        }
        List<String> globalTagNames = ArchiveNetworkManager.getCachedGlobalTags(server).stream()
                .map(GlobalTag::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        return TagUtil.orderTags(globalTagNames, server);
    }

    private List<String> getTagList(TagState state) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, TagState> entry : tagStates.entrySet()) {
            if (entry.getValue() == state) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    private Map<String, Integer> buildSubmissionStatusCounts(List<ArchivePostSummary> posts) {
        Map<String, Integer> counts = new HashMap<>();
        for (String status : ArchiveNetworkManager.getSubmissionStatuses()) {
            counts.put(ArchiveNetworkManager.submissionStatusPath(status), 0);
        }
        if (posts == null || posts.isEmpty()) {
            return counts;
        }
        for (ArchivePostSummary post : posts) {
            if (post == null || post.channelPath() == null || post.channelPath().isBlank()) {
                continue;
            }
            String path = post.channelPath();
            counts.put(path, counts.getOrDefault(path, 0) + 1);
        }
        return counts;
    }

    private void resetTagStatesForChannel(String path) {
        tagStates.clear();
        updateTagCounts();
    }

    private void updateTagCounts() {
        updateTagCounts(null);
    }

    private void updateTagCounts(Map<String, Integer> countsFromSearch) {
        tagCounts.clear();
        boolean shouldHideCounts = showSubmissionsView && !submissionDataComplete;
        if (shouldHideCounts) {
            if (tagFilterWidget != null) {
                tagFilterWidget.setData(getDisplayedTags(), Map.of(), convertTagStates());
            }
            return;
        }
        if (countsFromSearch != null) {
            for (Map.Entry<String, Integer> entry : countsFromSearch.entrySet()) {
                if (entry.getKey() == null)
                    continue;
                String key = entry.getKey().toLowerCase();
                int value = entry.getValue() != null ? entry.getValue() : 0;
                tagCounts.put(key, value);
            }
            baseTagCounts.clear();
            baseTagCounts.putAll(tagCounts);
            mergeTagCountsFromPosts(getCurrentSemanticPosts());
        } else {
            for (ArchivePostSummary post : currentPosts) {
                if (post == null || post.tags() == null)
                    continue;
                for (String tag : post.tags()) {
                    if (tag == null)
                        continue;
                    String key = tag.toLowerCase();
                    tagCounts.put(key, tagCounts.getOrDefault(key, 0) + 1);
                }
            }
        }
        for (String tag : getDisplayedTags()) {
            tagCounts.putIfAbsent(tag.toLowerCase(), 0);
        }
        if (tagFilterWidget != null) {
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
        }
    }

    private void rebuildTagCountsWithSemanticPosts() {
        tagCounts.clear();
        tagCounts.putAll(baseTagCounts);
        mergeTagCountsFromPosts(getCurrentSemanticPosts());
        for (String tag : getDisplayedTags()) {
            tagCounts.putIfAbsent(tag.toLowerCase(), 0);
        }
        if (tagFilterWidget != null) {
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
        }
    }

    private void mergeTagCountsFromPosts(List<ArchivePostSummary> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        for (ArchivePostSummary post : posts) {
            if (post == null || post.tags() == null) {
                continue;
            }
            for (String tag : post.tags()) {
                if (tag == null) {
                    continue;
                }
                String key = tag.toLowerCase();
                if (!key.isBlank()) {
                    tagCounts.put(key, tagCounts.getOrDefault(key, 0) + 1);
                }
            }
        }
    }

    private Map<String, TagFilterWidget.TagState> convertTagStates() {
        Map<String, TagFilterWidget.TagState> map = new HashMap<>();
        for (Map.Entry<String, TagState> entry : tagStates.entrySet()) {
            map.put(entry.getKey(), entry.getValue() == TagState.INCLUDE
                    ? TagFilterWidget.TagState.INCLUDE
                    : TagFilterWidget.TagState.EXCLUDE);
        }
        return map;
    }

    private void openApiTokenPrompt() {
        ServerEntry server = getActiveServer();
        String apiBase = ArchiveNetworkManager.getApiBase(server);
        if (apiBase.isBlank()) {
            System.err.println("Selected server does not provide apiBase in metadata.");
            return;
        }

        DownloadSettings settings = DownloadSettings.getInstance();
        boolean hasExistingToken = settings.hasApiToken(server);
        String serverName = server != null && server.name() != null ? server.name() : "Server";
        apiTokenPopup = new ApiTokenPopup(
                serverName,
                apiBase,
                hasExistingToken,
                tokenInput -> {
                    String token = normalizeApiTokenInput(tokenInput);
                    if (token.isBlank()) {
                        if (apiTokenPopup != null) {
                            apiTokenPopup.setStatus("Token is empty. Paste a token or use Clear.", true);
                        }
                        return;
                    }
                    if (apiTokenPopup != null) {
                        apiTokenPopup.setValidating(true, "Validating token...");
                    }
                    ArchiveNetworkManager.validateApiToken(server, token)
                            .thenAccept(validation -> {
                                UiMinecraftClient client = uiClientOrNull();
                                if (client == null) {
                                    return;
                                }
                                client.execute(() -> {
                                    if (apiTokenPopup == null) {
                                        return;
                                    }
                                    apiTokenPopup.setValidating(false, null);
                                    if (!validation.valid()) {
                                        apiTokenPopup.setStatus(validation.message(), true);
                                        return;
                                    }

                                    settings.setApiToken(server, token);
                                    clearApiTokenPopup();
                                    this.init();
                                    loadChannels();
                                    performSearch();
                                });
                            })
                            .exceptionally(throwable -> {
                                executeOnClient(() -> {
                                    if (apiTokenPopup != null) {
                                        apiTokenPopup.setValidating(false, null);
                                        apiTokenPopup.setStatus(
                                                "Validation failed: " + describeThrowable(throwable), true);
                                    }
                                });
                                return null;
                            });
                },
                () -> {
                    settings.setApiToken(server, "");
                    if (showSubmissionsView) {
                        showSubmissionsView = false;
                    }
                    submissionDataComplete = false;
                    clearApiTokenPopup();
                    this.init();
                    loadChannels();
                    performSearch();
                },
                this::clearApiTokenPopup);
    }

    private String normalizeApiTokenInput(String tokenInput) {
        String token = tokenInput != null ? tokenInput.trim() : "";
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token;
    }

    private String describeThrowable(Throwable throwable) {
        Throwable root = throwable;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        if (root == null || root.getMessage() == null || root.getMessage().isBlank()) {
            return "Unknown error";
        }
        return root.getMessage();
    }

    private void requestDiscordLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        DiscordLinkRequest request = parseDiscordLinkRequest(url);
        String targetUrl = request != null ? request.targetUrl() : url;
        if (targetUrl == null || targetUrl.isBlank()) {
            return;
        }
        ServerEntry server = getActiveServer();
        String defaultInviteUrl = getDiscordInviteUrlForServer();
        String inviteUrl = request != null && request.joinUrl() != null && !request.joinUrl().isBlank()
            ? request.joinUrl()
            : defaultInviteUrl;
        String defaultServerName = server != null && server.name() != null ? server.name() : "this";
        String serverName = request != null && request.serverName() != null && !request.serverName().isBlank()
            ? request.serverName()
            : defaultServerName;
        boolean hasCustomJoinTarget = request != null && request.joinUrl() != null && !request.joinUrl().isBlank();
        if (!hasCustomJoinTarget && DownloadSettings.getInstance().hasJoinedDiscord(server)) {
            openUrlSafe(targetUrl);
            return;
        }

        pendingDiscordUrl = targetUrl;
        String message = "These links live in the " + serverName + " Discord. Please join before continuing.";
        discordPopup = new DiscordJoinPopup(
                "Join " + serverName + " Discord?",
                message,
                () -> {
                    if (!hasCustomJoinTarget) {
                        DownloadSettings.getInstance().setJoinedDiscord(server, true);
                    }
                    openUrlSafe(pendingDiscordUrl);
                    clearDiscordPopup();
                },
                () -> openUrlSafe(inviteUrl),
                this::clearDiscordPopup);
    }

    private DiscordLinkRequest parseDiscordLinkRequest(String rawLink) {
        String trimmed = rawLink != null ? rawLink.trim() : "";
        if (trimmed.isEmpty()) {
            return null;
        }
        String path;
        String query;
        if (trimmed.startsWith("/")) {
            int queryStart = trimmed.indexOf('?');
            path = queryStart >= 0 ? trimmed.substring(0, queryStart) : trimmed;
            if (queryStart >= 0) {
                query = trimmed.substring(queryStart + 1);
            } else {
                query = "";
            }
            int hash = query.indexOf('#');
            if (hash >= 0) {
                query = query.substring(0, hash);
            }
        } else {
            try {
                URI uri = URI.create(trimmed);
                path = uri.getPath() != null ? uri.getPath() : "";
                query = uri.getRawQuery() != null ? uri.getRawQuery() : "";
            } catch (Exception ignored) {
                return null;
            }
        }
        if (!path.startsWith(INTERNAL_DISCORD_LINK_PATH_PREFIX)) {
            return null;
        }
        String targetUrl = extractQueryValue(query, "url");
        if (targetUrl.isBlank()) {
            return null;
        }
        String joinUrl = extractQueryValue(query, "join");
        String serverName = extractQueryValue(query, "server");
        return new DiscordLinkRequest(targetUrl, joinUrl, serverName);
    }

    private static String extractQueryValue(String query, String keyToFind) {
        if (query == null || query.isBlank() || keyToFind == null || keyToFind.isBlank()) {
            return "";
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).trim();
            if (!keyToFind.equalsIgnoreCase(key)) {
                continue;
            }
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8).trim();
        }
        return "";
    }

    private void openUrlSafe(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            UiPlatform.openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open link: " + e.getMessage());
        }
    }

    private void clearDiscordPopup() {
        discordPopup = null;
        pendingDiscordUrl = null;
    }

    private void clearUpdatePopup() {
        updatePopup = null;
    }

    private void clearApiTokenPopup() {
        if (apiTokenPopup != null) {
            apiTokenPopup.dismiss();
        }
        apiTokenPopup = null;
    }

    private record DiscordLinkRequest(String targetUrl, String joinUrl, String serverName) {
    }
}
