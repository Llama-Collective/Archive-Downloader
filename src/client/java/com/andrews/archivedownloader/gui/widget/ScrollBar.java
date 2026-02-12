package com.andrews.archivedownloader.gui.widget;

import org.lwjgl.glfw.GLFW;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;

public class ScrollBar implements UiRenderable {
    private static final int MIN_HANDLE_HEIGHT = 20;

    private final int x;
    private final int y;
    private final int height;
    private double scrollPercentage = 0.0;
    private double contentHeight = 0.0;
    private double visibleHeight = 0.0;
    private boolean isDragging = false;
    private double dragStartY = 0;
    private double dragStartScroll = 0;
    private boolean isHovered = false;
    private boolean wasMouseDown = false;

    public ScrollBar(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }
    
    public void setScrollData(double contentHeight, double visibleHeight) {
        this.contentHeight = contentHeight;
        this.visibleHeight = visibleHeight;
    }
    
    public void setScrollPercentage(double percentage) {
        this.scrollPercentage = Math.max(0.0, Math.min(1.0, percentage));
    }
    
    public double getScrollPercentage() {
        return scrollPercentage;
    }
    
    public boolean isVisible() {
        return contentHeight > visibleHeight;
    }
    
    public boolean isDragging() {
        return isDragging;
    }

    private double getHandleHeight() {
        return Math.max(MIN_HANDLE_HEIGHT, (visibleHeight / contentHeight) * height);
    }

    private double getMaxHandleY() {
        return height - getHandleHeight();
    }

    private double getHandleY() {
        return y + (scrollPercentage * getMaxHandleY());
    }

    private boolean isMouseOverHandle(int mouseX, int mouseY) {
        double handleHeight = getHandleHeight();
        double handleY = getHandleY();
        return mouseX >= x && mouseX < x + UITheme.Dimensions.SCROLLBAR_WIDTH &&
               mouseY >= handleY && mouseY < handleY + handleHeight;
    }

    private boolean isMouseOverTrack(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + UITheme.Dimensions.SCROLLBAR_WIDTH &&
               mouseY >= y && mouseY < y + height;
    }

    public boolean updateAndRender(UiRenderContext context, int mouseX, int mouseY, float delta, long windowHandle) {
        if (!isVisible()) return false;

        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        double handleHeight = getHandleHeight();
        double maxHandleY = getMaxHandleY();

        isHovered = isMouseOverHandle(mouseX, mouseY);
        boolean isOverTrack = isMouseOverTrack(mouseX, mouseY);
        boolean scrollChanged = false;

        if (isMouseDown && !wasMouseDown) {
            if (isHovered) {
                isDragging = true;
                dragStartY = mouseY;
                dragStartScroll = scrollPercentage;
            } else if (isOverTrack) {
                double clickPositionInTrack = mouseY - y - (handleHeight / 2);
                double newPercentage = Math.max(0.0, Math.min(1.0, clickPositionInTrack / maxHandleY));
                if (newPercentage != scrollPercentage) {
                    scrollPercentage = newPercentage;
                    scrollChanged = true;
                }
                isDragging = true;
                dragStartY = mouseY;
                dragStartScroll = scrollPercentage;
            }
        }

        if (isDragging && isMouseDown) {
            double deltaYMouse = mouseY - dragStartY;
            double deltaScroll = deltaYMouse / maxHandleY;
            double newPercentage = Math.max(0.0, Math.min(1.0, dragStartScroll + deltaScroll));
            if (newPercentage != scrollPercentage) {
                scrollPercentage = newPercentage;
                scrollChanged = true;
            }
        }

        if (!isMouseDown && isDragging) {
            isDragging = false;
        }

        wasMouseDown = isMouseDown;

        drawScrollBar(context, handleHeight);

        return scrollChanged;
    }

    private void drawScrollBar(UiRenderContext context, double handleHeight) {
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.SCROLLBAR_WIDTH, y + height, UITheme.Colors.SCROLLBAR_BG);

        double handleY = getHandleY();
        int handleColor = (isHovered || isDragging) ? UITheme.Colors.SCROLLBAR_THUMB_HOVER : UITheme.Colors.SCROLLBAR_THUMB;
        RenderUtil.fillRect(context, (int) x, (int) handleY, (int) (x + UITheme.Dimensions.SCROLLBAR_WIDTH), (int) (handleY + handleHeight), handleColor);
    }

    @Override
    public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        isHovered = isMouseOverHandle(mouseX, mouseY);
        drawScrollBar(context, getHandleHeight());
    }

    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (!isVisible() || button != 0) return false;

        if (isMouseOverHandle((int)mouseX, (int)mouseY)) {
            isDragging = true;
            dragStartY = mouseY;
            dragStartScroll = scrollPercentage;
            return true;
        }

        if (isMouseOverTrack((int) mouseX, (int) mouseY)) {
            double handleHeight = getHandleHeight();
            double maxHandleY = getMaxHandleY();
            double clickPositionInTrack = mouseY - y - (handleHeight / 2);
            double newPercentage = Math.max(0.0, Math.min(1.0, clickPositionInTrack / maxHandleY));
            scrollPercentage = newPercentage;
            isDragging = true;
            dragStartY = mouseY;
            dragStartScroll = scrollPercentage;
            return true;
        }

        return false;
    }
    
    public boolean mouseReleased(UiMouseEvent click) {
        if (click.button() == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }
    
    public boolean mouseDragged(UiMouseEvent click, double offsetX, double offsetY) {
        // if (!isDragging) return false;
        // double mouseY = click.y();
        // double maxHandleY = getMaxHandleY();
        // double deltaYMouse = mouseY - dragStartY;
        // double deltaScroll = deltaYMouse / maxHandleY;
        // double newPercentage = Math.max(0.0, Math.min(1.0, dragStartScroll + deltaScroll));
        // scrollPercentage = newPercentage;   
        // return true;
        return false;
    }

    public int getWidth() {
        return UITheme.Dimensions.SCROLLBAR_WIDTH;
    }
}
