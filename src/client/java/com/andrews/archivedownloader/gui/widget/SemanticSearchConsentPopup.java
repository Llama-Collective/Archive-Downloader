package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.text.UiText;
import org.lwjgl.glfw.GLFW;

public class SemanticSearchConsentPopup implements UiRenderable, UiEventListener {
	private static final int POPUP_WIDTH = 440;

	private final Runnable onAccept;
	private final Runnable onDecline;
	private final int x;
	private final int y;
	private final int popupHeight;
	private final int messageHeight;
	private final String message;

	private CustomButton declineButton;
	private CustomButton acceptButton;
	private boolean wasEnterPressed = false;
	private boolean wasEscapePressed = false;

	public SemanticSearchConsentPopup(Runnable onAccept, Runnable onDecline) {
		this.onAccept = onAccept != null ? onAccept : () -> {};
		this.onDecline = onDecline != null ? onDecline : () -> {};
		this.message = "Semantic search can find related archive posts even when the exact words do not match. "
			+ "To work, it needs to download a tiny machine learning model once. "
			+ "Search runs entirely locally on your machine and no data is sent to any servers. "
			+ "Approximate download size: about 65 MB.";

		UiMinecraftClient client = UiMinecraftClient.getInstance();
		int textWidth = POPUP_WIDTH - UITheme.Dimensions.PADDING * 2;
		this.messageHeight = Math.max(
			UITheme.Typography.LINE_HEIGHT,
			RenderUtil.getWrappedTextHeight(client.uiFont(), this.message, textWidth)
		);
		this.popupHeight =
			UITheme.Dimensions.PADDING +
			UITheme.Typography.LINE_HEIGHT +
			UITheme.Dimensions.PADDING +
			messageHeight +
			UITheme.Dimensions.PADDING +
			UITheme.Dimensions.BUTTON_HEIGHT +
			UITheme.Dimensions.PADDING;

		this.x = (client.guiScaledWidth() - POPUP_WIDTH) / 2;
		this.y = (client.guiScaledHeight() - popupHeight) / 2;
		initButtons();
	}

	private void initButtons() {
		int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
		int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 3) / 2;

		declineButton = new CustomButton(
			x + UITheme.Dimensions.PADDING,
			buttonY,
			buttonWidth,
			UITheme.Dimensions.BUTTON_HEIGHT,
			UiText.of("No Thanks"),
			button -> onDecline.run()
		);

		acceptButton = new CustomButton(
			x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
			buttonY,
			buttonWidth,
			UITheme.Dimensions.BUTTON_HEIGHT,
			UiText.of("Download"),
			button -> onAccept.run()
		);
	}

	@Override
	public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
		UiMinecraftClient client = UiMinecraftClient.getInstance();
		long windowHandle = client.windowHandle();
		if (windowHandle != 0) {
			boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
			boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
			if (enterPressed && !wasEnterPressed) {
				onAccept.run();
			}
			if (escapePressed && !wasEscapePressed) {
				onDecline.run();
			}
			wasEnterPressed = enterPressed;
			wasEscapePressed = escapePressed;
		}

		RenderUtil.fillRect(context, 0, 0, client.guiScaledWidth(), client.guiScaledHeight(), UITheme.Colors.OVERLAY_BG);
		RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BG_DISABLED);
		RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
		RenderUtil.fillRect(context, x, y + popupHeight - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
		RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
		RenderUtil.fillRect(context, x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);

		RenderUtil.drawCenteredString(
			context,
			client.uiFont(),
			"Enable Semantic Search?",
			x + POPUP_WIDTH / 2,
			y + UITheme.Dimensions.PADDING,
			UITheme.Colors.TEXT_PRIMARY
		);

		RenderUtil.drawWrappedText(
			context,
			client.uiFont(),
			message,
			x + UITheme.Dimensions.PADDING,
			y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING,
			POPUP_WIDTH - UITheme.Dimensions.PADDING * 2,
			UITheme.Colors.TEXT_TAG
		);

		declineButton.render(context, mouseX, mouseY, delta);
		acceptButton.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + popupHeight) {
			onDecline.run();
			return true;
		}
		if (isOver(declineButton, mouseX, mouseY)) {
			onDecline.run();
			return true;
		}
		if (isOver(acceptButton, mouseX, mouseY)) {
			onAccept.run();
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
