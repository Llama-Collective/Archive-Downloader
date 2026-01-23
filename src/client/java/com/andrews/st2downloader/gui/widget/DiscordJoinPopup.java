package com.andrews.st2downloader.gui.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import com.andrews.st2downloader.gui.theme.UITheme;
import com.andrews.st2downloader.util.RenderUtil;

public class DiscordJoinPopup implements Renderable, GuiEventListener {
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

		Minecraft client = Minecraft.getInstance();
		this.wrappedMessage = wrapText(message, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2, client);

		int screenHeight = client.getWindow().getGuiScaledHeight();
		int screenWidth = client.getWindow().getGuiScaledWidth();

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
				Component.nullToEmpty("Cancel"),
				button -> onCancel.run()
		);

		inviteButton = new CustomButton(
				x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
				buttonY,
				buttonWidth,
				UITheme.Dimensions.BUTTON_HEIGHT,
				Component.nullToEmpty("Open Invite"),
				button -> onOpenInvite.run()
		);

		continueButton = new CustomButton(
				x + UITheme.Dimensions.PADDING * 3 + buttonWidth * 2,
				buttonY,
				buttonWidth,
				UITheme.Dimensions.BUTTON_HEIGHT,
				Component.nullToEmpty("Continue"),
				button -> onContinue.run()
		);
	}

	private List<String> wrapText(String text, int maxWidth, Minecraft client) {
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
				int width = client.font.width(testLine);

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
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		long windowHandle = client.getWindow() != null ? client.getWindow().handle() : 0;

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

		int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
		int messageAreaHeight = visibleMessageHeight;
		RenderUtil.enableScissor(context, x + UITheme.Dimensions.PADDING, messageAreaY, x + POPUP_WIDTH - UITheme.Dimensions.PADDING, messageAreaY + messageAreaHeight);

		int messageY = messageAreaY - (int) scrollOffset;
		for (String line : wrappedMessage) {
			if (messageY + UITheme.Typography.LINE_HEIGHT >= messageAreaY && messageY < messageAreaY + messageAreaHeight) {
				RenderUtil.drawString(
						context,
						client.font,
						line,
						x + UITheme.Dimensions.PADDING,
						messageY,
						UITheme.Colors.TEXT_TAG
				);
			}
			messageY += UITheme.Typography.LINE_HEIGHT;
		}
		RenderUtil.disableScissor(context);

		if (scrollBar != null && client.getWindow() != null) {
			scrollBar.setScrollPercentage(scrollOffset / Math.max(1, actualMessageHeight - visibleMessageHeight));
			boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().handle());
			if (scrollChanged || scrollBar.isDragging()) {
				double maxScroll = actualMessageHeight - visibleMessageHeight;
				scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
			}
		}

		if (cancelButton != null) {
			cancelButton.render(context, mouseX, mouseY, delta);
		}
		if (inviteButton != null) {
			inviteButton.render(context, mouseX, mouseY, delta);
		}
		if (continueButton != null) {
			continueButton.render(context, mouseX, mouseY, delta);
		}
	}

	@Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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
