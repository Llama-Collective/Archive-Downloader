package com.andrews.archivedownloader.gui.widget;

//? <26.3
//import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.andrews.archivedownloader.wrapper.input.UiInput;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiTextFieldBase;
import com.andrews.archivedownloader.wrapper.text.UiText;

public class CustomTextField extends UiTextFieldBase {
	private static final long KEY_INITIAL_DELAY = 400;
	private static final long KEY_REPEAT_DELAY = 50;
	private static final int TEXT_PADDING = 4;
	private static final long CURSOR_BLINK_MS = 500;
	private static final int CLEAR_BUTTON_SIZE = UITheme.Dimensions.ICON_SMALL;

	private final UiMinecraftClient client;
	private Runnable onEnterPressed;
	private Runnable onChanged;
	private Runnable onClearPressed;
	private UiText placeholderText;

	private boolean wasEnterDown = false;
	private boolean wasClearButtonMouseDown = false;

	private static CustomTextField activeField = null;

	private final KeyRepeatState backspaceState = new KeyRepeatState();
	private final KeyRepeatState deleteState = new KeyRepeatState();
	private final KeyRepeatState leftState = new KeyRepeatState();
	private final KeyRepeatState rightState = new KeyRepeatState();
	private boolean wasHomePressed = false;
	private boolean wasEndPressed = false;
	private boolean wasPastePressed = false;

	private static class KeyRepeatState {
		boolean wasPressed = false;
		long holdStart = 0;
		long lastRepeat = 0;

		boolean shouldTrigger(long currentTime, boolean isKeyDown) {
			if (!isKeyDown) {
				wasPressed = false;
				return false;
			}

			if (!wasPressed) {
				wasPressed = true;
				holdStart = currentTime;
				lastRepeat = currentTime;
				return true;
			}

			if (currentTime - holdStart > KEY_INITIAL_DELAY && currentTime - lastRepeat > KEY_REPEAT_DELAY) {
				lastRepeat = currentTime;
				return true;
			}

			return false;
		}
	}

	public CustomTextField(UiMinecraftClient client, int x, int y, int width, int height, UiText text) {
		super(client, x, y, width, height, text);
		this.client = client;
		this.setMaxLength(256);
		this.setBordered(false);
		this.setCanLoseFocus(true);
	}

	@Override
	public void insertText(String text) {
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

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (focused) {
			activeField = this;
			installCharCallback();
		} else if (activeField == this) {
			activeField = null;
		}
	}

	private void installCharCallback() {
		//? <26.3 {
		/*long windowHandle = client.windowHandle();
		if (windowHandle == 0) return;
		// Always (re)install our char callback when focusing. Other code may replace
		// the GLFW char callback, causing typed characters to stop reaching us. By
		// reinstalling whenever a field gains focus we ensure input continues to
		// be delivered to the active field.
		GLFW.glfwSetCharCallback(windowHandle, (window, codepoint) -> {
			if (activeField != null && activeField.isFocused()) {
				activeField.onCharTyped((char) codepoint);
			}
		});
		*///? }
	}

	//? >=26.3 {
	// These fields manage focus themselves instead of using the screen child list.
	public static boolean dispatchCharacter(int codepoint) {
		if (activeField == null || !activeField.isFocused()) return false;
		for (char c : Character.toChars(codepoint)) {
			activeField.onCharTyped(c);
		}
		return true;
	}
	//? }

	private void onCharTyped(char c) {
		if (c < 32) {
			return;
		}

		String currentText = this.getValue();
		int cursorPos = this.getCursorPosition();

		if (currentText.length() < 256) {
			String newText = currentText.substring(0, cursorPos) + c + currentText.substring(cursorPos);
			this.setValue(newText);
			this.moveCursorTo(cursorPos + 1, false);
			if (onChanged != null) {
				onChanged.run();
			}
		}
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
		drawTextContent(context, mouseX, mouseY);
		drawClearButton(context, mouseX, mouseY);
	}

	private void handleMouseInput(int mouseX, int mouseY) {
		long windowHandle = client.windowHandle();
		if (windowHandle == 0) {
			wasClearButtonMouseDown = false;
			return;
		}

		boolean isMouseDown = UiInput.isLeftMouseDown(windowHandle);

		if (!this.getValue().isEmpty() && isMouseDown && !wasClearButtonMouseDown && isOverClearButton(mouseX, mouseY)) {
			this.setValue("");
			if (onChanged != null) {
				onChanged.run();
			}
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

		if (this.isFocused()) {
			handleSpecialKeys(windowHandle);
		}
	}

	private void handleEnterKey(long windowHandle) {
		boolean isEnterDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_RETURN) ||
				UiInput.isKeyDown(windowHandle, InputConstants.KEY_NUMPADENTER);

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

	private void drawTextContent(UiRenderContext context, int mouseX, int mouseY) {
		int textY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2;
		int textX = this.getX() + TEXT_PADDING;
		int maxTextWidth = this.getWidth() - TEXT_PADDING * 2 - (this.getValue().isEmpty() ? 0 : CLEAR_BUTTON_SIZE + 4);

		String text = this.getValue();
		if (text.isEmpty() && !this.isFocused()) {
			drawPlaceholder(context, textX, textY);
		} else {
			drawActiveText(context, text, textX, textY, maxTextWidth);
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

	private void handleSpecialKeys(long windowHandle) {
		long currentTime = System.currentTimeMillis();
		String currentText = this.getValue();
		int cursorPos = this.getCursorPosition();

		boolean ctrlDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_LCONTROL)
			|| UiInput.isKeyDown(windowHandle, InputConstants.KEY_RCONTROL);
		boolean superDown = UiInput.isKeyDown(windowHandle, UiInput.KEY_LEFT_SUPER)
			|| UiInput.isKeyDown(windowHandle, UiInput.KEY_RIGHT_SUPER);
		boolean shiftDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_LSHIFT)
			|| UiInput.isKeyDown(windowHandle, InputConstants.KEY_RSHIFT);
		boolean isVDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_V);
		boolean insertDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_INSERT);
		boolean pastePressed = (ctrlDown || superDown) && isVDown || (shiftDown && insertDown);
		if (pastePressed && !wasPastePressed) {
			String clipboard = client.nativeClient().keyboardHandler.getClipboard();
			if (clipboard != null && !clipboard.isEmpty()) {
				String insert = clipboard.replace("\r", "").replace("\n", "");
				int allowed = Math.max(0, 256 - currentText.length());
				if (!insert.isEmpty() && allowed > 0) {
					if (insert.length() > allowed) {
						insert = insert.substring(0, allowed);
					}
					String newText = currentText.substring(0, cursorPos) + insert + currentText.substring(cursorPos);
					this.setValue(newText);
					this.setCursorPosition(cursorPos + insert.length());
					if (onChanged != null) {
						onChanged.run();
					}
				}
			}
		}
		wasPastePressed = pastePressed;

		boolean isBackspaceDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_BACKSPACE);
		if (backspaceState.shouldTrigger(currentTime, isBackspaceDown) && cursorPos > 0) {
			String newText = currentText.substring(0, cursorPos - 1) + currentText.substring(cursorPos);
			this.setValue(newText);
			this.moveCursorTo(cursorPos - 1, false);
			if (onChanged != null) {
				onChanged.run();
			}
		}

		boolean isDeleteDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_DELETE);
		if (deleteState.shouldTrigger(currentTime, isDeleteDown) && cursorPos < currentText.length()) {
			String newText = currentText.substring(0, cursorPos) + currentText.substring(cursorPos + 1);
			this.setValue(newText);
			if (onChanged != null) {
				onChanged.run();
			}
		}

		boolean isLeftDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_LEFT);
		if (leftState.shouldTrigger(currentTime, isLeftDown) && cursorPos > 0) {
			this.moveCursorTo(cursorPos - 1, false);
		}

		boolean isRightDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_RIGHT);
		if (rightState.shouldTrigger(currentTime, isRightDown) && cursorPos < currentText.length()) {
			this.moveCursorTo(cursorPos + 1, false);
		}

		boolean isHomeDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_HOME);
		if (isHomeDown && !wasHomePressed) {
			this.moveCursorTo(0, false);
		}
		wasHomePressed = isHomeDown;

		boolean isEndDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_END);
		if (isEndDown && !wasEndPressed) {
			this.moveCursorTo(currentText.length(), false);
		}
		wasEndPressed = isEndDown;

		boolean isEscapeDown = UiInput.isKeyDown(windowHandle, InputConstants.KEY_ESCAPE);
		if (isEscapeDown) {
			this.setFocused(false);
		}
	}
}
