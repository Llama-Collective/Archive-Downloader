package com.andrews.archivedownloader.gui.widget;

import org.lwjgl.glfw.GLFW;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class CustomTextField extends TextFieldWidget {
	private static final long KEY_INITIAL_DELAY = 400;
	private static final long KEY_REPEAT_DELAY = 50;
	private static final int TEXT_PADDING = 4;
	private static final long CURSOR_BLINK_MS = 500;
	private static final int CLEAR_BUTTON_SIZE = UITheme.Dimensions.ICON_SMALL;

	private final MinecraftClient client;
	private Runnable onEnterPressed;
	private Runnable onChanged;
	private Runnable onClearPressed;
	private Text placeholderText;

	private boolean wasEnterDown = false;
	private boolean wasClearButtonMouseDown = false;

	private static CustomTextField activeField = null;
	private static boolean callbackInstalled = false;
	private static long installedWindowHandle = 0;

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

	public CustomTextField(MinecraftClient client, int x, int y, int width, int height, Text text) {
		super(client.textRenderer, x, y, width, height, text);
		this.client = client;
		this.setMaxLength(256);
		this.setDrawsBackground(false);
		this.setFocusUnlocked(true);
	}

	@Override
	public void write(String text) {
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
		long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;
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
		callbackInstalled = true;
		installedWindowHandle = windowHandle;
	}

	private void onCharTyped(char c) {
		if (c < 32) {
			return;
		}

		String currentText = this.getText();
		int cursorPos = this.getCursor();

		if (currentText.length() < 256) {
			String newText = currentText.substring(0, cursorPos) + c + currentText.substring(cursorPos);
			this.setText(newText);
			this.setCursor(cursorPos + 1, false);
			if (onChanged != null) {
				onChanged.run();
			}
		}
	}

	@Override
	public void setPlaceholder(Text placeholder) {
		super.setPlaceholder(placeholder);
		this.placeholderText = placeholder;
	}

	private boolean isOverClearButton(int mouseX, int mouseY) {
		if (this.getText().isEmpty()) return false;
		int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
		int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
		return mouseX >= clearX && mouseX < clearX + CLEAR_BUTTON_SIZE &&
		       mouseY >= clearY && mouseY < clearY + CLEAR_BUTTON_SIZE;
	}

	@Override
	public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		handleMouseInput(mouseX, mouseY);
		handleKeyboardInput();

		drawBackground(context);
		drawBorder(context);
		drawTextContent(context, mouseX, mouseY);
		drawClearButton(context, mouseX, mouseY);
	}

	private void handleMouseInput(int mouseX, int mouseY) {
		long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;
		if (windowHandle == 0) {
			wasClearButtonMouseDown = false;
			return;
		}

		boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if (!this.getText().isEmpty() && isMouseDown && !wasClearButtonMouseDown && isOverClearButton(mouseX, mouseY)) {
			this.setText("");
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
		long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;
		if (windowHandle == 0) return;

		handleEnterKey(windowHandle);

		if (this.isFocused()) {
			handleSpecialKeys(windowHandle);
		}
	}

	private void handleEnterKey(long windowHandle) {
		boolean isEnterDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
				GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;

		if (this.isFocused() && onEnterPressed != null && isEnterDown && !wasEnterDown) {
			onEnterPressed.run();
		}

		wasEnterDown = isEnterDown;
	}

	private void drawBackground(DrawContext context) {
		RenderUtil.fillRect(context, this.getX(), this.getY(),
				this.getX() + this.getWidth(), this.getY() + this.getHeight(),
				UITheme.Colors.FIELD_BG);
	}

	private void drawBorder(DrawContext context) {
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

	private void drawTextContent(DrawContext context, int mouseX, int mouseY) {
		int textY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2;
		int textX = this.getX() + TEXT_PADDING;
		int maxTextWidth = this.getWidth() - TEXT_PADDING * 2 - (this.getText().isEmpty() ? 0 : CLEAR_BUTTON_SIZE + 4);

		String text = this.getText();
		if (text.isEmpty() && !this.isFocused()) {
			drawPlaceholder(context, textX, textY);
		} else {
			drawActiveText(context, text, textX, textY, maxTextWidth);
		}
	}

	private void drawPlaceholder(DrawContext context, int x, int y) {
		if (placeholderText != null) {
			RenderUtil.drawString(context, client.textRenderer, placeholderText, x, y, UITheme.Colors.TEXT_MUTED);
		}
	}

	private void drawActiveText(DrawContext context, String text, int textX, int textY, int maxTextWidth) {
		int color = this.isFocused() ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_SUBTITLE;

		RenderUtil.enableScissor(context, textX, this.getY(), textX + maxTextWidth, this.getY() + this.getHeight());
		RenderUtil.drawString(context, client.textRenderer, text, textX, textY, color);
		RenderUtil.disableScissor(context);

		if (this.isFocused() && this.isActive()) {
			drawCursor(context, text, textX, textY);
		}
	}

	private void drawCursor(DrawContext context, String text, int textX, int textY) {
		if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
			int cursorPos = this.getCursor();
			String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
			int cursorX = textX + client.textRenderer.getWidth(beforeCursor);
			RenderUtil.fillRect(context, cursorX, textY - 1, cursorX + UITheme.Dimensions.BORDER_WIDTH, textY + 9, UITheme.Colors.TEXT_PRIMARY);
		}
	}

	private void drawClearButton(DrawContext context, int mouseX, int mouseY) {
		if (this.getText().isEmpty()) return;

		int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
		int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
		boolean isHovered = isOverClearButton(mouseX, mouseY);
		int clearColor = isHovered ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_MUTED;

		String xSymbol = "✕";
		int xWidth = client.textRenderer.getWidth(xSymbol);
		int xX = clearX + (CLEAR_BUTTON_SIZE - xWidth) / 2;
		int xY = clearY + (CLEAR_BUTTON_SIZE - UITheme.Typography.TEXT_HEIGHT) / 2;
		RenderUtil.drawString(context, client.textRenderer, xSymbol, xX, xY, clearColor);
	}

	private void handleSpecialKeys(long windowHandle) {
		long currentTime = System.currentTimeMillis();
		String currentText = this.getText();
		int cursorPos = this.getCursor();

		boolean ctrlDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
			|| GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
		boolean superDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
			|| GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
		boolean shiftDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
			|| GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		boolean isVDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
		boolean insertDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_INSERT) == GLFW.GLFW_PRESS;
		boolean pastePressed = (ctrlDown || superDown) && isVDown || (shiftDown && insertDown);
		if (pastePressed && !wasPastePressed) {
			String clipboard = GLFW.glfwGetClipboardString(windowHandle);
			if (clipboard != null && !clipboard.isEmpty()) {
				String insert = clipboard.replace("\r", "").replace("\n", "");
				int allowed = Math.max(0, 256 - currentText.length());
				if (!insert.isEmpty() && allowed > 0) {
					if (insert.length() > allowed) {
						insert = insert.substring(0, allowed);
					}
					String newText = currentText.substring(0, cursorPos) + insert + currentText.substring(cursorPos);
					this.setText(newText);
					this.setCursor(cursorPos + insert.length(), false);
					if (onChanged != null) {
						onChanged.run();
					}
				}
			}
		}
		wasPastePressed = pastePressed;

		boolean isBackspaceDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS;
		if (backspaceState.shouldTrigger(currentTime, isBackspaceDown) && cursorPos > 0) {
			String newText = currentText.substring(0, cursorPos - 1) + currentText.substring(cursorPos);
			this.setText(newText);
			this.setCursor(cursorPos - 1, false);
			if (onChanged != null) {
				onChanged.run();
			}
		}

		boolean isDeleteDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
		if (deleteState.shouldTrigger(currentTime, isDeleteDown) && cursorPos < currentText.length()) {
			String newText = currentText.substring(0, cursorPos) + currentText.substring(cursorPos + 1);
			this.setText(newText);
			if (onChanged != null) {
				onChanged.run();
			}
		}

		boolean isLeftDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
		if (leftState.shouldTrigger(currentTime, isLeftDown) && cursorPos > 0) {
			this.setCursor(cursorPos - 1, false);
		}

		boolean isRightDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
		if (rightState.shouldTrigger(currentTime, isRightDown) && cursorPos < currentText.length()) {
			this.setCursor(cursorPos + 1, false);
		}

		boolean isHomeDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_HOME) == GLFW.GLFW_PRESS;
		if (isHomeDown && !wasHomePressed) {
			this.setCursor(0, false);
		}
		wasHomePressed = isHomeDown;

		boolean isEndDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_END) == GLFW.GLFW_PRESS;
		if (isEndDown && !wasEndPressed) {
			this.setCursor(currentText.length(), false);
		}
		wasEndPressed = isEndDown;

		boolean isEscapeDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
		if (isEscapeDown) {
			this.setFocused(false);
		}
	}
}
