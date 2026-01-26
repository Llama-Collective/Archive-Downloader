package com.andrews.st2downloader.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.andrews.st2downloader.config.DownloadSettings;
import com.andrews.st2downloader.config.ServerDictionary;
import com.andrews.st2downloader.config.ServerDictionary.ServerEntry;
import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.gui.widget.ChannelDescriptionWidget;
import com.andrews.st2downloader.gui.widget.ChannelFilterPanel;
import com.andrews.st2downloader.gui.widget.CustomButton;
import com.andrews.st2downloader.gui.widget.CustomTextField;
import com.andrews.st2downloader.gui.widget.DiscordJoinPopup;
import com.andrews.st2downloader.gui.widget.LoadingSpinner;
import com.andrews.st2downloader.gui.widget.PostDetailPanel;
import com.andrews.st2downloader.gui.widget.PostGridWidget;
import com.andrews.st2downloader.gui.widget.TagFilterWidget;
import com.andrews.st2downloader.models.ArchiveChannel;
import com.andrews.st2downloader.models.ArchivePostSummary;
import com.andrews.st2downloader.models.ArchiveSearchResult;
import com.andrews.st2downloader.models.GlobalTag;
import com.andrews.st2downloader.network.ArchiveNetworkManager;
import com.andrews.st2downloader.util.RenderUtil;
import com.andrews.st2downloader.util.TagUtil;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int SIDEBAR_WIDTH = 200;
    private static final String DISCORD_INVITE_URL = "https://discord.gg/hztJMTsx2m";
    private static final String SUBMISSIONS_URL = "https://discord.com/channels/1375556143186837695/1375575317007040654";
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
    private CustomButton detailCloseButton;
    private LoadingSpinner loadingSpinner;
    private DiscordJoinPopup discordPopup;
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
    private List<ArchiveChannel> channels = new ArrayList<>();
    private List<ArchivePostSummary> currentPosts = new ArrayList<>();
    private boolean showDetailOverlay = false;
    private boolean showChannelPanel = false;
    private boolean showServerDropdown = false;
    private ArchiveChannel hoveredChannel = null;
    private ServerEntry hoveredServer = null;
    private enum TagState { INCLUDE, EXCLUDE }
    private final Map<String, TagState> tagStates = new HashMap<>();
    private final Map<String, Integer> tagCounts = new HashMap<>();
    private final Map<String, Integer> channelCounts = new HashMap<>();

    public LitematicDownloaderScreen() {
        super(Component.nullToEmpty("Litematic Downloader"));
    }

    @Override
    protected void init() {
        super.init();

        hoveredServer = null;
        showServerDropdown = false;
        String previousSearchText = (searchField != null) ? searchField.getValue() : "";

        int headerSpacing = 8;
        int closeButtonSize = 20;
        int submissionsWidth = 50;
        int serverButtonWidth = 100;
        int channelButtonWidth = 60;

        loadingSpinner = new LoadingSpinner(this.width / 2 - 16, this.height / 2 - 16);

        if (serverButton == null) {
            serverButton = new CustomButton(
                PADDING,
                PADDING,
                serverButtonWidth,
                SEARCH_BAR_HEIGHT,
                Component.nullToEmpty(getServerButtonLabel()),
                button -> {
                    showServerDropdown = !showServerDropdown;
                    hoveredServer = null;
                }
            );
        } else {
            serverButton.setWidth(serverButtonWidth);
            serverButton.setHeight(SEARCH_BAR_HEIGHT);
            serverButton.setX(PADDING);
            serverButton.setY(PADDING);
            serverButton.setMessage(Component.nullToEmpty(getServerButtonLabel()));
        }

        if (this.minecraft != null) {
            int channelButtonX = PADDING + serverButtonWidth + headerSpacing;
            int startX = channelButtonX + channelButtonWidth + headerSpacing;
            int rightReserve = PADDING + closeButtonSize + headerSpacing + submissionsWidth + headerSpacing;
            int availableWidth = Math.max(60, this.width - startX - rightReserve);
            int searchBarWidth = Math.max(120, availableWidth);

            searchField = new CustomTextField(
                this.minecraft,
                startX,
                PADDING,
                searchBarWidth,
                SEARCH_BAR_HEIGHT,
                Component.nullToEmpty("Search")
            );
            searchField.setHint(Component.nullToEmpty("Search posts, codes, tags"));
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
                Component.nullToEmpty("Submit"),
                button -> requestDiscordLink(getSubmissionsUrlForServer())
            );
        }

        channelToggleButton = new CustomButton(
            PADDING + serverButtonWidth + headerSpacing,
            PADDING,
            channelButtonWidth,
            SEARCH_BAR_HEIGHT,
            Component.nullToEmpty("Filters"),
            button -> {
                showChannelPanel = !showChannelPanel;
                this.init();
            }
        );

        int gridY = PADDING + PADDING/2 + SEARCH_BAR_HEIGHT;
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
            detailPanel = new PostDetailPanel(PADDING, gridY, gridWidth, gridHeight);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
            detailPanel.setServer(selectedServer);
        } else {
            detailPanel.setDimensions(PADDING, gridY, gridWidth, gridHeight);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
            detailPanel.setServer(selectedServer);
        }

        int detailCloseSize = 20;
        if (detailCloseButton == null) {
            detailCloseButton = new CustomButton(
                this.width - PADDING - detailCloseSize,
                PADDING,
                detailCloseSize,
                detailCloseSize,
                Component.nullToEmpty("X"),
                button -> {
                    showDetailOverlay = false;
                }
            );
            detailCloseButton.setRenderAsXIcon(true);
        } else {
            detailCloseButton.setX(this.width - PADDING - detailCloseSize);
            detailCloseButton.setY(PADDING);
        }

        if (showChannelPanel) {
            int channelHeight = this.height - (PADDING * 3 + SEARCH_BAR_HEIGHT);
            if (channelPanel == null) {
                channelPanel = new ChannelFilterPanel(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING, channelHeight);
                channelPanel.setOnSelectionChanged(path -> {
                    selectedChannelPath = path;
                    currentPage = 1;
                    resetTagStatesForChannel(path);
                    performSearch();
                });
                channelPanel.setOnHoverChanged(channel -> hoveredChannel = channel);
                channelPanel.setChannels(channels);
                channelPanel.setChannelCounts(channelCounts);
            } else {
                channelPanel.setDimensions(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING, channelHeight);
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
                    if (key.isEmpty()) return;
                    if (state == null) {
                        tagStates.remove(key);
                    } else {
                        tagStates.put(key, state == TagFilterWidget.TagState.INCLUDE ? TagState.INCLUDE : TagState.EXCLUDE);
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
            Component.nullToEmpty("X"),
            button -> this.onClose()
        );
        closeButton.setRenderAsXIcon(true);
        if (!initialized) {
            initialized = true;
            loadChannels();
            performSearch();
        } else {
            updatePaginationButtons();
        }
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }

    private void performSearch() {
        if (isLoading) return;

        currentSearchQuery = searchField != null ? searchField.getValue().trim() : "";
        currentTagFilter = "";
        currentPage = 1;
        currentPosts.clear();
        noResultsFound = false;

        if (detailPanel != null) {
            detailPanel.clear();
        }

        loadPage(false);
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

        ArchiveNetworkManager.searchPosts(requestServer, currentSearchQuery, selectedSort, currentTagFilter, includeTags, excludeTags, channelFilter, currentPage, itemsPerPage)
            .thenAccept(result -> handleSearchResponse(requestServer, result))
            .exceptionally(throwable -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
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
                }
                return null;
            });
    }

    private void handleSearchResponse(ServerEntry responseServer, ArchiveSearchResult response) {
        if (this.minecraft != null) {
            if (!isActiveServer(responseServer)) {
                this.minecraft.execute(() -> {
                    isLoading = false;
                    isLoadingMore = false;
                });
                return;
            }
            this.minecraft.execute(() -> {
                if (response == null) {
                    isLoading = false;
                    isLoadingMore = false;
                    return;
                }
                totalPages = response.totalPages();
                totalItems = response.totalItems();
                channelCounts.clear();
                if (response.channelCounts() != null) {
                    channelCounts.putAll(response.channelCounts());
                }

                List<ArchivePostSummary> posts = response.posts();
                if (posts != null) {
                    if (isLoadingMore) {
                        currentPosts.addAll(posts);
                        if (postGrid != null) {
                            postGrid.appendPosts(posts);
                            postGrid.setExpectedTotalPosts(totalItems);
                        }
                    } else {
                        currentPosts.clear();
                        currentPosts.addAll(posts);
                        if (postGrid != null) {
                            postGrid.resetPosts(new ArrayList<>(currentPosts));
                            postGrid.setExpectedTotalPosts(totalItems);
                        }
                    }
                } else if (!isLoadingMore && postGrid != null) {
                    postGrid.resetPosts(new ArrayList<>());
                    postGrid.setExpectedTotalPosts(totalItems);
                }
                if (channelPanel != null) {
                    channelPanel.setChannelCounts(channelCounts);
                }
                updateTagCounts(response.tagCounts());

                isLoading = false;
                isLoadingMore = false;
                updatePaginationButtons();

                noResultsFound = (totalItems == 0);
            });
        }
    }

    private void updatePaginationButtons() {
        // no-op with infinite scroll
    }

    private void loadNextPage() {
        if (isLoadingMore || isLoading) return;
        if (currentPage >= totalPages) return;
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
        channelCounts.clear();
        channels = new ArrayList<>();
        currentPosts.clear();
        currentPage = 1;
        totalPages = 1;
        totalItems = 0;
        noResultsFound = false;
        isLoading = false;
        isLoadingMore = false;

        DownloadSettings.getInstance().setSelectedServer(target);
        if (channelPanel != null) {
            channelPanel.setChannels(channels);
            channelPanel.setChannelCounts(channelCounts);
        }
        if (serverButton != null) {
            serverButton.setMessage(Component.nullToEmpty(getServerButtonLabel()));
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

        loadChannels();
        performSearch();
    }

    private void loadChannels() {
        ServerEntry requestServer = selectedServer != null ? selectedServer : ServerDictionary.getDefaultServer();
        ArchiveNetworkManager.getGlobalTags(requestServer);
        ArchiveNetworkManager.getChannels(requestServer)
            .thenAccept(list -> {
                if (!isActiveServer(requestServer)) {
                    return;
                }
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        channels = list != null ? list.stream()
                            .sorted(Comparator.comparing(ArchiveChannel::category).thenComparing(ArchiveChannel::name))
                            .collect(Collectors.toList()) : new ArrayList<>();
                        for (ArchiveChannel channel : channels) {
                            if (channel != null && channel.path() != null) {
                                channelCounts.putIfAbsent(channel.path(), channel.entryCount());
                            }
                        }
                        if (channelPanel != null) {
                            channelPanel.setChannels(channels);
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
            detailPanel.setDimensions(PADDING, PADDING, this.width - PADDING * 2, this.height - PADDING * 2);
            detailPanel.setPost(post);
            showDetailOverlay = true;
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = showChannelPanel ? SIDEBAR_WIDTH : 0;
        super.render(context, mouseX, mouseY, delta);

        if (postGrid != null) {
            postGrid.setBlocked(showChannelPanel || showServerDropdown);
            postGrid.render(context, mouseX, mouseY, delta);
        }

        if (isLoading) {
            loadingSpinner.render(context, mouseX, mouseY, delta);
        }

        if (noResultsFound) {
            String noResultsText = "No results found :(";
            RenderUtil.drawString(
                context,
                this.font,
                noResultsText,
                leftPanelWidth + PADDING + 20,
                this.height / 2 + 10,
                0xFFFFFFFF
            );
        }

        if (showChannelPanel && channelPanel != null) {
            RenderUtil.fillRect(context, 0, 0, this.width, this.height, 0x55000000);
            channelPanel.render(context, mouseX, mouseY, delta);
            renderChannelDescription(context, mouseX, mouseY, delta);
            
        }

        // Header controls rendered last so they remain visible and bright even when overlay dimming is active
        if (serverButton != null) {
            serverButton.render(context, mouseX, mouseY, delta);
        }

        if (channelToggleButton != null) {
            channelToggleButton.render(context, mouseX, mouseY, delta);
        }

        if (submissionsButton != null) {
            submissionsButton.render(context, mouseX, mouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        if (showServerDropdown) {
            renderServerDropdown(context, mouseX, mouseY, delta);
        }

        if (showDetailOverlay && detailPanel != null) {
            RenderUtil.fillRect(context, 0, 0, this.width, this.height, 0xAA000000);
            detailPanel.render(context, mouseX, mouseY, delta);
            if (detailCloseButton != null) {
                detailCloseButton.render(context, mouseX, mouseY, delta);
            }
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.renderImageViewer(context, mouseX, mouseY, delta);
        }

        if (discordPopup != null) {
            discordPopup.render(context, mouseX, mouseY, delta);
        }
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;

        if (discordPopup != null) {
            return discordPopup.mouseClicked(click, doubled);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            return detailPanel.mouseClicked(click, doubled);
        }

        if (showDetailOverlay && button == 0 && detailCloseButton != null && isMouseOverButton(detailCloseButton, mouseX, mouseY)) {
            if (this.minecraft != null) {
                detailCloseButton.playDownSound(this.minecraft.getSoundManager());
            }
            showDetailOverlay = false;
            return true;
        }

        if (showDetailOverlay && detailPanel != null) {
            if (detailPanel.mouseClicked(click, doubled)) {
                return true;
            }
            showDetailOverlay = false;
            return true;
        }

        if (button == 0 && serverButton != null && isMouseOverButton(serverButton, mouseX, mouseY)) {
            if (serverButton.active && this.minecraft != null) {
                serverButton.playDownSound(this.minecraft.getSoundManager());
            }
            showServerDropdown = !showServerDropdown;
            hoveredServer = null;
            return true;
        }

        if (showServerDropdown) {
            if (handleServerDropdownClick(mouseX, mouseY)) {
                return true;
            }
            showServerDropdown = false;
        }

        if (button == 0 && channelToggleButton != null && isMouseOverButton(channelToggleButton, mouseX, mouseY)) {
            if (channelToggleButton.active) {
                if (this.minecraft != null) {
                    channelToggleButton.playDownSound(this.minecraft.getSoundManager());
                }
                channelToggleButton.onPress(click);
            }
            return true;
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        if (button == 0 && isMouseOverButton(submissionsButton, mouseX, mouseY)) {
            requestDiscordLink(getSubmissionsUrlForServer());
            return true;
        }

        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        // tagField removed

        if (channelOverlayOpen) {
            if (channelPanel != null && channelPanel.mouseClicked(click, doubled)) {
                return true;
            }
            if (tagFilterWidget != null && tagFilterWidget.mouseClicked(click, doubled)) {
                return true;
            }
            showChannelPanel = false;
            return true;
        }

        if (postGrid != null && postGrid.mouseClicked(click, doubled)) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (showServerDropdown) {
            return false;
        }
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseDragged(click, offsetX, offsetY)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseDragged(click, offsetX, offsetY)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseDragged(click, offsetX, offsetY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (showServerDropdown) {
            return false;
        }
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseReleased(click)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseReleased(click)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseReleased(click)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showServerDropdown) {
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
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
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
        if (showServerDropdown) {
            showServerDropdown = false;
            return false;
        }
        if (discordPopup != null) {
            clearDiscordPopup();
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
            showDetailOverlay = false;
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        clearDiscordPopup();
        ArchiveNetworkManager.clearCache();
        super.onClose();
    }

    private void renderChannelDescription(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (channelPanel == null) return;
        ArchiveChannel channel = hoveredChannel != null ? hoveredChannel : channels.stream()
            .filter(c -> selectedChannelPath != null && selectedChannelPath.equals(c.path()))
            .findFirst()
            .orElse(null);

        int desiredWidth = 260;
        int available = this.width - (channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 3 : PADDING * 2);
        int boxWidth = Math.min(desiredWidth, available);
        int boxX = channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 2 : this.width - boxWidth - PADDING;
        if (boxX + boxWidth > this.width - PADDING) {
            boxX = Math.max(PADDING, this.width - boxWidth - PADDING);
        }
        int boxHeight = 60;
        int boxY = PADDING + SEARCH_BAR_HEIGHT + PADDING;

        if (channelDescriptionWidget != null) {
            channelDescriptionWidget.setBounds(boxX, boxY, boxWidth, boxHeight);
            channelDescriptionWidget.setChannel(channel);
            channelDescriptionWidget.render(context, this.font);
        }

        int tagY = boxY + boxHeight + UITheme.Dimensions.PADDING;
        int tagHeight = this.height - tagY - PADDING;
        if (tagFilterWidget != null) {
            tagFilterWidget.setBounds(boxX, tagY, boxWidth, tagHeight);
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
            long windowHandle = this.minecraft != null ? this.minecraft.getWindow().handle() : 0L;
            tagFilterWidget.render(context, this.font, mouseX, mouseY, delta, windowHandle);
        }
    }

    private void renderServerDropdown(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (serverButton == null) return;
        List<ServerEntry> servers = ServerDictionary.getServers();
        if (servers.isEmpty()) return;

        ServerDropdownLayout layout = buildServerDropdownLayout(servers);
        int baseX = layout.x();
        int baseY = layout.y();
        int width = layout.width();
        int itemHeight = layout.itemHeight();

        hoveredServer = null;

        RenderUtil.fillRect(context, baseX, baseY - 2, baseX + width, baseY + servers.size() * itemHeight + 2, UITheme.Colors.PANEL_BG_SECONDARY);

        for (int i = 0; i < servers.size(); i++) {
            ServerEntry server = servers.get(i);
            int itemY = baseY + i * itemHeight;
            boolean hovered = mouseX >= baseX && mouseX < baseX + width && mouseY >= itemY && mouseY < itemY + itemHeight;
            boolean selected = isActiveServer(server);
            if (hovered) {
                hoveredServer = server;
            }

            int bgColor = selected ? UITheme.Colors.BUTTON_BG : UITheme.Colors.CONTAINER_BG;
            if (hovered) {
                bgColor = UITheme.Colors.BUTTON_BG_HOVER;
            }

            RenderUtil.fillRect(context, baseX + 1, itemY, baseX + width - 1, itemY + itemHeight, bgColor);
            String serverName = server.name() != null && !server.name().isBlank()
                ? server.name()
                : (server.id() != null ? server.id() : "Server");
            RenderUtil.drawString(
                context,
                this.font,
                serverName,
                baseX + UITheme.Dimensions.PADDING,
                itemY + 4,
                UITheme.Colors.TEXT_PRIMARY
            );
        }

        ServerEntry descServer = hoveredServer != null ? hoveredServer : getActiveServer();
        renderServerDescriptionBox(context, descServer, baseX, baseY, width, itemHeight, servers.size());
    }

    private ServerDropdownLayout buildServerDropdownLayout(List<ServerEntry> servers) {
        int itemHeight = 18;
        int labelWidth = 0;
        for (ServerEntry server : servers) {
            if (server == null) continue;
            String name = server.name() != null ? server.name() : "Server";
            labelWidth = Math.max(labelWidth, this.font.width(name));
        }
        int width = Math.max(140, labelWidth + UITheme.Dimensions.PADDING * 2);
        int x = serverButton != null ? serverButton.getX() : PADDING;
        int y = (serverButton != null ? serverButton.getY() + serverButton.getHeight() : PADDING) + 4;
        if (x + width > this.width - PADDING) {
            x = Math.max(PADDING, this.width - width - PADDING);
        }
        return new ServerDropdownLayout(x, y, width, itemHeight);
    }

    private record ServerDropdownLayout(int x, int y, int width, int itemHeight) {}

    // Tag rendering handled by TagFilterWidget; this method kept for compatibility.
    private void renderServerDescriptionBox(GuiGraphics context, ServerEntry server, int dropdownX, int dropdownY, int dropdownWidth, int itemHeight, int itemCount) {
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
        int textHeight = RenderUtil.getWrappedTextHeight(this.font, server.description(), textWidth);
        int boxHeight = Math.max(itemHeight * itemCount + 4, textHeight + boxPadding * 2);

        RenderUtil.fillRect(context, boxX, boxY, boxX + boxWidth, boxY + boxHeight, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, boxX, boxY, boxX + boxWidth, boxY + 1, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, boxX, boxY, boxX + 1, boxY + boxHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawWrappedText(
            context,
            this.font,
            server.description(),
            boxX + boxPadding,
            boxY + boxPadding,
            textWidth,
            UITheme.Colors.TEXT_SUBTITLE
        );
    }

    private boolean handleServerDropdownClick(double mouseX, double mouseY) {
        if (!showServerDropdown || serverButton == null) {
            return false;
        }
        List<ServerEntry> servers = ServerDictionary.getServers();
        if (servers.isEmpty()) {
            showServerDropdown = false;
            return false;
        }
        ServerDropdownLayout layout = buildServerDropdownLayout(servers);
        int x = layout.x();
        int y = layout.y();
        int width = layout.width();
        int itemHeight = layout.itemHeight();
        int totalHeight = servers.size() * itemHeight;

        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + totalHeight;
        if (!inside) {
            showServerDropdown = false;
            hoveredServer = null;
            return false;
        }

        int index = (int) ((mouseY - y) / itemHeight);
        if (index >= 0 && index < servers.size()) {
            onServerSelected(servers.get(index));
            return true;
        }
        return false;
    }

    private List<String> getDisplayedTags() {
        ServerEntry server = getActiveServer();
        if (selectedChannelPath != null) {
            List<String> tags = channels.stream()
                .filter(c -> selectedChannelPath.equals(c.path()))
                .findFirst()
                .map(c -> c.availableTags() != null ? c.availableTags() : List.<String>of())
                .orElse(List.of());
            return TagUtil.orderTags(tags, server);
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

    private void resetTagStatesForChannel(String path) {
        tagStates.clear();
        updateTagCounts();
    }

    private void updateTagCounts() {
        updateTagCounts(null);
    }

    private void updateTagCounts(Map<String, Integer> countsFromSearch) {
        tagCounts.clear();
        if (countsFromSearch != null) {
            for (Map.Entry<String, Integer> entry : countsFromSearch.entrySet()) {
                if (entry.getKey() == null) continue;
                String key = entry.getKey().toLowerCase();
                int value = entry.getValue() != null ? entry.getValue() : 0;
                tagCounts.put(key, value);
            }
        } else {
            for (ArchivePostSummary post : currentPosts) {
                if (post == null || post.tags() == null) continue;
                for (String tag : post.tags()) {
                    if (tag == null) continue;
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

    private Map<String, TagFilterWidget.TagState> convertTagStates() {
        Map<String, TagFilterWidget.TagState> map = new HashMap<>();
        for (Map.Entry<String, TagState> entry : tagStates.entrySet()) {
            map.put(entry.getKey(), entry.getValue() == TagState.INCLUDE
                ? TagFilterWidget.TagState.INCLUDE
                : TagFilterWidget.TagState.EXCLUDE);
        }
        return map;
    }

    private void requestDiscordLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        ServerEntry server = getActiveServer();
        String inviteUrl = getDiscordInviteUrlForServer();
        String serverName = server != null && server.name() != null ? server.name() : "this";
        if (DownloadSettings.getInstance().hasJoinedDiscord(server)) {
            openUrlSafe(url);
            return;
        }

        pendingDiscordUrl = url;
        String message = "These links live in the " + serverName + " Discord. Please join before continuing.";
        discordPopup = new DiscordJoinPopup(
            "Join " + serverName + " Discord?",
            message,
            () -> {
                DownloadSettings.getInstance().setJoinedDiscord(server, true);
                openUrlSafe(pendingDiscordUrl);
                clearDiscordPopup();
            },
            () -> openUrlSafe(inviteUrl),
            this::clearDiscordPopup
        );
    }

    private void openUrlSafe(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open link: " + e.getMessage());
        }
    }

    private void clearDiscordPopup() {
        discordPopup = null;
        pendingDiscordUrl = null;
    }
}
