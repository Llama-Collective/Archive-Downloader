package com.andrews.archivedownloader.gui.widget;

import java.util.ArrayList;
import java.util.List;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.text.UiText;
import org.lwjgl.glfw.GLFW;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;

public class DiscordJoinPopup implements UiRenderable, UiEventListener {
	private static final int POPUP_WIDTH = 420;
	private static final int MAX_MESSAGE_HEIGHT = 260;

	private final String title;
	private final Runnable onContinue;
	private final Runnable onOpenInvite;
	private final Runnable onCancel;

	private CustomButton continueButton;
	private CustomButton inviteButton;
	private CustomButton cancelButton;

	private final List<String> wrappedMessage;
	private final int x;
	private final int y;
	private final int popupHeight;
	private final int actualMessageHeight;
	private final int visibleMessageHeight;
	private double scrollOffset = 0;
	private ScrollBar scrollBar;
	private boolean wasEnterPressed = false;
	private boolean wasEscapePressed = false;

	public DiscordJoinPopup(String title, String message, Runnable onContinue, Runnable onOpenInvite, Runnable onCancel) {
		this.title = title;
		this.onContinue = onContinue;
		this.onOpenInvite = onOpenInvite;
		this.onCancel = onCancel;

		UiMinecraftClient client = UiMinecraftClient.getInstance();
		this.wrappedMessage = wrapText(message, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2, client);

		int screenHeight = client.guiScaledHeight();
		int screenWidth = client.guiScaledWidth();

		int verticalMargin = 40;
		int chromeHeight = UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING +
				UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING;
		int maxAvailableMessageHeight = screenHeight - (verticalMargin * 2) - chromeHeight;
		int effectiveMaxHeight = Math.min(MAX_MESSAGE_HEIGHT, maxAvailableMessageHeight);

		this.actualMessageHeight = wrappedMessage.size() * UITheme.Typography.LINE_HEIGHT;
		this.visibleMessageHeight = Math.min(actualMessageHeight, effectiveMaxHeight);
		this.popupHeight = UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING +
				visibleMessageHeight + UITheme.Dimensions.PADDING + UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING;

		this.x = (screenWidth - POPUP_WIDTH) / 2;
		this.y = (screenHeight - popupHeight) / 2;

		if (actualMessageHeight > visibleMessageHeight) {
			int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
			int scrollBarX = x + POPUP_WIDTH - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH;
			this.scrollBar = new ScrollBar(scrollBarX, messageAreaY, visibleMessageHeight);
			this.scrollBar.setScrollData(actualMessageHeight, visibleMessageHeight);
		}

		initButtons();
	}

	private void initButtons() {
		int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
		int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 4) / 3;

		cancelButton = new CustomButton(
				x + UITheme.Dimensions.PADDING,
				buttonY,
				buttonWidth,
				UITheme.Dimensions.BUTTON_HEIGHT,
				UiText.of("Cancel"),
				button -> onCancel.run()
		);

		inviteButton = new CustomButton(
				x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
				buttonY,
				buttonWidth,
				UITheme.Dimensions.BUTTON_HEIGHT,
				UiText.of("Open Invite"),
				button -> onOpenInvite.run()
		);

		continueButton = new CustomButton(
				x + UITheme.Dimensions.PADDING * 3 + buttonWidth * 2,
				buttonY,
				buttonWidth,
				UITheme.Dimensions.BUTTON_HEIGHT,
				UiText.of("Continue"),
				button -> onContinue.run()
		);
	}

	private List<String> wrapText(String text, int maxWidth, UiMinecraftClient client) {
		List<String> lines = new ArrayList<>();
		String[] paragraphs = text.split("\n");

		for (String paragraph : paragraphs) {
			if (paragraph.isEmpty()) {
				lines.add("");
				continue;
			}

			String[] words = paragraph.split(" ");
			StringBuilder currentLine = new StringBuilder();

			for (String word : words) {
				String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
				int width = client.font().width(testLine);

				if (width <= maxWidth) {
					if (currentLine.length() > 0) {
						currentLine.append(" ");
					}
					currentLine.append(word);
				} else {
					if (currentLine.length() > 0) {
						lines.add(currentLine.toString());
					}
					currentLine = new StringBuilder(word);
				}
			}

			if (currentLine.length() > 0) {
				lines.add(currentLine.toString());
			}
		}

		return lines.isEmpty() ? List.of(text) : lines;
	}

	@Override
	public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
		var graphics = context.graphics();
		UiMinecraftClient client = UiMinecraftClient.getInstance();
		long windowHandle = client.windowHandle();

		if (windowHandle != 0) {
			boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
					GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
			boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

			if (enterPressed && !wasEnterPressed) {
				onContinue.run();
			}
			if (escapePressed && !wasEscapePressed) {
				onCancel.run();
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
				title,
				x + POPUP_WIDTH / 2,
				y + UITheme.Dimensions.PADDING,
				UITheme.Colors.TEXT_PRIMARY
		);

		int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
		int messageAreaHeight = visibleMessageHeight;
		RenderUtil.enableScissor(context, x + UITheme.Dimensions.PADDING, messageAreaY, x + POPUP_WIDTH - UITheme.Dimensions.PADDING, messageAreaY + messageAreaHeight);

		int messageY = messageAreaY - (int) scrollOffset;
		for (String line : wrappedMessage) {
			if (messageY + UITheme.Typography.LINE_HEIGHT >= messageAreaY && messageY < messageAreaY + messageAreaHeight) {
				RenderUtil.drawString(
						context,
						client.uiFont(),
						line,
						x + UITheme.Dimensions.PADDING,
						messageY,
						UITheme.Colors.TEXT_TAG
				);
			}
			messageY += UITheme.Typography.LINE_HEIGHT;
		}
		RenderUtil.disableScissor(context);

		if (scrollBar != null && client.windowHandle() != 0L) {
			scrollBar.setScrollPercentage(scrollOffset / Math.max(1, actualMessageHeight - visibleMessageHeight));
			boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.windowHandle());
			if (scrollChanged || scrollBar.isDragging()) {
				double maxScroll = actualMessageHeight - visibleMessageHeight;
				scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
			}
		}

		if (cancelButton != null) {
			cancelButton.render(graphics, mouseX, mouseY, delta);
		}
		if (inviteButton != null) {
			inviteButton.render(graphics, mouseX, mouseY, delta);
		}
		if (continueButton != null) {
			continueButton.render(graphics, mouseX, mouseY, delta);
		}
	}

	@Override
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
		if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + popupHeight) {
			onCancel.run();
			return true;
		}

		if (cancelButton != null && isOver(cancelButton, mouseX, mouseY)) {
			onCancel.run();
			return true;
		}
		if (inviteButton != null && isOver(inviteButton, mouseX, mouseY)) {
			onOpenInvite.run();
			return true;
		}
		if (continueButton != null && isOver(continueButton, mouseX, mouseY)) {
			onContinue.run();
			return true;
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (scrollBar != null) {
			int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
			if (mouseX >= x && mouseX < x + POPUP_WIDTH &&
				mouseY >= messageAreaY && mouseY < messageAreaY + visibleMessageHeight) {

				double maxScroll = actualMessageHeight - visibleMessageHeight;
				scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * UITheme.Typography.LINE_HEIGHT));
				return true;
			}
		}
		return false;
	}

	@Override
	public void setFocused(boolean focused) {
	}

	@Override
	public boolean isFocused() {
		return false;
	}

	private boolean isOver(CustomButton button, double mouseX, double mouseY) {
		return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
				mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
	}
}
