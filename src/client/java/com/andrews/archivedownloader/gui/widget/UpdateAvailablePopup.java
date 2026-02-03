package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

public class UpdateAvailablePopup implements Renderable, GuiEventListener {
    private static final int POPUP_WIDTH = 420;

    private final String title;
    private final String message;
    private final Runnable onOpenModPage;
    private final Runnable onClose;

    private final int x;
    private final int y;
    private final int popupHeight;
    private final int messageHeight;

    private CustomButton closeButton;
    private CustomButton openButton;
    private boolean wasEscapePressed = false;
    private boolean wasEnterPressed = false;

    public UpdateAvailablePopup(String title, String message, Runnable onOpenModPage, Runnable onClose) {
        this.title = title != null ? title : "Update Available";
        this.message = message != null ? message : "";
        this.onOpenModPage = onOpenModPage != null ? onOpenModPage : () -> {};
        this.onClose = onClose != null ? onClose : () -> {};

        Minecraft client = Minecraft.getInstance();
        int textWidth = POPUP_WIDTH - UITheme.Dimensions.PADDING * 2;
        this.messageHeight = Math.max(
            UITheme.Typography.LINE_HEIGHT,
            RenderUtil.getWrappedTextHeight(client.font, this.message, textWidth)
        );

        this.popupHeight =
            UITheme.Dimensions.PADDING +
            UITheme.Typography.LINE_HEIGHT +
            UITheme.Dimensions.PADDING +
            messageHeight +
            UITheme.Dimensions.PADDING +
            UITheme.Dimensions.BUTTON_HEIGHT +
            UITheme.Dimensions.PADDING;

        int screenHeight = client.getWindow().getGuiScaledHeight();
		int screenWidth = client.getWindow().getGuiScaledWidth();
        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - popupHeight) / 2;

        initButtons();
    }

    private void initButtons() {
        int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 3) / 2;

        closeButton = new CustomButton(
            x + UITheme.Dimensions.PADDING,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            Component.nullToEmpty("Later"),
            button -> onClose.run()
        );

        openButton = new CustomButton(
            x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            Component.nullToEmpty("Open Mod Page"),
            button -> onOpenModPage.run()
        );
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        long windowHandle = client.getWindow() != null ? client.getWindow().handle() : 0;

        if (windowHandle != 0) {
            boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (enterPressed && !wasEnterPressed) {
                onOpenModPage.run();
            }
            if (escapePressed && !wasEscapePressed) {
                onClose.run();
            }

            wasEnterPressed = enterPressed;
            wasEscapePressed = escapePressed;
        }

        RenderUtil.fillRect(context, 0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), UITheme.Colors.OVERLAY_BG);
        RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BG_DISABLED);

        RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + popupHeight - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawCenteredString(
            context,
            client.font,
            title,
            x + POPUP_WIDTH / 2,
            y + UITheme.Dimensions.PADDING,
            UITheme.Colors.TEXT_PRIMARY
        );

        int messageY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
        int messageX = x + UITheme.Dimensions.PADDING;
        int messageWidth = POPUP_WIDTH - UITheme.Dimensions.PADDING * 2;
        RenderUtil.drawWrappedText(
            context,
            client.font,
            message,
            messageX,
            messageY,
            messageWidth,
            UITheme.Colors.TEXT_TAG
        );

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }
        if (openButton != null) {
            openButton.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + popupHeight) {
            onClose.run();
            return true;
        }

        if (closeButton != null && isOver(closeButton, mouseX, mouseY)) {
            onClose.run();
            return true;
        }
        if (openButton != null && isOver(openButton, mouseX, mouseY)) {
            onOpenModPage.run();
            return true;
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
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
            && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
}
