package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.mixin.client.EditBoxAccessor;
import org.lwjgl.glfw.GLFW;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiTextFieldBase;
import com.andrews.archivedownloader.wrapper.text.UiText;

public class CustomTextField extends UiTextFieldBase {
	private static final int TEXT_PADDING = 4;
	private static final int HIGHLIGHT_PADDING = 2;
	private static final long CURSOR_BLINK_MS = 500;
	private static final int CLEAR_BUTTON_SIZE = UITheme.Dimensions.ICON_SMALL;

	private final UiMinecraftClient client;
	private Runnable onEnterPressed;
	private Runnable onChanged;
	private Runnable onClearPressed;
	private UiText placeholderText;

	private boolean wasEnterDown = false;
	private boolean wasClearButtonMouseDown = false;

	public CustomTextField(UiMinecraftClient client, int x, int y, int width, int height, UiText text) {
		super(client, x, y, width, height, text);
		this.client = client;
		this.setMaxLength(256);
		this.setBordered(false);
		this.setCanLoseFocus(true);
	}

	private void onChanged() {
		if(onChanged != null) {
			onChanged.run();
		}
	}

	@Override
	public void insertText(String text) {
		super.insertText(text);
		onChanged();
	}

	@Override
	public void deleteCharsToPos(final int pos) {
		super.deleteCharsToPos(pos);
		onChanged();
	}

	public void setOnEnterPressed(Runnable callback) {
		this.onEnterPressed = callback;
	}

	public void setOnChanged(Runnable callback) {
		this.onChanged = callback;
	}

	public void setOnClearPressed(Runnable callback) {
		this.onClearPressed = callback;
	}

	public void setHint(UiText placeholder) {
		super.setHint(placeholder.nativeComponent());
		this.placeholderText = placeholder;
	}

	private boolean isOverClearButton(int mouseX, int mouseY) {
		if (this.getValue().isEmpty()) return false;
		int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
		int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
		return mouseX >= clearX && mouseX < clearX + CLEAR_BUTTON_SIZE &&
		       mouseY >= clearY && mouseY < clearY + CLEAR_BUTTON_SIZE;
	}

	@Override
	protected void renderWidget(UiRenderContext context, int mouseX, int mouseY, float delta) {
		handleMouseInput(mouseX, mouseY);
		handleKeyboardInput();

		drawBackground(context);
		drawBorder(context);
		drawTextContent(context);
		drawClearButton(context, mouseX, mouseY);
	}

	private void handleMouseInput(int mouseX, int mouseY) {
		long windowHandle = client.windowHandle();
		if (windowHandle == 0) {
			wasClearButtonMouseDown = false;
			return;
		}

		boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if (!this.getValue().isEmpty() && isMouseDown && !wasClearButtonMouseDown && isOverClearButton(mouseX, mouseY)) {
			this.setValue("");
			onChanged();
			if (onClearPressed != null) {
				onClearPressed.run();
			}
		}

		wasClearButtonMouseDown = isMouseDown;
	}

	private void handleKeyboardInput() {
		long windowHandle = client.windowHandle();
		if (windowHandle == 0) return;

		handleEnterKey(windowHandle);
	}

	private void handleEnterKey(long windowHandle) {
		boolean isEnterDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
				GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;

		if (this.isFocused() && onEnterPressed != null && isEnterDown && !wasEnterDown) {
			onEnterPressed.run();
		}

		wasEnterDown = isEnterDown;
	}

	private void drawBackground(UiRenderContext context) {
		RenderUtil.fillRect(context, this.getX(), this.getY(),
				this.getX() + this.getWidth(), this.getY() + this.getHeight(),
				UITheme.Colors.FIELD_BG);
	}

	private void drawBorder(UiRenderContext context) {
		int borderColor = this.isFocused() ? UITheme.Colors.FIELD_BORDER_FOCUSED : UITheme.Colors.FIELD_BORDER;
		int borderWidth = UITheme.Dimensions.BORDER_WIDTH;
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		RenderUtil.fillRect(context, x, y, x + width, y + borderWidth, borderColor);
		RenderUtil.fillRect(context, x, y + height - borderWidth, x + width, y + height, borderColor);
		RenderUtil.fillRect(context, x, y, x + borderWidth, y + height, borderColor);
		RenderUtil.fillRect(context, x + width - borderWidth, y, x + width, y + height, borderColor);
	}

	private void drawTextContent(UiRenderContext context) {
		int textY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2;
		int textX = this.getX() + TEXT_PADDING;
		int maxTextWidth = this.getWidth() - TEXT_PADDING * 2 - (this.getValue().isEmpty() ? 0 : CLEAR_BUTTON_SIZE + 4);

		String text = this.getValue();
		if (text.isEmpty() && !this.isFocused()) {
			drawPlaceholder(context, textX, textY);
		} else {
			drawActiveText(context, text, textX, textY, maxTextWidth);
			drawTextHighlight(context, text, textX, textY);
		}
	}

	private void drawPlaceholder(UiRenderContext context, int x, int y) {
		if (placeholderText != null) {
			RenderUtil.drawString(context, client.uiFont(), placeholderText.string(), x, y, UITheme.Colors.TEXT_MUTED);
		}
	}

	private void drawActiveText(UiRenderContext context, String text, int textX, int textY, int maxTextWidth) {
		int color = this.isFocused() ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_SUBTITLE;

		RenderUtil.enableScissor(context, textX, this.getY(), textX + maxTextWidth, this.getY() + this.getHeight());
		RenderUtil.drawString(context, client.uiFont(), text, textX, textY, color);
		RenderUtil.disableScissor(context);

		if (this.isFocused() && this.canConsumeInput()) {
			drawCursor(context, text, textX, textY);
		}
	}

	private void drawTextHighlight(UiRenderContext context, String text, int textX, int textY) {
		int highlightPos = ((EditBoxAccessor) this).getHighlightPos();
		int cursorPos = this.getCursorPosition();

		if(highlightPos == cursorPos) {
			return;
		}

		int highlightStartPos = Math.min(cursorPos, highlightPos);
		String beforeStartText = text.substring(0, highlightStartPos);
		int beforeStartWidth = client.font().width(beforeStartText);

		String highlightedText = this.getHighlighted();
		int highlightedWidth = client.font().width(highlightedText);

		int highlightStart = textX + beforeStartWidth;

        RenderUtil.textHighlight(
				context,
				highlightStart,
				textY - HIGHLIGHT_PADDING,
				highlightStart + highlightedWidth,
				textY + UITheme.Typography.TEXT_HEIGHT + HIGHLIGHT_PADDING
		);
	}

	private void drawCursor(UiRenderContext context, String text, int textX, int textY) {
		if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
			int cursorPos = this.getCursorPosition();
			String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
			int cursorX = textX + client.font().width(beforeCursor);
			RenderUtil.fillRect(context, cursorX, textY - 1, cursorX + UITheme.Dimensions.BORDER_WIDTH, textY + 9, UITheme.Colors.TEXT_PRIMARY);
		}
	}

	private void drawClearButton(UiRenderContext context, int mouseX, int mouseY) {
		if (this.getValue().isEmpty()) return;

		int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
		int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
		boolean isHovered = isOverClearButton(mouseX, mouseY);
		int clearColor = isHovered ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_MUTED;

		String xSymbol = "✕";
		int xWidth = client.font().width(xSymbol);
		int xX = clearX + (CLEAR_BUTTON_SIZE - xWidth) / 2;
		int xY = clearY + (CLEAR_BUTTON_SIZE - UITheme.Typography.TEXT_HEIGHT) / 2;
		RenderUtil.drawString(context, client.uiFont(), xSymbol, xX, xY, clearColor);
	}
}
