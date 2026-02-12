package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.text.UiText;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

public class ApiTokenPopup implements UiRenderable, UiEventListener {
    private static final int MAX_POPUP_WIDTH = 460;
    private static final int MIN_POPUP_WIDTH = 280;
    private static final int STATUS_HEIGHT = UITheme.Typography.LINE_HEIGHT * 2;

    private final String serverName;
    private final String apiBase;
    private final Consumer<String> onSave;
    private final Runnable onClear;
    private final Runnable onCancel;

    private final int popupWidth;
    private final int x;
    private final int y;
    private final int popupHeight;
    private final int descriptionHeight;
    private final String descriptionText;
    private final boolean canClearToken;

    private final CustomTextField tokenField;
    private final CustomButton cancelButton;
    private final CustomButton clearButton;
    private final CustomButton saveButton;
    private boolean validating = false;
    private String statusMessage = "";
    private int statusColor = UITheme.Colors.TEXT_SUBTITLE;
    private boolean wasEnterPressed = false;
    private boolean wasEscapePressed = false;

    public ApiTokenPopup(
        String serverName,
        String apiBase,
        boolean hasExistingToken,
        Consumer<String> onSave,
        Runnable onClear,
        Runnable onCancel
    ) {
        this.serverName = serverName != null && !serverName.isBlank() ? serverName : "Server";
        this.apiBase = apiBase != null ? apiBase : "";
        this.onSave = onSave != null ? onSave : token -> {};
        this.onClear = onClear != null ? onClear : () -> {};
        this.onCancel = onCancel != null ? onCancel : () -> {};
        this.canClearToken = hasExistingToken;

        UiMinecraftClient client = UiMinecraftClient.getInstance();
        int screenWidth = client.guiScaledWidth();
        int screenHeight = client.guiScaledHeight();
        int horizontalMargin = UITheme.Dimensions.PADDING * 2;
        int verticalMargin = UITheme.Dimensions.PADDING * 2;
        int maxWidth = Math.max(220, screenWidth - horizontalMargin);
        this.popupWidth = Math.max(220, Math.min(MAX_POPUP_WIDTH, Math.max(MIN_POPUP_WIDTH, maxWidth)));

        int messageWidth = popupWidth - UITheme.Dimensions.PADDING * 2;
        this.descriptionText = buildDescription(hasExistingToken);
        int measuredDescriptionHeight = RenderUtil.getWrappedTextHeight(client.uiFont(), descriptionText, messageWidth);
        int fixedHeight =
            UITheme.Dimensions.PADDING +
            UITheme.Typography.LINE_HEIGHT +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.SEARCH_BAR_HEIGHT +
            UITheme.Dimensions.PADDING +
            STATUS_HEIGHT +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.BUTTON_HEIGHT +
            UITheme.Dimensions.PADDING;
        int maxDescriptionHeight = Math.max(UITheme.Typography.LINE_HEIGHT, screenHeight - verticalMargin - fixedHeight);
        this.descriptionHeight = Math.min(measuredDescriptionHeight, maxDescriptionHeight);

        this.popupHeight =
            UITheme.Dimensions.PADDING +
            UITheme.Typography.LINE_HEIGHT +
            UITheme.Dimensions.PADDING +
            this.descriptionHeight +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.SEARCH_BAR_HEIGHT +
            UITheme.Dimensions.PADDING +
            STATUS_HEIGHT +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.BUTTON_HEIGHT +
            UITheme.Dimensions.PADDING;

        this.x = Math.max(UITheme.Dimensions.PADDING, (screenWidth - popupWidth) / 2);
        this.y = Math.max(UITheme.Dimensions.PADDING, (screenHeight - popupHeight) / 2);

        tokenField = new CustomTextField(
            client,
            x + UITheme.Dimensions.PADDING,
            y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING + this.descriptionHeight + UITheme.Dimensions.PADDING,
            popupWidth - UITheme.Dimensions.PADDING * 2,
            UITheme.Dimensions.SEARCH_BAR_HEIGHT,
            UiText.literal("API Token")
        );
        tokenField.setSuggestion(hasExistingToken
            ? "Token saved. Paste a new token to replace it."
            : "Paste API token");
        tokenField.setOnEnterPressed(this::saveToken);
        tokenField.setFocused(true);

        int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonWidth = (popupWidth - UITheme.Dimensions.PADDING * 4) / 3;
        cancelButton = new CustomButton(
            x + UITheme.Dimensions.PADDING,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            UiText.literal("Cancel"),
            button -> this.onCancel.run()
        );
        clearButton = new CustomButton(
            x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            UiText.literal("Clear"),
            button -> this.onClear.run()
        );
        clearButton.active = canClearToken;
        saveButton = new CustomButton(
            x + UITheme.Dimensions.PADDING * 3 + buttonWidth * 2,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            UiText.literal("Save"),
            button -> saveToken()
        );
    }

    private String buildDescription(boolean hasExistingToken) {
        String status = hasExistingToken
            ? "A token is already saved for this server."
            : "No token is currently saved for this server.";
        String base = apiBase.isBlank()
            ? "API endpoint: not configured."
            : "API endpoint: " + abbreviateMiddle(apiBase, 64);
        return status + " Paste a bearer token from `/token get` in Discord.\n" + base;
    }

    private String abbreviateMiddle(String value, int maxLength) {
        if (value == null || value.length() <= maxLength || maxLength < 8) {
            return value != null ? value : "";
        }
        int keep = (maxLength - 3) / 2;
        return value.substring(0, keep) + "..." + value.substring(value.length() - keep);
    }

    private void saveToken() {
        onSave.accept(tokenField.getValue());
    }

    public void setStatus(String message, boolean isError) {
        this.statusMessage = message != null ? message.trim() : "";
        this.statusColor = isError ? UITheme.Colors.ERROR_TEXT : UITheme.Colors.TEXT_SUBTITLE;
    }

    public void setValidating(boolean validating, String message) {
        this.validating = validating;
        if (message != null && !message.isBlank()) {
            setStatus(message, false);
        } else if (!validating && statusMessage.equals("Validating token...")) {
            setStatus("", false);
        }
        saveButton.active = !validating;
        clearButton.active = !validating && canClearToken;
    }

    public void dismiss() {
        tokenField.setFocused(false);
    }

    @Override
    public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        var graphics = context.graphics();
        UiMinecraftClient client = UiMinecraftClient.getInstance();
        long windowHandle = client.windowHandle();
        if (windowHandle != 0L) {
            boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (enterPressed && !wasEnterPressed && !validating) {
                saveToken();
            }
            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }
            wasEnterPressed = enterPressed;
            wasEscapePressed = escapePressed;
        }

        RenderUtil.fillRect(context, 0, 0, client.guiScaledWidth(), client.guiScaledHeight(), UITheme.Colors.OVERLAY_BG);
        RenderUtil.fillRect(context, x, y, x + popupWidth, y + popupHeight, UITheme.Colors.BUTTON_BG_DISABLED);
        RenderUtil.fillRect(context, x, y, x + popupWidth, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + popupHeight - UITheme.Dimensions.BORDER_WIDTH, x + popupWidth, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + popupWidth - UITheme.Dimensions.BORDER_WIDTH, y, x + popupWidth, y + popupHeight, UITheme.Colors.BUTTON_BORDER);

        String title = "API Token - " + serverName;
        RenderUtil.drawCenteredString(
            context,
            client.uiFont(),
            title,
            x + popupWidth / 2,
            y + UITheme.Dimensions.PADDING,
            UITheme.Colors.TEXT_PRIMARY
        );

        int messageY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
        RenderUtil.enableScissor(
            context,
            x + UITheme.Dimensions.PADDING,
            messageY,
            x + popupWidth - UITheme.Dimensions.PADDING,
            messageY + descriptionHeight
        );
        RenderUtil.drawWrappedText(
            context,
            client.uiFont(),
            descriptionText,
            x + UITheme.Dimensions.PADDING,
            messageY,
            popupWidth - UITheme.Dimensions.PADDING * 2,
            UITheme.Colors.TEXT_TAG
        );
        RenderUtil.disableScissor(context);

        tokenField.render(graphics, mouseX, mouseY, delta);
        int statusY = tokenField.getY() + tokenField.getHeight() + 4;
        RenderUtil.enableScissor(
            context,
            x + UITheme.Dimensions.PADDING,
            statusY,
            x + popupWidth - UITheme.Dimensions.PADDING,
            statusY + STATUS_HEIGHT
        );
        RenderUtil.drawWrappedText(
            context,
            client.uiFont(),
            statusMessage,
            x + UITheme.Dimensions.PADDING,
            statusY,
            popupWidth - UITheme.Dimensions.PADDING * 2,
            statusColor
        );
        RenderUtil.disableScissor(context);
        cancelButton.render(graphics, mouseX, mouseY, delta);
        clearButton.render(graphics, mouseX, mouseY, delta);
        saveButton.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (mouseX < x || mouseX > x + popupWidth || mouseY < y || mouseY > y + popupHeight) {
            onCancel.run();
            return true;
        }

        if (button == 0) {
            if (isOver(cancelButton, mouseX, mouseY)) {
                onCancel.run();
                return true;
            }
            if (!validating && clearButton.active && isOver(clearButton, mouseX, mouseY)) {
                onClear.run();
                return true;
            }
            if (!validating && saveButton.active && isOver(saveButton, mouseX, mouseY)) {
                saveToken();
                return true;
            }
            if (tokenField.isMouseOver(mouseX, mouseY)) {
                tokenField.setFocused(true);
                return true;
            }
            tokenField.setFocused(false);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    private boolean isOver(CustomButton button, double mouseX, double mouseY) {
        return button != null
            && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
            && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
}
