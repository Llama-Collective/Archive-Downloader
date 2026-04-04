package com.andrews.archivedownloader.gui.widget;

import org.lwjgl.glfw.GLFW;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.render.UiRenderPipeline;
import com.andrews.archivedownloader.wrapper.render.UiTextureId;
import com.andrews.archivedownloader.wrapper.text.UiText;

public class ImageViewerWidget {
    private static final int IMAGE_MARGIN = 40;
    private static final int NAV_AREA_BOTTOM_OFFSET = 40;
    private static final int NAV_BUTTON_WIDTH = 25;
    private static final int NAV_BUTTON_HEIGHT = 16;
    private static final int NAV_BUTTON_SPACING = UITheme.Dimensions.PADDING;
    private static final int TEXT_VERTICAL_OFFSET = 4;
    private static final int NAV_BG_COLOR = 0xD0000000;

    private final UiTextureId imageTexture;
    private final int originalImageWidth;
    private final int originalImageHeight;
    private final Runnable onClose;
    private final UiMinecraftClient client;
    private final int currentImageIndex;
    private final int totalImages;
    private final Runnable onPrevious;
    private final Runnable onNext;

    private int screenWidth;
    private int screenHeight;
    private CustomButton closeButton;
    private CustomButton prevButton;
    private CustomButton nextButton;


    public ImageViewerWidget(UiMinecraftClient client, UiTextureId imageTexture,
                            int originalImageWidth, int originalImageHeight,
                            int currentImageIndex, int totalImages,
                            Runnable onPrevious, Runnable onNext, Runnable onClose) {
        this.client = client;
        this.imageTexture = imageTexture;
        this.originalImageWidth = originalImageWidth;
        this.originalImageHeight = originalImageHeight;
        this.currentImageIndex = currentImageIndex;
        this.totalImages = totalImages;
        this.onPrevious = onPrevious;
        this.onNext = onNext;
        this.onClose = onClose;

        updateLayout(client.guiScaledWidth(), client.guiScaledHeight());
    }

    public void updateLayout(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        updateCloseButton();

        if (totalImages > 1) {
            updateNavigationButtons();
        }
    }

    private void updateCloseButton() {
        int closeX = screenWidth - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING;
        int closeY = UITheme.Dimensions.PADDING;

        if (closeButton == null) {
            closeButton = new CustomButton(closeX, closeY, UITheme.Dimensions.BUTTON_HEIGHT,
                    UITheme.Dimensions.BUTTON_HEIGHT, UiText.of("×"), btn -> onClose.run());
            closeButton.setRenderAsXIcon(true);
        } else {
            setButtonBounds(closeButton, closeX, closeY, UITheme.Dimensions.BUTTON_HEIGHT, UITheme.Dimensions.BUTTON_HEIGHT);
        }
    }

    private void updateNavigationButtons() {
        String indicator = String.format("%d / %d", currentImageIndex + 1, totalImages);
        int indicatorWidth = client.font().width(indicator);
        int indicatorX = (screenWidth - indicatorWidth) / 2;
        int navY = screenHeight - NAV_AREA_BOTTOM_OFFSET;

        int prevX = indicatorX - NAV_BUTTON_WIDTH - NAV_BUTTON_SPACING;
        if (prevButton == null) {
            prevButton = new CustomButton(prevX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                    UiText.of("<"), btn -> onPrevious.run());
        } else {
            setButtonBounds(prevButton, prevX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT);
        }

        int nextX = indicatorX + indicatorWidth + NAV_BUTTON_SPACING;
        if (nextButton == null) {
            nextButton = new CustomButton(nextX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                    UiText.of(">"), btn -> onNext.run());
        } else {
            setButtonBounds(nextButton, nextX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT);
        }
    }

    private void setButtonBounds(CustomButton button, int x, int y, int width, int height) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(height);
    }

    private int[] getScaledImageDimensions() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return new int[]{screenWidth, screenHeight};
        }

        int maxWidth = screenWidth - IMAGE_MARGIN;
        int maxHeight = screenHeight - IMAGE_MARGIN;

        float imageAspect = (float) originalImageWidth / originalImageHeight;
        float screenAspect = (float) maxWidth / maxHeight;

        int displayWidth, displayHeight;

        if (imageAspect > screenAspect) {
            displayWidth = Math.min(maxWidth, originalImageWidth);
            displayHeight = (int) (displayWidth / imageAspect);
        } else {
            displayHeight = Math.min(maxHeight, originalImageHeight);
            displayWidth = (int) (displayHeight * imageAspect);
        }

        return new int[]{displayWidth, displayHeight};
    }

    public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        RenderUtil.fillRect(context, 0, 0, screenWidth, screenHeight, UITheme.Colors.OVERLAY_BG);

        renderImage(context);

        if (totalImages > 1) {
            renderNavigation(context, mouseX, mouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }
    }
    private void renderImage(UiRenderContext context) {
        int[] dimensions = getScaledImageDimensions();
        int displayWidth = dimensions[0];
        int displayHeight = dimensions[1];

        int imageX = (screenWidth - displayWidth) / 2;
        int imageY = (screenHeight - displayHeight) / 2;

        RenderUtil.fillRect(context, 0, 0, screenWidth, screenHeight, UITheme.Colors.PANEL_BG);

        if (imageTexture != null) {
            RenderUtil.blit(
                context,
                UiRenderPipeline.GUI_TEXTURED,
                imageTexture,
                imageX, imageY,
                0, 0,
                displayWidth, displayHeight,
                displayWidth, displayHeight
            );
        }
    }

    private void renderNavigation(UiRenderContext context, int mouseX, int mouseY, float delta) {
        String pageText = String.format("%d / %d", currentImageIndex + 1, totalImages);
        int textWidth = client.font().width(pageText);
        int textX = (screenWidth - textWidth) / 2;
        int textY = screenHeight - NAV_AREA_BOTTOM_OFFSET + TEXT_VERTICAL_OFFSET;

        int indicatorX = textX;
        int prevBtnX = indicatorX - NAV_BUTTON_WIDTH - NAV_BUTTON_SPACING;
        int nextBtnX = indicatorX + textWidth + NAV_BUTTON_SPACING;
        int navY = screenHeight - NAV_AREA_BOTTOM_OFFSET;

        drawNavigationBackground(context, prevBtnX, navY, nextBtnX);

        RenderUtil.drawString(context, client.uiFont(), pageText, textX, textY, UITheme.Colors.TEXT_SUBTITLE);

        if (prevButton != null) {
            prevButton.render(context, mouseX, mouseY, delta);
        }
        if (nextButton != null) {
            nextButton.render(context, mouseX, mouseY, delta);
        }
    }

    private void drawNavigationBackground(UiRenderContext context, int prevBtnX, int navY, int nextBtnX) {
        int bgX = prevBtnX;
        int bgWidth = (nextBtnX + NAV_BUTTON_WIDTH) - prevBtnX;
        int bgY = navY;
        int bgHeight = NAV_BUTTON_HEIGHT;

        RenderUtil.fillRect(context, bgX, bgY, bgX + bgWidth, bgY + bgHeight, NAV_BG_COLOR);
    }

    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) return false;

        if (isOverButton(closeButton, mouseX, mouseY)) {
            onClose.run();
            return true;
        }

        if (totalImages > 1) {
            if (isOverButton(prevButton, mouseX, mouseY)) {
                onPrevious.run();
                return true;
            }

            if (isOverButton(nextButton, mouseX, mouseY)) {
                onNext.run();
                return true;
            }
        }

        onClose.run();
        return true;
    }

    private boolean isOverButton(CustomButton btn, double mouseX, double mouseY) {
        if (btn == null) return false;
        return mouseX >= btn.getX() &&
               mouseX < btn.getX() + btn.getWidth() &&
               mouseY >= btn.getY() &&
               mouseY < btn.getY() + btn.getHeight();
    }
    
    public boolean mouseReleased(UiMouseEvent click) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose.run();
            return true;
        }

        if (totalImages > 1) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                onPrevious.run();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                onNext.run();
                return true;
            }
        }

        return false;
    }
}
